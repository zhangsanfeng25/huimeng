# Declare DPI awareness before WinForms creates any windows. Windows would
# otherwise bitmap-stretch this process at 150%/200% desktop scaling.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class HuiMengDpi {
    [DllImport("user32.dll")] private static extern bool SetProcessDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] private static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] private static extern uint GetDpiForSystem();
    [DllImport("user32.dll")] public static extern bool DestroyIcon(IntPtr icon);
    public static void Enable() {
        try { if (SetProcessDpiAwarenessContext(new IntPtr(-4))) return; }
        catch (EntryPointNotFoundException) { }
        try { SetProcessDPIAware(); } catch (EntryPointNotFoundException) { }
    }
    public static uint Dpi() {
        try { return GetDpiForSystem(); }
        catch (EntryPointNotFoundException) { return 96; }
    }
}
'@
[HuiMengDpi]::Enable()
$script:dpiScale = [Math]::Max(1.0, [Math]::Min(3.0, ([double][HuiMengDpi]::Dpi() / 96.0)))

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()
[System.Windows.Forms.Application]::SetCompatibleTextRenderingDefault($false)

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$htmlRoot = Split-Path -Parent $baseDir
$projectRoot = Split-Path -Parent $htmlRoot
$workerPath = Join-Path $baseDir 'launcher-worker.cmd'
$dataDir = Join-Path $htmlRoot 'server\data'
$pidPath = Join-Path $dataDir 'launcher.pid'
$logPath = Join-Path $dataDir 'launcher.log'
$serviceLogPath = Join-Path $dataDir 'service.log'
$logsDir = Join-Path $dataDir 'logs'
$logVersionMarker = Join-Path $dataDir 'launcher-log-v2.ready'
$fontScalePath = Join-Path $dataDir 'launcher-font-scale.txt'
. (Join-Path $baseDir 'log-utils.ps1')
$siteUrl = 'http://localhost:3000/login'
$knownNodePath = 'D:\ruanjian\node.js\node.exe'
$script:launchAttempted = $false
$script:openedAfterLaunch = $false
$script:currentState = 'stopped'
$script:fontScale = 1.0
$script:verifiedWorkerPid = 0
$script:lastLogCleanup = [DateTime]::MinValue

if (-not (Test-Path $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
}
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

# On the first run of this release, preserve any older mixed-encoding log in a
# dated backup. Start a new UTF-8 BOM log so Notepad reads Chinese correctly.
$script:logEncoding = New-Object System.Text.UTF8Encoding($true)
$logMigrationSucceeded = $true
if (-not (Test-Path $logVersionMarker) -and (Test-Path $logPath)) {
    try {
        $legacyLog = Join-Path $dataDir ('launcher-legacy-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        Move-Item -LiteralPath $logPath -Destination $legacyLog
    } catch { $logMigrationSucceeded = $false }
}
if (-not (Test-Path $logPath)) {
    [System.IO.File]::WriteAllText($logPath, '', $script:logEncoding)
}
if ($logMigrationSucceeded -and -not (Test-Path $logVersionMarker)) {
    [System.IO.File]::WriteAllText($logVersionMarker, 'UTF-8', (New-Object System.Text.UTF8Encoding($false)))
}

function Write-LauncherLog([string]$message) {
    try {
        $time = Get-Date
        $hourPath = Join-Path $logsDir ('launcher-' + $time.ToString('yyyyMMdd-HH') + '.log')
        $line = '[' + $time.ToString('yyyy-MM-dd HH:mm:ss') + '] [INFO] ' + $message + [Environment]::NewLine
        [System.IO.File]::AppendAllText($hourPath, $line, $script:logEncoding)
    } catch { }
}



Write-LauncherLog '控制中心已打开。日志编码：UTF-8。'
Remove-ExpiredLogs

function Get-UiColor([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Ui([double]$value) {
    return [int][Math]::Round($value * $script:dpiScale)
}
function Ui-Point([double]$x, [double]$y) {
    return [System.Drawing.Point]::new((Ui $x), (Ui $y))
}
function Ui-Size([double]$width, [double]$height) {
    return [System.Drawing.Size]::new((Ui $width), (Ui $height))
}
function New-PixelFont([string]$family, [float]$size, [System.Drawing.FontStyle]$style) {
    return [System.Drawing.Font]::new($family, [float](Ui $size), $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Set-ControlFont($control, [float]$size, [System.Drawing.FontStyle]$style, [string]$family = 'Microsoft YaHei UI') {
    # WinForms can still paint using its previous Font after assignment. Disposing
    # it here made bold labels and buttons render as red-cross error placeholders.
    $control.Font = New-PixelFont $family ([float][Math]::Round($size * $script:fontScale, 1)) $style
}

function Set-RoundedRegion($control, [int]$radius) {
    if ($control.Width -le 0 -or $control.Height -le 0) { return }
    $diameter = (Ui $radius) * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $diameter, $diameter, 180, 90)
    $path.AddArc($control.Width - $diameter - 1, 0, $diameter, $diameter, 270, 90)
    $path.AddArc($control.Width - $diameter - 1, $control.Height - $diameter - 1, $diameter, $diameter, 0, 90)
    $path.AddArc(0, $control.Height - $diameter - 1, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    # The control may still use the previous Region during an asynchronous paint.
    $control.Region = New-Object System.Drawing.Region($path)
    $path.Dispose()
}

function New-FixedLabel(
    [string]$text,
    [int]$x,
    [int]$y,
    [int]$width,
    [int]$height,
    [float]$fontSize,
    [System.Drawing.FontStyle]$style,
    [string]$color
) {
    $label = New-Object System.Windows.Forms.Label
    $label.Text = $text
    $label.Location = Ui-Point $x $y
    $label.Size = Ui-Size $width $height
    $label.AutoSize = $false
    $label.AutoEllipsis = $true
    $label.TextAlign = [System.Drawing.ContentAlignment]::MiddleLeft
    $label.Font = New-PixelFont 'Microsoft YaHei UI' $fontSize $style
    $label.ForeColor = Get-UiColor $color
    $label.BackColor = [System.Drawing.Color]::Transparent
    return $label
}

function New-RoundedBrandBitmap([string]$path) {
    $image = [System.Drawing.Image]::FromFile($path)
    $bitmap = [System.Drawing.Bitmap]::new(256, 256)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $clip = [System.Drawing.Drawing2D.GraphicsPath]::new()
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $diameter = 122
        $clip.AddArc(0,0,$diameter,$diameter,180,90)
        $clip.AddArc(255-$diameter,0,$diameter,$diameter,270,90)
        $clip.AddArc(255-$diameter,255-$diameter,$diameter,$diameter,0,90)
        $clip.AddArc(0,255-$diameter,$diameter,$diameter,90,90)
        $clip.CloseFigure()
        $graphics.SetClip($clip)
        $graphics.DrawImage($image,0,0,256,256)
        return $bitmap
    } catch {
        $bitmap.Dispose()
        throw
    } finally {
        $clip.Dispose();$graphics.Dispose();$image.Dispose()
    }
}

function New-RoundedWindowIcon([string]$path) {
    $bitmap = New-RoundedBrandBitmap $path
    $handle = [IntPtr]::Zero
    try {
        $handle = $bitmap.GetHicon()
        $temporary = [System.Drawing.Icon]::FromHandle($handle)
        try { return $temporary.Clone() } finally { $temporary.Dispose() }
    } finally {
        if ($handle -ne [IntPtr]::Zero) { [void][HuiMengDpi]::DestroyIcon($handle) }
        $bitmap.Dispose()
    }
}

$script:hoverStates = @{}
$script:hoverTimer = New-Object System.Windows.Forms.Timer
$script:hoverTimer.Interval = 16
$script:hoverTimer.Add_Tick({
    $moving = $false
    foreach ($button in @($script:hoverStates.Keys)) {
        if ($button.IsDisposed) { $script:hoverStates.Remove($button); continue }
        $state = $script:hoverStates[$button]
        $target = if ($button.Enabled) { $state.Target } else { $state.Normal }
        $current = $button.BackColor
        $channels = @()
        foreach ($channel in @('R','G','B')) {
            $delta = $target.$channel - $current.$channel
            $channels += if ([Math]::Abs($delta) -le 1) { $target.$channel } else { [int]($current.$channel + [Math]::Sign($delta) * [Math]::Max(1,[Math]::Ceiling([Math]::Abs($delta) * 0.3))) }
        }
        $color = [System.Drawing.Color]::FromArgb($channels[0],$channels[1],$channels[2])
        $button.BackColor = $color
        $button.FlatAppearance.MouseOverBackColor = $color
        if ($color.ToArgb() -ne $target.ToArgb()) { $moving = $true }
    }
    if (-not $moving) { $script:hoverTimer.Stop() }
})
function Add-ButtonHover($button, [string]$normal, [string]$hover) {
    $script:hoverStates[$button] = @{ Normal=(Get-UiColor $normal); Hover=(Get-UiColor $hover); Target=(Get-UiColor $normal) }
    $button.FlatStyle = 'Flat'
    $button.FlatAppearance.BorderSize = 0
    $button.UseVisualStyleBackColor = $false
    $button.BackColor = Get-UiColor $normal
    $button.Cursor = [System.Windows.Forms.Cursors]::Hand
    $button.Add_MouseEnter({ param($sender,$event); $script:hoverStates[$sender].Target = $script:hoverStates[$sender].Hover; $script:hoverTimer.Start() })
    $button.Add_MouseLeave({ param($sender,$event); $script:hoverStates[$sender].Target = $script:hoverStates[$sender].Normal; $script:hoverTimer.Start() })
    $button.Add_EnabledChanged({ param($sender,$event); if (-not $sender.Enabled) { $script:hoverStates[$sender].Target = $script:hoverStates[$sender].Normal }; $script:hoverTimer.Start() })
    $button.Add_Disposed({ param($sender,$event); $script:hoverStates.Remove($sender) })
}
function New-ServiceButton([string]$text, [string]$background, [string]$foreground, [string]$hover) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $text
    $button.Size = Ui-Size 245 54
    $button.Font = New-PixelFont 'Microsoft YaHei UI' 17 ([System.Drawing.FontStyle]::Regular)
    $button.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $button.FlatAppearance.BorderSize = 0
    $button.FlatAppearance.MouseOverBackColor = Get-UiColor $hover
    $button.FlatAppearance.MouseDownBackColor = Get-UiColor $hover
    $button.UseVisualStyleBackColor = $false
    $button.BackColor = Get-UiColor $background
    $button.ForeColor = Get-UiColor $foreground
    $button.Cursor = [System.Windows.Forms.Cursors]::Hand
    Add-ButtonHover $button $background $hover
    return $button
}

. (Join-Path $baseDir 'service-process.ps1')
$script:serviceFunctions = [IO.File]::ReadAllText((Join-Path $baseDir 'service-process.ps1'))
$script:serviceJob = $null
$script:serviceCanStop = $false
$script:localServerPid = 0
$script:serviceRunspace = [RunspaceFactory]::CreateRunspace()
$script:serviceRunspace.Open()

$form = New-Object System.Windows.Forms.Form
$form.SuspendLayout()
[Windows.Forms.Control].GetProperty('DoubleBuffered',[Reflection.BindingFlags]'Instance,NonPublic').SetValue($form,$true,$null)
$form.Text = '绘梦儿童管理网站 · 服务控制中心'
$form.ClientSize = Ui-Size 560 565
$form.MinimumSize = Ui-Size 560 605
$form.MaximumSize = Ui-Size 1000 780
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::Sizable
$form.SizeGripStyle = [System.Windows.Forms.SizeGripStyle]::Show
$form.MaximizeBox = $false
$form.MinimizeBox = $true
$form.BackColor = Get-UiColor '#F7F6FC'
$form.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::None
$form.Font = New-PixelFont 'Microsoft YaHei UI' 14 ([System.Drawing.FontStyle]::Regular)
$windowIconPath = Join-Path $htmlRoot 'public\images\launcher_icon-c3.png'
if (Test-Path $windowIconPath) { $form.Icon = New-RoundedWindowIcon $windowIconPath }

$header = New-Object System.Windows.Forms.Panel
$header.Dock = [System.Windows.Forms.DockStyle]::Top
$header.Height = Ui 88
$header.BackColor = Get-UiColor '#473B9C'
$form.Controls.Add($header)

$header.Add_Paint({
    param($sender, $eventArgs)
    $eventArgs.Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $rect = New-Object System.Drawing.Rectangle(0, 0, $sender.Width, $sender.Height)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        (Get-UiColor '#34276F'),
        (Get-UiColor '#7666DA'),
        0
    )
    $eventArgs.Graphics.FillRectangle($brush, $rect)
    $brush.Dispose()
})

$iconBox = New-Object System.Windows.Forms.PictureBox
$iconBox.Location = Ui-Point 22 17
$iconBox.Size = Ui-Size 54 54
$iconBox.SizeMode = [System.Windows.Forms.PictureBoxSizeMode]::Zoom
$iconBox.BackColor = [System.Drawing.Color]::Transparent
$iconPath = Join-Path $htmlRoot 'public\images\launcher_icon-c3.png'
if (Test-Path $iconPath) {
    try { $iconBox.Image = New-RoundedBrandBitmap $iconPath } catch { }
}
$header.Controls.Add($iconBox)

$brandTitle = New-FixedLabel '绘梦儿童管理网站' 92 9 360 39 27 ([System.Drawing.FontStyle]::Regular) '#FFFFFF'
$header.Controls.Add($brandTitle)

$brandSubtitle = New-FixedLabel '本地服务控制中心' 94 47 280 25 14 ([System.Drawing.FontStyle]::Regular) '#E3DFFB'
$header.Controls.Add($brandSubtitle)

$settingsButton = New-Object System.Windows.Forms.Button
$settingsButton.Text = [char]0x2699
$settingsButton.AccessibleName = '设置'
$settingsButton.Location = Ui-Point 484 19
$settingsButton.Size = Ui-Size 52 38
$settingsButton.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Right
$settingsButton.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$settingsButton.FlatAppearance.BorderSize = 0
$settingsButton.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(38, 255, 255, 255)
$settingsButton.FlatAppearance.MouseDownBackColor = [System.Drawing.Color]::FromArgb(52, 255, 255, 255)
$settingsButton.BackColor = [System.Drawing.Color]::Transparent
$settingsButton.ForeColor = [System.Drawing.Color]::White
$settingsButton.Font = New-PixelFont 'Segoe UI Symbol' 20 ([System.Drawing.FontStyle]::Regular)
$settingsButton.Cursor = [System.Windows.Forms.Cursors]::Hand
Add-ButtonHover $settingsButton '#6556B8' '#8473D3'
$header.Controls.Add($settingsButton)

$settingsTip = New-Object System.Windows.Forms.ToolTip
$settingsTip.SetToolTip($settingsButton, '设置')

$sectionTitle = New-FixedLabel '服务状态' 24 98 180 30 19 ([System.Drawing.FontStyle]::Regular) '#302A45'
$form.Controls.Add($sectionTitle)

$statusCard = New-Object System.Windows.Forms.Panel
$statusCard.Location = Ui-Point 24 132
$statusCard.Size = Ui-Size 512 110
$statusCard.BackColor = [System.Drawing.Color]::White
$statusCard.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right
$form.Controls.Add($statusCard)

$statusDot = New-Object System.Windows.Forms.Panel
$statusDot.Location = Ui-Point 22 27
$statusDot.Size = Ui-Size 14 14
$statusDot.BackColor = Get-UiColor '#A29EAE'
$statusCard.Controls.Add($statusDot)

$statusLabel = New-FixedLabel '正在检查服务状态…' 50 12 330 38 21 ([System.Drawing.FontStyle]::Regular) '#2F2A42'
$statusLabel.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right
$statusCard.Controls.Add($statusLabel)

$statusHint = New-FixedLabel '请稍候' 50 51 438 30 14 ([System.Drawing.FontStyle]::Regular) '#7E788E'
$statusHint.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right
$statusCard.Controls.Add($statusHint)

$statusBadge = New-FixedLabel '检测中' 410 18 78 30 14 ([System.Drawing.FontStyle]::Regular) '#6557BC'
$statusBadge.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter
$statusBadge.BackColor = Get-UiColor '#F0EEFC'
$statusBadge.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Right
$statusCard.Controls.Add($statusBadge)

$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Location = Ui-Point 50 92
$progress.Size = Ui-Size 438 5
$progress.Style = [System.Windows.Forms.ProgressBarStyle]::Marquee
$progress.MarqueeAnimationSpeed = 24
$progress.Visible = $false
$progress.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right
$statusCard.Controls.Add($progress)

$startButton = New-ServiceButton '打开服务' '#6F60D7' '#FFFFFF' '#5E50C4'
$stopButton = New-ServiceButton '关闭服务' '#FDECEE' '#B43B4D' '#F8DDE2'
$startButton.Location = Ui-Point 24 258
$stopButton.Location = Ui-Point 287 258
$form.Controls.Add($startButton)
$form.Controls.Add($stopButton)
$settingsTip.SetToolTip($stopButton, '关闭网站与 Android 共用服务；关闭网页不会自动停止服务')

. (Join-Path $baseDir 'maintenance-ui.ps1')
$runLogButton = New-ServiceButton '运行日志' '#EEEAFB' '#5848AD' '#E2DCF6'
$runLogButton.Add_Click({ Open-RunLog })
$form.Controls.Add($runLogButton)
$dataHint.Top = Ui 525

function Update-Layout {
    $clientWidth = $form.ClientSize.Width
    $clientHeight = $form.ClientSize.Height
    $brandTitle.Width = [Math]::Max((Ui 210), $settingsButton.Left - $brandTitle.Left - (Ui 12))
    $statusCard.Width = [Math]::Max((Ui 452), $clientWidth - (Ui 48))
    $statusBadge.Left = $statusCard.Width - (Ui 102)
    $statusLabel.Width = [Math]::Max((Ui 220), $statusBadge.Left - (Ui 62))
    $statusHint.Width = [Math]::Max((Ui 360), $statusCard.Width - (Ui 74))
    $progress.Width = [Math]::Max((Ui 360), $statusCard.Width - (Ui 74))

    $gutter = Ui 14
    $buttonsWidth = $clientWidth - (Ui 48)
    $buttonWidth = [int](($buttonsWidth - $gutter) / 2)
    $buttonTop = Ui 258
    $startButton.Location = [System.Drawing.Point]::new((Ui 24), [int]$buttonTop)
    $startButton.Size = [System.Drawing.Size]::new($buttonWidth, (Ui 52))
    $stopButton.Location = [System.Drawing.Point]::new([int]((Ui 24) + $buttonWidth + $gutter), [int]$buttonTop)
    $stopButton.Size = [System.Drawing.Size]::new($buttonWidth, (Ui 52))

    for ($i = 0; $i -lt $dataButtons.Count; $i++) {
        $dataButtons[$i].Location = [System.Drawing.Point]::new([int]((Ui 24) + ($i % 2) * ($buttonWidth + $gutter)), (Ui (366 + [Math]::Floor($i / 2) * 50)))
        $dataButtons[$i].Size = [System.Drawing.Size]::new($buttonWidth, (Ui 42))
        Set-ControlFont $dataButtons[$i] 14 ([System.Drawing.FontStyle]::Regular)
        Set-RoundedRegion $dataButtons[$i] 10
    }
    Set-RoundedRegion $statusCard 14
    Set-RoundedRegion $iconBox 13
    Set-RoundedRegion $statusDot 7
    Set-RoundedRegion $statusBadge 10
    Set-RoundedRegion $settingsButton 10
    Set-RoundedRegion $startButton 12
    Set-RoundedRegion $stopButton 12
    $runLogButton.Location = Ui-Point 24 466
    $runLogButton.Size = [Drawing.Size]::new($buttonsWidth,(Ui 42))
    Set-ControlFont $runLogButton 14 ([Drawing.FontStyle]::Regular)
    Set-RoundedRegion $runLogButton 10
}

function Set-FontScale([double]$scale) {
    $script:fontScale = $scale
    Set-ControlFont $brandTitle 27 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $brandSubtitle 14 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $sectionTitle 19 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $statusLabel 21 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $statusHint 14 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $statusBadge 14 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $startButton 17 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $stopButton 17 ([System.Drawing.FontStyle]::Regular)
    Set-ControlFont $runLogButton 14 ([System.Drawing.FontStyle]::Regular)
    Set-Content -Path $fontScalePath -Value $scale.ToString([System.Globalization.CultureInfo]::InvariantCulture) -Encoding Ascii
}





function Open-RunLog {
    Write-LauncherLog '打开运行日志查看器。'
    $logForm = New-Object System.Windows.Forms.Form
    $logForm.Text = '绘梦儿童管理网站 · 运行日志'
    $logForm.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterParent
    $logForm.ClientSize = Ui-Size 760 510
    $logForm.MinimumSize = Ui-Size 600 400
    $logForm.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::None
    $logForm.BackColor = [System.Drawing.Color]::White

    $logLayout = New-Object System.Windows.Forms.TableLayoutPanel
    $logLayout.Dock = 'Fill'; $logLayout.ColumnCount = 1; $logLayout.RowCount = 2
    [void]$logLayout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('AutoSize')))
    [void]$logLayout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('Percent',100)))
    $logToolbar = New-Object System.Windows.Forms.FlowLayoutPanel
    $logToolbar.Dock = 'Fill'; $logToolbar.AutoSize = $true; $logToolbar.AutoSizeMode = 'GrowAndShrink'
    $logToolbar.Padding = New-Object System.Windows.Forms.Padding((Ui 8))
    $logForm.Controls.Add($logLayout)
    $logLayout.Controls.Add($logToolbar,0,0)
    $refreshButton = New-Object System.Windows.Forms.Button
    $refreshButton.Text = '刷新日志'
    $refreshButton.Location = Ui-Point 14 8
    $refreshButton.Size = Ui-Size 110 32
    $refreshButton.Font = New-PixelFont 'Microsoft YaHei UI' 14 ([System.Drawing.FontStyle]::Regular)
    Add-ButtonHover $refreshButton '#EEEAFB' '#DCD4F4'
    $logToolbar.Controls.Add($refreshButton)
    $levelFilter = New-Object System.Windows.Forms.ComboBox
    $levelFilter.DropDownStyle = [System.Windows.Forms.ComboBoxStyle]::DropDownList
    $levelFilter.Location = Ui-Point 140 9
    $levelFilter.Size = Ui-Size 130 32
    $levelFilter.Font = New-PixelFont 'Microsoft YaHei UI' 13 ([System.Drawing.FontStyle]::Regular)
    [void]$levelFilter.Items.AddRange(@('全部', '错误', 'Debug'))
    $levelFilter.SelectedIndex = 0
    $logToolbar.Controls.Add($levelFilter)
    $retentionLabel = New-FixedLabel '保留三天 · 最新在顶部 · 最多 3000 行' 290 7 400 34 12 ([System.Drawing.FontStyle]::Regular) '#7E788E'
    $retentionLabel.Width = Ui 290
    $logToolbar.Controls.Add($retentionLabel)

    $logView = New-Object System.Windows.Forms.TextBox
    $logView.Multiline = $true
    $logView.ReadOnly = $true
    $logView.ScrollBars = [System.Windows.Forms.ScrollBars]::Both
    $logView.WordWrap = $false
    $logView.Dock = [System.Windows.Forms.DockStyle]::Fill
    $logView.Font = New-PixelFont 'Consolas' 14 ([System.Drawing.FontStyle]::Regular)
    $logLayout.Controls.Add($logView,0,1)

    $reloadLogs = {
        Remove-ExpiredLogs
        $logView.Text = Get-RunLogs ([string]$levelFilter.SelectedItem)
        $logView.SelectionStart = 0
        $logView.ScrollToCaret()
    }
    $refreshButton.Add_Click($reloadLogs)
    $levelFilter.Add_SelectedIndexChanged($reloadLogs)
    & $reloadLogs
    [void]$logForm.ShowDialog($form)
    $logForm.Dispose()
}

$settingsMenu = New-Object System.Windows.Forms.ContextMenuStrip
$settingsMenu.Font = New-PixelFont 'Microsoft YaHei UI' 14 ([System.Drawing.FontStyle]::Regular)
$fontMenu = New-Object System.Windows.Forms.ToolStripMenuItem('界面字体大小')
$fontSmall = New-Object System.Windows.Forms.ToolStripMenuItem('小（85%）')
$fontNormal = New-Object System.Windows.Forms.ToolStripMenuItem('标准（100%）')
$fontLarge = New-Object System.Windows.Forms.ToolStripMenuItem('大（115%）')
[void]$fontMenu.DropDownItems.Add($fontSmall)
[void]$fontMenu.DropDownItems.Add($fontNormal)
[void]$fontMenu.DropDownItems.Add($fontLarge)
[void]$settingsMenu.Items.Add($fontMenu)
[void]$settingsMenu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))
$menuLog = New-Object System.Windows.Forms.ToolStripMenuItem('查看运行日志')
[void]$settingsMenu.Items.Add($menuLog)

function Select-FontScale([double]$scale) {
    $fontSmall.Checked = $scale -eq 0.85
    $fontNormal.Checked = $scale -eq 1.0
    $fontLarge.Checked = $scale -eq 1.15
    Set-FontScale $scale
}

$fontSmall.Add_Click({ Select-FontScale 0.85 })
$fontNormal.Add_Click({ Select-FontScale 1.0 })
$fontLarge.Add_Click({ Select-FontScale 1.15 })
$menuLog.Add_Click({ Open-RunLog })
$settingsButton.Add_Click({ $settingsMenu.Show($settingsButton, 0, $settingsButton.Height) })

function Set-LauncherState([string]$state) {
    if ($state -ne $script:currentState) {
        switch ($state) {
            'running'  { Write-LauncherLog '统一服务运行中：网站与 Android 模拟器共用商城 API。' }
            'starting' { Write-LauncherLog '正在准备数据库并启动网站与 App 共用服务。' }
            'failed'   { Write-LauncherLog '启动失败。请检查上面的 Node.js 错误信息。' }
            'occupied' { Write-LauncherLog '启动失败：端口 3000 已被其他程序占用。' }
            default    { Write-LauncherLog '网站与 App 共用服务已关闭。' }
        }
    }
    $script:currentState = $state
    $progress.Visible = $false
    $startButton.Text = '打开服务'
    switch ($state) {
        'running' {
            $statusDot.BackColor = Get-UiColor '#2FAE85'
            $statusLabel.Text = '服务正在运行'
            $statusHint.Text = '网站与 Android 模拟器共用服务'
            $statusBadge.Text = '运行中'
            $statusBadge.BackColor = Get-UiColor '#E6F7F1'
            $statusBadge.ForeColor = Get-UiColor '#218A69'
            $startButton.Enabled = $true
            $stopButton.Enabled = $script:serviceCanStop
        }
        'starting' {
            $statusDot.BackColor = Get-UiColor '#E9A639'
            $statusLabel.Text = '正在打开服务'
            $statusHint.Text = '首次运行会安装依赖并初始化数据'
            $statusBadge.Text = '启动中'
            $statusBadge.BackColor = Get-UiColor '#FFF4DD'
            $statusBadge.ForeColor = Get-UiColor '#B77A18'
            $progress.Visible = $true
            $startButton.Enabled = $false
            $stopButton.Enabled = $true
        }
        'occupied' {
            $statusDot.BackColor = Get-UiColor '#D65660'
            $statusLabel.Text = '端口 3000 已被占用'
            $statusHint.Text = '旧网站服务占用端口时，请先关闭它'
            $statusBadge.Text = '端口冲突'
            $statusBadge.BackColor = Get-UiColor '#FDECEE'
            $statusBadge.ForeColor = Get-UiColor '#B94351'
            $startButton.Enabled = $false
            $stopButton.Enabled = $script:serviceCanStop
        }
        'failed' {
            $statusDot.BackColor = Get-UiColor '#D65660'
            $statusLabel.Text = '服务打开失败'
            $statusHint.Text = '可从右上角设置中查看运行日志'
            $statusBadge.Text = '启动失败'
            $statusBadge.BackColor = Get-UiColor '#FDECEE'
            $statusBadge.ForeColor = Get-UiColor '#B94351'
            $startButton.Text = '重新打开'
            $startButton.Enabled = $true
            $stopButton.Enabled = $false
        }
        default {
            $statusDot.BackColor = Get-UiColor '#A29EAE'
            $statusLabel.Text = '服务尚未打开'
            $statusHint.Text = '点击“打开服务”，网页将自动显示'
            $statusBadge.Text = '已关闭'
            $statusBadge.BackColor = Get-UiColor '#F0EEFC'
            $statusBadge.ForeColor = Get-UiColor '#6557BC'
            $startButton.Enabled = $true
            $stopButton.Enabled = $false
        }
    }
}

function Start-ServiceOperation([string]$action) {
    if ($script:serviceJob) { return }
    $pipeline = [PowerShell]::Create()
    $pipeline.Runspace = $script:serviceRunspace
    [void]$pipeline.AddScript($script:serviceFunctions).AddScript({
        param($action,$baseDir,$pidPath,$workerPath,$verifiedPid,$localPid,$launchAttempted)
        $script:verifiedWorkerPid=$verifiedPid; $script:localServerPid=$localPid
        if ($action -eq 'stop') { Stop-AppService; $launchAttempted=$false; $script:localServerPid=0 }
        Read-ServiceState $launchAttempted
    }).AddArgument($action).AddArgument($baseDir).AddArgument($pidPath).AddArgument($workerPath).AddArgument($script:verifiedWorkerPid).AddArgument($script:localServerPid).AddArgument($script:launchAttempted)
    $script:serviceJob = @{ Pipeline=$pipeline; Handle=$pipeline.BeginInvoke(); Action=$action }
    $serviceTimer.Start()
}
function Update-LauncherState { Start-ServiceOperation 'status' }

$serviceTimer = New-Object System.Windows.Forms.Timer
$serviceTimer.Interval = 50
$serviceTimer.Add_Tick({
    if (-not $script:serviceJob -or -not $script:serviceJob.Handle.IsCompleted) { return }
    $job=$script:serviceJob; $script:serviceJob=$null; $serviceTimer.Stop()
    try {
        $result=@($job.Pipeline.EndInvoke($job.Handle)) | Select-Object -Last 1
        if ($job.Pipeline.HadErrors -or -not $result) { throw ($job.Pipeline.Streams.Error | Out-String) }
        $script:verifiedWorkerPid=[int]$result.VerifiedWorkerPid
        $script:localServerPid=[int]$result.LocalServerPid
        $script:serviceCanStop=$result.CanStop
        if ($job.Action -eq 'stop') {
            $script:launchAttempted=$false; $script:openedAfterLaunch=$false
            Write-LauncherLog '已确认共用服务关闭。'
        }
        Set-LauncherState $result.State
        if ($result.State -eq 'running' -and $script:launchAttempted -and -not $script:openedAfterLaunch) {
            $script:openedAfterLaunch=$true; Start-Process $siteUrl
        }
    } catch {
        Write-LauncherLog ('服务状态操作失败：'+$_.Exception.Message)
        $statusHint.Text='操作未完成，请从设置查看运行日志后重试'
        $startButton.Enabled=$true; $stopButton.Enabled=$script:serviceCanStop
    } finally {
        $job.Pipeline.Dispose(); $stopButton.Text='关闭服务'
        if ($script:dataJob) { $startButton.Enabled=$false; $stopButton.Enabled=$false }
        $pending=$script:pendingServiceAction; $script:pendingServiceAction=$null
        if($pending -eq 'stop') { $stopButton.PerformClick() } elseif($pending -eq 'start') { $startButton.PerformClick() }
    }
})

$startButton.Add_Click({
    if ($script:currentState -eq 'running') {
        Start-Process $siteUrl
        return
    }
    if ($script:serviceJob) { if($script:serviceJob.Action -eq 'status') { $script:pendingServiceAction='start' }; return }
    if ($script:currentState -eq 'starting') { return }
    if ($script:currentState -eq 'occupied') {
        Set-LauncherState 'occupied'
        return
    }
    $pathNodeAvailable = Test-Path $knownNodePath
    $systemNodeAvailable = $null -ne (Get-Command node.exe -ErrorAction SilentlyContinue)
    if (-not $pathNodeAvailable -and -not $systemNodeAvailable) {
        Write-LauncherLog '启动失败：未找到 Node.js，请检查安装目录或系统 PATH。'
        [System.Windows.Forms.MessageBox]::Show(
            "未找到 Node.js。`r`n程序会优先检查：D:\ruanjian\node.js\node.exe",
            '缺少 Node.js',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning
        ) | Out-Null
        return
    }
    $script:launchAttempted = $true
    $script:openedAfterLaunch = $false
    Set-LauncherState 'starting'
    try {
        Write-LauncherLog ('统一服务目录：' + $projectRoot)
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $env:ComSpec
        $info.Arguments = '/d /s /c ""' + $workerPath + '""'
        $info.WorkingDirectory = $htmlRoot
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $process = [System.Diagnostics.Process]::Start($info)
        Set-Content -Path $pidPath -Value $process.Id -Encoding Ascii
    } catch {
        Write-LauncherLog ('启动进程失败：' + $_.Exception.Message)
        Set-LauncherState 'failed'
        [System.Windows.Forms.MessageBox]::Show(
            $_.Exception.Message,
            '打开服务失败',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
})

$stopButton.Add_Click({
    if ($script:dataJob) { return }
    if ($script:serviceJob) { if($script:serviceJob.Action -eq 'status') { $script:pendingServiceAction='stop'; $stopButton.Text='正在关闭…'; $startButton.Enabled=$false; $stopButton.Enabled=$false }; return }
    Write-LauncherLog '收到关闭服务指令。'
    $stopButton.Enabled=$false; $startButton.Enabled=$false
    $stopButton.Text='正在关闭…'
    Start-ServiceOperation 'stop'
})

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 2000
$timer.Add_Tick({ Update-LauncherState; if ($script:dataJob) { $startButton.Enabled = $false; $stopButton.Enabled = $false }; Remove-ExpiredLogs })
$timer.Start()

$form.Add_Resize({ Update-Layout })

    $savedScale = 1.0
    if (Test-Path $fontScalePath) {
        try {
            $candidate = [double]::Parse((Get-Content $fontScalePath -ErrorAction Stop | Select-Object -First 1), [System.Globalization.CultureInfo]::InvariantCulture)
            if ($candidate -in @(0.85, 1.0, 1.15)) { $savedScale = $candidate }
        } catch { }
    }
    Select-FontScale $savedScale
    Update-Layout

$form.Add_Shown({ Update-LauncherState })
$form.Add_FormClosed({
    $script:hoverTimer.Stop(); $script:hoverTimer.Dispose(); $script:hoverStates.Clear()
    Write-LauncherLog '控制中心已关闭；如果服务仍在运行，不会因此停止。'
    $timer.Stop(); $serviceTimer.Stop(); $dataTimer.Stop()
    if ($script:serviceJob) { [void]$script:serviceJob.Pipeline.BeginStop($null,$null) }
    if ($null -ne $iconBox.Image) { $iconBox.Image.Dispose() }
})

$form.ResumeLayout($false)
[void]$form.ShowDialog()
