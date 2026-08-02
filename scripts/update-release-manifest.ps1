# 发版后更新仓库根目录 update.json（App 通过 raw + jsDelivr@main 拉取，不走 GitHub API）
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [int]$VersionCode,
    [Parameter(Mandatory = $true)]
    [long]$ApkSize,
    [string]$Notes = "",
    [string]$ApkFileName = "",
    [switch]$VerifyRemote
)

$ErrorActionPreference = "Stop"
if ($ApkSize -le 0) {
    throw "ApkSize must be > 0. Write the final manifest only after the release APK is built."
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $ProjectRoot "update.json"

if ([string]::IsNullOrWhiteSpace($ApkFileName)) {
    $ApkFileName = "cebian-$Version.apk"
}

$apkUrl = "https://github.com/qpst4/cebian/releases/download/v$Version/$ApkFileName"
$manifest = [ordered]@{
    version     = $Version
    versionCode = $VersionCode
    apkUrl      = $apkUrl
    apkSize     = $ApkSize
    notes       = $Notes
}

$json = ($manifest | ConvertTo-Json -Depth 3) + "`n"
[System.IO.File]::WriteAllText($manifestPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $manifestPath"
Write-Host $json

$written = Get-Content -Raw -Path $manifestPath | ConvertFrom-Json
if ($written.version -ne $Version -or [long]$written.apkSize -ne $ApkSize) {
    throw "Local update.json validation failed."
}

$jsDelivrUrl = "https://cdn.jsdelivr.net/gh/qpst4/cebian@main/update.json"
$purgeUrl = $jsDelivrUrl -replace "https://cdn.jsdelivr.net/", "https://purge.jsdelivr.net/"
try {
    $purgeResponse = Invoke-RestMethod -Uri $purgeUrl
    Write-Host "Purged jsDelivr cache for $jsDelivrUrl"
    if ($purgeResponse.status) { Write-Host "Status: $($purgeResponse.status)" }
} catch {
    Write-Warning "jsDelivr purge failed (non-fatal): $_"
}

if ($VerifyRemote) {
    $rawUrl = "https://raw.githubusercontent.com/qpst4/cebian/main/update.json"
    foreach ($url in @($rawUrl, $jsDelivrUrl)) {
        $remote = Invoke-RestMethod -Uri $url -TimeoutSec 30
        if ($remote.version -ne $Version) {
            throw "Remote version mismatch at ${url}: $($remote.version)"
        }
        if ([long]$remote.apkSize -ne $ApkSize) {
            throw "Remote apkSize mismatch at ${url}: $($remote.apkSize) (expected $ApkSize)"
        }
        Write-Host "Verified $url"
    }
} else {
    Write-Host "After git push, re-run with -VerifyRemote to confirm both manifest sources."
}
