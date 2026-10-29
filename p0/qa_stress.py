# -*- coding: utf-8 -*-
"""R8 主动压测：构造重复 publish/loadWindow 场景，核验触发器是否去重。
以配置账号 张晋宝 登录（application.yml 的 itwb.auth），在 :18077 上：
1) 创建 INTERVAL(2min) 测试任务 -> 触发第 1 次 loadWindow；
2) 连续 4 次 no-op 更新 -> 再触发 4 次 loadWindow（共 5 次，复刻 R8 触发路径）；
3) 等待首次自然触发；
4) 直接查 Oracle SCHED_TODO / SCHED_FIRE_LOG 核验：是否重复触发 / 未来时刻待办 / FAILED。
仅读+创建+删除测试任务；测试任务用完即 DELETE（待办/执行日志行保留，按 id 可清理）。
"""
import json, time, subprocess, urllib.request, urllib.error

BASE = "http://localhost:18077"
SQLPLUS = "G:/app/Administrator/product/11.2.0/client_2/bin/sqlplus.exe"
DB = "jddba/xingx888@//172.16.253.180:1521/orcl"
USER, PWD = "张晋宝", "itwb@2026"
TASK_NAME = "QA-R8-STRESS"


def login(u, p):
    req = urllib.request.Request(BASE + "/api/auth/login",
        data=json.dumps({"username": u, "password": p}, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))["data"]["token"]


def apipost(tok, path, body):
    req = urllib.request.Request(BASE + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json; charset=utf-8"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))


def apiput(tok, path, body):
    req = urllib.request.Request(BASE + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json; charset=utf-8"}, method="PUT")
    return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))


def apiget(tok, path):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + tok}, method="GET")
    return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))


def run_sql(sql):
    p = subprocess.run([SQLPLUS, "-S", DB], input=sql.encode("utf-8"),
                      stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    return p.stdout.decode("utf-8", "replace")


def sql_count(task_id):
    sql = (
        "SET PAGESIZE 0 FEEDBACK OFF\n"
        "SELECT 'TODOS='||(SELECT COUNT(*) FROM SCHED_TODO WHERE SOURCE_TASK_ID=%d) ||\n"
        "       ' FIRES='||(SELECT COUNT(*) FROM SCHED_FIRE_LOG WHERE TASK_ID=%d) ||\n"
        "       ' FAILED='||(SELECT COUNT(*) FROM SCHED_FIRE_LOG WHERE TASK_ID=%d AND STATUS='FAILED')\n"
        "FROM DUAL;\nEXIT;\n" % (task_id, task_id, task_id)
    )
    return run_sql(sql)


def sql_detail(task_id):
    sql = (
        "SET PAGESIZE 200 LINESIZE 200 FEEDBACK OFF\n"
        "PROMPT == TODOS ==\n"
        "SELECT TODO_ID, TO_CHAR(SCHEDULED_FIRE_TIME,'YYYY-MM-DD HH24:MI:SS') SFT, TO_CHAR(CREATED_DATE,'YYYY-MM-DD') CD, TITLE\n"
        "FROM SCHED_TODO WHERE SOURCE_TASK_ID=%d ORDER BY SCHEDULED_FIRE_TIME;\n" % task_id
        + "PROMPT == FIRE_LOGS (minute-truncated dedup check) ==\n"
        "SELECT TO_CHAR(SCHEDULED_FIRE_TIME,'YYYY-MM-DD HH24:MI') SFT, COUNT(*) C\n"
        "FROM SCHED_FIRE_LOG WHERE TASK_ID=%d GROUP BY TO_CHAR(SCHEDULED_FIRE_TIME,'YYYY-MM-DD HH24:MI') HAVING COUNT(*)>1;\n" % task_id
        + "PROMPT == FIRE_LOG rows ==\n"
        "SELECT FIRE_ID, TO_CHAR(SCHEDULED_FIRE_TIME,'MM-DD HH24:MI:SS') SFT, TO_CHAR(ACTUAL_FIRE_TIME,'MM-DD HH24:MI:SS') AFT, STATUS\n"
        "FROM SCHED_FIRE_LOG WHERE TASK_ID=%d ORDER BY ACTUAL_FIRE_TIME;\nEXIT;\n" % task_id
    )
    return run_sql(sql)


def main():
    out = {}
    tok = login(USER, PWD)
    out["login"] = "ok"

    # 1) create test task (第1次 loadWindow)
    c = apipost(tok, "/api/sched/tasks",
                {"name": TASK_NAME, "content": "QA压测任务", "mode": "INTERVAL", "intervalMin": 2})
    task_id = c.get("data")
    out["created_task_id"] = task_id

    # 2) 4 次 no-op 更新 -> 再触发 4 次 loadWindow
    loads = 1
    for i in range(4):
        apiput(tok, "/api/sched/tasks/%d" % task_id, {"content": "QA压测任务"})
        loads += 1
        time.sleep(0.4)
    out["load_window_triggers"] = loads

    t0 = time.time()
    out["start_wait"] = time.strftime("%Y-%m-%d %H:%M:%S")

    # 3) poll until first fire detected (+buffer), up to ~200s
    first_fire_seen = False
    for _ in range(20):
        time.sleep(10)
        res = sql_count(task_id)
        if "FIRES=0" not in res and "FIRES=" in res:
            # parse FIRES count
            try:
                fires = int([l for l in res.splitlines() if l.startswith("TODOS=")][0].split("FIRES=")[1].split()[0])
            except Exception:
                fires = 0
            if fires >= 1:
                first_fire_seen = True
                time.sleep(25)  # buffer to capture any same-minute cascade
                break
    out["first_fire_seen"] = first_fire_seen
    out["waited_sec"] = int(time.time() - t0)

    # 4) verify
    out["count_snapshot"] = sql_count(task_id)
    out["detail"] = sql_detail(task_id)

    # 5) cleanup: delete test task (best effort)
    try:
        apipost(tok, "/api/sched/tasks/%d/disable" % task_id, {})
        apiget  # noop
    except Exception:
        pass
    try:
        d = apipost(tok, "/api/sched/tasks/%d/delete" % task_id, {}) if False else None
        # DELETE uses delete method
        req = urllib.request.Request(BASE + "/api/sched/tasks/%d" % task_id,
            headers={"Authorization": "Bearer " + tok}, method="DELETE")
        urllib.request.urlopen(req, timeout=15)
        out["deleted"] = True
    except Exception as e:
        out["deleted"] = "err:%s" % e

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
