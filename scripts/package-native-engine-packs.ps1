# 可选：单独发布引擎 zip 到 GitHub Release 时使用。
# Release APK 内置引擎已由 Gradle 任务 packageNativeEnginePacks 自动完成（见 app/build.gradle.kts）。
param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

Set-Location $ProjectRoot

function Find-GradleNativeLib {
    param([string]$LibraryName)
    $match = Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter $LibraryName -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "arm64-v8a" } |
        Select-Object -First 1
    if ($null -eq $match) {
        throw "Missing library in Gradle cache: $LibraryName"
    }
    return $match.FullName
}

function Find-ProjectNativeLib {
    param([string]$LibraryName)
    $match = Get-ChildItem $ProjectRoot -Recurse -Filter $LibraryName -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -match "arm64-v8a" -and
                $_.FullName -notmatch "\\build\\intermediates\\merged_native_libs\\"
        } |
        Select-Object -First 1
    if ($null -eq $match) {
        throw "Missing project-built library: $LibraryName (run :app:buildCMakeRelWithDebInfo[arm64-v8a])"
    }
    return $match.FullName
}

$outputDir = Join-Path $ProjectRoot "build\native-engine-packs"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function New-EngineZip {
    param(
        [string]$Name,
        [hashtable[]]$Libraries,
        [string[]]$AssetPaths = @()
    )

    $staging = Join-Path $env:TEMP "cebian-pack-$Name"
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    $libTarget = Join-Path $staging "lib\arm64-v8a"
    New-Item -ItemType Directory -Force -Path $libTarget | Out-Null

    foreach ($entry in $Libraries) {
        $source = if ($entry.Source -eq "project") {
            Find-ProjectNativeLib $entry.Name
        } else {
            Find-GradleNativeLib $entry.Name
        }
        Copy-Item $source (Join-Path $libTarget $entry.Name)
    }

    foreach ($asset in $AssetPaths) {
        $source = Join-Path $ProjectRoot "app\src\main\assets\$asset"
        $target = Join-Path $staging "assets\$asset"
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        Copy-Item $source $target
    }

    $zipPath = Join-Path $outputDir "$Name.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -Force
    Remove-Item $staging -Recurse -Force

    return [PSCustomObject]@{
        Name = $Name
        Path = $zipPath
        Sha256 = (Get-FileHash -Algorithm SHA256 -Path $zipPath).Hash.ToLowerInvariant()
        SizeBytes = (Get-Item $zipPath).Length
    }
}

Write-Host "Building CMake jieba library..."
& "$ProjectRoot\gradlew.bat" ":app:buildCMakeRelWithDebInfo[arm64-v8a]" | Out-Null

$ocr = New-EngineZip -Name "ocr-engine-arm64-v5" -Libraries @(
    @{ Name = "libonnxruntime.so"; Source = "gradle" },
    @{ Name = "libopencv_java5.so"; Source = "gradle" },
    @{ Name = "libleptonica.so"; Source = "gradle" },
    @{ Name = "libtesseract.so"; Source = "gradle" }
)

$translate = New-EngineZip -Name "translate-engine-arm64-v1" -Libraries @(
    @{ Name = "libtranslate_jni.so"; Source = "gradle" },
    @{ Name = "liblanguage_id_l2c_jni.so"; Source = "gradle" }
)

$segmentation = New-EngineZip -Name "segmentation-engine-arm64-v1" -Libraries @(
    @{ Name = "libslideindex_jieba.so"; Source = "project" }
) -AssetPaths @(
    "dict/jieba.dict.utf8",
    "dict/hmm_model.utf8",
    "dict/user.dict.utf8"
)

Write-Host ""
Write-Host "=== Native engine packs ==="
foreach ($pack in @($ocr, $translate, $segmentation)) {
    Write-Host "$($pack.Name): $([math]::Round($pack.SizeBytes/1MB, 2)) MB"
    Write-Host "  sha256: $($pack.Sha256)"
    Write-Host "  path: $($pack.Path)"
}

$manifestPath = Join-Path $outputDir "pack-manifest.json"
@{
    version = 1
    packs = @(
        @{ id = "ocr-engine"; file = "ocr-engine-arm64-v5.zip"; sha256 = $ocr.Sha256; sizeBytes = $ocr.SizeBytes },
        @{ id = "translate-engine"; file = "translate-engine-arm64-v1.zip"; sha256 = $translate.Sha256; sizeBytes = $translate.SizeBytes },
        @{ id = "segmentation-engine"; file = "segmentation-engine-arm64-v1.zip"; sha256 = $segmentation.Sha256; sizeBytes = $segmentation.SizeBytes }
    )
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $manifestPath

Write-Host ""
Write-Host "Manifest: $manifestPath"
Write-Host "Update core/native-engine/src/main/assets/native_engine_packs.json with sha256 and sizeBytes."
