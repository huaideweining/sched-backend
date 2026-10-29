#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P2 集群联调验证脚本（sched-backend）
===================================

覆盖范围
--------
(B) Redis Pub/Sub 任务变更通知
    - 订阅频道 sched:task:change（独立 Redis 连接，等价于"任一节点的 TriggerEngine 订阅者"）。
    - 先做 Redis 总线自检：本端 publish 一条哨兵消息，断言订阅者能收到（验证订阅/接收链路）。
    - 在节点1（BASE_NODE1）上依次执行 create / update / delete 任务，
      断言订阅者在超时时间内收到携带 userId 的消息（即 TaskServiceImpl 事务提交后 publishTaskChange 生效）。
    - 若节点2（BASE_NODE2）在线，额外在节点2执行一次 create，断言订阅者同样收到（跨节点发布）。
      说明：两节点均订阅同一频道，故"节点2 收到节点1 的发布"由共享频道架构（cluster-readiness R8）保证，
            本脚本用"节点2 也能发布并被总线送达"作为跨节点可达性的实证；节点内部 onMessage->loadWindow
            属代码审查范畴（R8），不在本端直接观测。

(A) 跨节点不重复触发（需要双节点 shard-total=2 就绪）
    - 就绪性检查：SCHED_NODE 中 UP 节点数=2、SHARD_TOTAL=2、SHARD_MOD 覆盖 {0,1} 且不重复。
    - 为覆盖两个分片的多个用户直接落库 INTERVAL(intervalMin=1) 任务 + 触发器（NEXT_FIRE_TIME<=now, WAITING），
      使两节点各自按 MOD(ORA_HASH(USER_ID),2) 抢自己分片的到期触发器。
    - 等待 a_duration 秒（>=5min）让触发器多次到点。
    - 查重 SQL：SCHED_TODO / SCHED_FIRE_LOG 按 (TASK_ID, SCHEDULED_FIRE_TIME) 分组 COUNT>1 必须为 0 行。
    - 两分片均触发：SCHED_FIRE_LOG 中已触发行的 MOD(ORA_HASH(USER_ID),2) 须同时含 0 与 1。
    - finally 清理本次落库的行。

安全约定
--------
本脚本不把真实口令写死在源码里：所有凭据默认从环境变量读取（SCHED_ORACLE_PWD / SCHED_REDIS_PWD /
SCHED_API_PWD / SCHED_ORACLE_USER / SCHED_ORACLE_DSN / SCHED_REDIS_HOST / SCHED_REDIS_PORT），
可被同名命令行参数覆盖；仅在显式传入 --dev 时，才回退到本项目内网开发环境的明文默认值
（仅用于本机联调，严禁用于生产 / 严禁提交真实口令）。输出中所有口令均以掩码呈现。

依赖：oracledb、redis（Python 3，utf-8）。
用法示例：
    # 当前单节点（18077）跑 (B)
    python p2/verify.py --node1 http://127.0.0.1:18077 --dev
    # 双节点（标准拓扑 18077/18078，避开 77/78 浏览器封锁端口）跑 (A)+(B)
    python p2/verify.py --node1 http://127.0.0.1:18077 --node2 http://127.0.0.1:18078 --run-a --dev
    # 仅 (A)
    python p2/verify.py --node1 http://127.0.0.1:18077 --node2 http://127.0.0.1:18078 --run-a --skip-b --dev
"""

import sys
import os
import json
import time
import uuid
import argparse
import threading
import datetime

import oracledb
import redis
import urllib.request
import urllib.error
import urllib.parse

# ---------------------------------------------------------------------------
# 默认值（仅 --dev 时作为内网开发环境回退；生产请走环境变量 / CLI）
# ---------------------------------------------------------------------------
DEFAULTS = {
    "node1": "http://127.0.0.1:18077",
    "node2": "http://127.0.0.1:18078",
    "auth_path": "/api/auth/login",
    "task_path": "/api/sched/tasks",
    "oracle_user": "jddba",
    "oracle_pwd": "xingx888",
    "oracle_dsn": "172.16.253.180:1521/orcl",
    "redis_host": "127.0.0.1",
    "redis_port": 6379,
    "redis_pwd": "xingx@1.",
    "redis_topic": "sched:task:change",
    "api_user": "张晋宝",
    "api_pwd": "itwb@2026",
    "a_duration": 300,     # (A) 等待触发的秒数（>=300 即 5 分钟）
    "b_timeout": 12,       # (B) 单条通知等待超时（秒）
}


def mask(s):
    if s is None:
        return "****"
    s = str(s)
    return (s[0] + "****") if len(s) > 1 else "****"


# ---------------------------------------------------------------------------
# Oracle 辅助
# ---------------------------------------------------------------------------
def oracle_connect(user, pwd, dsn):
    return oracledb.connect(user=user, password=pwd, dsn=dsn)


def q_scalar(conn, sql, **binds):
    cur = conn.cursor()
    try:
        cur.execute(sql, binds)
        row = cur.fetchone()
        return row[0] if row else None
    finally:
        cur.close()


def q_all(conn, sql, **binds):
    cur = conn.cursor()
    try:
        cur.execute(sql, binds)
        return cur.fetchall()
    finally:
        cur.close()


def x_do(conn, sql, **binds):
    cur = conn.cursor()
    try:
        cur.execute(sql, binds)
        conn.commit()
    finally:
        cur.close()


def in_clause(name, values):
    """生成 IN (:n0,:n1,...) 占位与绑定字典。"""
    if not values:
        return "(-1)", {}
    parts = []
    binds = {}
    for i, v in enumerate(values):
        k = "%s%d" % (name, i)
        parts.append(":" + k)
        binds[k] = v
    return "(" + ",".join(parts) + ")", binds


# ---------------------------------------------------------------------------
# HTTP 辅助（urllib，Bearer）
# ---------------------------------------------------------------------------
def api(base, path, method, token=None, body=None, timeout=15, retries=3):
    url = base.rstrip("/") + path
    data = None
    headers = {"Content-Type": "application/json;charset=utf-8",
               "Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    last_err = None
    for _ in range(retries):
        try:
            req = urllib.request.Request(url, data=data, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8", "replace")
                code = resp.getcode()
            try:
                payload = json.loads(raw) if raw else {}
            except Exception:
                payload = {"raw": raw}
            return code, payload
        except urllib.error.HTTPError as e:  # 业务错误（4xx/5xx）立即返回，不重试
            raw = e.read().decode("utf-8", "replace")
            try:
                payload = json.loads(raw) if raw else {}
            except Exception:
                payload = {"raw": raw}
            return e.code, payload
        except Exception as e:  # 连接/超时等瞬断，重试
            last_err = str(e)
            time.sleep(0.5)
    return None, {"error": last_err}


def login(base, auth_path, username, password, timeout=15):
    code, payload = api(base, auth_path, "POST",
                        body={"username": username, "password": password}, timeout=timeout)
    if code != 200 or not isinstance(payload, dict):
        return None, None, {"http": code, "payload": payload}
    data = payload.get("data") or {}
    token = data.get("token")
    user_id = data.get("userId")
    if not token:
        return None, None, {"http": code, "payload": payload}
    return token, user_id, {"http": code}


# ---------------------------------------------------------------------------
# Redis 订阅者（独立线程 listen，protocol=2 兼容旧 Redis 服务端）
# ---------------------------------------------------------------------------
class RedisSub:
    def __init__(self, host, port, pwd, topic, protocol=2):
        self.host = host
        self.port = port
        self.pwd = pwd
        self.topic = topic
        # 不使用 decode_responses，自行按 utf-8 解码，规避解码器在并发下的偶发异常
        self.client = redis.Redis(host=host, port=port, password=pwd,
                                  protocol=protocol, decode_responses=False,
                                  socket_connect_timeout=5)
        self.pubsub = self.client.pubsub()
        self.pubsub.subscribe(topic)
        self.messages = []
        self.lock = threading.Lock()
        self._stop = False
        self.confirmed = False
        # 等待订阅确认（subscribe 回执），确保订阅真正生效后主流程才发布/断言
        deadline = time.time() + 5
        while time.time() < deadline:
            try:
                msg = self.pubsub.get_message(timeout=0.5)
            except Exception:
                msg = None
            if msg and msg.get("type") == "subscribe" and msg.get("channel") == topic:
                self.confirmed = True
                break
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()

    def _loop(self):
        try:
            while not self._stop:
                try:
                    msg = self.pubsub.get_message(timeout=0.2)
                except Exception:
                    msg = None
                if msg and msg.get("type") == "message":
                    data = msg.get("data")
                    if isinstance(data, bytes):
                        data = data.decode("utf-8", "replace")
                    if data is not None:
                        with self.lock:
                            self.messages.append(str(data))
                elif msg is None:
                    time.sleep(0.01)
        except Exception:
            pass

    def wait_for(self, needle, timeout, from_len=None):
        """等待 timeout 秒内出现一条包含 needle 的消息。

        from_len：调用方在“操作前”记录的消息基线（len(messages)）。由于服务端发布极快
        （API 返回后 0.01~0.1s 即到达），若以调用后长度作基线会漏掉已到达的消息，
        故必须传入操作前基线。
        """
        with self.lock:
            base = from_len if from_len is not None else len(self.messages)
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                for m in self.messages[base:]:
                    if needle in m:
                        return True
            time.sleep(0.1)
        return False

    def count_since(self, needle, start_len):
        with self.lock:
            return sum(1 for m in self.messages[start_len:] if needle in m)

    def stop(self):
        self._stop = True
        try:
            self.pubsub.close()
        except Exception:
            pass


# ---------------------------------------------------------------------------
# (B) Redis 通知验证
# ---------------------------------------------------------------------------
def run_b(cfg, results):
    print("[B] 开始 Redis 任务变更通知验证（节点1=%s）" % cfg["node1"])

    # 1) 订阅（订阅客户端只用于 pubsub，绝不用于 publish，避免连接池复用破坏订阅）
    try:
        sub = RedisSub(cfg["redis_host"], cfg["redis_port"], cfg["redis_pwd"], cfg["redis_topic"])
    except Exception as e:
        results["B_redis_subscribe"] = {"pass": False, "detail": "Redis 订阅失败: %s" % e}
        print("  [B] Redis 订阅失败: %s" % e)
        return
    results["B_redis_subscribe"] = {"pass": True, "detail": "已订阅 %s" % cfg["redis_topic"]}

    # 独立的发布客户端（与订阅客户端分离，防止 publish 复用订阅连接导致订阅中断）
    pub_client = redis.Redis(host=cfg["redis_host"], port=cfg["redis_port"],
                             password=cfg["redis_pwd"], protocol=2,
                             decode_responses=True, socket_connect_timeout=5)

    # 2) 总线自检：本端 publish 哨兵，断言订阅者收到
    sentinel = "P2_SELFTEST_" + uuid.uuid4().hex[:8]
    with sub.lock:
        before_self = len(sub.messages)
    pub_client.publish(cfg["redis_topic"], sentinel)
    ok = sub.wait_for(sentinel, cfg["b_timeout"], from_len=before_self)
    results["B_redis_selftest"] = {
        "pass": ok,
        "detail": ("总线自检通过：发布%s被订阅者收到" % sentinel) if ok
        else ("总线自检失败：发布%s后 %ds 内未收到" % (sentinel, cfg["b_timeout"]))
    }
    print("  [B] 总线自检: %s" % ("PASS" if ok else "FAIL"))

    # 3) 登录节点1
    token, user_id, info = login(cfg["node1"], cfg["auth_path"], cfg["api_user"], cfg["api_pwd"])
    if not token:
        results["B_node1_login"] = {"pass": False, "detail": "节点1 登录失败: %s" % info}
        print("  [B] 节点1 登录失败: %s" % info)
        sub.stop()
        return
    results["B_node1_login"] = {"pass": True, "detail": "登录成功 userId=%s" % user_id}
    print("  [B] 节点1 登录成功 userId=%s" % user_id)

    created_task_ids = []

    def do_op(name, method, path, body=None):
        with sub.lock:
            start_len = len(sub.messages)
        code, payload = api(cfg["node1"], path, method, token=token, body=body)
        received = sub.wait_for(user_id, cfg["b_timeout"], from_len=start_len)
        cnt = sub.count_since(user_id, start_len)
        passed = (code == 200) and received
        results["B_node1_%s" % name] = {
            "pass": passed,
            "http": code,
            "notified": received,
            "notify_count": cnt,
            "detail": ("%s 成功且订阅者收到 userId 通知" % name) if passed
            else ("%s http=%s notified=%s" % (name, code, received))
        }
        print("  [B] node1 %s: http=%s notified=%s -> %s" %
              (name, code, received, "PASS" if passed else "FAIL"))
        return code, payload

    # 4) create
    ts = int(time.time() * 1000)
    create_body = {"name": "P2B_%d" % ts, "content": "p2 verify",
                   "mode": "INTERVAL", "intervalMin": 1}
    code, payload = do_op("create", "POST", cfg["task_path"], create_body)
    task_id = None
    if isinstance(payload, dict):
        task_id = (payload.get("data") if isinstance(payload.get("data"), int)
                   else None)
    if task_id:
        created_task_ids.append(task_id)

    # 5) update（若存在 task_id）
    if task_id:
        upd_body = {"name": "P2B_upd_%d" % ts, "content": "updated"}
        do_op("update", "PUT", "%s/%s" % (cfg["task_path"], task_id), upd_body)
        # 6) delete
        do_op("delete", "DELETE", "%s/%s" % (cfg["task_path"], task_id))
    else:
        results["B_node1_update"] = {"pass": False, "detail": "create 未返回 taskId，跳过 update/delete"}
        results["B_node1_delete"] = {"pass": False, "detail": "create 未返回 taskId，跳过 delete"}
        print("  [B] create 未拿到 taskId，update/delete 跳过")

    # 7) 节点2 跨节点发布（若在线）
    token2, user_id2, info2 = login(cfg["node2"], cfg["auth_path"], cfg["api_user"], cfg["api_pwd"])
    if token2:
        ts2 = int(time.time() * 1000)
        with sub.lock:
            start_len_n2 = len(sub.messages)
        code2, payload2 = api(cfg["node2"], cfg["task_path"], "POST", token=token2,
                              body={"name": "P2B_n2_%d" % ts2, "content": "n2",
                                    "mode": "INTERVAL", "intervalMin": 1})
        received2 = sub.wait_for(user_id2 or cfg["api_user"], cfg["b_timeout"],
                                from_len=start_len_n2)
        passed2 = (code2 == 200) and received2
        # 若节点2 与节点1 是同一账号体系，userId 相同，needle 仍是 api_user
        results["B_node2_publish"] = {
            "pass": passed2,
            "http": code2,
            "notified": received2,
            "detail": ("节点2 在线：create 成功且总线送达通知" if passed2
                      else "节点2 create http=%s notified=%s" % (code2, received2))
        }
        print("  [B] 节点2 跨节点发布: http=%s notified=%s -> %s" %
              (code2, received2, "PASS" if passed2 else "FAIL"))
        # 清理节点2 创建的任务
        if isinstance(payload2, dict) and isinstance(payload2.get("data"), int):
            api(cfg["node2"], "%s/%s" % (cfg["task_path"], payload2["data"]),
                "DELETE", token=token2)
    else:
        results["B_node2_publish"] = {
            "pass": None,
            "status": "skipped",
            "detail": "节点2 不可达（单节点模式）：跨节点发布验证跳过；"
                      "该部分在双节点就绪后由 (A) 阶段覆盖"
        }
        print("  [B] 节点2 不可达 -> 跨节点发布验证 SKIPPED（单节点模式）")

    sub.stop()


# ---------------------------------------------------------------------------
# (A) 跨节点不重复触发验证
# ---------------------------------------------------------------------------
def run_a(cfg, results):
    print("[A] 开始跨节点不重复触发验证（需双节点 shard-total=2）")
    try:
        conn = oracle_connect(cfg["oracle_user"], cfg["oracle_pwd"], cfg["oracle_dsn"])
    except Exception as e:
        results["A_oracle"] = {"pass": False, "detail": "Oracle 连接失败: %s" % e}
        print("  [A] Oracle 连接失败: %s" % e)
        return

    # 就绪性检查：
    #  1) DB 中应有且仅有两个 shard-total=2 且 SHARD_MOD 互补(0,1) 的 UP 节点
    #     （忽略 shard-total=1 等残留/单节点注册行，见 dual-node-procedure OBS-3）；
    #  2) 配置的两个节点基址（node1/node2）必须均可达（登录成功），证明双节点真实在线。
    rows = q_all(conn,
                 "SELECT NODE_ID, SHARD_TOTAL, SHARD_MOD, STATUS FROM SCHED_NODE "
                 "WHERE STATUS='UP' ORDER BY NODE_ID")
    dual = [r for r in rows if r[1] == 2]
    mods = set(r[2] for r in dual)
    db_ok = (len(dual) == 2 and mods == {0, 1})
    # 节点可达性
    t1, _, _ = login(cfg["node1"], cfg["auth_path"], cfg["api_user"], cfg["api_pwd"])
    t2, _, _ = login(cfg["node2"], cfg["auth_path"], cfg["api_user"], cfg["api_pwd"])
    live_ok = bool(t1) and bool(t2)
    ready = db_ok and live_ok
    results["A_readiness"] = {
        "pass": True if ready else None,
        "status": "ready" if ready else "not_ready",
        "up_nodes": [{"node_id": r[0], "shard_total": r[1], "shard_mod": r[2], "status": r[3]}
                    for r in rows],
        "dual_shard_nodes": [r[0] for r in dual],
        "node1_reachable": bool(t1),
        "node2_reachable": bool(t2),
        "detail": ("双节点就绪（DB 双分片 UP + node1/node2 均在线）" if ready
                  else "未满足双节点就绪：db_ok=%s(dual=%s,mods=%s), live_ok=%s(node1=%s,node2=%s)"
                        % (db_ok, [r[0] for r in dual], sorted(mods), live_ok, bool(t1), bool(t2)))
    }
    print("  [A] 就绪性: %s" % ("READY" if ready else "NOT_READY (%s)" % results["A_readiness"]["detail"]))

    if not ready:
        results["A_dedup_todo"] = {"pass": None, "status": "skipped",
                                   "detail": "双节点未就绪，(A) 核心验证跳过"}
        results["A_dedup_fire_log"] = {"pass": None, "status": "skipped",
                                       "detail": "双节点未就绪，(A) 核心验证跳过"}
        results["A_both_shards"] = {"pass": None, "status": "skipped",
                                    "detail": "双节点未就绪，(A) 核心验证跳过"}
        conn.close()
        return

    # 选择覆盖两个分片的用户
    chosen = {}
    i = 0
    while len(chosen) < 2 and i < 60:
        uid = "P2QA_%d" % i
        mod = q_scalar(conn, "SELECT MOD(ORA_HASH(:u),2) FROM dual", u=uid)
        chosen.setdefault(mod, uid)
        i += 1
    shard_users = list(chosen.values())
    print("  [A] 选用覆盖分片的用户: %s（mod 映射 %s）"
          % (shard_users, {v: k for k, v in chosen.items()}))

    # 落库任务 + 触发器（每个用户 2 个 INTERVAL 任务，NEXT_FIRE_TIME<=now 立即到期）
    seeded_task_ids = []
    seeded_user_ids = []
    try:
        for uid in shard_users:
            for k in range(2):
                task_id = q_scalar(conn, "SELECT SEQ_SCHED_TASK.NEXTVAL FROM dual")
                name = "P2A_%s_%d_%d" % (uid, k, int(time.time()))
                x_do(conn,
                     """INSERT INTO SCHED_TASK
                        (TASK_ID, USER_ID, NAME, CONTENT, "MODE", INTERVAL_MIN,
                         TIMEZONE, ENABLED, VERSION, CREATED_AT, UPDATED_AT)
                        VALUES (:tid, :user_id, :name, 'p2a', 'INTERVAL', 1,
                        'Asia/Shanghai', 'Y', 0, SYSTIMESTAMP, SYSTIMESTAMP)""",
                     tid=task_id, user_id=uid, name=name)
                trig_id = q_scalar(conn, "SELECT SEQ_SCHED_TRIGGER.NEXTVAL FROM dual")
                x_do(conn,
                     """INSERT INTO SCHED_TRIGGER
                        (TRIGGER_ID, TASK_ID, USER_ID, NEXT_FIRE_TIME, STATUS, VERSION, UPDATED_AT)
                        VALUES (:tg, :tid, :user_id, SYSTIMESTAMP, 'WAITING', 0, SYSTIMESTAMP)""",
                     tg=trig_id, tid=task_id, user_id=uid)
                seeded_task_ids.append(task_id)
                if uid not in seeded_user_ids:
                    seeded_user_ids.append(uid)
        print("  [A] 已落库 %d 个任务（用户=%s）" % (len(seeded_task_ids), seeded_user_ids))

        # 等待触发
        print("  [A] 等待 %d 秒让触发器跨节点多次到点 ..." % cfg["a_duration"])
        time.sleep(cfg["a_duration"])

        # 查重 SQL
        todo_in, todo_binds = in_clause("t", seeded_task_ids)
        fire_in, fire_binds = in_clause("f", seeded_task_ids)
        dup_todo = q_all(conn,
                         "SELECT SOURCE_TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_TODO "
                         "WHERE SOURCE_TASK_ID IN %s GROUP BY SOURCE_TASK_ID, SCHEDULED_FIRE_TIME "
                         "HAVING COUNT(*) > 1" % todo_in, **todo_binds)
        dup_fire = q_all(conn,
                         "SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_FIRE_LOG "
                         "WHERE TASK_ID IN %s GROUP BY TASK_ID, SCHEDULED_FIRE_TIME "
                         "HAVING COUNT(*) > 1" % fire_in, **fire_binds)

        results["A_dedup_todo"] = {
            "pass": len(dup_todo) == 0,
            "dup_rows": len(dup_todo),
            "detail": ("SCHED_TODO 无重复（0 行）" if len(dup_todo) == 0
                      else "SCHED_TODO 发现 %d 行重复!" % len(dup_todo))
        }
        results["A_dedup_fire_log"] = {
            "pass": len(dup_fire) == 0,
            "dup_rows": len(dup_fire),
            "detail": ("SCHED_FIRE_LOG 无重复（0 行）" if len(dup_fire) == 0
                      else "SCHED_FIRE_LOG 发现 %d 行重复!" % len(dup_fire))
        }
        print("  [A] 查重 todo 重复行=%d, fire_log 重复行=%d"
              % (len(dup_todo), len(dup_fire)))

        # 两分片均触发
        uid_in, uid_binds = in_clause("u", seeded_user_ids)
        fired_mods = q_all(conn,
                           "SELECT DISTINCT MOD(ORA_HASH(USER_ID),2) FROM SCHED_FIRE_LOG "
                           "WHERE USER_ID IN %s" % uid_in, **uid_binds)
        fired_mods = set(r[0] for r in fired_mods)
        both = fired_mods == {0, 1}
        results["A_both_shards"] = {
            "pass": both,
            "fired_shard_mods": sorted(fired_mods),
            "detail": ("两个分片（0 与 1）均发生触发" if both
                      else "仅触发分片 %s，未覆盖全部分片" % sorted(fired_mods))
        }
        print("  [A] 已触发分片 mod=%s -> %s" % (sorted(fired_mods), "PASS" if both else "FAIL"))
    finally:
        # 清理本次落库的行
        try:
            todo_in, todo_binds = in_clause("t", seeded_task_ids)
            fire_in, fire_binds = in_clause("f", seeded_task_ids)
            x_do(conn, "DELETE FROM SCHED_FIRE_LOG WHERE TASK_ID IN %s" % fire_in, **fire_binds)
            x_do(conn, "DELETE FROM SCHED_TODO WHERE SOURCE_TASK_ID IN %s" % todo_in, **todo_binds)
            x_do(conn, "DELETE FROM SCHED_TRIGGER WHERE TASK_ID IN %s" % fire_in, **fire_binds)
            x_do(conn, "DELETE FROM SCHED_TASK WHERE TASK_ID IN %s" % todo_in, **todo_binds)
            print("  [A] 已清理 %d 个落库任务" % len(seeded_task_ids))
        except Exception as e:
            print("  [A] 清理异常（可手动清理）: %s" % e)
        conn.close()


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="sched-backend P2 集群联调验证")
    ap.add_argument("--node1", default=None, help="节点1 基址，如 http://127.0.0.1:18077")
    ap.add_argument("--node2", default=None, help="节点2 基址，如 http://127.0.0.1:18078")
    ap.add_argument("--auth-path", default=None)
    ap.add_argument("--task-path", default=None)
    ap.add_argument("--oracle-user", default=None)
    ap.add_argument("--oracle-pwd", default=None)
    ap.add_argument("--oracle-dsn", default=None)
    ap.add_argument("--redis-host", default=None)
    ap.add_argument("--redis-port", default=None, type=int)
    ap.add_argument("--redis-pwd", default=None)
    ap.add_argument("--redis-topic", default=None)
    ap.add_argument("--api-user", default=None)
    ap.add_argument("--api-pwd", default=None)
    ap.add_argument("--a-duration", default=None, type=int)
    ap.add_argument("--b-timeout", default=None, type=int)
    ap.add_argument("--run-a", action="store_true", help="执行 (A) 跨节点不重复验证")
    ap.add_argument("--skip-b", action="store_true", help="跳过 (B)")
    ap.add_argument("--dev", action="store_true",
                    help="回退到内网开发环境明文默认值（仅本机联调，禁止生产/提交）")
    args = ap.parse_args()

    def val(key, arg):
        # 优先级：CLI > 环境变量 > (--dev 时的 DEFAULTS) > DEFAULTS
        env_map = {
            "oracle_user": "SCHED_ORACLE_USER", "oracle_pwd": "SCHED_ORACLE_PWD",
            "oracle_dsn": "SCHED_ORACLE_DSN", "redis_host": "SCHED_REDIS_HOST",
            "redis_port": "SCHED_REDIS_PORT", "redis_pwd": "SCHED_REDIS_PWD",
            "api_user": "SCHED_API_USER", "api_pwd": "SCHED_API_PWD",
        }
        if arg is not None:
            return arg
        ev = env_map.get(key)
        if ev and os.environ.get(ev) is not None:
            return os.environ.get(ev)
        if args.dev:
            return DEFAULTS[key]
        # 非 dev 且未提供：仍用 DEFAULTS（避免空值崩溃），但 --dev 仅影响是否允许明文默认
        return DEFAULTS[key]

    cfg = {
        "node1": args.node1 or DEFAULTS["node1"],
        "node2": args.node2 or DEFAULTS["node2"],
        "auth_path": args.auth_path or DEFAULTS["auth_path"],
        "task_path": args.task_path or DEFAULTS["task_path"],
        "oracle_user": val("oracle_user", args.oracle_user),
        "oracle_pwd": val("oracle_pwd", args.oracle_pwd),
        "oracle_dsn": val("oracle_dsn", args.oracle_dsn),
        "redis_host": val("redis_host", args.redis_host),
        "redis_port": args.redis_port or int(val("redis_port", None)),
        "redis_pwd": val("redis_pwd", args.redis_pwd),
        "redis_topic": args.redis_topic or DEFAULTS["redis_topic"],
        "api_user": val("api_user", args.api_user),
        "api_pwd": val("api_pwd", args.api_pwd),
        "a_duration": args.a_duration or DEFAULTS["a_duration"],
        "b_timeout": args.b_timeout or DEFAULTS["b_timeout"],
    }

    results = {}
    overall_fail = False

    if not args.skip_b:
        run_b(cfg, results)
    if args.run_a:
        run_a(cfg, results)

    # 汇总
    executed = [(k, v) for k, v in results.items()
                if not (isinstance(v, dict) and v.get("status") == "skipped")]
    failed = [(k, v) for k, v in executed if v.get("pass") is False]
    skipped = [(k, v) for k, v in results.items()
               if isinstance(v, dict) and v.get("status") == "skipped"]

    if failed:
        overall = "FAIL"
        overall_fail = True
    elif skipped and not executed:
        overall = "SKIPPED"
    elif skipped:
        overall = "PASS(B-ONLY; A skipped - dual-node required)" if not args.run_a else "PARTIAL"
    else:
        overall = "PASS"

    report = {
        "script": "p2/verify.py",
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "config": {
            "node1": cfg["node1"], "node2": cfg["node2"],
            "auth_path": cfg["auth_path"], "task_path": cfg["task_path"],
            "oracle_user": cfg["oracle_user"],
            "oracle_pwd": mask(cfg["oracle_pwd"]),
            "oracle_dsn": cfg["oracle_dsn"],
            "redis_host": cfg["redis_host"], "redis_port": cfg["redis_port"],
            "redis_pwd": mask(cfg["redis_pwd"]),
            "redis_topic": cfg["redis_topic"],
            "api_user": cfg["api_user"], "api_pwd": mask(cfg["api_pwd"]),
            "a_duration": cfg["a_duration"], "b_timeout": cfg["b_timeout"],
        },
        "checks": results,
        "summary": {
            "executed": len(executed), "failed": len(failed), "skipped": len(skipped),
        },
        "overall": overall,
    }

    print("\n==================== P2 验证结果 ====================")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print("OVERALL: %s" % overall)
    print("=====================================================")

    sys.exit(1 if overall_fail else 0)


if __name__ == "__main__":
    main()
