param(
    [string]$Tag = "",
    [switch]$SkipLint
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
$sw = [System.Diagnostics.Stopwatch]::StartNew()

try {
    Write-Host "=== [1/3] Docs & README sync check ==="
    $syncArgs = @('scripts/check-docs-sync.py')
    if ($Tag) { $syncArgs += @('--tag', $Tag) }
    & python @syncArgs
    if ($LASTEXITCODE -ne 0) { throw "check-docs-sync.py failed (exit $LASTEXITCODE)" }

    if ($Tag) {
        Write-Host "=== [2/3] Version & tag check ($Tag) ==="
        & python scripts/check-version.py --tag $Tag
        if ($LASTEXITCODE -ne 0) { throw "check-version.py failed (exit $LASTEXITCODE)" }
    } else {
        Write-Host "=== [2/3] Version & tag check (skipped, no -Tag provided) ==="
    }

    $gradleTasks = @(':app:testDebugUnitTest')
    if (-not $SkipLint) { $gradleTasks += ':app:lintDebug' }
    Write-Host "=== [3/3] Gradle verification: $($gradleTasks -join ' ') ==="
    & .\gradlew.bat @gradleTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed (exit $LASTEXITCODE)" }

    $sw.Stop()
    Write-Host ("[PASS] Local CI succeeded in {0:N1}s" -f $sw.Elapsed.TotalSeconds)
}
finally {
    Pop-Location
}
