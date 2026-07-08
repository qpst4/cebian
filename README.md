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

### 在 Android Studio 中打开

1. **File → Open**，选择项目根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 模块，点击 Run。

---

## 项目结构

```
app/src/main/java/com/slideindex/app/
├── gesture/        # 手势识别与动作执行
├── overlay/        # 系统悬浮窗与触摸层
├── ui/             # Jetpack Compose 设置界面
├── notification/   # 通知历史与过滤规则
├── message/        # 消息提醒
├── otp/            # 验证码提取与自动输入
├── shake/          # 摇一摇手势
├── settings/       # 配置模型与 DataStore
├── service/        # 前台服务、无障碍、通知监听
├── shizuku/        # Shizuku 集成
└── widget/         # Widget 悬浮面板
```

---

## CI

GitHub Actions 工作流（`.github/workflows/ci.yml`）在 push/PR 时自动执行：

- `assembleDebug` — 编译 Debug APK
- `lintDebug` — 静态检查（基于 baseline，仅拦截新问题）

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

尚未声明开源许可证。如需分发或二次开发，请先与项目维护者确认。
