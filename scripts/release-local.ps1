param(
    [string]$Tag = "",
    [switch]$DryRun,
    [switch]$SkipLint
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Get-KeystorePassword {
    if ($env:VIGIL_STORE_PASSWORD) { return $env:VIGIL_STORE_PASSWORD }
    $propsPath = Join-Path $repoRoot 'keystore.properties'
    if (Test-Path $propsPath) {
        foreach ($line in Get-Content $propsPath) {
            if ($line -match '^\s*storePassword\s*=\s*(.+)$') {
                return $Matches[1].Trim()
            }
        }
    }
    return $null
}

function Find-ApkSigner {
    $sdkRoots = @($env:ANDROID_HOME, 'C:\Users\wen\VisionGuard\.local\android-sdk') | Where-Object { $_ -and (Test-Path $_) }
    foreach ($sdk in $sdkRoots) {
        $btDir = Join-Path $sdk 'build-tools'
        if (Test-Path $btDir) {
            $candidates = Get-ChildItem $btDir -Directory | Sort-Object Name -Descending
            foreach ($dir in $candidates) {
                $signer = Join-Path $dir.FullName 'apksigner.bat'
                if (Test-Path $signer) { return $signer }
            }
        }
    }
    throw "apksigner.bat not found under ANDROID_HOME/build-tools"
}

try {
    $gradleText = Get-Content -Raw -Encoding UTF8 (Join-Path $repoRoot 'app\build.gradle.kts')
    if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') { throw "versionName not found in app/build.gradle.kts" }
    $versionName = $Matches[1]
    if (-not $Tag) { $Tag = "v$versionName" }

    if (-not $DryRun) {
        $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
        if ($branch -ne 'main') { throw "Release must run on 'main' branch (current: $branch)" }
        $dirty = (& git status --porcelain)
        if ($dirty) { throw "Working tree is not clean. Commit or stash changes before releasing." }
    }

    $ksPath = Join-Path $repoRoot 'keystore\vigil.keystore'
    if (-not (Test-Path $ksPath)) {
        throw "Missing local keystore at keystore/vigil.keystore. Restore from offline backup before releasing."
    }
    $storePw = Get-KeystorePassword
    if (-not $storePw) {
        throw "Missing signing credentials: provide keystore.properties or VIGIL_STORE_PASSWORD/VIGIL_KEY_PASSWORD env vars."
    }

    Write-Host "=== Running Local CI for $Tag ==="
    $ciParams = @{ Tag = $Tag }
    if ($SkipLint) { $ciParams['SkipLint'] = $true }
    & "$PSScriptRoot\local-ci.ps1" @ciParams
    if ($LASTEXITCODE -ne 0) { throw "Local CI failed" }

    Write-Host "=== Building signed Release APK ==="
    & .\gradlew.bat :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw ":app:assembleRelease failed (exit $LASTEXITCODE)" }

    $rawApk = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path $rawApk)) { throw "Expected release APK not found: $rawApk" }

    $apksigner = Find-ApkSigner
    Write-Host "=== Verifying APK signature via $apksigner ==="
    $prevPref = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $verifyOut = (& $apksigner verify --print-certs $rawApk 2>&1) | Out-String
    $verifyExit = $LASTEXITCODE
    $ktOut = (cmd /c "`"keytool`" -list -v -keystore `"$ksPath`" -alias vigil -storepass $storePw 2>&1") | Out-String
    $ktExit = $LASTEXITCODE
    $ErrorActionPreference = $prevPref
    $storePw = $null

    if ($verifyExit -ne 0) { throw "apksigner verify failed:`n$verifyOut" }
    if ($ktExit -ne 0) { throw "keytool -list failed on keystore/vigil.keystore" }

    if ($verifyOut -notmatch 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)') {
        throw "Could not parse SHA-256 digest from apksigner output"
    }
    $apkSha256 = ($Matches[1] -replace ':', '').ToLowerInvariant()

    if ($ktOut -notmatch 'SHA256:\s*([0-9a-fA-F:]+)') {
        throw "Could not parse SHA-256 digest from keytool output"
    }
    $ksSha256 = ($Matches[1] -replace ':', '').ToLowerInvariant()

    if ($apkSha256 -ne $ksSha256) {
        throw "Certificate SHA-256 mismatch! APK=$apkSha256 vs Keystore=$ksSha256"
    }

    $namedApk = Join-Path $repoRoot "app\build\outputs\apk\release\vigil-$Tag.apk"
    Copy-Item -Force $rawApk $namedApk
    $apkSize = (Get-Item $namedApk).Length
    $fileSha256 = (Get-FileHash -Algorithm SHA256 $namedApk).Hash.ToLowerInvariant()

    Write-Host "APK Ready: $namedApk ($apkSize bytes)"
    Write-Host "APK File SHA-256: $fileSha256"
    Write-Host "Cert SHA-256:     $apkSha256 (verified against keystore/vigil.keystore)"

    if ($DryRun) {
        $sw.Stop()
        Write-Host ("[DRY-RUN COMPLETE] Local release pipeline verified in {0:N1}s (no git push / no GitHub release created)." -f $sw.Elapsed.TotalSeconds)
        exit 0
    }

    # Live release: push main + tag + create GitHub Release directly
    $remoteTag = (& git ls-remote --tags origin "refs/tags/$Tag")
    if ($remoteTag) { throw "Tag $Tag already exists on origin!" }
    & gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "gh CLI is not authenticated" }

    Write-Host "=== Pushing main & tag $Tag ==="
    $env:VIGIL_SKIP_HOOKS = '1'
    try {
        & git push origin main
        if ($LASTEXITCODE -ne 0) { throw "git push origin main failed" }

        $localTag = (& git tag -l $Tag).Trim()
        if (-not $localTag) {
            & git tag $Tag
            if ($LASTEXITCODE -ne 0) { throw "git tag $Tag failed" }
        }
        & git push origin $Tag
        if ($LASTEXITCODE -ne 0) { throw "git push origin $Tag failed" }
    }
    finally {
        Remove-Item Env:\VIGIL_SKIP_HOOKS -ErrorAction SilentlyContinue
    }

    Write-Host "=== Creating GitHub Release $Tag ==="
    $notesFile = Join-Path $repoRoot "release-notes\$Tag.md"
    & gh release create $Tag --title "Vigil $Tag" --notes-file $notesFile $namedApk
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }

    $relJson = (& gh release view $Tag --json tagName,url,assets) | ConvertFrom-Json
    $sw.Stop()
    Write-Host ("[RELEASE COMPLETE] {0} published in {1:N1}s" -f $relJson.url, $sw.Elapsed.TotalSeconds)
}
finally {
    Pop-Location
}
