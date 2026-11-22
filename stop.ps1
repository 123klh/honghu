param([switch]$NoPause)

$ErrorActionPreference = 'Stop'
$exitCode = 0
try {
    $listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 18080, 8080, 7401 }
    $processIds = $listeners.OwningProcess | Select-Object -Unique
    foreach ($projectProcessId in $processIds) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$projectProcessId"
        $commandLine = $process.CommandLine
        $belongsToProject = $commandLine -and ($commandLine.IndexOf($PSScriptRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or $commandLine.Contains('honghu-backend-1.0.1.jar') -or $commandLine.Contains('com.honghu.HonghuApplication'))
        if ($belongsToProject) {
            Stop-Process -Id $projectProcessId -ErrorAction Stop
            Write-Host "Stopped project process $projectProcessId."
        } else {
            Write-Host "Skipped another application's process $projectProcessId."
        }
    }
    Write-Host 'Project services have stopped.'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $exitCode = 1
}
if (-not $NoPause) { Read-Host 'Press Enter to close this window' | Out-Null }
exit $exitCode
