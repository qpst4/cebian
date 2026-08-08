# Third-Party Notices

Cebian（`com.slideindex.app`）在 [GNU Affero General Public License v3.0](LICENSE) 下发布。
以下开源项目的代码或思路被参考、移植或复用，特此致谢。

完整文件级映射见 [docs/ATTRIBUTIONS.md](docs/ATTRIBUTIONS.md)。

---

## SideGesture (gulugulu / SideGesture)

- **Copyright:** aaronzzx and contributors
- **License:** [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- **Source:** https://github.com/aaronzzx/SideGesture (formerly https://github.com/aaronzzx/gulugulu)
- **Used in:** 边缘手势触发动画（`GestureAnimation*`）、快捷方式解析（`ShortcutUtils`）、无障碍覆盖层窗口类型与系统手势排除区域、屏幕尺寸度量、顶部触钮几何与窗口布局等
- **Modifications:** 以 Kotlin 重写并集成至 Cebian；架构拆分为 `EdgeOverlayHost`、`OverlayWindowTypes` 等模块；v1.6 顶部触钮按 Bottom 镜像适配

---

## Clipboard Whitelist

- **Copyright:** Tehcneko
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/Tehcneko/ClipboardWhitelist
- **Used in:** LSPosed 剪贴板白名单 Hook（`ClipboardWhitelistHook`）及 App 侧同步桥接（`ClipboardWhitelistBridge`）
- **Modifications:** 集成至 SlideIndex Xposed 模块；prefs 命名修正；增加 Compose 管理界面与设置备份联动

---

## Circle To Search

- **Copyright:** AKS-Labs and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/AKS-Labs/CircleToSearch
- **Used in:** 以图搜图图床上传（Litterbox/Catbox）、Google/Yandex/TinEye hosted-URL 构建、多引擎 WebView 预加载面板
- **Modifications:** 拆分为 `ImageHostUploader` / `ImageSearchUrlBuilder` 等模块；扩展 IQDB、SauceNAO、ASCII2D 等 direct-POST 引擎；集成至悬浮球取词面板

---

## Nova Text (BigBang)

- **Copyright:** CashewTeam and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/CashewTeam/BigBang_NovaText
- **Used in:** 悬浮球取词、无障碍文本提取与 OCR 降级等交互思路（无直接代码移植）
- **Note:** Cebian 悬浮球主实现参考 GestureEVO / FooView 路线；与 Nova Text 属同类产品思路借鉴

---

## XposedSmsCode

- **Copyright:** Tianma (tianma8023) and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/tianma8023/XposedSmsCode
- **Used in:** LSPosed 短信验证码捕获（`SmsHandlerHook`、`SmsProviderHook`、`SmsCaptureForwarder`）、安装期权限自动授予（`PermissionGranterHook`）、系统级 OTP 自动输入（`SystemInputInjectorHook`）、验证码提取与规则匹配（`VerificationCodeExtractor`、`OtpKeywords`、`OtpRulesParser`）、官方规则资产（`smscode-rules.json`）、OTP 桥接（`OtpSmsBridgeReceiver`）
- **Modifications:** 以 Kotlin + LibXposed 重写并集成至 Cebian OTP 中心；补充 Telephony Provider 层短信捕获；广播桥接与通知监听提取路径合并；官方规则以 assets 内置并按相同 JSON schema 解析

---

## ClipboardListener

- **Copyright:** aa2013
- **License:** [MIT License](app/src/main/assets/licenses/MIT.txt)
- **Source:** https://github.com/aa2013/ClipboardListener
- **Used in:** Android 10+ 剪贴板后台监听（Shizuku/Root 前台服务、`logcat` 与隐藏 API 双路径）、`listener.zip` 监听 DEX、`IClipboardListenerService` / `IOnClipboardChanged` AIDL（`clipboard/monitor/*`）
- **Modifications:** 从 Flutter 插件抽离并以 Kotlin 重写；集成至 Cebian 剪贴板历史/暂存与 Hilt 依赖注入；去除 Dart 桥接层

---

## ClipShare

- **Copyright:** aa2013 and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/aa2013/ClipShare
- **Used in:** Android 10+ 后台剪贴板读取（`ClipboardFocusReader`：短暂添加 1×1 可聚焦悬浮窗以获取 `ClipboardManager` 读权限）；剪贴板监听整体架构参考
- **Modifications:** 适配 Cebian `ClipboardPayload` 管线；与 ClipboardListener 监听服务联动

---

## Root Activity Launcher

- **Copyright:** Zachary Wander (zacharee)
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/zacharee/RootActivityLauncher
- **Used in:** 应用内直达启动未导出 Activity（`search/ral/*`、`NonExportedActivityLauncher`）；快捷方式启动与搜索引擎深链中的特权启动路径
- **Modifications:** 以 Kotlin 移植 RootActivityLauncher 启动策略链（Normal / Iterative / Shizuku / SamsungExploit / Root / Assistant）；裁剪为 Activity 特权启动场景；接入 Shizuku 权限检查

---

## FanFreeform / Hyper手势

- **Copyright:** oxohang and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/oxohang/FanFreeform
- **Used in:** 蜂窝启动器几何与命中检测（`HoneycombGeometry`）、全屏蜂窝覆盖层视图与控制器（`HoneycombOverlayView`、`HoneycombOverlayController`）、模糊背景缓存（`BlurredWallpaperCache`）、运行时启动目标（`HoneycombRuntimeTarget`）、overlay 窗口编排（`HoneycombAppPickerOverlayWindow`）、动画原点角（`HoneycombCorner`）、蜂窝布局编辑器触摸逻辑（`HoneycombLayoutEditorScreen` 部分）；显示与动效默认参数对齐 FanFreeform `ConfigContract`（`HoneycombDisplaySettings`）
- **Modifications:** 以 Kotlin / Jetpack Compose 重写设置与编辑界面；集成至 Cebian 边缘手势与 `QuickLauncher` 数据模型；壁纸背景改为无障碍截图捕获并去除 Xposed / SystemUI Hook 依赖；补充 `layoutSlots` 布局编辑 API；接入小窗 / 全屏启动策略

---

## 许可证全文

- Apache License 2.0：`app/src/main/assets/licenses/Apache-2.0.txt`
- MIT License：`app/src/main/assets/licenses/MIT.txt`
- GNU General Public License v3.0：`app/src/main/assets/licenses/GPL-3.0.txt`
