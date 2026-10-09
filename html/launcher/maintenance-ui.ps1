function Start-DataOperation([string[]]$Arguments, [scriptblock]$Completed) {
    if ($script:dataJob) { return }
    try {
        $node = if (Test-Path $knownNodePath) { $knownNodePath } else { (Get-Command node.exe -ErrorAction Stop).Source }
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $node
        $allArgs = @((Join-Path $baseDir 'data-manager.mjs')) + $Arguments
        $info.Arguments = ($allArgs | ForEach-Object { '"' + [regex]::Replace([regex]::Replace($_, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"' }) -join ' '
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $info.StandardOutputEncoding = [System.Text.Encoding]::UTF8
        $info.StandardErrorEncoding = [System.Text.Encoding]::UTF8
        $process = [System.Diagnostics.Process]::Start($info)
        $script:dataJob = @{ Process=$process; Output=$process.StandardOutput.ReadToEndAsync(); Error=$process.StandardError.ReadToEndAsync(); Completed=$Completed }
        foreach ($button in $dataButtons) { $button.Enabled = $false }
        $dataHint.Text = '正在处理，请等待完成…'
        $dataTimer.Start()
    } catch { [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, '数据管理失败') | Out-Null }
}
function Select-DataFolder([string]$Title) {
    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = $Title
    $dialog.ShowNewFolderButton = $true
    try { if ($dialog.ShowDialog($form) -eq 'OK') { return $dialog.SelectedPath } } finally { $dialog.Dispose() }
    return $null
}
function Invoke-ManagementRecordQuery {
    $view = $script:recordControls
    if ($script:dataJob) { return }
    if ($view.From.Value.Date -gt $view.To.Value.Date) { $view.Hint.Text = '开始日期不能晚于结束日期。'; return }
    $view.Hint.Text = '正在查询…'
    Start-DataOperation @('records','--keyword',$view.Keyword.Text,'--start',$view.From.Value.ToString('yyyy-MM-dd'),'--end',$view.To.Value.ToString('yyyy-MM-dd')) {
        param($result)
        $view = $script:recordControls
        if ($view.Window.IsDisposed) { return }
        $table = New-Object System.Data.DataTable
        foreach ($name in @('时间','操作人','模块','操作','结果','详情')) { [void]$table.Columns.Add($name) }
        foreach ($record in $result.records) { [void]$table.Rows.Add(@([DateTime]::Parse($record.createdAt).ToLocalTime().ToString('yyyy-MM-dd HH:mm:ss'),$record.operator,$record.module,$record.action,$record.result,$record.detail)) }
        $view.Grid.DataSource = $table
        $widths = @(185,120,85,75,65,200)
        for ($i=0;$i -lt $widths.Count;$i++) { $view.Grid.Columns[$i].MinimumWidth = Ui $widths[$i]; $view.Grid.Columns[$i].FillWeight = $widths[$i] }
        $view.Hint.Text = '查询完成，共 ' + $table.Rows.Count + ' 条；详细内容可在单元格中查看。'
    }
}
function Show-ManagementRecords {
    $window = New-Object System.Windows.Forms.Form
    $window.Text = '管理记录查询 · 最新记录在上方'
    $window.Size = Ui-Size 920 600
    $window.MinimumSize = Ui-Size 760 480
    $window.StartPosition = 'CenterParent'
    $window.AutoScaleMode = 'None'
    $window.Font = New-PixelFont 'Microsoft YaHei UI' 14 ([System.Drawing.FontStyle]::Regular)
    $layout = New-Object System.Windows.Forms.TableLayoutPanel
    $layout.Dock = 'Fill'; $layout.ColumnCount = 1; $layout.RowCount = 4
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('AutoSize')))
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('Percent',100)))
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('Absolute',(Ui 96))))
    [void]$layout.RowStyles.Add((New-Object System.Windows.Forms.RowStyle('AutoSize')))
    $window.Controls.Add($layout)
    $filter = New-Object System.Windows.Forms.FlowLayoutPanel
    $filter.Dock = 'Fill'; $filter.AutoSize = $true; $filter.AutoSizeMode = 'GrowAndShrink'; $filter.Padding = New-Object System.Windows.Forms.Padding((Ui 12))
    $keyword = New-Object System.Windows.Forms.TextBox; $keyword.Width = Ui 160
    $keyword.AccessibleName = '记录关键词'
    $from = New-Object System.Windows.Forms.DateTimePicker; $from.Format = 'Short'; $from.Width = Ui 130; $from.Value = (Get-Date).AddDays(-7)
    $to = New-Object System.Windows.Forms.DateTimePicker; $to.Format = 'Short'; $to.Width = Ui 130
    $from.AccessibleName = '开始日期'; $to.AccessibleName = '结束日期'
    $search = New-Object System.Windows.Forms.Button; $search.Text = '查询'; $search.Size = Ui-Size 80 32
    Add-ButtonHover $search '#EEEAFB' '#DCD4F4'
    $filter.Controls.AddRange(@($keyword,$from,$to,$search))
    $hint = New-Object System.Windows.Forms.Label; $hint.Dock = 'Fill'; $hint.AutoSize = $true; $hint.Padding = New-Object System.Windows.Forms.Padding((Ui 8)); $hint.Text = '输入关键词并选择起止日期；最多显示最新 1000 条记录。'
    $script:recordHint = $hint
    $grid = New-Object System.Windows.Forms.DataGridView
    $grid.Dock = 'Fill'; $grid.ReadOnly = $true; $grid.AllowUserToAddRows = $false; $grid.AllowUserToDeleteRows = $false
    $grid.AutoSizeColumnsMode = 'Fill'; $grid.AutoSizeRowsMode = 'DisplayedCells'; $grid.DefaultCellStyle.WrapMode = 'True'; $grid.RowHeadersVisible = $false
    $grid.ColumnHeadersHeightSizeMode = 'AutoSize'
    $grid.ColumnHeadersDefaultCellStyle.WrapMode = 'True'
    $grid.BackgroundColor = [System.Drawing.Color]::White
    $details = New-Object System.Windows.Forms.TextBox
    $details.Dock = 'Fill'; $details.Multiline = $true; $details.ReadOnly = $true; $details.ScrollBars = 'Vertical'
    $details.Text = '选择记录查看完整详情，可选中文字复制。'
    $grid.Add_SelectionChanged({
        $view = $script:recordControls
        if ($view -and $view.Grid.CurrentRow) {
            $cells = $view.Grid.CurrentRow.Cells
            $view.Details.Text = '时间：' + $cells[0].Value + '    操作人：' + $cells[1].Value + "`r`n" + $cells[2].Value + ' / ' + $cells[3].Value + ' / ' + $cells[4].Value + "`r`n" + $cells[5].Value
        }
    })
    $layout.Controls.Add($filter,0,0)
    $layout.Controls.Add($grid,0,1)
    $layout.Controls.Add($details,0,2)
    $layout.Controls.Add($hint,0,3)
    $script:recordControls = @{ Window=$window; Grid=$grid; Hint=$hint; Details=$details; Keyword=$keyword; From=$from; To=$to; Search=$search }
    $search.Add_Click({ Invoke-ManagementRecordQuery })
    $window.Add_Shown({ Invoke-ManagementRecordQuery })
    $window.Add_FormClosing({ if ($script:dataJob) { $_.Cancel = $true } })
    [void]$window.ShowDialog($form)
    $window.Dispose()
}
$dataTitle = New-FixedLabel '数据管理' 24 330 480 26 16 ([System.Drawing.FontStyle]::Regular) '#302B48'
$dataHint = New-FixedLabel '备份包含数据库和图片；恢复前请关闭服务。' 24 465 510 28 10 ([System.Drawing.FontStyle]::Regular) '#79738B'
$form.Controls.AddRange(@($dataTitle,$dataHint))
$dataButtons = @()
foreach ($text in @('备份数据库','恢复数据库','导出业务数据','查询管理记录')) {
    $button = New-ServiceButton $text '#EEEAFB' '#5848AD' '#E2DCF6'
    $form.Controls.Add($button)
    $dataButtons += $button
}
$script:dataJob = $null
$dataTimer = New-Object System.Windows.Forms.Timer
$dataTimer.Interval = 150
$dataTimer.Add_Tick({
    if (-not $script:dataJob -or -not $script:dataJob.Process.HasExited) { return }
    $dataTimer.Stop()
    $job = $script:dataJob; $script:dataJob = $null
    foreach ($button in $dataButtons) { $button.Enabled = $true }
    try {
        if ($job.Process.ExitCode -ne 0) { throw $job.Error.Result }
        $result = $job.Output.Result | ConvertFrom-Json
        $dataHint.Text = '操作完成，已记录到管理记录。'
        & $job.Completed $result
    } catch {
        $dataHint.Text = '操作失败，请查看提示后重试。'
        if ($script:recordHint -and -not $script:recordHint.IsDisposed) { $script:recordHint.Text = '查询失败，可修改条件后再次点击查询。' }
        [System.Windows.Forms.MessageBox]::Show([string]$_.Exception.Message,'数据管理失败','OK','Error') | Out-Null
    } finally { $job.Process.Dispose() }
})
$dataButtons[0].Add_Click({ Start-DataOperation @('backup') { param($result); [System.Windows.Forms.MessageBox]::Show("备份完成，包含数据库和 $($result.images) 个素材文件。`r`n$($result.path)",'备份完成') | Out-Null } })
$dataButtons[1].Add_Click({
    if (Test-LocalPort) { [System.Windows.Forms.MessageBox]::Show('请先点击关闭服务，确认网站与手机暂停访问，再恢复数据。','请先关闭服务') | Out-Null; return }
    $source = Select-DataFolder '选择包含 manifest.json、database.sqlite 和 uploads 的完整备份目录'
    if (-not $source) { return }
    Start-DataOperation @('validate','--source',$source) {
        param($result)
        if ([System.Windows.Forms.MessageBox]::Show("备份验证通过。恢复将替换当前数据和图片，并自动备份当前数据。`r`n$($result.path)`r`n是否恢复？",'确认恢复','YesNo','Warning') -eq 'Yes') {
            Start-DataOperation @('restore','--source',$result.path,'--service-stopped') { param($restored); [System.Windows.Forms.MessageBox]::Show("$($restored.message)`r`n恢复前备份：$($restored.safetyBackup)",'恢复完成') | Out-Null }
        }
    }
})
$dataButtons[2].Add_Click({
    $target = Select-DataFolder '选择导出目录，将生成 CSV 和 JSON 文件（不包含密码和令牌）'
    if ($target) { Start-DataOperation @('export','--output',$target) { param($result); [System.Windows.Forms.MessageBox]::Show("已导出 $($result.tables) 类业务数据。`r`n$($result.path)",'导出完成') | Out-Null } }
})
$dataButtons[3].Add_Click({ Show-ManagementRecords })
$form.Add_FormClosing({ if ($script:dataJob) { $_.Cancel = $true; [System.Windows.Forms.MessageBox]::Show('正在处理数据，请等待完成后关闭控制中心。','数据处理中') | Out-Null } })
