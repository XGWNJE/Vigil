$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & git config core.hooksPath scripts/hooks
    if ($LASTEXITCODE -ne 0) { throw "git config core.hooksPath failed" }
    $configured = (& git config --get core.hooksPath).Trim()
    Write-Host "Git hooks installed: core.hooksPath=$configured"
}
finally {
    Pop-Location
}
