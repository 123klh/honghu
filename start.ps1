param([switch]$NoPause)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = $PSScriptRoot
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'
$jarPath = Join-Path $backendDirectory 'target\honghu-backend-1.0.1.jar'
$logDirectory = Join-Path $projectRoot 'logs'

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
    $backendReady = Test-Application 'http://localhost:8080/api/health' 'honghu-backend'
    $frontendReady = Test-Application 'http://localhost:7001/' '波波公考'
    if (-not $backendReady -and (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
        throw 'Port 8080 is occupied by another service.'
    }
    if (-not $frontendReady -and (Get-NetTCPConnection -LocalPort 7001 -State Listen -ErrorAction SilentlyContinue)) {
        throw 'Port 7001 is occupied by another service.'
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
        Write-Host 'Building backend...'
        Push-Location $backendDirectory
        try {
            & .\mvnw.cmd package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        } finally { Pop-Location }
        $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"' + $jarPath + '"')) -WorkingDirectory $backendDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'backend.log') -RedirectStandardError (Join-Path $logDirectory 'backend-error.log') -PassThru
        Wait-Application 'http://localhost:8080/api/health' 'honghu-backend' $backendProcess
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
        $npmArguments = '""{0}" run dev -- --strictPort"' -f $npmPath
        $frontendProcess = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', $npmArguments) -WorkingDirectory $frontendDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'frontend.log') -RedirectStandardError (Join-Path $logDirectory 'frontend-error.log') -PassThru
        Wait-Application 'http://localhost:7001/' '波波公考' $frontendProcess
        Write-Host 'Frontend is ready.'
    } else {
        Write-Host 'Frontend is already running.'
    }

    Write-Host ''
    Write-Host 'Open http://localhost:7001/' -ForegroundColor Green
    Write-Host 'API documentation http://localhost:8080/swagger-ui.html'
    Write-Host "Services run in the background. Logs are in $logDirectory"
    Write-Host 'Use the stop batch file to stop the frontend and backend.'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $exitCode = 1
} finally {
    if ($ownsStartupMutex) { $startupMutex.ReleaseMutex() }
    $startupMutex.Dispose()
}
if (-not $NoPause) { Read-Host 'Press Enter to close this window' | Out-Null }
exit $exitCode