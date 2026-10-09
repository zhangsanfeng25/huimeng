param([string]$PreviewPath)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $root 'html\launcher\launcher.ps1'
$preferencePath = Join-Path $root 'html\server\data\launcher-font-scale.txt'
$preferenceExisted = Test-Path $preferencePath
$preferenceText = if ($preferenceExisted) { [IO.File]::ReadAllText($preferencePath) } else { $null }
$source = [IO.File]::ReadAllText($launcher)
$source = $source.Replace('$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path', ('$baseDir = ''' + (Split-Path -Parent $launcher).Replace("'","''") + ''''))
$source = $source.Replace('[void]$form.ShowDialog()', '$timer.Stop()')
$maintenanceSource = [IO.File]::ReadAllText((Join-Path $root 'html\launcher\maintenance-ui.ps1'))
$maintenanceSource = $maintenanceSource.Replace("[System.Windows.Forms.MessageBox]::Show([string]`$_.Exception.Message,'数据管理失败','OK','Error') | Out-Null", '$script:verificationError = $_.Exception.Message')
$source = $source.Replace(". (Join-Path `$baseDir 'maintenance-ui.ps1')", '. ([scriptblock]::Create($maintenanceSource))')
. ([scriptblock]::Create($source))
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('huimeng-native-' + [Guid]::NewGuid().ToString())
[void][IO.Directory]::CreateDirectory($fixture)
$savedEnvironment = @{}
foreach ($key in @('DATABASE_PATH','UPLOAD_DIR','BACKUP_DIR','PORT')) { $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key) }
$env:DATABASE_PATH = Join-Path $fixture 'data.db'
$env:UPLOAD_DIR = Join-Path $fixture 'uploads'
$env:BACKUP_DIR = Join-Path $fixture 'backups with spaces'
$env:PORT = '31997'
function Invoke-NativeDataCheck([string[]]$TaskArguments) {
    $script:verificationData = $null
    Start-DataOperation $TaskArguments { param($result); $script:verificationData = $result }
    $deadline = (Get-Date).AddSeconds(20)
    while ($script:dataJob -and (Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 30 }
    if (-not $script:verificationData) { throw 'Native asynchronous data operation failed' }
    return $script:verificationData
}
try {
    Push-Location (Join-Path $root 'server')
    try {
        & node dist/scripts/migrate.js | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Fixture migration failed' }
        & node dist/scripts/seed.js | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Fixture seed failed' }
    } finally { Pop-Location }
    foreach ($scale in @(0.85,1.0,1.15)) {
        Set-FontScale $scale
        Update-Layout
        $reference = $dataButtons[0].Size
        foreach ($button in $dataButtons) {
            if ($button.Size -ne $reference) { throw 'Data buttons have inconsistent sizes' }
            if ($button.Bottom -gt $form.ClientSize.Height - (Ui 8)) { throw 'Button extends outside window' }
            $textSize = [System.Windows.Forms.TextRenderer]::MeasureText($button.Text,$button.Font)
            if ($textSize.Width -gt $button.Width - (Ui 16) -or $textSize.Height -gt $button.Height - (Ui 6)) { throw 'Button text does not fit' }
        }
        for ($i=0;$i -lt $dataButtons.Count;$i++) { for ($j=$i+1;$j -lt $dataButtons.Count;$j++) { if ($dataButtons[$i].Bounds.IntersectsWith($dataButtons[$j].Bounds)) { throw 'Buttons overlap' } } }
        if ($stopButton.Bottom -gt $dataTitle.Top) { throw 'Service and data sections overlap' }
    }
    if ($PreviewPath) {
        Set-FontScale 1.0; Update-Layout
        $form.StartPosition = 'Manual'; $form.Location = [Drawing.Point]::new(-12000,-12000); $form.Show(); $timer.Stop(); [Windows.Forms.Application]::DoEvents()
        $bitmap = New-Object Drawing.Bitmap($form.Width,$form.Height)
        try { $form.DrawToBitmap($bitmap,[Drawing.Rectangle]::new(0,0,$form.Width,$form.Height)); $bitmap.Save($PreviewPath,[Drawing.Imaging.ImageFormat]::Png) } finally { $bitmap.Dispose() }
    }
    $backupResult = Invoke-NativeDataCheck @('backup')
    $exportResult = Invoke-NativeDataCheck @('export','--output',(Join-Path $fixture 'exports with spaces'))
    if (-not (Test-Path (Join-Path $exportResult.path 'data.json'))) { throw 'Export missing' }
    [void](Invoke-NativeDataCheck @('validate','--source',$backupResult.path))
    [void](Invoke-NativeDataCheck @('restore','--source',$backupResult.path,'--service-stopped'))
    [void](Invoke-NativeDataCheck @('records'))
    $script:recordDialogChecked = $false
    $dialogTimer = New-Object Windows.Forms.Timer
    $dialogTimer.Interval = 100
    $dialogTimer.Add_Tick({
        foreach ($window in [Windows.Forms.Application]::OpenForms) {
            if ($window.Text -like '管理记录查询*') {
                $gridView = $script:recordControls.Grid
                if ($gridView.DataSource -and $gridView.Rows.Count -gt 0 -and -not $script:dataJob) {
                    try {
                        foreach ($size in @($window.Size,$window.MinimumSize)) {
                            $window.Size = $size; $window.PerformLayout()
                            $layout = $gridView.Parent; $filter = $layout.GetControlFromPosition(0,0)
                            if ($filter.Bottom -gt $gridView.Top -or $gridView.Bottom -gt $script:recordControls.Hint.Top) { throw 'Record toolbar or footer overlaps data' }
                            if ($gridView.ColumnHeadersHeight -lt [Windows.Forms.TextRenderer]::MeasureText('时间',$gridView.Font).Height) { throw 'Record header clipped' }
                            if ($PreviewPath) {
                                $bitmap = New-Object Drawing.Bitmap($window.Width,$window.Height)
                                try { $window.DrawToBitmap($bitmap,[Drawing.Rectangle]::new(0,0,$window.Width,$window.Height)); $bitmap.Save(($PreviewPath + '.records.png'),[Drawing.Imaging.ImageFormat]::Png) } finally { $bitmap.Dispose() }
                            }
                        }
                        $script:recordDialogChecked = $true
                    } catch { $script:verificationError = $_.Exception.Message }
                    $dialogTimer.Stop(); $window.Close(); break
                }
            }
        }
    })
    $script:dialogDeadline = (Get-Date).AddSeconds(10)
    $dialogTimer.Add_Tick({ if ((Get-Date) -gt $script:dialogDeadline) { $dialogTimer.Stop(); foreach ($recordWindow in @([Windows.Forms.Application]::OpenForms)) { if ($recordWindow.Text -like '管理记录查询*') { Write-Host ('Record query diagnostic: ' + $script:verificationError); $recordWindow.Close() } } } })
    $dialogTimer.Start()
    Show-ManagementRecords
    $dialogTimer.Dispose()
    if (-not $script:recordDialogChecked) { throw ('Management records check failed: ' + $script:verificationError) }
    $script:logDialogChecked = $false
    $logCheckTimer = New-Object Windows.Forms.Timer; $logCheckTimer.Interval = 100
    $logCheckTimer.Add_Tick({
        foreach ($window in @([Windows.Forms.Application]::OpenForms)) {
            if ($window.Text -like '*运行日志') {
                try {
                    foreach ($size in @($window.Size,$window.MinimumSize)) {
                        $window.Size = $size; $window.PerformLayout()
                        $layout = $window.Controls[0]; $toolbar = $layout.GetControlFromPosition(0,0); $textView = $layout.GetControlFromPosition(0,1)
                        if ($toolbar.Bottom -gt $textView.Top -or $textView.Bottom -gt $window.ClientSize.Height) { throw 'Log toolbar overlaps first line' }
                        foreach ($control in $toolbar.Controls) { if ($control.Bottom -gt $toolbar.ClientSize.Height) { throw 'Log filter clipped' } }
                        if ($PreviewPath) {
                            $bitmap = New-Object Drawing.Bitmap($window.Width,$window.Height)
                            try { $window.DrawToBitmap($bitmap,[Drawing.Rectangle]::new(0,0,$window.Width,$window.Height)); $bitmap.Save(($PreviewPath + '.logs.png'),[Drawing.Imaging.ImageFormat]::Png) } finally { $bitmap.Dispose() }
                        }
                    }
                    $script:logDialogChecked = $true
                } catch { $script:verificationError = $_.Exception.Message }
                $logCheckTimer.Stop(); $window.Close(); break
            }
        }
    })
    $logCheckTimer.Start(); Open-RunLog; $logCheckTimer.Dispose()
    if (-not $script:logDialogChecked) { throw ('Log layout check failed: ' + $script:verificationError) }
    $button = $dataButtons[0]; $originalBounds = $button.Bounds; $normal = $button.BackColor.ToArgb()
    $raiseEnter = [Windows.Forms.Control].GetMethod('OnMouseEnter',[Reflection.BindingFlags]'Instance,NonPublic')
    [void]$raiseEnter.Invoke($button,@([EventArgs]::Empty))
    $deadline = (Get-Date).AddMilliseconds(500)
    while ((Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 16 }
    if ($button.BackColor.ToArgb() -eq $normal -or $button.Bounds -ne $originalBounds -or $script:hoverTimer.Enabled) { throw 'Hover transition failed or changed button bounds' }
    [void][Windows.Forms.Control].GetMethod('OnMouseLeave',[Reflection.BindingFlags]'Instance,NonPublic').Invoke($button,@([EventArgs]::Empty))
    $deadline = (Get-Date).AddMilliseconds(500)
    while ((Get-Date) -lt $deadline) { [Windows.Forms.Application]::DoEvents(); Start-Sleep -Milliseconds 16 }
    if ($button.BackColor.ToArgb() -ne $normal) { throw 'Hover color did not reset' }
    'Control center passed: uniform buttons, non-overlapping records/logs at default and minimum sizes, hover transitions, asynchronous backup/restore/export and query.'
} finally {
    $dataTimer.Stop(); $form.Dispose()
    if ($preferenceExisted) { [IO.File]::WriteAllText($preferencePath,$preferenceText,[Text.Encoding]::ASCII) } else { Remove-Item -LiteralPath $preferencePath -Force -ErrorAction SilentlyContinue }
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key]) }
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    if (-not $resolvedFixture.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture cleanup path invalid' }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
