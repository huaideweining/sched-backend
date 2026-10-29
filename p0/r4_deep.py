# -*- coding: utf-8 -*-
"""R4 注册接口深度联调（基线 + 回归对照用）。

覆盖：正常 / 中文 / 边界长度 / 校验失败 / 重复用户名 / 并发同名 / SQL 注入。
所有请求以 UTF-8 编码发送（避免之前 curl GBK 假象）。
测试用户统一在结尾清理（SCHED_USER DELETE）。
"""
import json, time, threading, urllib.request, urllib.error, subprocess, os

BASE = "http://localhost:18077"
# SQL*Plus 以管道方式读取脚本时，会按 ';' 切分语句（即便 ';' 位于引号内）；
# 测试用户名 'qa\'inj;--' 含 ';' 会破坏单条 IN(...) 语句。同时客户端需以 UTF-8 解释
# 含中文的字符字面量，否则会撞 ORA-01756。故统一注入 NLS_LANG。
_SQL_ENV = dict(os.environ)
_SQL_ENV["NLS_LANG"] = "AMERICAN_AMERICA.AL32UTF8"
SQLPLUS = "G:/app/Administrator/product/11.2.0/client_2/bin/sqlplus.exe"
DB = "jddba/xingx888@//172.16.253.180:1521/orcl"
created = []  # 已成功创建的用户名（待清理）


def post(path, payload):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=body,
                                 headers={"Content-Type": "application/json; charset=utf-8"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {}


def reg(u, p):
    sc, b = post("/api/auth/register", {"username": u, "password": p})
    if sc == 200 and isinstance(b, dict) and b.get("code") == 0:
        # 记录服务端实际落库的用户名（register 会对 username 做 trim），
        # 否则 space_trim 用例的清理会因「原值带空格」匹配不到而残留。
        data = b.get("data") or {}
        stored = data.get("username") or data.get("userId") or u
        created.append(stored)
    return sc, b


def T(tag, u, p, expect):
    sc, b = reg(u, p)
    code = b.get("code") if isinstance(b, dict) else None
    msg = (b.get("msg") or "") if isinstance(b, dict) else ""
    ok = (code == expect)
    print(f"[{'PASS' if ok else 'FAIL'}] {tag:18} u={u!r:24} p_len={len(p):3} HTTP {sc} code={code} (expect {expect}) | {str(msg)[:46]}")
    return ok, code


def concurrent_same_user(u, n=8):
    lock = threading.Lock()
    codes = []
    def worker():
        sc, b = reg(u, "Test@123456")
        c = b.get("code") if isinstance(b, dict) else None
        with lock:
            codes.append(c)
    ts = [threading.Thread(target=worker) for _ in range(n)]
    for t in ts: t.start()
    for t in ts: t.join()
    succ = codes.count(0)
    print(f"[INFO] 并发同名(u={u!r}, n={n}) -> 成功={succ} 各code={codes}")
    return succ, codes


def sql(statement):
    p = subprocess.run([SQLPLUS, "-S", DB], input=statement.encode("utf-8"),
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30,
                       env=_SQL_ENV)
    return p.stdout.decode("utf-8", "replace")


def clean_created():
    """安全清理测试用户：逐条 DELETE，含 ';' 的用户名改用 LIKE 前缀匹配，
    避免 SQL*Plus 按 ';' 切分语句导致整条 IN(...) 解析失败、残留测试数据。"""
    if not created:
        print("[DB] 无测试用户需清理")
        return
    uniq = list(dict.fromkeys(created))
    stmts = []
    for u in uniq:
        esc = u.replace("'", "''")
        if ";" in u:
            # ';' 会切断脚本：用 ';' 前的内容做 LIKE 前缀（已转义单引号）
            prefix = u.split(";", 1)[0].replace("'", "''")
            stmts.append("DELETE FROM SCHED_USER WHERE USERNAME LIKE '%s%%'" % prefix)
        else:
            stmts.append("DELETE FROM SCHED_USER WHERE USERNAME='%s'" % esc)
    stmt = "SET PAGESIZE 0 FEEDBACK OFF\n" + "\n".join(s + ";" for s in stmts) + "\nCOMMIT;\nEXIT;\n"
    res = sql(stmt)
    print("[DB] 已提交清理 %d 个测试用户；回显含 'deleted'=%s" % (len(uniq), "deleted" in res.lower()))


def main():
    print("==== R4 深度联调基线 ====")
    T("happy_ascii", "qa_deep_001", "Test@123456", 0)
    T("happy_cn", "深度用户甲", "Test@123456", 0)
    T("user_len_3", "abc", "Test@123456", 0)
    T("user_len_32", "a" * 32, "Test@123456", 0)
    T("user_len_2", "ab", "Test@123456", 1001)        # 校验：期望参数错误码
    T("user_len_33", "a" * 33, "Test@123456", 1001)
    T("pwd_len_6", "qa_deep_006", "Abcd12", 0)
    T("pwd_len_5", "qa_deep_007", "Abcd1", 1001)
    T("pwd_len_128", "qa_deep_008", "X" * 128, 0)
    T("pwd_len_129", "qa_deep_009", "X" * 129, 1001)
    T("miss_username", "", "Test@123456", 1001)
    T("miss_password", "qa_deep_010", "", 1001)
    T("space_trim", "  qa_trim_01  ", "Test@123456", 0)   # 期望 trim 后 'qa_trim_01'
    T("inject_user", "qa'inj;--", "Test@123456", 0)        # 期望参数化存储，不注入

    # 重复用户名：期望 USER_DUP=1004（回归检测点：合并到 itwb.AuthController 后可能回退 500）
    T("dup_first", "qa_dup_001", "Test@123456", 0)
    T("dup_second", "qa_dup_001", "Test@123456", 1004)

    # 并发同名：期望恰好 1 成功，其余 1004/1001，无 500、无 2 成功
    concurrent_same_user("qa_conc_001", 8)

    # 校验 trim 后存储值
    print("---- trim 存储校验 ----")
    out = sql("SET PAGESIZE 0 FEEDBACK OFF\nSELECT USERNAME FROM SCHED_USER WHERE USERNAME IN ('qa_trim_01','  qa_trim_01  ');\nEXIT;\n")
    print("[DB] trim 查询:", repr(out.strip()))

    # 清理
    print("---- 清理测试用户 ----")
    clean_created()
    print("==== DONE ====")


if __name__ == "__main__":
    main()
