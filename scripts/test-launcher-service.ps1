$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $taskRoot 'html\launcher\launcher.ps1'
$source = [IO.File]::ReadAllText($launcher)
$source = $source.Replace('$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path', ('$baseDir = ''' + (Split-Path -Parent $launcher).Replace("'","''") + ''''))
$source = $source.Replace('[void]$form.ShowDialog()', '$timer.Stop()')
. ([scriptblock]::Create($source))
# Exercise the real WinForms event handler with controlled process discovery.
$script:fixtureProcess = [pscustomobject]@{ Id=31997; ProcessName='node' }
$script:workerProcess = $null
$script:portOpen = $true
$script:stoppedProcess = 0
$script:killTree = $false
$script:pidPath = Join-Path ([IO.Path]::GetTempPath()) ('huimeng-stop-' + [Guid]::NewGuid() + '.pid')
function Get-ManagedProcess { return $script:workerProcess }
function Get-LocalServerProcess { if ($script:portOpen) { return $script:fixtureProcess }; return $null }
function Test-AppService { return $script:portOpen }
function Test-LocalPort { return $script:portOpen }
function Stop-Process { param([int]$Id,$ErrorAction); $script:stoppedProcess=$Id; $script:portOpen=$false }
function Start-Process { param($FilePath,$ArgumentList,$WindowStyle,[switch]$Wait,[switch]$PassThru); if($FilePath -ne 'taskkill.exe'){throw 'Unexpected process launch'}; $script:killTree=$true; $script:workerProcess=$null; $script:portOpen=$false; return [pscustomobject]@{ExitCode=0} }
try {
    $script:serviceCanStop=$true; Set-LauncherState 'running'
    if (-not $stopButton.Enabled) { throw 'Running service discovered without worker PID must allow closing' }
    if ($stopButton.Cursor -ne [Windows.Forms.Cursors]::Hand) { throw 'Stop button lacks pointer feedback' }
    $normal = $stopButton.BackColor.ToArgb()
    [void][Windows.Forms.Control].GetMethod('OnMouseEnter',[Reflection.BindingFlags]'Instance,NonPublic').Invoke($stopButton,@([EventArgs]::Empty))
    $deadline = (Get-Date).AddMilliseconds(500)
    while ((Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 16 }
    if ($stopButton.BackColor.ToArgb() -eq $normal) { throw 'Stop button hover does not change color' }
    Stop-AppService; $script:serviceCanStop=$false; Set-LauncherState 'stopped'
    if ($script:stoppedProcess -ne 31997 -or $script:currentState -ne 'stopped') { throw 'Stop click did not close independently started server' }
    $script:workerProcess=[pscustomobject]@{Id=31998;ProcessName='cmd'}; $script:portOpen=$true
    Set-LauncherState 'running'
    Stop-AppService; $script:serviceCanStop=$false; Set-LauncherState 'stopped'
    if (-not $script:killTree -or $script:currentState -ne 'stopped') { throw 'Worker service tree did not close' }
    if ($stopButton.Enabled) { throw 'Stopped service must disable close button' }
    'Launcher service checks passed: PID fallback, stop click, worker tree, hover feedback, stopped state.'
} finally { $timer.Stop(); $dataTimer.Stop(); $script:hoverTimer.Stop(); $form.Dispose(); Remove-Item -LiteralPath $script:pidPath -Force -ErrorAction SilentlyContinue }
