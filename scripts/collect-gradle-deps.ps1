$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$modules = @(
    ":app",
    ":core:common",
    ":core:autofill",
    ":core:gesture",
    ":core:notification",
    ":core:monitoring",
    ":core:overlay-layout",
    ":core:ocr",
    ":core:translate",
    ":core:native-engine",
    ":feature:settings",
    ":feature:otp",
    ":feature:notification",
    ":feature:apps",
    ":feature:shake",
    ":feature:message",
    ":vendor:ppocr-sdk"
)

$configsByModule = @{
    ":app" = @(
        "liteReleaseRuntimeClasspath",
        "fullReleaseRuntimeClasspath",
        "liteDebugRuntimeClasspath",
        "fullDebugRuntimeClasspath",
        "testLiteReleaseUnitTestRuntimeClasspath",
        "testLiteDebugUnitTestRuntimeClasspath"
    )
}

$defaultConfigs = @(
    "releaseRuntimeClasspath",
    "debugRuntimeClasspath",
    "testReleaseUnitTestRuntimeClasspath",
    "testDebugUnitTestRuntimeClasspath"
)

$outFile = Join-Path $root ".tmp-gradle-deps.txt"
$tmpDir = Join-Path $root ".tmp-deps-chunks"
Remove-Item $outFile -ErrorAction SilentlyContinue
Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $tmpDir | Out-Null

foreach ($module in $modules) {
    $configs = if ($configsByModule.ContainsKey($module)) { $configsByModule[$module] } else { $defaultConfigs }
    foreach ($cfg in $configs) {
        Write-Host "Collecting $module $cfg ..."
        $raw = & .\gradlew.bat "${module}:dependencies" --configuration $cfg --no-configuration-cache 2>&1
        $chunk = Join-Path $tmpDir ("$($module.Replace(':','_'))_$cfg.txt")
        $lines = @()
        foreach ($line in $raw) {
            if ($line -match '^[\\+|\|].*?([\w\.\-]+):([\w\.\-]+):([\w\.\-\+\!]+)') {
                $g = $Matches[1]
                $a = $Matches[2]
                $v = $Matches[3]
                if ($g -eq 'project') { continue }
                $lines += "$module`t$cfg`t$g`:$a`:$v"
            }
        }
        if ($lines.Count -gt 0) {
            Set-Content -Path $chunk -Value $lines -Encoding utf8
        }
    }
}

Get-ChildItem $tmpDir -Filter *.txt | ForEach-Object { Get-Content $_.FullName } | Set-Content $outFile -Encoding utf8
Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Done. Lines: $((Get-Content $outFile | Measure-Object -Line).Lines)"
