# Shared by the control center and its regression checks. Times are local.
function Get-LogText([string]$path, [switch]$Full) {
    if (-not (Test-Path -LiteralPath $path)) { return '' }
    $stream = $null; $reader = $null
    try {
        $share = [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete
        $stream = [IO.FileStream]::new($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, $share)
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
        if (-not $Full -and $stream.Length -gt 524288) {
            [void]$stream.Seek(-524288, [IO.SeekOrigin]::End)
            [void]$reader.ReadLine()
        }
        return $reader.ReadToEnd().TrimStart([char]0xFEFF)
    } catch { return '' } finally {
        if ($null -ne $reader) { $reader.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Get-LogEntries([string]$content, [DateTime]$fallbackTime) {
    $time = $fallbackTime
    $stamp = '[' + $time.ToString('yyyy-MM-dd HH:mm:ss') + ']'
    $sequence = 0
    foreach ($line in ($content -split "`r?`n")) {
        if (-not $line.Trim()) { continue }
        if ($line -match '^\[([^\]]*\d{2}:\d{2}:\d{2}[^\]]*)\]') {
            $stamp = $matches[0]; $raw = $matches[1]
            $parsed = [DateTime]::MinValue
            # Older cmd logs contain a weekday and either yyyy/MM/dd or dd/MM/yyyy.
            $dateText = [regex]::Match($raw, '\d{4}[-/]\d{2}[-/]\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z)?').Value
            if ($dateText -and [DateTime]::TryParse($dateText, [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::None, [ref]$parsed)) {
                $time = $parsed
            } elseif ($raw -match '(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})(?:\.(\d+))?') {
                $dateText = $matches[1] + ' ' + $matches[2]
                if ([DateTime]::TryParseExact($dateText, 'dd/MM/yyyy HH:mm:ss', [Globalization.CultureInfo]::InvariantCulture,
                    [Globalization.DateTimeStyles]::None, [ref]$parsed)) { $time = $parsed }
            }
            $text = $line
        } else { $text = $stamp + ' ' + $line }
        [pscustomobject]@{ Time = $time; Sequence = $sequence++; Text = $text }
    }
}

function Get-RunLogs([string]$level) {
    $cutoff = (Get-Date).AddDays(-3)
    $files = @(Get-ChildItem -LiteralPath $logsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(launcher|service)-\d{8}-\d{2}\.log$|^startup-\d{8}-\d{6}\.log$' })
    foreach ($path in @($logPath, $serviceLogPath)) {
        if (Test-Path -LiteralPath $path) { $files += Get-Item -LiteralPath $path }
    }
    $entries = foreach ($file in $files) {
        foreach ($entry in (Get-LogEntries (Get-LogText $file.FullName) $file.LastWriteTime)) {
            if ($entry.Time -lt $cutoff) { continue }
            $errorLine = $entry.Text -match '(\[ERROR\]|\b(error|exception|fatal|failed)\b|错误|失败|异常)'
            if ($level -eq '错误' -and -not $errorLine) { continue }
            if ($level -eq 'Debug' -and -not $errorLine -and $entry.Text -notmatch '(\[DEBUG\]|\bdebug\b|调试)') { continue }
            $entry
        }
    }
    $result = @($entries | Sort-Object -Property @{Expression='Time';Descending=$true}, @{Expression='Sequence';Descending=$true} |
        Select-Object -First 3000 -ExpandProperty Text)
    if (-not $result.Count) { return '（最近三天暂无相关记录）' }
    return [string]::Join([Environment]::NewLine, $result)
}

function Remove-ExpiredLogs {
    $now = Get-Date
    if (($now - $script:lastLogCleanup).TotalMinutes -lt 60) { return }
    $script:lastLogCleanup = $now
    $cutoff = $now.AddDays(-3)
    $files = @(Get-ChildItem -LiteralPath $logsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(launcher|service)-\d{8}-\d{2}\.log$|^startup-\d{8}-\d{6}\.log$' })
    $files += @(Get-ChildItem -LiteralPath $dataDir -Filter 'launcher-legacy-*.log' -File -ErrorAction SilentlyContinue)
    foreach ($path in @($logPath, $serviceLogPath)) {
        if (Test-Path -LiteralPath $path) { $files += Get-Item -LiteralPath $path }
    }
    foreach ($file in $files) {
        $stream = $null; $reader = $null
        try {
            $existing = @(Get-LogEntries (Get-LogText $file.FullName -Full) $file.LastWriteTime)
            if ($file.LastWriteTime -ge $cutoff -and -not @($existing | Where-Object { $_.Time -lt $cutoff }).Count) { continue }
            # Exclusive write access prevents truncating a log still held by an old worker.
            $stream = [IO.FileStream]::new($file.FullName, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 4096, $true)
            $content = $reader.ReadToEnd(); $reader.Dispose(); $reader = $null
            $entries = @(Get-LogEntries $content $file.LastWriteTime)
            $retained = @($entries | Where-Object { $_.Time -ge $cutoff })
            if ($entries.Count -eq $retained.Count -and $file.LastWriteTime -ge $cutoff) { continue }
            if (-not $retained.Count) {
                $stream.Dispose(); $stream = $null
                Remove-Item -LiteralPath $file.FullName -Force -ErrorAction Stop
            } else {
                $bytes = [Text.Encoding]::UTF8.GetBytes([string]::Join([Environment]::NewLine, @($retained.Text)) + [Environment]::NewLine)
                $stream.Position = 0; $stream.SetLength(0); $stream.Write($bytes, 0, $bytes.Length)
            }
        } catch {
            Write-LauncherLog ('清理旧日志失败：' + $file.Name + ' · ' + $_.Exception.Message)
        } finally {
            if ($null -ne $reader) { $reader.Dispose() }
            if ($null -ne $stream) { $stream.Dispose() }
        }
    }
}
