# 发版后更新仓库根目录 update.json（App 通过 raw + jsDelivr@main 拉取，不走 GitHub API）
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [Parameter(Mandatory = $true)]
    [int]$VersionCode,
    [Parameter(Mandatory = $true)]
    [long]$ApkSize,
    [string]$Notes = "",
    [string]$NotesFile = "",
    [switch]$FromChangelog,
    # 从 CHANGELOG 生成时每个分组（新增 / 变更 / 修复）最多取前 N 条
    [int]$MaxChangelogItems = 8,
    [string]$ApkFileName = "",
    [switch]$VerifyRemote
)

$ErrorActionPreference = "Stop"
if ($ApkSize -le 0) {
    throw "ApkSize must be > 0. Write the final manifest only after the release APK is built."
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $ProjectRoot "update.json"
$changelogPath = Join-Path $ProjectRoot "CHANGELOG.md"

function Normalize-UpdateNotes {
    param(
        [string]$Raw,
        # 仅对手写 notes（-Notes / -NotesFile）拆分中文分号；CHANGELOG 条目内的分号保留原样
        [switch]$SplitSemicolons
    )
    if ([string]::IsNullOrWhiteSpace($Raw)) { return "" }
    $normalized = $Raw
    if ($SplitSemicolons) {
        $fullWidthSemicolon = [char]0xFF1B
        $normalized = $Raw.Replace($fullWidthSemicolon, "`n")
    }
    $lines = $normalized -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
    return ($lines -join "`n")
}

function Get-ChangelogBulletNotes {
    param(
        [string]$Version,
        [string]$ChangelogPath,
        [int]$MaxItems
    )
    $section = & (Join-Path $PSScriptRoot "extract-changelog-section.ps1") -Version $Version -ChangelogPath $ChangelogPath
    # Map CHANGELOG's ### Added/Changed/Fixed headers to the app's in-dialog group labels.
    $labelByHeader = @{
        "added"   = "新增"
        "changed" = "变更"
        "fixed"   = "修复"
    }
    $plainBullets = @()
    $groups = [System.Collections.Generic.List[object]]::new()
    $currentTitle = ""
    $currentItems = [System.Collections.Generic.List[string]]::new()
    $sawGroupHeader = $false

    foreach ($line in ($section -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^###\s+(.+)$') {
            $title = $labelByHeader[$Matches[1].Trim().ToLowerInvariant()]
            if ($title) {
                $sawGroupHeader = $true
                if ($currentItems.Count -gt 0) {
                    $groups.Add([pscustomobject]@{ Title = $currentTitle; Items = @($currentItems) })
                }
                $currentTitle = $title
                $currentItems = [System.Collections.Generic.List[string]]::new()
            }
            continue
        }
        if ($trimmed.StartsWith("- ")) {
            # Strip markdown emphasis used by CHANGELOG bullets so plain-text dialogs stay clean.
            $item = $trimmed.Substring(2).Trim().Replace("**", "").Replace("``", "")
            if ($currentTitle) {
                if ($MaxItems -le 0 -or $currentItems.Count -lt $MaxItems) {
                    $currentItems.Add($item)
                }
            } else {
                $plainBullets += $item
            }
        }
    }
    if ($currentItems.Count -gt 0) {
        $groups.Add([pscustomobject]@{ Title = $currentTitle; Items = @($currentItems) })
    }

    if (-not $sawGroupHeader) {
        # Legacy changelog without ### headers: keep the old flat bullet list.
        return ($plainBullets -join "`n")
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($bullet in $plainBullets) { $lines.Add($bullet) }
    foreach ($group in $groups) {
        if ($group.Items.Count -eq 0) { continue }
        $lines.Add("## $($group.Title)")
        foreach ($item in $group.Items) { $lines.Add("- $item") }
    }
    if ($lines.Count -eq 0) { return "" }
    return ($lines -join "`n")
}

function Resolve-UpdateNotes {
    if ($FromChangelog) {
        return Normalize-UpdateNotes (Get-ChangelogBulletNotes -Version $Version -ChangelogPath $changelogPath -MaxItems $MaxChangelogItems)
    }
    if (-not [string]::IsNullOrWhiteSpace($NotesFile)) {
        if (-not (Test-Path $NotesFile)) { throw "NotesFile not found: $NotesFile" }
        $fileNotes = [System.IO.File]::ReadAllText($NotesFile, [System.Text.UTF8Encoding]::new($false))
        return Normalize-UpdateNotes $fileNotes -SplitSemicolons
    }
    return Normalize-UpdateNotes $Notes -SplitSemicolons
}

if ([string]::IsNullOrWhiteSpace($ApkFileName)) {
    $ApkFileName = "cebian-$Version-lite.apk"
}

$resolvedNotes = Resolve-UpdateNotes
if ([string]::IsNullOrWhiteSpace($resolvedNotes)) {
    throw "Notes are empty. Pass -Notes, -NotesFile, or -FromChangelog."
}

$apkUrl = "https://github.com/qpst4/cebian/releases/download/v$Version/$ApkFileName"
$manifest = [ordered]@{
    version     = $Version
    versionCode = $VersionCode
    apkUrl      = $apkUrl
    apkSize     = $ApkSize
    notes       = $resolvedNotes
}

$json = ($manifest | ConvertTo-Json -Depth 3) + "`n"
[System.IO.File]::WriteAllText($manifestPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $manifestPath"
Write-Host $json

$writtenRaw = [System.IO.File]::ReadAllText($manifestPath, [System.Text.UTF8Encoding]::new($false))
$written = $writtenRaw | ConvertFrom-Json
if ($written.version -ne $Version -or [long]$written.apkSize -ne $ApkSize) {
    throw "Local update.json validation failed."
}
if ($written.notes -ne $resolvedNotes) {
    throw "Local update.json notes validation failed."
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
