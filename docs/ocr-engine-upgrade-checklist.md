# OCR / 原生引擎包升级检查清单

> **适用范围**：仅在做 OCR 引擎包或 ONNX Runtime 相关升级时使用（例如升 `onnxruntime`、`packRevision`、重打 `ocr-engine-arm64-v*.zip`）。  
> 普通依赖（WorkManager、MIUIX 等）**不需要**走本文档。

当前基线（2026-09）：ORT **1.29.0**，OCR 引擎包 **revision 4**，zip 名 `ocr-engine-arm64-v4.zip`，仅 **arm64-v8a**。

---

## 1. 架构约束（必读，避免踩坑）

Android 上 ONNX Runtime 拆成两部分，**主版本必须一致**：

| 组件 | 位置 | 说明 |
|------|------|------|
| `libonnxruntime4j_jni.so` + Java API | **APK 内**（`onnxruntime-android` 依赖） | 只能用 `System.loadLibrary`，**不能**放进可下载引擎包 |
| `libonnxruntime.so` | **引擎包 zip** | 由 `ensurePackReady` / 下载 / Full 内置解压加载 |

**禁止**：

- 把 `libonnxruntime4j_jni.so` 打进引擎包并从 APK `packaging.jniLibs.excludes` 里排除（已验证不可行）。
- 只升 APK 内 ORT 版本而不 bump 引擎包 `packRevision`（会导致 `UnsatisfiedLinkError: OrtGetApiBase` 等错配）。

引擎包 zip 内还应包含（与 `app/build.gradle.kts` 中 `nativeEnginePackSpecs` 一致）：

- `libopencv_java4.so`
- `libleptonica.so`
- `libtesseract.so`

APK 的 `jniLibs.excludes` 已排除上述大库，避免与引擎包重复打包。

---

## 2. 需要修改的文件

升 ORT / 发新 OCR 引擎包时，至少核对以下文件 **同步修改**：

| 文件 | 改什么 |
|------|--------|
| `gradle/libs.versions.toml` | `onnxruntime` 版本号 |
| `app/build.gradle.kts` | `zipName`（如 `ocr-engine-arm64-v5.zip`）、`nativeEnginePackSpecs` 若库列表变化 |
| `scripts/package-native-engine-packs.ps1` | OCR zip 名称（与上保持一致） |
| `core/native-engine/src/main/assets/native_engine_packs.json` | `packRevision`、`displayVersion`、`sizeBytes`、`sha256`、`url` |

`packRevision` 与 zip 文件名中的 `vN` 应对齐（应用内迁移逻辑依赖 revision，不只靠文件名）。

---

## 3. 构建与校验

### 3.1 打引擎 zip（本地 / CI 前）

**推荐**：由 Gradle 自动打包（与 Full Release 同源）：

```powershell
.\gradlew.bat packageNativeEnginePacks copyBundledNativeEnginePacks
```

产物目录：`build/native-engine-packs/ocr-engine-arm64-vN.zip`

**可选**：单独脚本（主要用于上传 GitHub Release 时核对哈希）：

```powershell
.\scripts\package-native-engine-packs.ps1
```

脚本会输出 `sha256` 和 `sizeBytes`；**不要手填**，复制到 `native_engine_packs.json`。

### 3.2 更新 `native_engine_packs.json`

对 `ocr-engine` 条目更新：

- `packRevision`（递增）
- `displayVersion`（如 `ONNX Runtime 1.30.0`）
- `sizeBytes`、`sha256`（与 zip 一致）
- `url`（指向对应 GitHub Release 资产）

### 3.3 编译自检

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat :app:assembleFullDebug
```

Debug 包在 `OcrStartupSmokeVerifier` 下会做启动 OCR 冒烟（仅 `BuildConfig.DEBUG`）。

### 3.4 Full / Lite 差异

| 变体 | 内置引擎 zip | 说明 |
|------|----------------|------|
| **full** | ✅ `bundled-native-engine/ocr-engine.zip` | `mergeFullReleaseAssets` / `mergeFullDebugAssets` 依赖 `copyBundledNativeEnginePacks` |
| **lite** | ❌ 无 | 用户须在设置中下载引擎包 |

---

## 4. 迁移与用户提示

逻辑入口（**不要**在别处重复调用 `upgradePackIfOutdated`）：

- `OcrEnginePackMigrationStartup`（应用启动）
- `NativeEnginePackMigrationRunner` + `OcrEnginePackMigrationHost`（UI 弹窗）

行为摘要：

- 本地引擎 **revision 落后** → 删旧包 → Full 尝试内置解压 / Lite 需网络下载。
- **Full 成功**：弹「引擎已升级」；**Lite 需下载**：弹「请下载引擎包」。
- 同一 `packRevision` 用户点「知道了」或「稍后」后 **不再弹**（`native_engine_migration_notice` prefs）。
- 引擎包 revision 变更时会 `invalidateEngineBlocking()`，避免旧 ORT 实例残留。

---

## 5. 真机测试矩阵（发版前必做）

**不要**仅用 adb 往 `files/native-engine-packs/` 手推 `.so` 作为唯一验证；与正式下载/内置路径不一致。

| # | 起点 | 安装包 | 期望 |
|---|------|--------|------|
| 1 | 上一正式版 + 旧 OCR 引擎（如 v2） | 新版 **Full**，保留数据覆盖安装 | 弹升级成功；设置里为 vN · 新版本；取词正常 |
| 2 | 上一正式版 + 旧 OCR 引擎 | 新版 **Lite**，保留数据 | 弹需下载；设置里可下新包；取词正常 |
| 3 | 已是新引擎 revision | 新版覆盖安装 | 不弹（或仅补弹一次后不再出现） |
| 4 | 清数据全新安装 **Full** | 新版 | 一般不弹；引擎已内置 |
| 5 | 清数据全新安装 **Lite** | 新版 | 不弹；设置中手动下载后取词正常 |
| 6 | 设置里删除 OCR 引擎再下载 | Full 或 Lite | 版本显示正确；取词正常 |

可取 logcat 过滤：`OcrStartupSmoke`、`OcrInferenceService`、`native_engine`。

---

## 6. 发布

1. 更新 `CHANGELOG.md`、升 `versionCode` / `versionName`。
2. 提交并 push `main`。
3. **只 push 一次** tag（如 `v1.9.23`），触发 `.github/workflows/release.yml`。
4. GitHub Release 应包含：
   - `cebian-<version>-full.apk`
   - `cebian-<version>-lite.apk`
   - （若单独分发）`ocr-engine-arm64-vN.zip`
5. 确认 `native_engine_packs.json` 里的 `url` 与 Release 资产一致。
6. CI 会更新 `update.json`；检查 Release 页无错误版本 APK。

避免：tag 指向旧 commit 后又 force 更新 tag，导致 CI 跑两次、Release 混入错误 APK。

---

## 7. 历史踩坑速查

| 现象 | 常见原因 |
|------|----------|
| 取词空白 / `OrtGetApiBase` | APK ORT 与引擎包 `libonnxruntime.so` 版本不一致；或残留旧 revision |
| OpenCV 初始化失败误报 | `initLocal` 失败但 JNI 可用，需探测（见 `OpenCVUtils`） |
| 升级后无弹窗 | 启动时静默迁移与 UI 重复执行（已改为单路径 + 持久化通知） |
| Lite 不提示下载 | 删包后二次检查返回 UpToDate（已用 pending 状态修复） |
| 下载校验失败 | `native_engine_packs.json` 的 sha256/size 与 zip 不符 |
| 1.9.20 用户下不了 v4 | 旧 APK 目录仍指向 v2 URL；需发含新 catalog 的 App 版本 |

---

## 8. 相关代码索引

- 目录与下载：`NativeEnginePackCoordinator`、`NativeEnginePackDownloader`
- 迁移弹窗：`NativeEnginePackMigrationRunner`、`OcrEnginePackMigrationHost`
- OCR 加载：`OcrInferenceService`、`NativeEnginePackLoader`
- 打包：`app/build.gradle.kts`（`nativeEnginePackSpecs`、`copyBundledNativeEnginePacks`）
- 冒烟：`app/.../OcrStartupSmokeVerifier.kt`（仅 Debug）
