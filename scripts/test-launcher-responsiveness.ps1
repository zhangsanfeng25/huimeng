$ErrorActionPreference='Stop'
$launcher=Join-Path (Split-Path -Parent $PSScriptRoot) 'html/launcher/launcher.ps1'
$source=[IO.File]::ReadAllText($launcher).Replace('$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path',('$baseDir = '''+(Split-Path -Parent $launcher)+''''))
$source=$source.Replace('[void]$form.ShowDialog()','$timer.Stop()')
. ([scriptblock]::Create($source))
try {
    Set-LauncherState 'starting'
    if($timer.Interval -ne 250){throw 'Startup readiness polling must use 250ms'}
    Set-LauncherState 'stopped'
    if($timer.Interval -ne 2000){throw 'Stable readiness polling must use 2000ms'}
    # A slow operating-system query must never run on the WinForms thread.
    function Get-ManagedProcess { return $null }
    function Test-LocalPort { Start-Sleep -Milliseconds 2000; return $false }
    $script:serviceFunctions += "`nfunction Get-ManagedProcess { return `$null }; function Test-LocalPort { Start-Sleep -Milliseconds 2000; return `$false }"
    $watch=[Diagnostics.Stopwatch]::StartNew()
    Update-LauncherState
    $jobIdentity=$script:serviceJob
    1..4 | ForEach-Object {Update-LauncherState}
    if(-not [object]::ReferenceEquals($jobIdentity,$script:serviceJob)){throw 'Overlapping service status tasks created'}
    $watch.Stop()
    if ($watch.ElapsedMilliseconds -gt 150) { throw ('Status refresh blocks UI for '+$watch.ElapsedMilliseconds+'ms') }
    $script:pulses=0
    $pulse=New-Object Windows.Forms.Timer; $pulse.Interval=25
    $pulse.Add_Tick({$script:pulses++}); $pulse.Start()
    $deadline=(Get-Date).AddSeconds(6)
    while ($script:serviceJob -and (Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 10 }
    $pulse.Stop()
    if ($script:serviceJob) { throw 'Background status query did not finish' }
    if ($script:pulses -lt 20) { throw 'UI heartbeat stalled during background query' }
    $script:serviceFunctions += "`nfunction Stop-AppService { Start-Sleep -Milliseconds 2000 }"
    $script:serviceCanStop=$true; Set-LauncherState 'running'
    $watch.Restart()
    [void][Windows.Forms.Control].GetMethod('OnClick',[Reflection.BindingFlags]'Instance,NonPublic').Invoke($stopButton,@([EventArgs]::Empty))
    $watch.Stop()
    if ($watch.ElapsedMilliseconds -gt 150) { throw 'Stop click blocks the UI' }
    if ($startButton.Enabled -or $stopButton.Enabled) { throw 'Duplicate start/stop remains enabled during shutdown' }
    $script:pulses=0; $pulse.Start()
    $deadline=(Get-Date).AddSeconds(8)
    while ($script:serviceJob -and (Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 10 }
    $pulse.Stop()
    if ($script:serviceJob -or $script:currentState -ne 'stopped' -or $script:pulses -lt 20) { throw 'Background shutdown stalled UI or failed to display stopped state' }
    $pulse.Dispose()
    'Launcher responsiveness passed: nonblocking refresh, '+$script:pulses+' UI heartbeats during delayed query.'
} finally { $timer.Stop();$dataTimer.Stop();$script:hoverTimer.Stop(); if($serviceTimer){$serviceTimer.Stop()}; $form.Dispose() }
