# 📱 Cebian（边栏）

Android 边缘手势与系统增强工具：侧滑面板、悬浮球取词搜图、摇一摇/扣桌手势、通知与 OTP 管理、悬浮指针等。

- **包名：** `com.slideindex.app`
- **版本：** 1.2.1（versionCode 4）
- **最低系统：** Android 11（API 30）
- **目标 SDK：** API 37
- **许可证：** [GPLv3](LICENSE)

---

## 功能概览

底部四个 Tab：🏠 **首页** · 📳 **晃动** · 🔔 **通知** · 🧩 **扩展**。详见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。

### 🏠 首页 — 边缘手势与悬浮球

**边缘手势**

- 左右边缘触发条，外观支持气泡 / 胶囊 / 波浪等动画样式
- 48 种可配置手势动作：返回、Home、多任务、应用索引、快速启动器、任务切换器、启动应用/快捷方式、音量/亮度、截图/全屏截图取词、录屏、锁屏（含静音变体）、媒体控制、Shell 命令、Widget 面板、悬浮指针、以图搜图、钉图暂存、快捷工具环等
- 触发条位置/角度、横屏/锁屏/桌面隐藏策略、排除应用（需使用情况访问权限）
- 自由窗口模式、应用保活、QS 磁贴快捷开关
- 需开启无障碍服务与悬浮窗权限；手势总开关在首页

**🔮 悬浮球**（首页 → 悬浮球，与「悬浮指针」相互独立）

- 桌面常驻可拖动悬浮球；球体充当摇杆，加号在全屏充当取词指针
- **取词**：优先无障碍取词，失败可降级本地 OCR（ML Kit / Tesseract / PPOCR）
- **取词面板**：搜索、翻译、复制、点词/全选/去空格；支持区域截图、扫码、分享图片 OCR
- **文字搜索**：自定义搜索引擎列表与面板网格排序；支持从 GestureEVO / SearchEVO 等格式独立导入
- **以图搜图**：区域截图后打开聚合搜图面板（Google、Yandex、TinEye、SauceNAO、IQDB、3D-IQDB、ASCII2D、trace.moe、AnimeTrace、Copyseeker）；可配置显示、并行搜索、面板内 WebView 或跳转浏览器
- **翻译**：Google / ML Kit，即时翻译悬浮窗或跳转网页
- 上滑/下滑/侧滑/点击等手势可绑定独立动作；外观支持预设 / 自定义图片 / GIF / 幻灯片

### 📳 晃动 — 摇一摇与扣桌

- 六方向晃动识别（左/右翻转、前/后翻转、左/右甩）
- **扣桌手势**：亮屏时屏幕朝下平放静止后触发（默认锁屏并静音响铃，可自定义动作）
- 锁屏晃动、按应用独立配置、独立灵敏度、应用黑名单
- 振动与动画反馈

### 🔔 通知 — 消息提醒、历史与 OTP

- **消息提醒**：拦截通知并以卡片 / 悬浮球 / 侧边气泡 / 弹幕等样式展示
- **通知历史**：活跃/历史/已隐藏分类，高级过滤规则
- **OTP 中心**：验证码提取、规则匹配、自动输入、成功率统计；可选 LSPosed 短信捕获与系统注入增强

### 🧩 扩展 — 工具与备份

| 功能 | 入口 | 说明 |
|------|------|------|
| 应用索引 | 扩展 → 应用索引 | 侧滑应用列表面板，可调列数/透明度 |
| 快速启动器 | 扩展 → 快速启动器 | 侧滑网格启动器 |
| Shell 命令 | 扩展 → Shell 命令 | 命令面板与模板变量；执行依赖 Shizuku |
| Widget 面板 | 扩展 → Widget 面板 | 悬浮绑定桌面小部件 |
| 悬浮指针 | 扩展 → 悬浮指针 | 也可通过边缘手势动作呼出 |
| 设置备份 | 扩展 → 设置备份 | ZIP 导出/导入，见下文 |
| 关于 | 扩展 → 关于 | 版本、Release Notes、隐私政策 |

**🎯 悬浮指针**（扩展 → 悬浮指针）

- 触摸屏幕呼出跟手虚拟摇杆，控制屏幕环形指针；轻点摇杆在指针处模拟单击
- 摇杆功能环（径向菜单）、四边边缘触发、手势录制与回放
- 指针外观（圆环/箭头/准星/手势等）、尾影、点击震动与波纹反馈

无独立设置页、通过手势动作触发：屏幕录制、快捷工具环（OHO 风格）、钉图暂存面板。

### 首次启动引导

首次打开会分步说明悬浮窗、无障碍、通知监听等权限用途，并可直接跳转授权。完成后不再显示；引导完成状态不会写入备份。

### 无障碍

设置界面与主要交互控件已补充 `contentDescription`，便于 TalkBack 等读屏软件使用。

---

## 💾 设置备份（ZIP）

入口：**扩展 → 设置备份**。默认文件名 `cebian-backup-{timestamp}.zip`。

### ZIP 结构

```
cebian-backup-*.zip
├── settings.json              # DataStore 偏好（formatVersion=2）
├── search_engine_icons/       # 自定义搜索引擎图标（若有）
├── float_ball_assets/         # 悬浮球自定义图片/GIF/幻灯片（若有）
└── stash/                     # 钉图暂存本地文件（若有）
```

`settings.json` 内含全部 DataStore 偏好序列化，以及可选嵌入的 OTP 记录与通知历史 JSON。

### 导出与导入

- 默认导出全部应用设置与上述资产目录
- 可勾选「包含验证码记录与通知历史」一并导出敏感数据（仅保存在本地 ZIP）
- 导入前显示预览（备份时间、来源版本、设置域、是否含敏感数据）
- 确认后覆盖当前偏好并恢复资产文件；保留本机「已完成引导」标记（`onboarding_completed` 不随导入覆盖）

### 与系统自动备份的区别

系统自动备份（`fullBackupContent`）排除 `otp_records.json` 与 `notification_history.json`。应用内 ZIP 备份是独立机制，可选手动包含敏感数据，并额外打包搜索引擎图标、悬浮球资源与钉图暂存。

---

## 🔐 权限说明

未授权的模块无法正常工作，但不影响已授权模块。

### Manifest 声明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 翻译、OCR/翻译模型下载、以图搜图上传等 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `SYSTEM_ALERT_WINDOW` | 边缘触发条、侧滑面板、悬浮球、悬浮指针等 Overlay |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | 前台服务保活（摇一摇监听、无障碍 watchdog） |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 屏幕录制、区域截图 |
| `POST_NOTIFICATIONS` | 前台服务通知 |
| `VIBRATE` | 手势与摇一摇触觉反馈 |
| `HIGH_SAMPLING_RATE_SENSORS` | 摇一摇高精度传感器采样 |
| `WAKE_LOCK` | 锁屏亮屏状态下的摇一摇检测 |
| `CAMERA` | 手电筒快捷动作 |
| `WRITE_SETTINGS` | 调节系统亮度 |
| `WRITE_SECURE_SETTINGS` | 高级系统设置（需 ADB 或 Shizuku） |
| `ACCESS_NOTIFICATION_POLICY` | 勿扰模式切换 |
| `QUERY_ALL_PACKAGES` | 已安装应用列表（启动器、排除列表） |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复通知监听绑定 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 降低后台被杀概率（可选） |
| `BIND_APPWIDGET` | Widget 悬浮面板绑定桌面小部件 |

### 服务与特殊权限

| 权限 / 能力 | 用途 |
|-------------|------|
| 无障碍服务 | 注入系统手势、悬浮球取词、OTP 自动填充、托管边缘 Overlay |
| 通知监听 | 读取通知、通知历史、消息提醒、验证码提取 |
| 使用情况访问 | 排除应用、前台包识别 |
| Shizuku | 任务切换、Shell 执行、快捷方式、自由窗口等系统集成 |
| MediaProjection | 录屏与区域截图（用户授权） |
| QS 磁贴 | 手势总开关快捷切换 |

---

## Shizuku 配置

部分高级功能依赖 [Shizuku](https://shizuku.rikka.app/)：任务切换器/关闭应用/自由窗口、强制停止应用、快捷方式启动、Shell 命令执行（含 root/adb 模式探测）。

1. 安装并启动 Shizuku（无线调试或 root）
2. 打开 Cebian，在首页权限卡片点击「授予 Shizuku 权限」
3. 在 Shizuku 弹窗中确认授权

- Provider：`${applicationId}.shizuku`
- 远程服务：`TaskManagerUserService`（AIDL `ITaskManagerService`）

未安装或未授权时，核心边缘手势仍可用，仅高级系统集成能力受限。

---

## LSPosed / Xposed 模块（可选）

APK 内嵌 **LibXposed** 模块（`SlideIndexLibXposedModule`），用于 Root + LSPosed 环境下增强 OTP：

| 钩子 | 作用域 | 功能 |
|------|--------|------|
| `SystemInputInjectorHook` | `system` | 系统级输入注入（OTP 自动输入主路径） |
| `PermissionGranterHook` | `system` | 安装期自动授予模块权限 |
| `SmsHandlerHook` | `com.android.phone` | 拦截短信分发，转发验证码 |
| `SmsProviderHook` | `com.android.providers.telephony` | Telephony Provider 层短信捕获 |

作用域：`system`、`android`、`com.android.phone`、`com.android.providers.telephony`。

`OtpAutoInputOrchestrator` 优先走 LSPosed 系统注入，失败或未安装模块时回退无障碍 `OtpAutoFillController`。未使用 LSPosed 时，核心手势、通知、摇一摇、悬浮球等功能不受影响。

---

## 构建方式

### 环境要求

- JDK 21、Android SDK（compileSdk 37）、Gradle 9.6+（含 Wrapper）
- 仅构建 arm64-v8a ABI

### 本地构建

```bash
gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug            # macOS / Linux
```

Release（R8 压缩 + 资源收缩）：

```bash
gradlew.bat assembleRelease        # Windows
./gradlew assembleRelease          # macOS / Linux
```

### Release 签名

1. `cp keystore.properties.example keystore.properties` 并填写密钥信息
2. 执行 `assembleRelease`

`keystore.properties` 与 `*.jks` 已加入 `.gitignore`。未配置时 Release 可编译但不会签名。

### Lint

```bash
gradlew.bat lintDebug
```

CI 对 lint 报错失败。`IconDensities` / `IconMissingDensityFolder` 在 `app/lint.xml` 中忽略。

### 在 Android Studio 中打开

**File → Open** 选择项目根目录，等待 Gradle Sync，选择 `app` 模块运行。

---

## 项目结构

| 模块 | 说明 |
|------|------|
| `:app` | 主应用、Compose UI、服务、Overlay 运行时 |
| `:core:common` | 共享类型、Shell/快捷方式解析、OTP 工具等 |
| `:core:autofill` | OTP 自动输入广播契约、节点查找、回退策略 |
| `:core:gesture` | 手势动作/规则编解码、摇一摇与扣桌设置 |
| `:core:notification` | 通知规则、消息提醒、历史编解码 |
| `:core:monitoring` | Debug 性能监控（Overlay FPS、主线程阻塞） |
| `:core:overlay-layout` | Overlay 网格布局纯逻辑 |
| `:core:ocr` | 本地 OCR（ML Kit / Tesseract / PPOCR 模型） |
| `:core:translate` | 翻译引擎（Google / ML Kit） |
| `:feature:settings` | AppSettings、DataStore、ZIP 备份、各域 Mutator |
| `:feature:otp` | OTP 记录持久化、内置规则资产 |
| `:feature:notification` | 通知过滤/历史仓库与规则执行 |
| `:feature:apps` | 已安装应用目录 |
| `:feature:shake` | 摇一摇/扣桌检测 Host |
| `:feature:message` | 消息提醒编排 |
| `:vendor:ppocr-sdk` | PPOCR 原生 SDK 封装 |

`:app` 保留悬浮球取词与图搜、消息 Overlay、悬浮指针、边缘面板协调器等 Android 资源相关运行时。

### 依赖注入（Hilt）

- `SlideIndexApp`（`@HiltAndroidApp`）注入 `AppDependencies`、`ShizukuInitializer`
- 服务与 Activity 通过 `@AndroidEntryPoint` 注入；UI 通过 `@HiltViewModel`
- Overlay 窗口通过 `OverlayEntryPoint` 获取 `OverlayDependencies`

### 性能监控（Debug）

应用索引设置页可开关性能监控。活跃 Overlay（边缘面板、悬浮球、悬浮指针、Widget 面板）经引用计数启用 `PerformanceMonitor`，统计 FPS 与主线程阻塞。

---

## CI

GitHub Actions（`.github/workflows/ci.yml`）在 push/PR 到 `main`/`master` 时执行：

| 步骤 | 条件 |
|------|------|
| `assembleDebug` + `lintDebug` | 始终 |
| `assembleRelease`（未签名冒烟） | 未配置 Release Secrets 时 |
| 签名 `assembleRelease` + 上传 `release-apk` | push 且 Secrets 齐全 |

仅修改 `*.md` 文件的提交会跳过 CI（`paths-ignore`）。

### GitHub Secrets（CI Release 签名）

| Secret | 说明 |
|--------|------|
| `RELEASE_KEYSTORE_BASE64` | 密钥库 Base64 |
| `RELEASE_STORE_PASSWORD` | 密钥库密码 |
| `RELEASE_KEY_PASSWORD` | 密钥密码 |
| `RELEASE_KEY_ALIAS` | 密钥别名（默认 `slideindex`） |

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\keystore\release.jks")) | Set-Clipboard
```

```bash
# macOS / Linux
base64 -i app/keystore/release.jks | tr -d '\n'
```

---

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE)（GPLv3）开源。

## Release Notes

见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。
