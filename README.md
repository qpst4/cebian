# SlideIndex（边栏）

Android 边缘手势与系统增强工具，支持侧滑面板、摇一摇手势、通知管理、验证码助手、悬浮指针等功能。

- **包名：** `com.slideindex.app`
- **最低系统版本：** Android 11（API 30）
- **目标 SDK：** API 37

---

## 功能概览

### 边缘手势（核心）

- 左右边缘触发条，支持多种外观（气泡 / 胶囊 / 波浪等）
- 40+ 种手势动作：返回、Home、多任务、启动应用/快捷方式、音量/亮度调节、截图、录屏、锁屏、媒体控制、Shell 命令等
- 快速启动器、任务切换器、索引面板、自由窗口模式

### 摇一摇手势

- 多方向晃动识别（翻转、甩动等）
- 全局/独立灵敏度、应用黑名单、按应用独立配置
- 振动与动画反馈

### 通知与消息

- 通知历史记录与高级过滤规则
- 消息提醒（卡片 / 侧边气泡 / 弹幕等多种样式）
- OTP/验证码提取、规则匹配与自动输入

### 扩展工具

- 快捷启动器、Shell 命令面板
- 桌面 Widget 悬浮面板
- 悬浮指针 / 虚拟摇杆 / 径向菜单
- 屏幕录制

---

## 权限说明

应用需要以下权限以实现对应功能。未授权的模块将无法正常工作，但不会影响已授权模块。

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 显示边缘触发条、侧滑面板、悬浮指针等 Overlay |
| `BIND_ACCESSIBILITY_SERVICE`（无障碍） | 注入系统手势（返回/Home 等）、OTP 自动填充、托管边缘 Overlay |
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

---

## Shizuku 配置

部分高级功能依赖 [Shizuku](https://shizuku.rikka.app/) 提供的系统 API 访问能力，包括：

- 任务切换器 / 关闭应用 / 自由窗口
- 强制停止应用、快捷方式启动
- Shell 命令执行（含 root / adb 模式探测）

### 使用步骤

1. 在设备上安装并启动 **Shizuku**（通过无线调试或 root 激活）。
2. 打开 SlideIndex，在首页权限卡片中点击 **授予 Shizuku 权限**。
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
     -dname "CN=SlideIndex, OU=Dev, O=SlideIndex, L=Unknown, ST=Unknown, C=CN"
   ```

3. 在 `keystore.properties` 中设置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。

4. 执行 `assembleRelease`，输出已签名的 Release APK。

> `keystore.properties` 与 `*.jks` 已加入 `.gitignore`，不会进入版本库。未配置时 Release 构建仍可完成，但 APK 不会签名。

### Lint 检查

```bash
gradlew.bat lintDebug
```

项目使用 `lint-baseline.xml` 固化既有 lint 问题，CI 仅对**新增** lint 报错失败。

### 单元测试

```bash
gradlew.bat testDebugUnitTest
```

覆盖通知规则匹配、各 Codec 往返、手势几何分类、OTP 提取与解析、`NotificationShadeHider` 与 `PerformanceMonitor` 引用计数等纯逻辑测试。

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
| `:core:monitoring` | Debug 性能监控（Overlay FPS、主线程阻塞） |
| `:core:autofill` | OTP 自动输入纯逻辑：`OtpAutoInputBroadcastContract`（广播 Action/Extra 契约）、`OtpAutoInputNodeHelper`（无障碍节点查找）、`OtpAutoInputFallbackPolicy`（系统注入 vs 无障碍回退策略） |
| `:core:gesture` | 手势纯逻辑：动作/规则/触发器编解码、路径识别、`GestureShortcutPayload`、快速启动器模型、`ShakeGestureSettings` |
| `:core:notification` | 通知纯逻辑：规则匹配、历史/过滤编解码、Intent 捕获、`NotificationShadePolicy`、消息提醒过滤/`MessageAction`/`MessageStyle`/`MessageSettings`/`NotificationData`/`MessageDisplayPlan` 编解码、`MessageThemeIds`/`MessageThemeColors` |
| `:feature:settings` | 设置核心：`AppSettings`、`SettingsRepository`（DataStore + Hilt `@Inject`）、`SettingsPreferenceKeys`（偏好键常量）、`WidgetPanelPersistence`、手势/边缘/样式扩展、`AppLaunchPolicy`/`FreeWindowMode`、`AnimationStyles`、动画编解码、`HapticStrength`、`GestureHintStyle`、`FloatingPointerRadialMenuCodec` 等 |
| `:feature:shake` | 摇一摇：`ShakeGestureDetector`、`ShakeVibrationHelper`、`ShakeGestureHost`（Hilt）；端口 `ShakeRuntimePort`/`ShakeActionPort`/`ShakeFeedbackPort` 由 `:app` 绑定 |
| `:feature:message` | 消息提醒编排：`MessageReminderOrchestrator`、`MessageActionExecutor`、`MessageNotificationFilter`、`MessageSwipeGesture`、`MessageThemeExtensions`；端口 `MessageOverlayPort`/`MessageThemePort`/`MessageForegroundPort`/`MessageEnvironmentPort` 由 `:app` 绑定 |
| `:feature:otp` | OTP 持久化：`OtpRecordsRepository`（本地 JSON）、`OtpOfficialRulesLoader`（内置规则资产） |

`:app` 仍保留依赖 Android 资源的 UI 层、消息 Overlay 窗口（`MessageCardOverlayWindow` 等）、`MessageThemeCatalog`/`MessageThemeUi`、`NotificationShadeHider` 等 shade 运行时桥接（经 `NotificationListenerPort` 注入）、`ShakeFeedbackOverlay`、`OtpAutoFillController` / LSPosed 模块入口等无障碍与系统集成逻辑。Overlay 巨型类已拆为 coordinator + layout/renderer/touch：`TaskSwitcherLayoutEngine`/`TaskSwitcherRenderer`、`QuickLauncherPanelLayoutEngine`/`QuickLauncherRenderer`/`QuickLauncherTouchHandler`、`FloatingPointerSession`/`FloatingPointerBounds`/`FloatingPointerDisplay`。

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
- 部分 Overlay 窗口（悬浮指针、Widget 面板、Oho 快捷工具等）通过 Hilt **`OverlayEntryPoint`** + `OverlayDependencyAccess.overlayDependencies()` 获取 `OverlayDependencies`（与 `AppDependencies` 同源）；`SlideIndexAccessibilityService` 仅保留 `overlayHostContext()` 供 Context
- Compose 主界面通过 `LocalAppDependencies` / `rememberAppDependencies()` 向下传递依赖；单元测试直接构造所需 Repository（如 `SettingsRepository`），不再依赖 `AppEntryPoints`

### 性能监控（Debug）

在 **应用索引** 设置页（仅 Debug 构建可见）可开关 **性能监控**。开启后，任一活跃的重型 Overlay 会经引用计数启用 `PerformanceMonitor`（`EdgeOverlayHost`、悬浮指针、`WidgetPopupOverlayWindow`），统一由 `OverlayPerformanceMonitorBinding` 绑定：

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
├── overlay/        # 系统悬浮窗与触摸层
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

- `assembleDebug` — 编译 Debug APK
- `lintDebug` — 静态检查（基于 baseline，仅拦截新问题）
- `testDebugUnitTest` — 单元测试

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

本项目采用 [MIT License](LICENSE) 开源。
