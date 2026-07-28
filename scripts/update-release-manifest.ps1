# 发版后更新仓库根目录 update.json（App 通过 jsDelivr/raw 拉取，不走 GitHub API）
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [int]$VersionCode,
    [Parameter(Mandatory = $true)]
    [long]$ApkSize,
    [string]$Notes = "",
    [string]$ApkFileName = "app-release.apk"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $ProjectRoot "update.json"

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
