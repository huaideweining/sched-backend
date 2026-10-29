#!/usr/bin/env bash
# run-node2.sh — 启动 sched-backend 第二个集群节点（兼容 Linux / macOS / Git Bash）
#
# 用途：以 port=18078 / node-id=dev-node-2 / shard-total=2 启动，复用同一个 Oracle + Redis。
#
# 安全约定：本脚本【不硬编码】任何数据库 / Redis / JWT 口令。
#          Oracle、Redis、JWT 等凭据全部从 base application.yml（默认 profile）继承；
#          本脚本仅覆盖三项调度相关参数：server.port / scheduler.node-id / scheduler.shard-total。
#
# 端口说明（重要）：不使用 77/78。
#          - 77（以及 78 等同段端口）在 Chromium/Edge 的封锁端口黑名单（ERR_UNSAFE_PORT），
#            浏览器 fetch 会直接拒连（curl 不受限，易误判为正常）；
#          - node-1 已固定在 18077（见 application.yml 端口安全说明）。
#          故两节点沿用 18077（node-1）/ 18078（node-2），均落在黑名单外。
#
# 用法（在项目根目录 E:/工作平台/sched-backend 下执行）：
#   bash p2/run-node2.sh
#
# 可选环境变量覆盖（默认见下；仅用于切换值，绝不用于填口令）：
#   SCHED_PORT=18078 SCHED_NODE_ID=dev-node-2 SCHED_SHARD_TOTAL=2 \
#   SCHED_ONLINE=1 bash p2/run-node2.sh

set -euo pipefail

# 切到项目根（p2/ 的上一级）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"
echo "project root = $PROJECT_ROOT"

PORT="${SCHED_PORT:-18078}"
NODE_ID="${SCHED_NODE_ID:-dev-node-2}"
SHARD_TOTAL="${SCHED_SHARD_TOTAL:-2}"

OFFLINE_FLAG="-o"
if [ "${SCHED_ONLINE:-0}" = "1" ]; then OFFLINE_FLAG=""; fi

# 预检：端口是否被占用
if command -v nc >/dev/null 2>&1; then
  if nc -z 127.0.0.1 "$PORT" 2>/dev/null; then
    echo "WARN: 端口 $PORT 已被占用，请先释放后再启动 node-2。" >&2
    exit 2
  fi
fi

echo "=================================================="
echo " sched-backend NODE-2"
echo "   port        = $PORT"
echo "   node-id     = $NODE_ID"
echo "   shard-total = $SHARD_TOTAL"
echo "   profile     = default (凭据继承自 base application.yml，脚本不出现明文)"
echo "=================================================="

# 启动方式 A（推荐，无需先打包）：mvn spring-boot:run
#   -o 离线依赖本机已缓存依赖（node-1 构建过即可）；需在线则 SCHED_ONLINE=1
#   仅覆盖三项调度参数；其余凭据继承 base application.yml（默认 profile）
mvn $OFFLINE_FLAG \
  -Dspring-boot.run.arguments="--server.port=$PORT --scheduler.node-id=$NODE_ID --scheduler.shard-total=$SHARD_TOTAL" \
  spring-boot:run

# 启动方式 B（已 mvn -o package 打包后）：取消注释改用 java -jar
# jar=$(ls -t target/sched-backend-*.jar 2>/dev/null | head -n1)
# java -jar "$jar" --server.port="$PORT" --scheduler.node-id="$NODE_ID" --scheduler.shard-total="$SHARD_TOTAL"
