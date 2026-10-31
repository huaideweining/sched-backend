#!/usr/bin/env bash
#
# verify-cors.sh — 调度后端 CORS 运行时验证 + 可选重启脚本
#
# 背景：前端 个人办公工作台.html 以 127.0.0.1:8088 / localhost:8088 / 部署态 IP:8088 打开时，
#       发给 18077 后端的请求 Origin 必须被 CORS 白名单覆盖，否则预检(OPTIONS) 403 且不返回
#       Access-Control-Allow-Origin，前端实时数据被浏览器拦截（右下角「调度服务缓存加载失败」）。
#       Phase C 修复：将 allowedOriginPatterns 外部化为 itwb.session.cors-allowed-origins
#       （默认覆盖上面三类 8088 来源），本脚本用于「运行时验证该修复是否生效」。
#
# 用法：
#   ./verify-cors.sh                 # 仅健康检查（假设后端已在运行）
#   RESTART=1 ./verify-cors.sh       # 先重启再健康检查
#   DO_BUILD=1 RESTART=1 ./verify-cors.sh   # 重启前先用 Maven 重新打包
#
# 健康检查（必做）：对每类来源、每个前端实际调用接口发起 CORS 预检(OPTIONS)，
#       断言返回 200 且响应头含 Access-Control-Allow-Origin（与请求 Origin 对应）。
#
# 重启（可选，需部署环境密钥）：停旧后端 -> （可选 Maven 构建）-> 以 ITWB_* 环境变量启动
#       -> 等 /actuator/health 就绪 -> 跑健康检查。重启所需变量由调用方提供，切勿提交密钥：
#         - JAVA_HOME 指向 JDK18（或 java 已在 PATH）
#         - BACKEND_CP：完整 classpath（target/classes + 本地 maven 仓库 jars）
#         - JVM_OPTS（可选，默认 -Xms512m -Xmx1g）
#         - ITWB_DB_PASSWORD / ITWB_REDIS_PASSWORD / ITWB_AUTH_PASSWORD / ITWB_TOKEN_SECRET
#       可直接 source 一个本地 .cors-verify.env（已在 .gitignore 中排除）：
#         BACKEND_CP='E:/工作平台/sched-backend/target/classes;D:/maven-Repository/...'
#         export ITWB_DB_PASSWORD='***'  (其余同理)
#
set -uo pipefail

# ---------- 配置（可被环境变量覆盖） ----------
BACKEND_HOST="${BACKEND_HOST:-172.16.132.64}"
BACKEND_PORT="${BACKEND_PORT:-18077}"
WEB_PORT="${WEB_PORT:-8088}"
# 受测的三类 8088 来源（与 itwb.session.cors-allowed-origins 默认一致）
ORIGINS=(
  "http://${BACKEND_HOST}:${WEB_PORT}"
  "http://127.0.0.1:${WEB_PORT}"
  "http://localhost:${WEB_PORT}"
)
# 受测接口（前端实际失败的正是 /api/sched/**）
TEST_PATHS=(
  "/api/sched/tasks"
  "/api/sched/todos"
  "/api/auth/login"
)
HEALTH_PATH="/actuator/health"
MAIN_CLASS="com.appliance.itwb.ItwbSessionApplication"

# 若本地存在环境变量文件则加载（不入库）
if [ -f "$(dirname "$0")/.cors-verify.env" ]; then
  # shellcheck disable=SC1091
  source "$(dirname "$0")/.cors-verify.env"
fi

# ---------- 工具函数 ----------
log()  { printf '[verify-cors] %s\n' "$*"; }
pass() { printf '  \033[32m[PASS]\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m[FAIL]\033[0m %s\n' "$*"; }

http_status() { # $1=headers
  printf '%s' "$1" | head -n1 | grep -oE '[0-9]{3}' | head -n1
}
header_val() { # $1=headers $2=header-name(lowercase)
  printf '%s' "$1" | tr -d '\r' | awk -F': ' "tolower(\$1)==\"$2\"{print \$2; exit}"
}

# ---------- 健康检查 ----------
health_check() {
  log "=== CORS 预检健康检查（后端 ${BACKEND_HOST}:${BACKEND_PORT}）==="
  local all_ok=1

  # 正控制：健康检查端点应 200
  local h_resp
  h_resp=$(curl -s -o /dev/null -w '%{http_code}' --noproxy "${BACKEND_HOST}" \
    "http://${BACKEND_HOST}:${BACKEND_PORT}${HEALTH_PATH}" || true)
  if [ "$h_resp" = "200" ]; then pass "actuator/health -> $h_resp"; else fail "actuator/health -> ${h_resp:-无响应}"; all_ok=0; fi

  for origin in "${ORIGINS[@]}"; do
    log "Origin = ${origin}"
    for path in "${TEST_PATHS[@]}"; do
      local hdrs status acao
      hdrs=$(curl -s -o /dev/null -D - -X OPTIONS \
        --noproxy "${BACKEND_HOST}" \
        "http://${BACKEND_HOST}:${BACKEND_PORT}${path}" \
        -H "Origin: ${origin}" \
        -H "Access-Control-Request-Method: GET" \
        -H "Access-Control-Request-Headers: Authorization" 2>/dev/null || true)
      status=$(http_status "$hdrs")
      acao=$(header_val "$hdrs" "access-control-allow-origin")
      if [ "$status" = "200" ] && [ -n "$acao" ]; then
        pass "${path} 预检 ${status}  ACAO=${acao}"
      else
        fail "${path} 预检 ${status:-无响应}  ACAO=${acao:-缺失}"
        all_ok=0
      fi
    done
  done

  if [ "$all_ok" -eq 1 ]; then
    log "结论：所有来源/接口 CORS 预检通过 [OK]"
  else
    log "结论：存在未通过项 [FAIL]（若后端仍为旧代码，127.0.0.1/localhost 预期 403 无 ACAO）"
  fi
  # 退出码：全部通过=0；存在失败=1（供 CI/自动化判定）
  [ "$all_ok" -eq 1 ] && return 0 || return 1
}

# ---------- 重启（可选） ----------
find_backend_pid() {
  # 用 netstat 找监听 BACKEND_PORT 的 PID（避免调用 PowerShell）
  netstat -ano 2>/dev/null | awk -v p=":${BACKEND_PORT}" '$1 ~ /TCP/ && $2 ~ p && $4=="LISTENING"{print $5; exit}'
}

stop_backend() {
  local pid
  pid=$(find_backend_pid || true)
  if [ -n "$pid" ]; then
    log "停止旧后端 PID=${pid}"
    taskkill //PID "$pid" //F >/dev/null 2>&1 || true
    sleep 2
  else
    log "未发现监听 ${BACKEND_PORT} 的后端进程"
  fi
}

build_backend() {
  if [ "${DO_BUILD:-0}" = "1" ]; then
    log "Maven 重新打包（跳过测试）..."
    if command -v mvnw >/dev/null 2>&1; then ./mvnw -o package -DskipTests
    elif command -v mvn >/dev/null 2>&1; then mvn -o package -DskipTests
    else log "未找到 mvn/mvnw，跳过构建（请确保 target/classes 已含最新 CORS 修复）"; fi
  else
    log "跳过构建（DO_BUILD!=1）；请确保 target/classes 已含最新 CORS 修复"
  fi
}

start_backend() {
  if [ -z "${BACKEND_CP:-}" ]; then
    log "未设置 BACKEND_CP，无法自动启动；请手动以含 ITWB_* 的环境启动后端后重跑本脚本（不带 RESTART）"
    return 0
  fi
  log "启动后端（server.port=${BACKEND_PORT}）..."
  local jvm_opts="${JVM_OPTS:--Xms512m -Xmx1g}"
  nohup java -cp "$BACKEND_CP" $jvm_opts \
    "$MAIN_CLASS" --server.port="${BACKEND_PORT}" \
    > backend.log 2>&1 &
  # 等待就绪
  local i=0
  until curl -s -o /dev/null --noproxy "${BACKEND_HOST}" "http://${BACKEND_HOST}:${BACKEND_PORT}${HEALTH_PATH}"; do
    i=$((i+1)); [ "$i" -ge 60 ] && { log "等待后端就绪超时（见 backend.log）"; return 1; }; sleep 2
  done
  log "后端已就绪"
}

# ---------- 主流程 ----------
if [ "${RESTART:-0}" = "1" ]; then
  stop_backend
  build_backend
  start_backend || true
fi

health_check
rc=$?
exit $rc
