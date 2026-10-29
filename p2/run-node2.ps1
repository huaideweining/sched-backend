# run-node2.ps1 — 启动 sched-backend 第二个集群节点（Windows / PowerShell）
#
# 用途：以 port=18078 / node-id=dev-node-2 / shard-total=2 启动，复用同一个 Oracle + Redis。
#
# 安全约定：本脚本【不硬编码】任何数据库 / Redis / JWT 口令。
#          Oracle、Redis、JWT 等凭据全部从 base application.yml（默认 profile）继承；
#          本脚本仅覆盖三项调度相关参数：server.port / scheduler.node-id / scheduler.shard-total。
#
# 端口说明（重要）：不使用 77/78。77（及同段端口）在 Chromium/Edge 封锁端口黑名单
#          （ERR_UNSAFE_PORT），浏览器 fetch 会直接拒连；node-1 已固定在 18077。
#          两节点沿用 18077（node-1）/ 18078（node-2），均落黑名单外。
#
# 用法（在项目根目录 E:\工作平台\sched-backend 下执行）：
#   powershell -ExecutionPolicy Bypass -File p2\run-node2.ps1
#
# 可选环境变量覆盖（默认见下；仅用于切换值，绝不用于填口令）：
#   $env:SCHED_PORT        = '18078'
#   $env:SCHED_NODE_ID     = 'dev-node-2'
#   $env:SCHED_SHARD_TOTAL = '2'
#   $env:SCHED_ONLINE      = '1'      # 设为 1 则去掉 mvn -o 离线模式（默认离线）

$ErrorActionPreference = 'Stop'

# ---- 解析脚本目录并把工作目录切到项目根（p2/ 的上一级）----
$scriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
Write-Host "project root = $projectRoot"

# ---- 参数（环境变量覆盖；默认值按 P2 双节点要求）----
$port       = if ($env:SCHED_PORT)        { $env:SCHED_PORT }        else { '18078' }
$nodeId     = if ($env:SCHED_NODE_ID)     { $env:SCHED_NODE_ID }     else { 'dev-node-2' }
$shardTotal = if ($env:SCHED_SHARD_TOTAL) { $env:SCHED_SHARD_TOTAL } else { '2' }

# ---- 预检：mvn 是否可用 ----
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 mvn，请先安装 Maven 并加入 PATH，或使用 p2/run-node2.sh 的 java -jar 方式。"
    exit 3
}

# ---- 预检：端口是否被占用 ----
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $iar = $tcp.BeginConnect('127.0.0.1', [int]$port, $null, $null)
    $ok  = $iar.AsyncWaitHandle.WaitOne(500)
    if ($ok -and $tcp.Connected) {
        Write-Warning "端口 $port 已被占用，请先释放（例如结束占用进程）后再启动 node-2。"
        exit 2
    }
} catch {
    # 未占用，继续
} finally {
    if ($tcp) { $tcp.Close() }
}

Write-Host "=================================================="
Write-Host " sched-backend NODE-2"
Write-Host "   port        = $port"
Write-Host "   node-id     = $nodeId"
Write-Host "   shard-total = $shardTotal"
Write-Host "   profile     = default (凭据继承自 base application.yml，脚本不出现明文)"
Write-Host "=================================================="

# ---- 启动：mvn spring-boot:run ----
# 离线模式（-o）依赖本机已缓存依赖（node-1 构建过即可）；
# 如需在线，设置 $env:SCHED_ONLINE=1。
# 仅覆盖三项调度参数；其余凭据继承 base application.yml（默认 profile）
$mvnArgs = [System.Collections.Generic.List[string]]::new()
if ($env:SCHED_ONLINE -ne '1') { $mvnArgs.Add('-o') }
$mvnArgs.Add("-Dspring-boot.run.arguments=--server.port=$port --scheduler.node-id=$nodeId --scheduler.shard-total=$shardTotal")
$mvnArgs.Add('spring-boot:run')

Write-Host "exec: mvn $($mvnArgs -join ' ')"
& mvn @mvnArgs

# 备选（无需联网/已打包）：取消下面注释改用 java -jar
# $jar = (Get-ChildItem target\sched-backend-*.jar | Sort-Object LastWriteTime | Select-Object -Last 1).FullName
# java -jar $jar --server.port=$port --scheduler.node-id=$nodeId --scheduler.shard-total=$shardTotal
