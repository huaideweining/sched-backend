# sched-backend 双节点（P2）运行步骤

> 配套脚本：`p2/run-node2.ps1`（Windows）、`p2/run-node2.sh`（兼容 shell）
> 就绪度评估：`p2/cluster-readiness.md`（源码已核对，确认**无需改业务代码**）
> 目标：两个实例共用同一 Oracle(172.16.253.180:1521/orcl) + Redis(127.0.0.1:6379)，`shard-total=2`，分别 `dev-node-1` / `dev-node-2`。
>
> ⚠️ **端口与 profile 对齐（2026-10-27 修订）**：当前 `application.yml` 已固定在 **18077**（默认 profile，凭据直接写在 base `application.yml` 中），
> 且 **77/78 落在 Chromium/Edge 的封锁端口黑名单（ERR_UNSAFE_PORT）**，浏览器 fetch 会直接拒连。
> 因此本流程**不使用 77/78**，两节点改用 **18077（node-1）/ 18078（node-2）**，并**不启用 `dev` profile**（凭据由 base `application.yml` 默认继承，仓库内并无 `application-dev.yml`）。

---

## 0. 前置条件

| 项 | 要求 |
|----|------|
| Maven 依赖 | 本机已缓存依赖（`mvn -o` 可用）。node-1 已构建过通常即可；若首次离线失败，临时用 `SCHED_ONLINE=1` 在线一次 |
| 端口 | **18077**（node-1）、**18078**（node-2）均空闲（不使用 77/78：浏览器封锁端口黑名单） |
| Oracle / Redis | 与 node-1 同一实例可达；**凭据不在此出现**，由 base `application.yml`（默认 profile）继承 |
| Profile | **默认 profile（不指定 `dev`）**：仓库无 `application-dev.yml`，凭据已在 base `application.yml` 中；脚本只通过 CLI 覆盖三项调度参数 |
| 分片参数 | **两个节点都必须 `scheduler.shard-total=2`**（详见 §4 与 cluster-readiness.md OBS-4） |

---

## 1. 当前单节点状态回顾

单节点默认 `application.yml`（默认 profile）：`server.port=18077`、`scheduler.node-id=merged-node-1`、`scheduler.shard-total=1`。
进入 P2 前，该实例**必须以 shard-total=2 重启**（不能保留 1，否则分片失效、node-1 过载，见 OBS-4）。

---

## 2. 清理旧注册（建议）

为得到干净的 `SCHED_NODE` 注册视图（避免 OBS-3 残留旧 shardTotal），联调前清一次：

```sql
DELETE FROM SCHED_NODE;
COMMIT;
```

> 不清亦可运行（运行期用内存分片值，不受影响），但清空后 §5 的核对更直观。

---

## 3. 启动 节点1（port 18077, dev-node-1, shard-total=2）

先停掉正在运行的旧单节点（Ctrl+C）。然后在**项目根目录**启动（等价于 node-2 脚本、只是参数不同）：

```bash
# 方式 A：mvn（推荐）—— 默认 profile，凭据继承 base application.yml
mvn -o \
  -Dspring-boot.run.arguments="--server.port=18077 --scheduler.node-id=dev-node-1 --scheduler.shard-total=2" \
  spring-boot:run

# 方式 B：已打包
# java -jar target/sched-backend-*.jar --server.port=18077 --scheduler.node-id=dev-node-1 --scheduler.shard-total=2
```

等待日志出现：`scheduler bootstrap started (dispatchIntervalMs=...)`
（Windows 也可用：`powershell -ExecutionPolicy Bypass -File p2\run-node2.ps1`，但把其中默认 port/node-id 改为 18077/dev-node-1；或临时设环境变量 `SCHED_PORT=18077 SCHED_NODE_ID=dev-node-1` 后运行同一脚本。）

---

## 4. 启动 节点2（port 18078, dev-node-2, shard-total=2）

新开一个终端，在项目根目录执行（**脚本已自带端口占用探测与目录切换**）：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File p2\run-node2.ps1
```
```bash
# Linux / macOS / Git Bash
bash p2/run-node2.sh
```

脚本默认即：`port=18078`、`node-id=dev-node-2`、`shard-total=2`、`profile=默认（无 dev）`。
同样等待日志：`scheduler bootstrap started ...`

> 两个节点都启动后，二者都会 `ClusterManager.register()` 写入 / 刷新 `SCHED_NODE`，并以 10s 间隔心跳。

---

## 5. 确认两者都已注册（count=2, status=UP）

```sql
SELECT NODE_ID, HOST, SHARD_TOTAL, SHARD_MOD, STATUS, LAST_HEARTBEAT
FROM SCHED_NODE
ORDER BY NODE_ID;
```

期望：
- 恰好 **2 行**：`dev-node-1`、`dev-node-2`；
- `STATUS` 均为 `UP`；
- `SHARD_TOTAL` = 2；
- `SHARD_MOD` 覆盖 `0` 与 `1` 且不重复 —— **`dev-node-1`→0，`dev-node-2`→1**（已验证 hashCode 互补，见 cluster-readiness.md OBS-1）。
- 若两行 `SHARD_MOD` 相同（如都为 0）：说明 node-id 取模碰撞，分片失效（部分用户触发器永不触发）。**此时必须换 node-id 组合**（不要使用 `dev-node-1`+`dev-node-3` 这类同模组合），然后回到 §2 清理并重启。

---

## 6. 确认选主（一个 leader）

> ⚠️ **重要**：`SCHED_NODE.IS_LEADER` 列**不会被置为 'Y'**（源码只维护内存 leader 标志，从不回写列，见 OBS-2）。
> 因此**不要**用 `WHERE IS_LEADER='Y'` 来确认——它永远为 0。改用以下任一方式：

**方式 1（确定性规则）**：UP 节点中 `node_id` 字典序最小者即 leader → 本场景为 `dev-node-1`。

**方式 2（SQL 复刻选主逻辑）**：
```sql
SELECT NODE_ID FROM SCHED_NODE
WHERE STATUS='UP'
ORDER BY NODE_ID ASC
FETCH FIRST 1 ROWS ONLY;   -- 期望 DEV-NODE-1
```

**方式 3（调试）**：在 `ClusterManager.refreshLeader()`（第 91 行）临时加 `log.info("leader={} for {}", leader, nodeId)` 观察（纯观察，不改主流程）。

---

## 7. 功能验证（跨节点不重复 + Redis 通知）

### 7.1 REST 入口（两个端口都通，连同一库）
- node-1：`http://localhost:18077/api`
- node-2：`http://localhost:18078/api`
- 任取一个登录、建任务（`POST /api/sched/tasks`，"近未来" `NEXT_FIRE_TIME` 设当前 +1~9 分钟，时间轮只装未来 10 分钟窗口）。

### 7.2 跨节点不重复（核心）
让一批任务在两节点并存运行 ≥5 分钟，执行联调测试计划 §6.2 查重 SQL，期望 **0 行**：
```sql
SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c
FROM SCHED_TODO
GROUP BY TASK_ID, SCHEDULED_FIRE_TIME
HAVING COUNT(*) > 1;

SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c
FROM SCHED_FIRE_LOG
GROUP BY TASK_ID, SCHEDULED_FIRE_TIME
HAVING COUNT(*) > 1;
```
> 两节点各自只抢自己分片（SKIP LOCKED + CAS 双保险），结果应为 0 行重复。

### 7.3 Redis 实时通知（P2 要点）
开一个终端订阅变更频道（凭据同 base application.yml）：
```bash
redis-cli -h 127.0.0.1 -p 6379 -a 'xingx@1.' SUBSCRIBE sched:task:change
```
在任一节点经 REST 创建/修改/删除任务（`TaskServiceImpl` 事务提交后 `publishTaskChange`），应看到另一终端收到 `userId` 消息，且两节点 `TriggerEngine` 都会 `loadWindow()` 重载。

---

## 8. 停止 / 回退

### 8.1 正常停止
分别在两个终端 `Ctrl+C`（Spring 关闭钩子会 `SchedulerBootstrap.stop()` 停时间轮线程）。两节点停止后 `SCHED_NODE.STATUS` 不会自动置 DOWN（心跳停了，但 `refreshLeader` 只看 UP 行）——**可选清理**：
```sql
DELETE FROM SCHED_NODE; COMMIT;
```

### 8.2 回退到单节点
1. 停掉 node-2（Ctrl+C）并删除其进程/终端。
2. 停掉 node-1，以**默认 `shard-total=1`** 重启（即去掉 `--scheduler.shard-total=2`，或直接用原 `application.yml` 默认）：
   ```bash
   mvn -o spring-boot:run
   ```
   此时 `MOD(ORA_HASH(USER_ID),1)=0` 恒成立，单节点全量接管，回归 P0/P1 状态。
3. 清 `SCHED_NODE`（§2）避免残留 node-2 行干扰选主视图。

---

## 9. 关键提醒（来自就绪度评估）

1. **OBS-4（强制）**：node-1 **必须**也用 `shard-total=2` 重启，不能留 `1`。
2. **OBS-1**：node-id 取模互补（`dev-node-1`→0 / `dev-node-2`→1）已验证；勿用同模组合（如 `dev-node-1`+`dev-node-3`）。
3. **OBS-2**：选主确认用 §6 方式，不要查 `IS_LEADER='Y'`（恒为 0）。
4. **OBS-3**：联调前清 `SCHED_NODE` 可得干净视图。
5. **端口安全**：不使用 77/78（浏览器封锁端口黑名单 ERR_UNSAFE_PORT）；node-1=18077、node-2=18078，均落在黑名单外，且前端 BaseURL 已指向 18077。
6. **凭据安全**：两个脚本均**不含** Oracle/Redis/JWT 明文，全部从 base `application.yml`（默认 profile）继承；如需显式传口令，请用占位环境变量自行注入，切勿把口令写死进脚本或提交仓库。

---

## 10. 验证速查 SQL 汇总
```sql
-- 注册核对
SELECT NODE_ID, SHARD_TOTAL, SHARD_MOD, STATUS FROM SCHED_NODE ORDER BY NODE_ID;
-- 选主（字典序最小 UP）
SELECT NODE_ID FROM SCHED_NODE WHERE STATUS='UP' ORDER BY NODE_ID ASC FETCH FIRST 1 ROWS ONLY;
-- 跨节点不重复
SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_TODO
  GROUP BY TASK_ID, SCHEDULED_FIRE_TIME HAVING COUNT(*) > 1;
SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_FIRE_LOG
  GROUP BY TASK_ID, SCHEDULED_FIRE_TIME HAVING COUNT(*) > 1;
-- 清理
DELETE FROM SCHED_NODE; COMMIT;
```
