function Get-ManagedProcess {
    if (-not (Test-Path $pidPath)) { return $null }
    try {
        $processId = [int](Get-Content $pidPath -ErrorAction Stop | Select-Object -First 1)
        $process = Get-Process -Id $processId -ErrorAction Stop
        if ($script:verifiedWorkerPid -ne $processId) {
            $details = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction Stop
            $commandLine=$details.CommandLine.Replace('/','\')
            if (($details.Name -ne 'cmd.exe' -or $commandLine -notmatch [regex]::Escape($workerPath)) -and ($details.Name -ne 'node.exe' -or $commandLine -notmatch [regex]::Escape((Join-Path $baseDir 'service-runner.mjs')))) {
                Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
                return $null
            }
            $script:verifiedWorkerPid = $processId
        }
        return $process
    } catch {
        $script:verifiedWorkerPid = 0
        Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Get-LocalServerProcess {
    if ($script:localServerPid) {
        try { return Get-Process -Id $script:localServerPid -ErrorAction Stop } catch { $script:localServerPid = 0 }
    }
    # Discover ownership once; cache it until the process exits.
    try {
        $connection = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($null -eq $connection) { return $null }
        $ownerId = [int]$connection.OwningProcess
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId" -ErrorAction Stop
        $commandLine=$details.CommandLine.Replace('/','\')
        if ($details.Name -ne 'node.exe' -or $commandLine -notmatch '(server[\\/]+index\.mjs|dist[\\/]+main\.js)') { return $null }
        $script:localServerPid = $ownerId
        return Get-Process -Id $ownerId -ErrorAction Stop
    } catch {
        return $null
    }
}

function Test-AppService {
    try {
        $request = [System.Net.WebRequest]::Create('http://127.0.0.1:3000/api/v1/health')
        $request.Proxy = $null
        $request.Timeout = 800
        $request.ReadWriteTimeout = 800
        $response = $request.GetResponse()
        try {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            try { $data = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            return $data.service -eq 'huimeng-api'
        } finally { $response.Dispose() }
    } catch { return $false }
}

function Test-LocalPort {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect('127.0.0.1', 3000, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne(100)) { return $false }
        $client.EndConnect($result)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-ServiceProcess {
    $managed = Get-ManagedProcess
    if ($null -ne $managed) { return $managed }
    # A healthy shared API may have been started directly, without launcher.pid.
    if (Test-AppService) { return Get-LocalServerProcess }
    return $null
}

function Stop-AppService {
    $process = Get-ServiceProcess
    if ($null -eq $process) { throw '未找到可关闭的绘梦服务进程，请刷新状态后重试。' }
    if ($process.ProcessName -eq 'cmd' -or $process.Id -eq $script:verifiedWorkerPid) {
        $result = Start-Process -FilePath 'taskkill.exe' -ArgumentList "/PID $($process.Id) /T /F" -WindowStyle Hidden -Wait -PassThru
        if ($result.ExitCode -ne 0) { throw '关闭服务失败，进程可能已退出或当前账户没有权限。' }
    } else {
        Stop-Process -Id $process.Id -ErrorAction Stop
    }
    $deadline = (Get-Date).AddSeconds(3)
    while ((Test-AppService) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Test-AppService) { throw '关闭后服务仍有响应，请查看运行日志并重试。' }
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    $script:verifiedWorkerPid = 0
    $script:launchAttempted = $false
    $script:openedAfterLaunch = $false
}


function Read-ServiceState([bool]$launchAttempted) {
    $managed = Get-ManagedProcess
    $portOpen = Test-LocalPort
    $healthy = $portOpen -and (Test-AppService)
    if ($null -eq $managed -and $healthy) { $managed = Get-LocalServerProcess }
    $state = if ($healthy) { 'running' } elseif ($portOpen) { 'occupied' } elseif ($managed) { 'starting' } elseif ($launchAttempted) { 'failed' } else { 'stopped' }
    return [pscustomobject]@{ State=$state; CanStop=($null -ne $managed); VerifiedWorkerPid=$script:verifiedWorkerPid; LocalServerPid=$script:localServerPid }
}
