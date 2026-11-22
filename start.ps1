param([switch]$NoPause)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'
$jarPath = Join-Path $backendDirectory 'target\honghu-backend-1.0.1.jar'
$logDirectory = Join-Path $projectRoot 'logs'
$backendPort = 18080
$backendUrl = "http://127.0.0.1:$backendPort"

function Test-Application {
    param([string]$Url, [string]$ExpectedText)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        $content = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        return $response.StatusCode -eq 200 -and $content.Contains($ExpectedText)
    } catch {
        return $false
    }
}

function Wait-Application {
    param([string]$Url, [string]$ExpectedText, [System.Diagnostics.Process]$Process)
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Application -Url $Url -ExpectedText $ExpectedText) { return }
        if ($Process.HasExited) { throw "The service exited during startup. Check $logDirectory" }
        Start-Sleep -Milliseconds 500
    }
    throw "The service did not become ready within 60 seconds. Check $logDirectory"
}

$startupMutex = [System.Threading.Mutex]::new($false, 'Local\HongHu-Startup')
$ownsStartupMutex = $false
$exitCode = 0
try {
    try { $ownsStartupMutex = $startupMutex.WaitOne(0) }
    catch [System.Threading.AbandonedMutexException] { $ownsStartupMutex = $true }
    if (-not $ownsStartupMutex) { throw 'Another startup is already in progress. Wait for that startup to finish.' }
    $javaPath = (Get-Command java.exe -ErrorAction Stop).Source
    $npmPath = (Get-Command npm.cmd -ErrorAction Stop).Source
    $backendReady = Test-Application "$backendUrl/api/health" 'honghu-backend'
    $frontendReady = Test-Application 'http://localhost:7401/' '波波公考'
    if (-not $backendReady -and (Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue)) {
        throw "Port $backendPort is occupied by another service."
    }
    if (-not $frontendReady -and (Get-NetTCPConnection -LocalPort 7401 -State Listen -ErrorAction SilentlyContinue)) {
        throw 'Port 7401 is occupied by another service.'
    }

    $mysqlService = Get-Service -Name MySQL80 -ErrorAction SilentlyContinue
    if ($mysqlService -and $mysqlService.Status -ne 'Running') {
        Write-Host 'Starting MySQL80. Approve the Windows administrator prompt if it appears.'
        try {
            Start-Service -Name MySQL80 -ErrorAction Stop
        } catch {
            $serviceCommand = 'Start-Service -Name MySQL80 -ErrorAction Stop'
            Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -Wait -ArgumentList @('-NoProfile', '-Command', $serviceCommand)
        }
        if ((Get-Service -Name MySQL80).Status -ne 'Running') { throw 'MySQL80 could not be started.' }
    }

    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    if (-not $backendReady) {
        $passwordFile = Join-Path $env:LOCALAPPDATA 'Honghu\mysql-localhost-13306-root.xml'
        $useLocalPassword = -not ($env:HONGHU_DB_URL -or $env:HONGHU_DB_PASSWORD -or ($env:HONGHU_DB_USER -and $env:HONGHU_DB_USER -cne 'root') -or $env:SPRING_DATASOURCE_URL -or $env:SPRING_DATASOURCE_USERNAME -or $env:SPRING_DATASOURCE_PASSWORD)
        $securePassword = $null
        $loadedPassword = $false
        if (-not $env:HONGHU_DB_PASSWORD) {
            if ($useLocalPassword -and (Test-Path -LiteralPath $passwordFile)) {
                try {
                    $securePassword = Import-Clixml -LiteralPath $passwordFile
                    $loadedPassword = $securePassword -is [Security.SecureString]
                } catch { $loadedPassword = $false }
            }
            if (-not $loadedPassword) {
                $securePassword = Read-Host '请输入 MySQL 数据库密码' -AsSecureString
            }
            $env:HONGHU_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
        }
        if (-not $env:HONGHU_JWT_SECRET) {
            $jwtBytes = New-Object byte[] 32
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($jwtBytes) } finally { $random.Dispose() }
            $env:HONGHU_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
        }
        Write-Host 'Building backend...'
        Push-Location $backendDirectory
        try {
            & .\mvnw.cmd package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        } finally { Pop-Location }
        $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"' + $jarPath + '"'), "--server.port=$backendPort") -WorkingDirectory $backendDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'backend.log') -RedirectStandardError (Join-Path $logDirectory 'backend-error.log') -PassThru
        try {
            Wait-Application "$backendUrl/api/health" 'honghu-backend' $backendProcess
        } catch {
            if (Select-String -LiteralPath (Join-Path $logDirectory 'backend.log') -SimpleMatch "Access denied for user" -Quiet) {
                if ($loadedPassword) { Remove-Item -LiteralPath $passwordFile }
                throw 'MySQL 拒绝了数据库用户名或密码。请核对数据库凭据，再双击一键启动。'
            }
            throw
        }
        if ($useLocalPassword -and -not $loadedPassword) {
            New-Item -ItemType Directory -Path (Split-Path -Parent $passwordFile) -Force | Out-Null
            $securePassword | Export-Clixml -LiteralPath $passwordFile
        }
        Remove-Item Env:HONGHU_DB_PASSWORD, Env:HONGHU_JWT_SECRET -ErrorAction SilentlyContinue
        Write-Host 'Backend is ready.'
    } else {
        Write-Host 'Backend is already running.'
    }

    if (-not $frontendReady) {
        if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'node_modules\vite\bin\vite.js'))) {
            Write-Host 'Installing frontend dependencies...'
            Push-Location $frontendDirectory
            try {
                & $npmPath install
                if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            } finally { Pop-Location }
        }
        $env:HONGHU_BACKEND_URL = $backendUrl
        $npmArguments = '""{0}" run dev -- --strictPort"' -f $npmPath
        $frontendProcess = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', $npmArguments) -WorkingDirectory $frontendDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'frontend.log') -RedirectStandardError (Join-Path $logDirectory 'frontend-error.log') -PassThru
        Wait-Application 'http://localhost:7401/' '波波公考' $frontendProcess
        Write-Host 'Frontend is ready.'
    } else {
        Write-Host 'Frontend is already running.'
    }

    Start-Process 'http://localhost:7401/'
    Write-Host ''
    Write-Host '波波公考已启动，浏览器正在打开。' -ForegroundColor Green
    Write-Host "日志位置：$logDirectory"
    Write-Host '关闭服务请双击“停止服务.bat”。'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $exitCode = 1
} finally {
    if ($ownsStartupMutex) { $startupMutex.ReleaseMutex() }
    $startupMutex.Dispose()
}
if ($exitCode -ne 0 -and -not $NoPause) { Read-Host '按 Enter 关闭窗口' | Out-Null }
exit $exitCode
