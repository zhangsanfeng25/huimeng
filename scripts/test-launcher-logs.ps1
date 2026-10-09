$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../html/launcher/log-utils.ps1')
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ('huimeng-log-test-' + [guid]::NewGuid())
[void](New-Item -ItemType Directory $fixture)
try {
    $logsDir = $fixture
    $logPath = Join-Path $fixture 'launcher.log'
    $serviceLogPath = Join-Path $fixture 'service.log'
    $legacyStamp = '[周五 ' + (Get-Date).AddMinutes(-3).ToString('dd/MM/yyyy HH:mm:ss.ff', [Globalization.CultureInfo]::InvariantCulture) + ']'
    [System.IO.File]::WriteAllText($serviceLogPath, "$legacyStamp Building admin website...`nnpm error No workspaces found:`nnpm error --workspace=html`nERROR: Project build failed.`n", [System.Text.Encoding]::UTF8)
    $actual = Get-RunLogs 'Debug'
    if ($actual -notmatch ([regex]::Escape($legacyStamp) + ' npm error No workspaces found:')) { throw 'DEBUG must include errors with their preceding timestamp' }
    if ($actual -notmatch ([regex]::Escape($legacyStamp) + ' ERROR: Project build failed\.')) { throw 'DEBUG must preserve the final failure' }
    $errorsOnly = Get-RunLogs '错误'
    if ($errorsOnly -match 'Building admin website') { throw 'Error filter must exclude info' }
    $old = (Get-Date).AddDays(-4).ToString('yyyy-MM-dd HH:mm:ss')
    $recent = (Get-Date).AddMinutes(-2).ToString('yyyy-MM-dd HH:mm:ss')
    $latest = (Get-Date).AddMinutes(-1).ToString('yyyy-MM-dd HH:mm:ss')
    $hourFile = Join-Path $fixture ('service-' + (Get-Date).ToString('yyyyMMdd-HH') + '.log')
    [IO.File]::WriteAllText($hourFile, "[$old] [ERROR] expired`n[$recent] [INFO] recent`n[$latest] [DEBUG] latest`n", [Text.Encoding]::UTF8)
    $actual = Get-RunLogs '全部'
    if ($actual -match 'expired') { throw 'Records older than three days must be excluded by timestamp' }
    if ($actual.IndexOf('latest') -gt $actual.IndexOf('recent')) { throw 'Newest records must appear first' }
    $dataDir = $fixture
    $script:lastLogCleanup = [DateTime]::MinValue
    function Write-LauncherLog([string]$message) { throw $message }
    $expiredFile = Join-Path $fixture ('startup-' + (Get-Date).AddDays(-4).ToString('yyyyMMdd-HHmmss') + '.log')
    [IO.File]::WriteAllText($expiredFile, "[$old] old startup")
    Remove-ExpiredLogs
    if (Test-Path $expiredFile) { throw 'Old startup files must be deleted even if recently copied' }
    if ((Get-Content $hourFile -Raw) -match 'expired') { throw 'Old entries in mixed logs must be removed' }
    if ((Get-Content $hourFile -Raw) -notmatch 'latest') { throw 'Cleanup must retain recent records' }
    Write-Output 'Launcher log regression checks passed: dates, filters, newest first, three-day deletion.'
} finally { Remove-Item -LiteralPath $fixture -Recurse -Force }
