# P2 集群联调测试计划（sched-backend：双节点 + Redis 通知）

> 文档 owner：QA「严过关」｜配套脚本：`p2/verify.py`
> 范围：验证 (B) Redis 任务变更 Pub/Sub 通知链路，以及 (A) 双节点（shard-total=2）跨节点不重复触发。
> 说明：本计划所有用例均通过 `p2/verify.py` 自动执行；本文给出用例设计、制造方法、期望、验证 SQL 与通过标准。

---

## 0. 目标

| 编号 | 验证点 | 类型 | 当前状态 |
|------|--------|------|----------|
| (B) | Redis `sched:task:change` 任务变更通知：节点执行 create/update/delete 后，订阅者能收到携带 `userId` 的消息（事务提交后 `afterCommit` 发布） | 集成/通知 | **已通过**（单节点自环验证） |
| (A) | 双节点（shard-total=2）并存 ≥5 分钟，多用户覆盖两分片，触发器跨节点无重复执行 | 集群/不重复 | 已实现脚本，待双节点环境就绪后执行 |

> 关键约束：跨节点不重复依赖"双节点 + shard-total=2"；单节点仅能验证 (B) 的 Redis 通知自环。

---

## 1. 环境与时序契约（已核对源码）

### 1.1 当前实际运行态（2026-10-27 核对）
- **后端**：Spring Boot 3 + MyBatis-Plus + Oracle 19c + Redis + JWT。
- **单节点在线**：端口 **18077**（非 77/78），`scheduler.node-id=merged-node-1`、`shard-total=1`。
  - 端口 77 / 78 / 51128 当前均**未监听**（netstat 已确认）。
- **认证（现状）**：`ConfigUserAuthServiceImpl`（`@Primary`）——登录走**固定配置账号**，不使用 `SCHED_USER` 表。
  - 登录名 / 密码 / userId：`张晋宝` / `itwb@2026` / `张晋宝`（来自 `application.yml` 的 `itwb.auth.*`）。
  - 注意：`SCHED_USER` 表存在且存有 `QA_userA/B/E/G` 等 8 行，但**当前登录不查该表**（属后续接 ERP 的改造点）。
- **Oracle**：`jddba` / `xingx888` / `172.16.253.180:1521/orcl`（已用该口令连通）。
- **Redis**：`127.0.0.1:6379`，密码 `xingx@1.`（已连通）。

### 1.2 接口契约（源码核对）
| 用途 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 登录 | POST | `/api/auth/login` | body `{username,password}` → `data.token`、`data.userId` |
| 创建任务 | POST | `/api/sched/tasks` | body `TaskCreateReq{name,mode,intervalMin,...}` → `data`=taskId(Long) |
| 更新任务 | PUT | `/api/sched/tasks/{id}` | body `TaskUpdateReq{name,content,...}` |
| 删除任务 | DELETE | `/api/sched/tasks/{id}` | 返回 200 即成功 |
| 任务列表 | GET | `/api/sched/tasks` | 分页查询（调试用） |

- 所有业务接口需 Header：`Authorization: Bearer <token>`。
- 返回统一包装 `Result<T>{code(0=ok), message, data}`。

### 1.3 Redis 通知契约
- 频道：**`sched:task:change`**（常量 `Constants.REDIS_TOPIC_TASK_CHANGE`）。
- 发布：`RedisTriggerPubSub.publishTaskChange(userId)` → `convertAndSend(topic, userId)`，在 `TaskServiceImpl` 事务 `afterCommit` 后触发（避免事务内 Redis I/O 死锁）。
- 订阅：`TriggerEngine.onMessage` → `loadWindow()` 重载本节点时间轮。
- 消息体：经 `GenericJackson2JsonRedisSerializer` 序列化，`userId` 字符串会被包成 **JSON 字符串** `'"张晋宝"'`（`"张晋宝"` 带双引号）。订阅侧按**子串匹配** userId 即可。

### 1.4 分片与抢占（跨节点不重复的理论基础，见 `cluster-readiness.md`）
- 节点分片：`shardMod = Math.floorMod(nodeId.hashCode(), shardTotal)`，各节点**独立**按自身 `nodeId` 计算（Java 侧）。
- 触发器归属：`Dispatcher.scanDue` → `SchedTriggerMapper.claimDue`：
  `MOD(ORA_HASH(t.USER_ID), #{shardTotal}) = #{shardMod}` + `FOR UPDATE SKIP LOCKED`。
- 到点执行：`tryAcquireAndRun` 用 `compareAndSet(WAITING→ACQUIRED)`（单条 CAS），与扫描路径双保险。
- 结论：两节点对同一触发器同时尝试，仅一个成功 → **天然不重复**。

---

## 2. 验证脚本 `p2/verify.py`

### 2.1 设计要点
- 纯 Python（3，utf-8），仅依赖 `oracledb` / `redis` / 标准库（`urllib` HTTP、`json`）。
- 参数化：节点基址、上下文路径、Oracle/Redis 连接、登录账号、超时全部可经 **CLI 参数** 或 **环境变量** 覆盖；`--dev` 时回退到内网开发环境明文默认值（禁止生产 / 禁止提交）。**输出中口令一律掩码**。
- 输出：结构化 JSON + `OVERALL PASS/FAIL/PARTIAL` + 正确退出码（有失败=1，无失败=0）。
- (B) 与 (A) 分离：`--run-a` 触发 (A)；`--skip-b` 跳过 (B)。

### 2.2 运行状态（实战中修复的测试侧缺陷，非系统 Bug）
验证脚本在单节点联调中暴露并修复了**测试框架自身**的问题（已在脚本内修正，供复盘）：
1. **Redis 订阅可靠性**：`pubsub.listen()` 生成器在并发流量下偶发丢消息；改为 `get_message()` 轮询 + 启动前**等待 subscribe 确认回执**，发布客户端与订阅客户端**分离**（避免 publish 复用订阅连接导致订阅中断）；并统一手动 `utf-8` 解码（规避 `decode_responses` 偶发异常）。
2. **通知到达基线**：Spring 在 `afterCommit` 后发布，消息于 API 返回后 **0.01~0.1s** 即到达；`wait_for` 必须以"操作前"的消息条数为基线（而非调用后），否则会漏掉已到达的消息。已改为 `wait_for(needle, timeout, from_len=操作前基线)`。
3. **HTTP 瞬断重试**：`api()` 对连接/超时类异常重试 3 次（业务 4xx/5xx 立即返回）。

> 结论：上述均为**测试脚本缺陷**，被测系统的 Redis 通知链路与任务接口均正确。

### 2.3 运行命令
```bash
# (B) 单节点自环（当前 18077 在线）
python p2/verify.py --node1 http://127.0.0.1:18077 --dev

# (A)+(B) 双节点标准拓扑（端口 77/78，需先按 §4 前置准备）
python p2/verify.py --node1 http://127.0.0.1:77 --node2 http://127.0.0.1:78 --run-a --dev

# 仅 (A)
python p2/verify.py --node1 http://127.0.0.1:77 --node2 http://127.0.0.1:78 --run-a --skip-b --dev
```
> 凭据亦可用环境变量注入（不依赖 `--dev`）：`SCHED_ORACLE_PWD` / `SCHED_REDIS_PWD` / `SCHED_API_PWD` / `SCHED_ORACLE_USER` / `SCHED_ORACLE_DSN` / `SCHED_REDIS_HOST` / `SCHED_REDIS_PORT` / `SCHED_API_USER`。

---

## 3. (B) Redis 任务变更通知验证

### 3.1 用例
| 用例 | 制造方法 | 期望 |
|------|----------|------|
| B_redis_subscribe | 订阅 `sched:task:change` | 订阅成功（收到 subscribe 确认） |
| B_redis_selftest | 独立发布客户端 publish 一条哨兵消息 | 订阅者在超时内收到该消息（验证总线链路） |
| B_node1_login | `POST /api/auth/login`（张晋宝） | 返回 token，userId=张晋宝 |
| B_node1_create | 节点1 创建 INTERVAL 任务 | HTTP 200 **且**订阅者收到含 userId 的消息 |
| B_node1_update | 节点1 更新该任务 | HTTP 200 **且**订阅者收到含 userId 的消息 |
| B_node1_delete | 节点1 删除该任务 | HTTP 200 **且**订阅者收到含 userId 的消息 |
| B_node2_publish | 若节点2 在线，在节点2 创建任务 | HTTP 200 **且**订阅者收到消息（跨节点发布可达性） |

### 3.2 断言逻辑
- 每个 create/update/delete 后，`wait_for(userId, b_timeout=12s, from_len=操作前消息数)`，断言收到子串匹配 userId 的消息。
- 单节点模式下节点2 不可达 → `B_node2_publish` 记为 **SKIPPED**（不计入失败），跨节点部分在 (A) 阶段由双节点覆盖。

### 3.3 当前结论
- **单节点（18077）实测：6 项执行全部 PASS**（含总线自检与 create/update/delete 通知），连续多次稳定通过。
- 已确认消息体为 `'"张晋宝"'`，子串匹配有效；节点接口与 Redis 发布链路工作正常。

---

## 4. (A) 跨节点不重复触发验证

### 4.1 前置条件（双节点就绪）
1. **清注册视图**（OBS-3）：`DELETE FROM SCHED_NODE; COMMIT;`（消除历史残留 `merged-node-1` / 旧 `dev-node-*` 行）。
2. **启动两个节点**，二者 `shard-total=2` 且 `node-id` 互补（`dev-node-1`→mod 0，`dev-node-2`→mod 1，已验证 hashCode 互补，见 `cluster-readiness.md` OBS-1）。
   - 当前 `application.yml` 为单节点（18077 / `merged-node-1` / `shard-total=1`）且**无 dev profile**；双节点启动需与工程师对齐端口、`node-id`、profile（ teammates 的 `run-node2.sh` 默认 78 / `dev-node-2` / `shard-total=2` / profile=dev，需按当前配置修正）。
3. **确认注册**（期望 2 行 UP，`SHARD_TOTAL=2`，`SHARD_MOD`∈{0,1} 互补）：
   ```sql
   SELECT NODE_ID, SHARD_TOTAL, SHARD_MOD, STATUS FROM SCHED_NODE WHERE STATUS='UP' ORDER BY NODE_ID;
   ```

### 4.2 就绪性门禁（脚本内）
脚本在 (A) 执行前自动校验：
- DB 中恰有 **2 个 `shard-total=2`** 且 `SHARD_MOD` 互补（{0,1}）的 UP 节点；
- 配置的 `node1` / `node2` 基址**均可达**（登录成功）。
- 任一不满足 → `(A)` 标记为 **SKIPPED**（不计入失败），并打印就绪详情。当前单节点环境即命中此分支。

### 4.3 制造方法（覆盖两分片的多用户任务）
> 由于当前登录固定账号（userId 恒为 `张晋宝`，只能落单分片），(A) 直接**落库**多用户触发器，绕过登录、直接驱动调度内核的分片路由：
1. 运行期用 SQL 计算候选 userId 的 `MOD(ORA_HASH(userId),2)`，挑出映射为 **0 与 1** 的两个用户（如 `P2QA_0` / `P2QA_1`）。
2. 对每个分片用户插入 **2 个** INTERVAL(`intervalMin=1`) 任务 + 触发器：
   - `SCHED_TASK`：`STATUS` 默认 `Y`、`MODE='INTERVAL'`、`INTERVAL_MIN=1`、唯一 `NAME`。
   - `SCHED_TRIGGER`：`NEXT_FIRE_TIME=SYSTIMESTAMP`（≤ now，立即进入 claimDue 窗口）、`STATUS='WAITING'`。
3. 等待 `a_duration`（默认 300s ≥ 5min），让触发器跨节点多次到点。

### 4.4 查重 SQL（核心断言：0 行重复）
```sql
-- TODO 侧
SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_TODO
WHERE SOURCE_TASK_ID IN (:ids)
GROUP BY TASK_ID, SCHEDULED_FIRE_TIME HAVING COUNT(*) > 1;

-- FIRE_LOG 侧
SELECT TASK_ID, SCHEDULED_FIRE_TIME, COUNT(*) c FROM SCHED_FIRE_LOG
WHERE TASK_ID IN (:ids)
GROUP BY TASK_ID, SCHEDULED_FIRE_TIME HAVING COUNT(*) > 1;
```
期望：两个查询均 **0 行**（CAS + SKIP LOCKED 双保险，无跨节点重复执行）。

### 4.5 两分片均触发断言
```sql
SELECT DISTINCT MOD(ORA_HASH(USER_ID),2) FROM SCHED_FIRE_LOG WHERE USER_ID IN (:seeded_uids);
```
期望：返回集合 == **{0, 1}**（两个分片各自的节点都抢到了自己分片的触发器并成功执行）。

### 4.6 清理（finally）
删除本次落库的 `SCHED_FIRE_LOG` / `SCHED_TODO` / `SCHED_TRIGGER` / `SCHED_TASK`（按 `:ids`），`COMMIT`，避免污染环境。

### 4.7 当前状态
- 脚本逻辑已实现并通过**就绪性门禁**与**代码路径**自检（单节点下正确判定 not_ready → skip，退出码 0）。
- **尚未在真实双节点环境执行**（当前仅单节点在线）。待工程师按 §4.1 准备双节点后，由 `--run-a` 触发完整 (A) 验证。

---

## 5. 已知问题 / 观察项

| 编号 | 项 | 性质 | 处理 |
|------|----|------|------|
| §5.1 | `DELETE /api/sched/tasks/{id}` 曾返回 500（FK_TRIGGER_TASK NO ACTION，先删父后删子） | 系统 Bug（已修复） | 工程师已调换删除顺序（先子后父），并在节点重启后由 (B) 的 delete 用例验证通过 |
| §5.2 | 分片归属：节点侧 `nodeId.hashCode()` vs 用户侧 `ORA_HASH` 口径是否一致 | 观察项（非 Bug） | `cluster-readiness.md` 已确认当前代码两处一致；如未来改动需回归 |
| §5.3 | `SCHED_NODE.IS_LEADER` 永不回写 'Y'（`register` 写死 'N'，`refreshLeader` 仅维护内存标志） | 可观测性缺口（非功能 Bug） | 选主以"UP 节点 node_id 字典序最小者"为准（即 `dev-node-1`） |
| §5.4 | 端口 / profile 漂移：源码默认 18077 且无 dev profile；teammates 双节点文档假设 77/78 + dev profile | 环境约定不一致 | (A) 启动前需工程师对齐端口/`node-id`/`shard-total`/profile 与当前 `application.yml` |
| §5.5 | 本验证脚本暴露的测试侧缺陷（订阅可靠性、`wait_for` 基线、HTTP 重试） | 测试脚本缺陷（已修复） | 已在 `verify.py` 内修正（见 §2.2） |

---

## 6. 运行结论（截至 2026-10-27）

- **(B) Redis 通知**：✅ 通过。在单节点（18077）上，登录 → 创建/更新/删除任务均能在超时内被订阅者收到携带 userId 的 Redis 消息（总线自检亦通过），多次运行稳定。验证了 `afterCommit` 发布链路与 TriggerEngine 订阅链路可用。
- **(A) 跨节点不重复**：⏸ 待双节点。脚本已实现并就绪性门禁验证通过；因当前仅单节点（`shard-total=1`）在线，(A) 核心用例自动 SKIP，不计入失败。待工程师准备双节点（端口/`node-id`/`shard-total=2` 对齐）后，执行 `--run-a` 即可完成。

---

## 7. 通过标准

| 阶段 | 通过标准 |
|------|----------|
| (B) | B_redis_subscribe / B_redis_selftest / B_node1_login / B_node1_create / update / delete 全部 `pass=true`；节点2 不可达时 `B_node2_publish=skipped` 不计失败 |
| (A) | 双节点就绪；`A_dedup_todo` 与 `A_dedup_fire_log` 查重 **0 行**；`A_both_shards` 分片集合 == {0,1} |
| 总判定 | 无失败项 → `OVERALL: PASS`（或 (A) 因环境未就绪而 skip 时 `PASS(B-ONLY)` / `PARTIAL`）；存在失败项 → `FAIL`，退出码 1 |

---

## 8. 命令速查

```bash
# 依赖
pip install oracledb redis

# 单节点 (B) 自环
python p2/verify.py --node1 http://127.0.0.1:18077 --dev

# 双节点 (A)+(B)
python p2/verify.py --node1 http://127.0.0.1:77 --node2 http://127.0.0.1:78 --run-a --dev

# 仅看 JSON 结果
python p2/verify.py --node1 http://127.0.0.1:18077 --dev | python -c "import sys,json;print(json.dumps([l for l in sys.stdin][-1],ensure_ascii=False))"
```
