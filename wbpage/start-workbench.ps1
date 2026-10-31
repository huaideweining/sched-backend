<#
 .SYNOPSIS
    Workbench static server one-click launcher (idempotent)
 .DESCRIPTION
    - Resolves WorkBuddy-bundled node.exe dynamically (prefer versions\current, then newest version dir; fallback to PATH node)
    - Default port 8088; root = script's own directory (repo wbpage/)
    - If port already held by OUR serve.js -> skip; if held by another process -> warn and exit
    - Starts detached from the launching session (survives WorkBuddy session end)
 .PARAMETER Port
    Listen port, default 8088
#>
param(
  [int]$Port = 8088
)

$ErrorActionPreference = 'Stop'

# root = directory of this script (repo wbpage/)
$Root = Split-Path -Parent $MyInvocation.MyCommand.Definition

# resolve node.exe
$nodeBin = $null
$candidateBase = Join-Path $env:USERPROFILE ".workbuddy\binaries\node\versions"
$candidateDirs = @(Join-Path $candidateBase "current")
if (Test-Path $candidateBase) {
  $candidateDirs += Get-ChildItem $candidateBase -Directory | Select-Object -ExpandProperty FullName
}
foreach ($d in $candidateDirs) {
  $p = Join-Path $d "node.exe"
  if (Test-Path $p) { $nodeBin = $p; break }
}
if (-not $nodeBin) {
  $nodeBin = (Get-Command node -ErrorAction SilentlyContinue).Source
}
if (-not $nodeBin -or -not (Test-Path $nodeBin)) {
  Write-Error "node.exe not found. Please ensure WorkBuddy is installed or hardcode node path at top."
  exit 1
}

# port occupancy check (idempotent)
$listen = @(Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' })
if ($listen.Count -gt 0) {
  $pidv = $listen[0].OwningProcess
  $proc = Get-Process -Id $pidv -ErrorAction SilentlyContinue
  if ($proc -and ($proc.CommandLine -like "*serve.js*")) {
    Write-Output "[$(Get-Date -Format HH:mm:ss)] Port $Port already served by this server (PID $pidv), skip."
    exit 0
  } else {
    Write-Output "[$(Get-Date -Format HH:mm:ss)] Port $Port occupied by another process (PID $pidv). Release it first."
    exit 1
  }
}

# Clean proxy env vars: Node on Windows throws a duplicate-key error when both
# lowercase and uppercase proxy vars exist (http_proxy vs HTTP_PROXY).
Remove-Item Env:http_proxy -ErrorAction SilentlyContinue
Remove-Item Env:HTTP_PROXY -ErrorAction SilentlyContinue
Remove-Item Env:https_proxy -ErrorAction SilentlyContinue
Remove-Item Env:HTTPS_PROXY -ErrorAction SilentlyContinue

# start detached
$logOut = Join-Path $Root "serve.log"
$logErr = Join-Path $Root "serve.err.log"
try {
  Start-Process -FilePath $nodeBin `
    -ArgumentList "`"$(Join-Path $Root 'serve.js')`"" `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $logOut `
    -RedirectStandardError $logErr `
    -WindowStyle Hidden
  Start-Sleep -Milliseconds 800
  $up = @(Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' })
  if ($up.Count -gt 0) {
    Write-Output "[$(Get-Date -Format HH:mm:ss)] Started: http://127.0.0.1:$Port/%E4%B8%AA%E4%BA%BA%E5%8A%9E%E5%85%AC%E5%B7%A5%E4%BD%9C%E5%8F%B0.html"
    Write-Output "For LAN use, replace 127.0.0.1 with your intranet IP. Log: $logOut"
  } else {
    Write-Output "[$(Get-Date -Format HH:mm:ss)] Started but port $Port not listening. Check $logErr"
    exit 1
  }
} catch {
  Write-Error "Start failed: $_"
  exit 1
}
