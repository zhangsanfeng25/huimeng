. (Join-Path $PSScriptRoot 'log-utils.ps1')
$dataDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'server\data'
$logsDir = Join-Path $dataDir 'logs'
$logPath = Join-Path $dataDir 'launcher.log'
$serviceLogPath = Join-Path $dataDir 'service.log'
$script:lastLogCleanup = [DateTime]::MinValue
function Write-LauncherLog([string]$message) { Write-Warning $message }
Remove-ExpiredLogs
