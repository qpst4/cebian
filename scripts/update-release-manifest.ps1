# 发版后更新仓库根目录 update.json（App 通过 raw + jsDelivr@latest 拉取，不走 GitHub API）
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [int]$VersionCode,
    [Parameter(Mandatory = $true)]
    [long]$ApkSize,
    [string]$Notes = "",
    [string]$ApkFileName = ""
)

$ErrorActionPreference = "Stop"
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

$jsDelivrUrl = "https://cdn.jsdelivr.net/gh/qpst4/cebian@latest/update.json"
$purgeUrl = $jsDelivrUrl -replace "https://cdn.jsdelivr.net/", "https://purge.jsdelivr.net/"
try {
    $purgeResponse = Invoke-RestMethod -Uri $purgeUrl
    Write-Host "Purged jsDelivr cache for $jsDelivrUrl"
    if ($purgeResponse.status) { Write-Host "Status: $($purgeResponse.status)" }
} catch {
    Write-Warning "jsDelivr purge failed (non-fatal): $_"
}
