param([int]$ExpectedProcessId)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$launcher=Join-Path $taskRoot 'html\launcher\launcher.ps1'
$source=[IO.File]::ReadAllText($launcher).Replace('$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path',('$baseDir = '''+(Split-Path -Parent $launcher).Replace("'","''")+''''))
$source=$source.Replace('[void]$form.ShowDialog()','$timer.Stop()')
$loadWatch=[Diagnostics.Stopwatch]::StartNew()
. ([scriptblock]::Create($source))
$metrics=@{ControlConstructionMs=$loadWatch.ElapsedMilliseconds}
$script:openedAfterLaunch=$true
$taskRestartRequired=$false
try {
    $service=Get-ServiceProcess
    if ($null -eq $service -or $service.Id -ne $ExpectedProcessId) { throw 'Service identity changed; refusing to interrupt another process' }
    $script:serviceCanStop=$true; Set-LauncherState 'running'
    if (-not $stopButton.Enabled) { throw 'Stop button disabled for real running service' }
    $taskRestartRequired=$true
    $stopWatch=[Diagnostics.Stopwatch]::StartNew()
    [void][Windows.Forms.Control].GetMethod('OnClick',[Reflection.BindingFlags]'Instance,NonPublic').Invoke($stopButton,@([EventArgs]::Empty))
    $metrics.StopDispatchMs=$stopWatch.ElapsedMilliseconds
    $deadline=(Get-Date).AddSeconds(15)
    while ($script:serviceJob -and (Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 20 }
    $metrics.StopCompletedMs=$stopWatch.ElapsedMilliseconds
    if (Test-LocalPort) { throw 'Port 3000 remains open after stop' }
    Update-LauncherState
    $deadline=(Get-Date).AddSeconds(15)
    while ($script:serviceJob -and (Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 20 }
    if ($script:currentState -ne 'stopped') { throw 'Stopped state not displayed' }
    # Invoke the real Open button event without automatically opening a browser.
    $startWatch=[Diagnostics.Stopwatch]::StartNew()
    [void][Windows.Forms.Control].GetMethod('OnClick',[Reflection.BindingFlags]'Instance,NonPublic').Invoke($startButton,@([EventArgs]::Empty))
    $script:openedAfterLaunch=$true
    $deadline=(Get-Date).AddSeconds(45)
    while (-not (Test-AppService) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 300 }
    if (-not (Test-AppService)) { throw 'Service did not restart via launcher worker' }
    $metrics.ServiceReadyMs=$startWatch.ElapsedMilliseconds
    Update-LauncherState
    $deadline=(Get-Date).AddSeconds(12)
    while (($script:currentState -ne 'running' -or -not $stopButton.Enabled) -and (Get-Date) -lt $deadline) {
        if(-not $script:serviceJob){Update-LauncherState}
        [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 20
    }
    if ($script:currentState -ne 'running' -or -not $stopButton.Enabled) { throw ('Restarted service is not closable: state='+$script:currentState+', canStop='+$script:serviceCanStop) }
    $worker=Get-ManagedProcess
    if ($null -eq $worker) { throw 'Worker PID was not recorded' }
    $taskRestartRequired=$false
    $metrics.ButtonsReadyMs=$startWatch.ElapsedMilliseconds
    $metrics | ConvertTo-Json | Set-Content (Join-Path $taskRoot 'output/account-launcher-20261003/service-performance.json')
    'Real service stop/restart passed; current launcher worker PID: '+$worker.Id
} finally {
    if ($taskRestartRequired -and -not (Test-LocalPort)) {
        Start-Process -FilePath node -ArgumentList 'dist/main.js' -WorkingDirectory (Join-Path $taskRoot 'server') -WindowStyle Hidden | Out-Null
    }
    $timer.Stop();$dataTimer.Stop();$script:hoverTimer.Stop();$form.Dispose()
}
