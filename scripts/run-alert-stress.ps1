[CmdletBinding()]
param(
    [string]$AvdName = "VisionGuard_API36",
    [int]$CooldownSeconds = 5,
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = "Stop"
$Package = "com.example.vigil"
$Listener = "$Package/$Package.MyNotificationListenerService"
$Activity = "$Package/$Package.MainActivity"
$StartedEmulator = $false
$Serial = $null
$ConfirmX = $null
$ConfirmY = $null
$Failures = [System.Collections.Generic.List[string]]::new()
$Results = [System.Collections.Generic.List[object]]::new()
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ReportDir = Join-Path $RepoRoot "app\build\reports\alert-stress\$RunStamp"
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Add-Result([string]$Scenario, [bool]$Passed, [string]$Detail) {
    $Results.Add([pscustomobject]@{ scenario = $Scenario; passed = $Passed; detail = $Detail })
    if (-not $Passed) { $Failures.Add("${Scenario}: $Detail") }
    Write-Host "[$(if ($Passed) { 'PASS' } else { 'FAIL' })] $Scenario - $Detail"
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:Adb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw "adb $($Arguments -join ' ') failed: $output" }
    return ($output -join "`n")
}

function Invoke-DeviceAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $allArguments = @('-s', $script:Serial) + $Arguments
    return Invoke-Adb @allArguments
}

function Get-SdkRoot {
    $propertiesPath = Join-Path $RepoRoot "local.properties"
    if (Test-Path $propertiesPath) {
        $sdkLine = Get-Content $propertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $value = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path $value) { return $value }
        }
    }
    foreach ($name in @('ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value -and (Test-Path $value)) { return $value }
    }
    throw "Android SDK not found. Set ANDROID_HOME or create local.properties with sdk.dir."
}

function Get-RunningAvdSerial([string]$Name) {
    $lines = & $script:Adb devices 2>$null
    foreach ($line in $lines) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            $candidate = $Matches[1]
            $runningName = (& $script:Adb -s $candidate emu avd name 2>$null | Select-Object -First 1).Trim()
            if ($runningName -eq $Name) { return $candidate }
        }
    }
    return $null
}

function Wait-Until([scriptblock]$Condition, [string]$Description, [int]$Timeout = $WaitSeconds) {
    $deadline = (Get-Date).AddSeconds($Timeout)
    do {
        try { if (& $Condition) { return } } catch { }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description"
}

function Save-Evidence([string]$Name) {
    $safeName = $Name -replace '[^A-Za-z0-9_.-]', '_'
    Invoke-DeviceAdb exec-out run-as $Package cat "files/logs/vigil.log" |
        Set-Content -Encoding utf8 (Join-Path $ReportDir "$safeName-vigil.log")
    Invoke-DeviceAdb shell dumpsys media.player |
        Set-Content -Encoding utf8 (Join-Path $ReportDir "$safeName-media-player.txt")
    Get-PrefsXml | Set-Content -Encoding utf8 (Join-Path $ReportDir "$safeName-prefs.xml")
}

function Get-UiXml([string]$Name) {
    $devicePath = "/sdcard/$Name.xml"
    $localPath = Join-Path $ReportDir "$Name-live.xml"
    Invoke-DeviceAdb shell uiautomator dump $devicePath | Out-Null
    Invoke-DeviceAdb pull $devicePath $localPath | Out-Null
    return [System.IO.File]::ReadAllText($localPath, [System.Text.Encoding]::UTF8)
}

function Get-PrefsXml {
    return Invoke-DeviceAdb exec-out run-as $Package cat "shared_prefs/vigil_prefs.xml"
}

function Get-PrefString([string]$Name) {
    [xml]$xml = Get-PrefsXml
    $node = $xml.SelectSingleNode("/map/string[@name='$Name']")
    if ($null -eq $node) { return $null }
    return [System.Net.WebUtility]::HtmlDecode($node.InnerText)
}

function Get-Queue {
    $raw = Get-PrefString "alert_queue"
    if ([string]::IsNullOrWhiteSpace($raw)) { return @() }
    $root = $raw | ConvertFrom-Json
    return @($root.items)
}

function Post-Keyword([string]$Keyword, [string]$Suffix = ([guid]::NewGuid().ToString('N'))) {
    $tag = "vigil-stress-$Keyword-$Suffix"
    Invoke-DeviceAdb @('shell', 'cmd', 'notification', 'post', '-t', $Keyword, $tag, $Keyword) | Out-Null
}

function Wait-QueueCount([int]$Count, [string]$Description) {
    Wait-Until { @(Get-Queue).Count -eq $Count } $Description
}

function Wait-ActiveKeyword([string]$Keyword) {
    Wait-Until {
        $queue = @(Get-Queue)
        $queue.Count -gt 0 -and $queue[0].keyword -eq $Keyword
    } "active keyword $Keyword"
}

function Confirm-VisibleAlert {
    if ($null -eq $script:ConfirmX -or $null -eq $script:ConfirmY) {
        $raw = Get-UiXml "vigil-confirm"
        $match = [regex]::Match($raw, 'text="已知晓，停止报警"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $match.Success) {
            $match = [regex]::Match($raw, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="已知晓，停止报警"')
        }
        if (-not $match.Success) { throw "Alert confirmation button is not visible" }
        $script:ConfirmX = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
        $script:ConfirmY = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
    }
    Invoke-DeviceAdb shell input tap $script:ConfirmX $script:ConfirmY | Out-Null
    # Let the Compose dialog consume the complete tap and finish its exit before
    # another adb action can expose the underlying main-screen controls.
    Start-Sleep -Seconds 1
}

function Install-TestPreferences {
    $prefPath = Join-Path $ReportDir "setup-vigil_prefs.xml"
    $keywords = 1..5 | ForEach-Object { "    <string>K0$_</string>" }
    $xml = @(
        '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>'
        '<map>'
        '  <set name="keywords">'
        $keywords
        '    <string>KBG</string>'
        '    <string>KSTORM</string>'
        '    <string>KCR1</string>'
        '    <string>KCR2</string>'
        '    <string>KCR3</string>'
        '  </set>'
        '  <boolean name="service_enabled" value="true" />'
        '  <boolean name="filter_apps_enabled" value="false" />'
        '  <boolean name="is_first_launch" value="false" />'
        '  <boolean name="has_shown_donate_dialog" value="true" />'
        '  <int name="default_loop_count" value="1" />'
        "  <long name=`"keyword_repeat_interval_ms`" value=`"$($CooldownSeconds * 1000)`" />"
        '  <string name="ringtone_uri">android.resource://com.example.vigil/raw/vigil_preset_target_spot</string>'
        '  <string name="keyword_loop_counts">{&quot;K01&quot;:10,&quot;K02&quot;:10,&quot;K03&quot;:10,&quot;K04&quot;:10,&quot;K05&quot;:10,&quot;KSTORM&quot;:10,&quot;KCR1&quot;:10,&quot;KCR2&quot;:10,&quot;KCR3&quot;:10}</string>'
        '</map>'
    ) -join "`n"
    [System.IO.File]::WriteAllText($prefPath, $xml, [System.Text.UTF8Encoding]::new($false))

    Invoke-DeviceAdb shell cmd notification disallow_listener $Listener | Out-Null
    Invoke-DeviceAdb shell am force-stop $Package | Out-Null
    Invoke-DeviceAdb push $prefPath /data/local/tmp/vigil-stress-prefs.xml | Out-Null
    Invoke-DeviceAdb shell chmod 644 /data/local/tmp/vigil-stress-prefs.xml | Out-Null
    try { Invoke-DeviceAdb shell run-as $Package mkdir shared_prefs | Out-Null } catch { }
    Invoke-DeviceAdb shell run-as $Package cp /data/local/tmp/vigil-stress-prefs.xml shared_prefs/vigil_prefs.xml | Out-Null
    Invoke-DeviceAdb shell cmd notification allow_listener $Listener | Out-Null
    Invoke-DeviceAdb @('shell', 'am', 'start', '-n', $Activity) | Out-Null
    Wait-Until { (Get-PrefsXml) -match 'listener_connected.*true' } "notification listener binding"
}

try {
    $SdkRoot = Get-SdkRoot
    $script:Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
    $Emulator = Join-Path $SdkRoot "emulator\emulator.exe"
    if (-not (Test-Path $script:Adb) -or -not (Test-Path $Emulator)) {
        throw "adb.exe or emulator.exe is missing below SDK root $SdkRoot"
    }

    $Serial = Get-RunningAvdSerial $AvdName
    if (-not $Serial) {
        Start-Process -FilePath $Emulator -ArgumentList @('-avd', $AvdName, '-no-boot-anim', '-no-snapshot-save') | Out-Null
        $StartedEmulator = $true
        Wait-Until {
            $newSerial = Get-RunningAvdSerial $AvdName
            if ($newSerial) { $script:Serial = $newSerial; return $true }
            return $false
        } "emulator device registration" 120
        $Serial = $script:Serial
    }
    $script:Serial = $Serial
    Wait-Until { (Invoke-DeviceAdb shell getprop sys.boot_completed).Trim() -eq '1' } "Android boot completion" 180

    $help = (& $script:Adb -s $script:Serial shell cmd notification help 2>&1) -join "`n"
    if ($help -notmatch '\bpost\b') { throw "This emulator does not support cmd notification post" }

    Push-Location $RepoRoot
    try { & .\gradlew.bat assembleDebug; if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" } }
    finally { Pop-Location }
    $Apk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
    Invoke-DeviceAdb @('install', '-r', $Apk) | Out-Null
    Invoke-DeviceAdb shell cmd media_session volume --stream 4 --set 1 | Out-Null
    Invoke-DeviceAdb shell dumpsys deviceidle whitelist "+$Package" | Out-Null
    Invoke-DeviceAdb shell pm clear $Package | Out-Null
    try { Invoke-DeviceAdb shell pm grant $Package android.permission.POST_NOTIFICATIONS | Out-Null } catch { }
    Invoke-DeviceAdb @('shell', 'am', 'start', '-n', $Activity) | Out-Null
    Start-Sleep -Seconds 1
    Install-TestPreferences
    Invoke-DeviceAdb @('logcat', '-c') | Out-Null

    # Background auto-end must not be resurrected by a stale activity intent.
    Invoke-DeviceAdb shell input keyevent HOME | Out-Null
    Post-Keyword KBG background
    Wait-Until { @(Get-Queue).Count -gt 0 } "background alert enqueue"
    Wait-QueueCount 0 "background alert auto-end"
    Invoke-DeviceAdb @('shell', 'am', 'start', '-n', $Activity) | Out-Null
    Start-Sleep -Seconds 1
    $backgroundUi = Get-UiXml "background"
    Add-Result "background-auto-end" ($backgroundUi -notmatch '已知晓，停止报警') "No stale dialog after returning to foreground"
    Save-Evidence "background-auto-end"

    # Five distinct keywords must retain FIFO order. Capture order, then confirm each item.
    1..5 | ForEach-Object { Post-Keyword ("K0$_") "fifo-$_" }
    Wait-QueueCount 5 "five FIFO items"
    $fifo = @(Get-Queue)
    $actualOrder = @($fifo | ForEach-Object keyword)
    $expectedOrder = @(1..5 | ForEach-Object { "K0$_" })
    Add-Result "fifo-enqueue" (($actualOrder -join ',') -eq ($expectedOrder -join ',')) "Observed $($actualOrder -join ' -> ')"
    foreach ($keyword in $expectedOrder) {
        Wait-ActiveKeyword $keyword
        Start-Sleep -Milliseconds 500
        Confirm-VisibleAlert
    }
    Wait-QueueCount 0 "FIFO drain"
    Add-Result "fifo-drain" $true "All five items advanced and drained"
    Save-Evidence "fifo"

    # Unique notification tags bypass sbn.key dedupe; the active item must aggregate all occurrences.
    1..10 | ForEach-Object { Post-Keyword KSTORM "storm-$_" }
    Wait-Until {
        $q = @(Get-Queue)
        $q.Count -eq 1 -and [int]$q[0].occurrenceCount -eq 10
    } "same-keyword aggregation to occurrenceCount=10"
    $storm = @(Get-Queue)
    Add-Result "same-keyword-storm" ($storm.Count -eq 1 -and [int]$storm[0].occurrenceCount -eq 10) "10 unique notifications aggregated into one alert"
    Confirm-VisibleAlert
    Wait-QueueCount 0 "storm alert completion"

    # Inside the configured cooldown it stays ignored after completion; beyond the boundary it starts again.
    Post-Keyword KSTORM cooldown-inside
    Start-Sleep -Milliseconds 750
    Add-Result "cooldown-inside" (@(Get-Queue).Count -eq 0) "Repeat remained suppressed inside ${CooldownSeconds}s"
    Start-Sleep -Seconds ([Math]::Max(1, $CooldownSeconds))
    Post-Keyword KSTORM cooldown-after
    Wait-ActiveKeyword KSTORM
    Add-Result "cooldown-after" $true "Repeat accepted after cooldown boundary"
    Confirm-VisibleAlert
    Wait-QueueCount 0 "post-cooldown completion"
    Save-Evidence "storm-cooldown"

    $serviceStillEnabled = (Get-PrefsXml) -match 'name="service_enabled" value="true"'
    Add-Result "service-switch-stability" $serviceStillEnabled "Automated alert confirmations did not toggle the main service switch"
    if (-not $serviceStillEnabled) { throw "Service switch changed during alert confirmation flow" }

    # Crash with an active item and queued successors; IDs and order must survive process recreation.
    Post-Keyword KCR1 crash-1
    Post-Keyword KCR2 crash-2
    Post-Keyword KCR3 crash-3
    Wait-QueueCount 3 "pre-crash queue"
    $beforeCrash = @(Get-Queue)
    $beforeIds = @($beforeCrash | ForEach-Object id)
    $preCrashLog = Invoke-DeviceAdb @('shell', 'logcat', '-d', '-v', 'brief')
    $preCrashLog | Set-Content -Encoding utf8 (Join-Path $ReportDir "logcat-pre-crash.txt")
    $preCrashClean = $preCrashLog -notmatch '(FATAL EXCEPTION[\s\S]{0,500}Process: com\.example\.vigil|ANR in com\.example\.vigil|MediaPlayer 播放错误)'
    Add-Result "runtime-health-pre-crash" $preCrashClean "No unexpected Vigil fatal exception, ANR, or playback error before forced crash"
    $oldPid = (Invoke-DeviceAdb shell pidof $Package).Trim()
    Start-Process -FilePath $script:Adb `
        -ArgumentList @('-s', $script:Serial, 'shell', 'am', 'crash', $Package) `
        -WindowStyle Hidden | Out-Null
    # am crash may leave Android's crash dialog in front and some builds restart the
    # listener so quickly that polling never observes an empty pid. Preserve that
    # evidence, then close the crashed task deterministically and relaunch the app.
    Start-Sleep -Seconds 3
    $postCrashPid = (Invoke-DeviceAdb shell pidof $Package).Trim()
    $crashUi = Get-UiXml "forced-crash-dialog"
    $crashObserved = ($postCrashPid -ne $oldPid) -or
        ($crashUi -match 'Application Error|keeps stopping|已停止运行|关闭应用')
    Add-Result "forced-crash-observed" $crashObserved "Old pid=$oldPid; post-crash pid=$postCrashPid"
    if ($crashUi -match 'Application Error|keeps stopping|已停止运行|关闭应用') {
        Invoke-DeviceAdb shell input keyevent BACK | Out-Null
    }
    Invoke-DeviceAdb @('shell', 'am', 'start', '-n', $Activity) | Out-Null
    Wait-Until {
        $newPid = (Invoke-DeviceAdb shell pidof $Package).Trim()
        $newPid.Length -gt 0 -and $newPid -ne $oldPid
    } "process recreation after forced crash" 45
    Wait-Until { @(Get-Queue).Count -eq 3 } "persisted queue after crash"
    $afterCrash = @(Get-Queue)
    $afterIds = @($afterCrash | ForEach-Object id)
    Add-Result "crash-restore" (($beforeIds -join ',') -eq ($afterIds -join ',')) "Active and queued IDs retained in order"
    # The deliberately injected am crash appears as FATAL EXCEPTION; archive it, then
    # clear logcat so the final health assertion only covers recovery behavior.
    Invoke-DeviceAdb @('shell', 'logcat', '-d', '-v', 'brief') |
        Set-Content -Encoding utf8 (Join-Path $ReportDir "logcat-forced-crash.txt")
    Invoke-DeviceAdb @('logcat', '-c') | Out-Null
    foreach ($keyword in @('KCR1', 'KCR2', 'KCR3')) {
        Wait-ActiveKeyword $keyword
        Start-Sleep -Milliseconds 500
        Confirm-VisibleAlert
    }
    Wait-QueueCount 0 "restored queue drain"
    Save-Evidence "crash-restore"

    $media = Invoke-DeviceAdb shell dumpsys media.player
    $fatal = Invoke-DeviceAdb @('shell', 'logcat', '-d', '-v', 'brief')
    $mediaStopped = $media -notmatch 'packageName:\s*com\.example\.vigil'
    $runtimeClean = $fatal -notmatch '(FATAL EXCEPTION[\s\S]{0,500}Process: com\.example\.vigil|ANR in com\.example\.vigil|MediaPlayer 播放错误)'
    Add-Result "resource-release" $mediaStopped "No active Vigil MediaPlayer remained"
    Add-Result "runtime-health" $runtimeClean "No Vigil fatal exception, ANR, or playback error"
    $fatal | Set-Content -Encoding utf8 (Join-Path $ReportDir "logcat.txt")
    Get-FileHash -Algorithm SHA256 $Apk | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $ReportDir "apk-sha256.json")
    [pscustomobject]@{
        serial = $Serial
        avd = $AvdName
        sdk = (Invoke-DeviceAdb shell getprop ro.build.version.sdk).Trim()
        fingerprint = (Invoke-DeviceAdb shell getprop ro.build.fingerprint).Trim()
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $ReportDir "device.json")
}
catch {
    Add-Result "runner" $false $_.Exception.Message
    try { Save-Evidence "failure" } catch { }
}
finally {
    [pscustomobject]@{
        startedAt = $RunStamp
        finishedAt = (Get-Date).ToString('o')
        passed = ($Failures.Count -eq 0)
        failures = @($Failures)
        results = @($Results)
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $ReportDir "summary.json")
    if ($StartedEmulator -and $Serial) {
        try { Invoke-Adb @('-s', $Serial, 'emu', 'kill') | Out-Null } catch { }
    }
}

if ($Failures.Count -gt 0) {
    Write-Error "Alert stress test failed. Evidence: $ReportDir"
    exit 1
}
Write-Host "Alert stress test passed. Evidence: $ReportDir"
exit 0
