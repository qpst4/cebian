# 从 CHANGELOG.md 截取指定版本的 Markdown 段落（用于 GitHub Release，勿用整份 CHANGELOG）
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ChangelogPath = "",
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"

function Get-ChangelogSectionText {
    param(
        [string]$Version,
        [string]$ChangelogPath
    )
    if ($Version -eq "Unreleased") {
        throw "Cannot extract [Unreleased] for release notes."
    }
    if (-not (Test-Path $ChangelogPath)) {
        throw "Changelog not found: $ChangelogPath"
    }

    $content = [System.IO.File]::ReadAllText($ChangelogPath, [System.Text.UTF8Encoding]::new($false))
    $header = "## [$Version]"
    $start = $content.IndexOf($header)
    if ($start -lt 0) {
        throw "Section not found in CHANGELOG: $header"
    }

    $afterHeader = $start + $header.Length
    $remainder = $content.Substring($afterHeader)
    $nextMatch = [regex]::Match($remainder, '(?m)^## \[')
    if ($nextMatch.Success) {
        $section = $content.Substring($start, $afterHeader + $nextMatch.Index - $start).TrimEnd()
    } else {
        $section = $content.Substring($start).TrimEnd()
    }
    return $section
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ChangelogPath)) {
    $ChangelogPath = Join-Path $ProjectRoot "CHANGELOG.md"
}

$section = Get-ChangelogSectionText -Version $Version -ChangelogPath $ChangelogPath
if ([string]::IsNullOrWhiteSpace($OutFile)) {
    Write-Output $section
} else {
    [System.IO.File]::WriteAllText($OutFile, $section + "`n", [System.Text.UTF8Encoding]::new($false))
    Write-Host "Wrote $OutFile"
}
