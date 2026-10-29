# P2 双节点集群就绪度评估（源码核对版）

> 评估人：工程师「寇豆码」｜范围：ClusterManager / Dispatcher / SchedulerBootstrap / RedisTriggerPubSub / TriggerEngine / SchedulerConfig / SchedTriggerMapper
> 结论先行：**双节点联调无需改动任何业务代码**，仅需把 `scheduler.shard-total` 改为 `2` 并给两个实例不同的 `scheduler.node-id`。下面给出"已就绪项"与"需留意的观察项"。

---

## 0. 结论

- **无需改业务代码即可跑 P2 双节点。** 当前源码在分片、抢占、选主、Redis 通知四方面均已"集群就绪"。
- 只需配置层两处改动（非代码）：两个实例都设置 `scheduler.shard-total=2`，且 `scheduler.node-id` 互不相同（推荐 `dev-node-1` / `dev-node-2`）。
- 存在 **3 个观察项（OBS-1~3）**，都不是正确性 bug、不阻断 P2，但 QA 在验证与选主确认环节需按本文处理；另有 **1 个配置纪律要求（OBS-4）** 必须强制遵守。

---

## 1. 已就绪项（无需改动）

| # | 能力 | 源码依据 | 说明 |
|---|------|----------|------|
| R1 | 节点注册幂等 | `ClusterManager.register()` P1-B 修复：`DuplicateKeyException` 分支改 upsert `update`（仅刷新心跳/状态），重启不再 ORA-00001 | 双节点反复启停安全 |
| R2 | 扫描兜底分片化 | `Dispatcher.scanDue` → `SchedTriggerMapper.claimDue`：`MOD(ORA_HASH(t.USER_ID), #{shardTotal}) = #{shardMod}` + `FOR UPDATE SKIP LOCKED` | 每个节点只抢自己分片的到期触发器；同一条被并发抢时 SKIP LOCKED 保证仅一个节点拿到，**天然不重复** |
| R3 | 时间轮装载已做分片过滤 | `TriggerEngine.loadWindow()` 第 46~48 行：`if (Math.floorMod(t.getUserId().hashCode(), shardTotal) != shardMod) continue;` | **注意**：任务中假设的"loadWindow 未过滤、两节点都装全部触发器"在当前代码中**不成立**——已正确按分片过滤。因此不会出现"两节点都装全量"的低效场景 |
| R4 | 时间轮到点 CAS 防重复 | `SchedulerBootstrap.wheelWorker` → `Dispatcher.tryAcquireAndRun` → `compareAndSet(WAITING→ACQUIRED)`（自动提交，不持锁） | 即便两个实例对同一触发器同时尝试，CAS 使仅一个成功 |
| R5 | 漏触发恢复也分片化 | `MisfireRecovery.java` 第 36 行同样用 `floorMod(userId.hashCode(), shardTotal) == shardMod` 过滤 | 与扫描/时间轮口径一致，无遗漏 |
| R6 | 分片参数来源统一 | `ClusterManager` 的 `shardTotal` / `shardMod` 来自 `SchedulerConfig.getShardTotalValue()` 与 `nodeId.hashCode()`；`Dispatcher` / `TriggerEngine` / `MisfireRecovery` 均读 `clusterManager.getShardTotal()/getShardMod()` | 运行期分片值唯一来源是内存中的 ClusterManager，全链路一致 |
| R7 | 抢占与执行分离（R11 已修复） | `scanDue` 仅 `@Transactional` 抢占+置 ACQUIRED（提交即释放行锁），`runClaimed` 在事务外逐个 `jobExecutor.execute` | 多节点下也不会因持锁导致扫描路径死锁 |
| R8 | Redis 跨节点实时通知 | `RedisTriggerPubSub.publishTaskChange(userId)` → `convertAndSend("sched:task:change", userId)`；`TriggerEngine.onMessage` 订阅后 `loadWindow()` 重载；`TaskServiceImpl` 经 `afterCommit` 发布（R10 修复） | 任一节点改任务，两节点都会重新装载对应窗口触发器；发布在事务提交后，无跨资源死锁 |
| R9 | 选主逻辑存在 | `ClusterManager.refreshLeader()`：UP 节点中 `nodeId` 字典序最小者为 leader（内存 `leader` 标志） | 双节点下 `dev-node-1` < `dev-node-2`，故 node-1 为 leader |

> 综上：分片算法（`userId.hashCode() % shardTotal` 对应用户侧、`nodeId.hashCode() % shardTotal` 对节点侧）一致且闭环；CAS + SKIP LOCKED 双保险防重复执行。P2 联调只差"把 shard-total 从 1 调到 2"。

---

## 2. 观察项（非 bug，但需 QA / 主理人知晓）

### OBS-1｜分片归属依赖 `nodeId.hashCode() % shardTotal`，存在碰撞可能
- 节点分片：`shardMod = Math.floorMod(nodeId.hashCode(), shardTotal)`，**各节点独立按自己 nodeId 计算**。
- 若两个 `node-id` 的 hashCode 对 `shardTotal` 取模结果相同 → 该 shard 被两个节点重复覆盖，而另一个 shard 无人覆盖 → **未被覆盖分片的用户触发器永远不会被触发**（不会重复，但会漏触发）。
- 实测（`dev-node-1`/`dev-node-2`）：
  - `dev-node-1` → mod **0**，`dev-node-2` → mod **1** → 互补，**安全**。
  - 反例：`dev-node-1`(0) 与 `dev-node-3`(0) 会碰撞；`node-1`(0) 与 `node-2`(1) 互补但命名不统一。
- **建议**：双节点固定使用 `dev-node-1` / `dev-node-2`（已验证互补）；启动后**务必用 SQL 核对** `SCHED_NODE.SHARD_MOD` 是否覆盖 `0..shardTotal-1` 且不重复（见 dual-node-procedure.md §5）。

### OBS-2｜`is_leader` 列不会被置为 'Y'，无法用 DB 列确认选主
- `register()` 写死 `isLeader("N")`；`refreshLeader()` 只设置**内存** `leader` 标志，**从不回写** `SCHED_NODE.IS_LEADER` 列。
- 因此 `SELECT COUNT(*) FROM SCHED_NODE WHERE IS_LEADER='Y'` **恒为 0**，不能据此确认"一个 leader=true"。
- **这是可观测性缺口，非功能 bug**：当前调度逻辑并不依赖 leader 标志（无"仅 leader 执行"的强约束），所以不影响触发正确性。
- **确认选主的正确方式**（二选一）：
  1. 确定性规则：`UP` 节点中 `node_id` 字典序最小者即 leader → `dev-node-1`。
  2. 用 SQL 复刻 `refreshLeader` 逻辑：
     ```sql
     SELECT NODE_ID FROM SCHED_NODE
     WHERE STATUS='UP'
     ORDER BY NODE_ID ASC
     FETCH FIRST 1 ROWS ONLY;   -- 12c+ 语法；老版本用 ROWNUM<=1
     ```
  3. 调试用：在 `ClusterManager.refreshLeader()`（第 91 行）临时加日志打印 `leader` 与 `nodeId`（**不改动主流程**，纯观察）。
- **需主理人决策/知会**：若 P2 验收要求"DB 中可见 leader 标记"，则需新增"回写 IS_LEADER"的小改动（建议由主理人评估后再排期，本次不擅自改源码）。

### OBS-3｜重启再注册时 `SCHED_NODE.SHARD_TOTAL/SHARD_MOD` 列可能残留旧值
- 若某 `node_id` 曾以旧 `shardTotal`（例如单节点时的 `1`）注册过，idempotent upsert 分支只更新心跳/状态，**不会更新 shardTotal/shardMod 列** → DB 列残留旧值。
- 运行期用的是内存值（`ClusterManager` 启动时按当前配置重算），**功能不受影响**；但会让"按 DB 列核对分片"产生误导。
- **建议**：双节点联调开始前执行 `DELETE FROM SCHED_NODE; COMMIT;`，让两节点以干净状态重新注册（见 dual-node-procedure.md §3 前置清理）。

### OBS-4（配置纪律，必须）｜两个节点都必须 `shard-total=2`
- 若 node-1 仍沿用单节点的 `shard-total=1` 而 node-2 用 `2`：
  - node-1：`MOD(ORA_HASH(USER_ID),1)=0` 恒成立 → **抢走全部触发器**并把全部装进时间轮；
  - node-2：只抢 `MOD=1` 的一半。
  - 结果：SKIP LOCKED/CAS 仍能防重复（不会双发），但**分片失效、node-1 过载**，违背双节点初衷。
- **结论**：node-1（port 18077）必须**重启**并同样带 `scheduler.shard-total=2`，不能保持 `1`。详见 dual-node-procedure.md §4。

---

## 3. 关于任务中"loadWindow 未做 shard 过滤"的假设

任务描述里作为可能观察项提到的"loadWindow 未做 shard 过滤导致两节点都装载全部触发器"——**当前代码中不成立**：`TriggerEngine.loadWindow()` 第 46~48 行已显式按 `floorMod(userId.hashCode(), shardTotal) == shardMod` 过滤，每个节点只把**自己分片**的触发器装入时间轮。

即便历史上曾存在"未过滤"版本，由于 `tryAcquireAndRun` 用的是 `compareAndSet` 单条 CAS（而非无脑执行），两节点对同一触发器同时到点也只会成功一次，**不会重复触发**；最多是效率上的重复装载（且当前已修复）。因此：无需为此改源码，无正确性风险。

---

## 4. 交付物清单（本批 p2/ 新增）
- `p2/cluster-readiness.md`（本文件，就绪度评估）
- `p2/run-node2.ps1`（Windows 启动 node-2 脚本）
- `p2/run-node2.sh`（兼容 shell 启动 node-2 脚本）
- `p2/dual-node-procedure.md`（双节点运行步骤 / 确认 / 回退）

> 全部为新增文件，未触碰任何业务源码、未提交 SVN、未触碰 `target/`。
