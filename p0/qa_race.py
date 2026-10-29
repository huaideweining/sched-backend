# -*- coding: utf-8 -*-
"""R8 并发竞态压测：持续并发触发 loadWindow（多线程反复 no-op 更新），
同时任务按 INTERVAL(1min) 自然触发，观察 TimingWheel.schedule() 非原子去重是否会被击穿
-> 产生重复触发 / 未来时刻待办 / FAILED。仅读+创建+删除测试任务。
"""
import json, time, subprocess, threading, urllib.request, urllib.error

BASE = "http://localhost:18077"
SQLPLUS = "G:/app/Administrator/product/11.2.0/client_2/bin/sqlplus.exe"
DB = "jddba/xingx888@//172.16.253.180:1521/orcl"
USER, PWD = "张晋宝", "itwb@2026"
NAME = "QA-R8-RACE"
THREADS = 5
DURATION = 150  # 秒


def login(u, p):
    req = urllib.request.Request(BASE + "/api/auth/login",
        data=json.dumps({"username": u, "password": p}, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"}, method="POST")
    return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))["data"]["token"]


def mkput(tok, tid):
    def loop():
        end = time.time() + DURATION
        while time.time() < end:
            try:
                req = urllib.request.Request(BASE + "/api/sched/tasks/%d" % tid,
                    data=json.dumps({"content": "QA并发压测"}, ensure_ascii=False).encode("utf-8"),
                    headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json; charset=utf-8"}, method="PUT")
                urllib.request.urlopen(req, timeout=5).read()
            except Exception:
                pass
            time.sleep(0.08)
    return loop


def run_sql(sql):
    p = subprocess.run([SQLPLUS, "-S", DB], input=sql.encode("utf-8"),
                      stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    return p.stdout.decode("utf-8", "replace")


def main():
    out = {}
    tok = login(USER, PWD)
    c = json.loads(urllib.request.urlopen(urllib.request.Request(BASE + "/api/sched/tasks",
        data=json.dumps({"name": NAME, "content": "QA并发压测", "mode": "INTERVAL", "intervalMin": 1}, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json; charset=utf-8"}, method="POST")).read().decode("utf-8"))
    tid = c.get("data")
    out["task_id"] = tid
    out["start"] = time.strftime("%Y-%m-%d %H:%M:%S")

    threads = [threading.Thread(target=mkput(tok, tid)) for _ in range(THREADS)]
    for t in threads: t.start()
    for t in threads: t.join()
    time.sleep(20)  # 等待在途触发落库
    out["end"] = time.strftime("%Y-%m-%d %H:%M:%S")

    sql = (
        "SET PAGESIZE 200 LINESIZE 220 FEEDBACK OFF\n"
        "PROMPT == summary ==\n"
        "SELECT 'TODOS='||(SELECT COUNT(*) FROM SCHED_TODO WHERE SOURCE_TASK_ID=%d)||' FIRES='||(SELECT COUNT(*) FROM SCHED_FIRE_LOG WHERE TASK_ID=%d)||' FAILED='||(SELECT COUNT(*) FROM SCHED_FIRE_LOG WHERE TASK_ID=%d AND STATUS='FAILED') FROM DUAL;\n" % (tid, tid, tid)
        + "PROMPT == dup fire groups (same minute) ==\n"
        "SELECT TO_CHAR(SCHEDULED_FIRE_TIME,'MM-DD HH24:MI') SFT, COUNT(*) C FROM SCHED_FIRE_LOG WHERE TASK_ID=%d GROUP BY TO_CHAR(SCHEDULED_FIRE_TIME,'MM-DD HH24:MI') HAVING COUNT(*)>1;\n" % tid
        + "PROMPT == future-moment todos ==\n"
        "SELECT TODO_ID, TO_CHAR(SCHEDULED_FIRE_TIME,'YYYY-MM-DD HH24:MI') SFT, TO_CHAR(CREATED_DATE,'YYYY-MM-DD') CD FROM SCHED_TODO WHERE SOURCE_TASK_ID=%d AND TRUNC(SCHEDULED_FIRE_TIME)<>CREATED_DATE;\n" % tid
        + "PROMPT == fire rows ==\n"
        "SELECT FIRE_ID, TO_CHAR(SCHEDULED_FIRE_TIME,'MM-DD HH24:MI:SS') SFT, TO_CHAR(ACTUAL_FIRE_TIME,'MM-DD HH24:MI:SS') AFT, STATUS FROM SCHED_FIRE_LOG WHERE TASK_ID=%d ORDER BY ACTUAL_FIRE_TIME;\nEXIT;\n" % tid
    )
    out["oracle"] = run_sql(sql)

    # cleanup
    try:
        req = urllib.request.Request(BASE + "/api/sched/tasks/%d" % tid, headers={"Authorization": "Bearer " + tok}, method="DELETE")
        urllib.request.urlopen(req, timeout=15); out["deleted"] = True
    except Exception as e:
        out["deleted"] = "err:%s" % e

    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
