# Verify release APK versionCode/versionName match app/build.gradle.kts.
param(
    [string]$ApkPath = "app/build/outputs/apk/release/app-release.apk",
    [string]$GradleFile = "app/build.gradle.kts"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ApkPath = Join-Path $ProjectRoot $ApkPath
$GradleFile = Join-Path $ProjectRoot $GradleFile

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
}
if (-not (Test-Path $GradleFile)) {
    throw "Gradle file not found: $GradleFile"
}

$gradleText = Get-Content $GradleFile -Raw
if ($gradleText -notmatch 'versionCode\s*=\s*(\d+)') {
    throw "Failed to parse versionCode from $GradleFile"
}
$expectedCode = $Matches[1]
if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "Failed to parse versionName from $GradleFile"
}
$expectedName = $Matches[1]

$aapt = @(
    "$env:ANDROID_SDK_ROOT\build-tools\*\aapt.exe",
    "$env:ANDROID_HOME\build-tools\*\aapt.exe",
    "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt.exe"
) | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } |
    Sort-Object { $_.Directory.Name } -Descending |
    Select-Object -First 1

if (-not $aapt) {
    throw "aapt not found. Install Android build-tools or set ANDROID_SDK_ROOT."
}

$badgingText = (& $aapt.FullName dump badging $ApkPath 2>&1 | Out-String)
if ($badgingText -notmatch "versionCode='(\d+)'") {
    throw "Failed to parse versionCode from APK"
}
$actualCode = $Matches[1]
if ($badgingText -notmatch "versionName='([^']+)'") {
    throw "Failed to parse versionName from APK"
}
$actualName = $Matches[1]

Write-Host "Expected: versionCode=$expectedCode versionName=$expectedName"
Write-Host "Actual:   versionCode=$actualCode versionName=$actualName"

if ($actualCode -ne $expectedCode -or $actualName -ne $expectedName) {
    throw "Release APK version mismatch."
}

Write-Host "OK: Release APK version matches $GradleFile"
