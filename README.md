# Cebian（边栏）

Android 边缘手势与系统增强工具，支持侧滑面板、悬浮球取词、摇一摇手势、通知管理、验证码助手、悬浮指针等功能。

- **包名：** `com.slideindex.app`
- **最低系统版本：** Android 11（API 30）
- **目标 SDK：** API 37

---

## 功能概览

**边栏 v1.2.0** 将边缘手势、消息中心（通知 + OTP）、悬浮球取词与扩展工具整合为一体，适合需要侧滑启动、屏幕取词搜图、通知管理与验证码助手的进阶用户。详见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。

### 边缘手势（核心）

- 左右边缘触发条，支持多种外观（气泡 / 胶囊 / 波浪等）
- 40+ 种手势动作：返回、Home、多任务、启动应用/快捷方式、音量/亮度调节、截图、录屏、锁屏、媒体控制、Shell 命令等
- 快速启动器、任务切换器、索引面板、自由窗口模式

### 摇一摇手势

- 多方向晃动识别（翻转、甩动等）
- **扣桌手势**：亮屏时屏幕朝下平放静止后触发（默认锁屏并静音响铃，可自定义动作）
- 全局/独立灵敏度、应用黑名单、按应用独立配置
- 振动与动画反馈

### 通知与消息

- 通知历史记录与高级过滤规则
- 消息提醒（卡片 / 侧边气泡 / 弹幕等多种样式）
- OTP/验证码提取、规则匹配与自动输入

### 悬浮球（取词 / 搜图 / 翻译）

桌面常驻可拖动悬浮球，与边缘手势触发的「悬浮指针」相互独立（首页 → **悬浮球**）。

- **取词**：球体充当摇杆，加号在全屏充当取词指针；优先无障碍取词，失败可降级本地 OCR（`:core:ocr`，支持 ML Kit / Tesseract 模型下载）
- **取词面板**：选中文字后可搜索、翻译、复制；支持点词、全选、去空格等编辑操作
- **文字搜索**：可自定义搜索引擎列表与取词面板网格排序（悬浮球 → 文字搜索引擎）
- **以图搜图**：区域截图后打开聚合搜图面板，内置 Google、Yandex、TinEye、SauceNao、Ascii2D、Trace.moe 等引擎；可配置显示/并行搜索、面板内 WebView 预览或跳转浏览器（悬浮球 → 图片搜索引擎）
- **翻译**：Google / ML Kit 引擎，支持即时翻译悬浮窗或跳转网页
- **其他**：区域截图保存、扫码识别、分享图片 OCR、钉图到通知栏；上滑/下滑/侧滑/点击等手势可绑定独立动作；外观支持预设 / 自定义图片 / GIF / 幻灯片

### 悬浮指针

通过边缘手势动作「悬浮指针」呼出，或在扩展页单独配置（扩展 → **悬浮指针**）。

- 触摸屏幕呼出跟手虚拟摇杆，控制屏幕上的环形指针；轻点摇杆在指针处模拟单击
- **摇杆功能环**：长按摇杆弹出径向菜单，滑到扇区松手执行动作
- **边缘功能**：指针顶到屏幕边缘时触发预览与配置的动作
- **手势录制与回放**：以指针为起点录制滑动轨迹并回放为 Swipe
- 指针外观（圆环 / 箭头 / 准星 / 手势等）、尾影、点击震动与波纹反馈；摇杆与指针速度、隐藏策略可独立调节

### 扩展工具

- 快捷启动器、Shell 命令面板
- 桌面 Widget 悬浮面板
- 屏幕录制
- **设置导出/导入**（扩展页 → 设置备份）：将 DataStore 偏好导出为 JSON，换机或重装后可一键恢复（不含首次引导完成标记）

### 首次启动引导

首次打开应用时会显示分步引导，依次说明悬浮窗、无障碍、通知监听等核心权限的用途，并可直接跳转系统授权页。完成引导后不再显示；该状态不会写入设置备份文件。

### 无障碍

设置界面与主要交互控件已补充 `contentDescription`，便于 TalkBack 等读屏软件使用。

---

## 权限说明

应用需要以下权限以实现对应功能。未授权的模块将无法正常工作，但不会影响已授权模块。

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 显示边缘触发条、侧滑面板、悬浮球、悬浮指针等 Overlay |
| `BIND_ACCESSIBILITY_SERVICE`（无障碍） | 注入系统手势（返回/Home 等）、悬浮球取词、OTP 自动填充、托管边缘 Overlay |
| `BIND_NOTIFICATION_LISTENER_SERVICE`（通知监听） | 读取通知内容、通知历史、消息提醒、验证码提取 |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | 前台服务保活，维持摇一摇监听与无障碍 watchdog |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 屏幕录制 |
| `POST_NOTIFICATIONS` | 显示前台服务通知 |
| `VIBRATE` | 手势与摇一摇触觉反馈 |
| `HIGH_SAMPLING_RATE_SENSORS` | 摇一摇高精度传感器采样 |
| `WAKE_LOCK` | 锁屏亮屏状态下的摇一摇检测 |
| `CAMERA` | 手电筒快捷动作 |
| `WRITE_SETTINGS` | 调节系统亮度 |
| `WRITE_SECURE_SETTINGS` | 高级系统设置（需 ADB 或 Shizuku 授权） |
| `ACCESS_NOTIFICATION_POLICY` | 勿扰模式切换 |
| `QUERY_ALL_PACKAGES` | 列出已安装应用，用于启动器与排除列表 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复通知监听绑定 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 降低后台被杀概率（可选） |
| `BIND_APPWIDGET` | Widget 悬浮面板绑定桌面小部件 |

### 敏感数据与备份

以下本地文件**不会**纳入系统自动备份（`fullBackupContent` / `data-extraction-rules`）：

- `otp_records.json` — OTP/验证码历史记录
- `notification_history.json` — 通知历史记录

用户设置（DataStore）与其他配置文件仍会正常备份。

应用内还提供 **设置导出/导入**（JSON 格式，扩展页入口），可手动备份到任意存储位置；导入会覆盖当前偏好，但保留本机「已完成引导」标记。

---

## Shizuku 配置

部分高级功能依赖 [Shizuku](https://shizuku.rikka.app/) 提供的系统 API 访问能力，包括：

- 任务切换器 / 关闭应用 / 自由窗口
- 强制停止应用、快捷方式启动
- Shell 命令执行（含 root / adb 模式探测）

### 使用步骤

1. 在设备上安装并启动 **Shizuku**（通过无线调试或 root 激活）。
2. 打开 Cebian（边栏），在首页权限卡片中点击 **授予 Shizuku 权限**。
3. 在 Shizuku 弹窗中确认授权。
4. 授权成功后，任务切换、Shell 命令等功能即可使用。

### 技术说明

- 应用通过 `ShizukuProvider`（`${applicationId}.shizuku`）注册 Shizuku 客户端。
- 远程服务实现位于 `TaskManagerUserService`，AIDL 接口为 `ITaskManagerService`。
- 首次调用高级功能时会自动绑定 UserService；若连接失败，可在应用内重新授权或重启 Shizuku。

> **注意：** 未安装 Shizuku 或未授权时，核心边缘手势功能仍可正常使用，仅高级系统集成能力受限。

---

## 构建方式

### 环境要求

- **JDK 21**
- **Android SDK**（compileSdk 37）
- **Gradle 9.6+**（项目已包含 Wrapper，无需单独安装）

### 本地构建

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

Release 构建（已启用 R8 代码压缩与资源收缩）：

```bash
gradlew.bat assembleRelease   # Windows
./gradlew assembleRelease   # macOS / Linux
```

### Release 签名

1. 复制模板并填写密钥信息：

   ```bash
   cp keystore.properties.example keystore.properties
   ```

2. 若尚无密钥库，可生成一个（请替换密码与 DN）：

   ```bash
   keytool -genkeypair -v ^
     -keystore app/keystore/release.jks ^
     -alias slideindex ^
     -keyalg RSA -keysize 2048 -validity 10000 ^
     -storepass YOUR_STORE_PASSWORD ^
     -keypass YOUR_KEY_PASSWORD ^
     -dname "CN=Cebian, OU=Dev, O=Cebian, L=Unknown, ST=Unknown, C=CN"
   ```

3. 在 `keystore.properties` 中设置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。

4. 执行 `assembleRelease`，输出已签名的 Release APK。

> `keystore.properties` 与 `*.jks` 已加入 `.gitignore`，不会进入版本库。未配置时 Release 构建仍可完成，但 APK 不会签名。

### Lint 检查

```bash
gradlew.bat lintDebug
```

CI 对 lint 报错失败（`abortOnError = true`）。密度相关检查（`IconDensities` / `IconMissingDensityFolder`）在 `app/lint.xml` 中忽略，因项目以 xxhdpi 资源为主。

### 在 Android Studio 中打开

1. **File → Open**，选择项目根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 模块，点击 Run。

---

## 项目结构

### Gradle 模块

| 模块 | 说明 |
|------|------|
| `:app` | 主应用、UI、服务、DataStore、运行时逻辑 |
| `:core:common` | 跨模块共享类型（`PanelSide`、`GestureAnimationPosition`）、`QuickLauncherGridLogic`、`ShellCommand`、`PinyinHelper`、Widget 面板模型/编解码/`WidgetGridMetrics`、`OtpMatchRule`/`OtpRecord`/`OtpKeywords`/`VerificationCodeExtractor`、`TaskExclusions`/`RecentPackageResolver`/`ShortcutDisplayRules`/`ShortcutShellParser`、`TaskShellParser`/`AbxXmlParser`/`ShortcutSystemXmlParser` 等 util 纯逻辑 |
| `:feature:apps` | 已安装应用目录：`AppInfo`/`AppRepository`（`PackageManager` + `AppLaunchPort`） |
| `:feature:notification` | 通知过滤与历史：`NotificationFilterRepository`、`NotificationFilterPreferences`、`NotificationHistoryRepository`、`NotificationHistoryRecorder`、`NotificationRuleExecutor`；端口 `NotificationListenerPort`/`NotificationShadeActions`/`NotificationHistoryLaunchPort`/`NotificationIntentLaunchPort`/`NotificationOtpSideEffects`/`NotificationRuleUiStrings` 由 `:app` 绑定 |
| `:core:overlay-layout` | Overlay 网格布局纯逻辑：`OverlayPanelLayoutHost`、`OverlayGridLayout`（`gridLayoutInfo` / `visualColumn`）、`QuickLauncherPanelLayoutEngine` |
| `:core:monitoring` | Debug 性能监控（Overlay FPS、主线程阻塞） |
| `:core:autofill` | OTP 自动输入纯逻辑：`OtpAutoInputBroadcastContract`（广播 Action/Extra 契约）、`OtpAutoInputNodeHelper`（无障碍节点查找）、`OtpAutoInputFallbackPolicy`（系统注入 vs 无障碍回退策略） |
| `:core:ocr` | 本地 OCR：`OcrInferenceService`、ML Kit / Tesseract 模型目录与下载、悬浮球取词降级与分享图片识别 |
| `:core:gesture` | 手势纯逻辑：动作/规则/触发器编解码、路径识别、`GestureShortcutPayload`、快速启动器模型、`ShakeGestureSettings`、`FaceDownGestureSettings` |
| `:core:notification` | 通知纯逻辑：规则匹配、历史/过滤编解码、Intent 捕获、`NotificationShadePolicy`、消息提醒过滤/`MessageAction`/`MessageStyle`/`MessageSettings`/`NotificationData`/`MessageDisplayPlan` 编解码、`MessageThemeIds`/`MessageThemeColors` |
| `:feature:settings` | 设置核心：`AppSettings`、`SettingsRepository`（薄门面 + Hilt `@Inject`）、按域 Mutator（`EdgeSettingsMutator` / `OverlaySettingsMutator` / `ShakeSettingsMutator` / `MessageSettingsMutator` / `OtpSettingsMutator`）、`SettingsPreferencesEditor` / `SettingsSnapshotReader` / `SettingsTriggerStore`、`SettingsPreferenceKeys`、`WidgetPanelPersistence`、手势/边缘/样式扩展等 |
| `:feature:shake` | 摇一摇与扣桌：`ShakeGestureDetector`、`FaceDownDetector`、`ShakeGestureHost`、`FaceDownGestureHost`（Hilt）；端口 `ShakeRuntimePort`/`ShakeActionPort`/`ShakeFeedbackPort` 由 `:app` 绑定 |
| `:feature:message` | 消息提醒编排：`MessageReminderOrchestrator`、`MessageActionExecutor`、`MessageNotificationFilter`、`MessageSwipeGesture`、`MessageThemeExtensions`；端口 `MessageOverlayPort`/`MessageThemePort`/`MessageForegroundPort`/`MessageEnvironmentPort` 由 `:app` 绑定 |
| `:feature:otp` | OTP 持久化：`OtpRecordsRepository`（本地 JSON）、`OtpOfficialRulesLoader`（内置规则资产） |

`:app` 仍保留依赖 Android 资源的 UI 层、悬浮球取词与图搜（`FloatBallStripHost`、`FloatBallImageSearchPanel`、`RegionalScreenshotOcr`）、消息 Overlay 窗口（`MessageCardOverlayWindow` 等）、`MessageThemeCatalog`/`MessageThemeUi`、`NotificationShadeHider` 等 shade 运行时桥接（经 `NotificationListenerPort` 注入）、`ShakeFeedbackOverlay`、`OtpAutoFillController` / LSPosed 模块入口等无障碍与系统集成逻辑。Overlay 巨型类已拆为 coordinator + layout/renderer/touch：`TaskSwitcherOverlayController` + `TaskSwitcherTouchHandler`/`TaskSwitcherLayoutEngine`/`TaskSwitcherRenderer`、`QuickLauncherPanelLayoutEngine`（`:core:overlay-layout`）/`QuickLauncherRenderer`/`QuickLauncherTouchHandler`、`FloatingPointerSession`/`FloatingPointerBounds`/`FloatingPointerDisplay`/`FloatingPointerRadialMenu`。

### LSPosed / Xposed 模块（可选）

APK 内嵌 **LibXposed** 模块（`SlideIndexLibXposedModule`），用于在已 Root + LSPosed 环境下增强 OTP 与系统集成能力。模块元数据位于 `app/src/main/resources/META-INF/xposed/`。

| 钩子 | 作用域 | 功能 |
|------|--------|------|
| `SystemInputInjectorHook` | `system` | 在 `system_server` 注册高优先级广播接收器，尝试系统级输入注入（OTP 自动输入主路径） |
| `PermissionGranterHook` | `system` | 安装期自动授予模块请求的权限 |
| `SmsHandlerHook` | `com.android.phone` | 拦截短信分发，转发验证码到应用 |
| `SmsProviderHook` | `com.android.providers.telephony` | Telephony Provider 层短信捕获补充 |

**作用域**（`scope.list`）：`system`、`android`、`com.android.phone`、`com.android.providers.telephony`。

**与无障碍的关系：** 应用内 `OtpAutoInputOrchestrator` 优先走 LSPosed 系统注入；失败或未安装模块时回退到无障碍 `OtpAutoFillController`。广播契约由 `:core:autofill` 的 `OtpAutoInputBroadcastContract` 统一定义。

> 未使用 LSPosed 时，核心手势、通知、摇一摇等功能不受影响；仅 OTP 系统级注入与短信 Hook 增强不可用。

### 依赖注入（Hilt）

- `SlideIndexApp` 标注 `@HiltAndroidApp`，仅注入 `AppDependencies` 与 `ShizukuInitializer`；`AppModule` 仅提供 `applicationScope`，`AppPortsModule` 绑定通知/应用启动等端口
- Trampoline Activity、`OverlayService`、`SlideIndexAccessibilityService`、`MediaNotificationListener`、`PackageChangeReceiver` 等使用 `@AndroidEntryPoint` 注入 `AppDependencies`
- UI 通过 `@HiltViewModel` / `hiltViewModel()` 获取 ViewModel
- 部分 Overlay 窗口（悬浮球、悬浮指针、Widget 面板、Oho 快捷工具等）通过 Hilt **`OverlayEntryPoint`** + `OverlayDependencyAccess.overlayDependencies()` 获取 `OverlayDependencies`（与 `AppDependencies` 同源）；`SlideIndexAccessibilityService` 仅保留 `overlayHostContext()` 供 Context
- Compose 主界面通过 `LocalAppDependencies` / `rememberAppDependencies()` 向下传递依赖

### 性能监控（Debug）

在 **应用索引** 设置页（仅 Debug 构建可见）可开关 **性能监控**。开启后，任一活跃的重型 Overlay 会经引用计数启用 `PerformanceMonitor`（`EdgeOverlayHost`、悬浮球、悬浮指针、`WidgetPopupOverlayWindow`），统一由 `OverlayPerformanceMonitorBinding` 绑定：

- **setUserPreference** — 跟随设置项 `debugPerformanceMonitorEnabled`
- **acquireOverlay / releaseOverlay** — 各 Overlay 显示/销毁时增减引用，避免单个 Overlay 关闭误关全局监控

监控组件：

- **FrameRateMonitor** — Choreographer 统计 Overlay FPS 与 jank
- **MainThreadWatchdog** — Looper 消息分发耗时检测

日志标签：`FrameRateMonitor`、`MainThreadWatchdog`

### 应用包内目录

```
app/src/main/java/com/slideindex/app/
├── di/             # Hilt 模块、AppDependencies、EntryPoint
├── gesture/        # 手势识别与动作执行
├── overlay/        # 系统悬浮窗与触摸层（边缘面板、悬浮球、悬浮指针等）
├── ui/             # Jetpack Compose 设置界面
├── notification/   # 通知录制、隐藏、Intent 启动等运行时桥接
├── monitoring/     # Overlay 性能监控绑定（Debug）
├── message/        # 消息提醒端口实现（编排逻辑在 :feature:message）
├── otp/            # 验证码提取与自动输入
├── shake/          # 摇一摇端口实现与 ShakeFeedbackOverlay（编排逻辑在 :feature:shake）
├── settings/       # 依赖 R 资源的设置 UI 辅助（指针外观、启动策略文案等）
├── service/        # 前台服务、无障碍、通知监听
├── shizuku/        # Shizuku 集成
├── xposed/         # LSPosed 模块入口与系统 Hook
└── widget/         # Widget 悬浮面板
```

---

## CI

GitHub Actions 工作流（`.github/workflows/ci.yml`）在 push/PR 时自动执行：

- `assembleDebug` + `lintDebug` — 编译 Debug APK 与静态检查

**Push 到 `main`/`master` 时**（已配置 Secrets 的情况下）额外执行：

- `assembleRelease` — 编译已签名的 Release APK
- 上传 `release-apk` artifact，可在 Actions 运行页的 **Artifacts** 中下载

### 配置 GitHub Secrets（CI Release 签名）

在仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret | 说明 |
|--------|------|
| `RELEASE_KEYSTORE_BASE64` | 密钥库文件的 Base64 编码（见下方命令） |
| `RELEASE_STORE_PASSWORD` | 密钥库密码（`storePassword`） |
| `RELEASE_KEY_PASSWORD` | 密钥密码（`keyPassword`） |
| `RELEASE_KEY_ALIAS` | 密钥别名（可选，默认 `slideindex`） |

**生成 Base64（在项目根目录执行）：**

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\keystore\release.jks")) | Set-Clipboard
# 已复制到剪贴板，粘贴到 GitHub Secret 即可
```

```bash
# macOS / Linux
base64 -i app/keystore/release.jks | tr -d '\n'
```

> Secrets 未配置时，CI 仍正常跑 Debug 构建与 Lint，仅跳过 Release 步骤。

---

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE)（GPLv3）开源。

## Release Notes

见 [RELEASE_NOTES.md](RELEASE_NOTES.md)（v1.2.0 差异化说明）。
