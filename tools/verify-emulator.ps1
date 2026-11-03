param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Use a disposable Android emulator.' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA/Android/Sdk" }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$root = Split-Path -Parent $PSScriptRoot
function Invoke-Adb {
    & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
if ((Invoke-Adb shell getprop sys.boot_completed).Trim() -ne '1') { throw 'Wait until Android has finished booting.' }
Invoke-Adb install -r (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk')
Invoke-Adb install -r -t (Join-Path $root 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
Invoke-Adb shell cmd notification allow_listener dev.mwidget.runtime/dev.mwidget.runtime.NotifListener
Invoke-Adb shell cmd appwidget grantbind --package dev.mwidget.runtime --user 0
$result = Invoke-Adb shell am instrument -w dev.mwidget.runtime.test/dev.mwidget.runtime.RuntimeSmoke
$result | Write-Output
if (($result -join "`n") -notmatch 'PASS: imports') { throw 'Instrumentation suite failed.' }
