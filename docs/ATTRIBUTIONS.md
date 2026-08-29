# 开源归属映射（开发维护用）

| 本仓库路径 | 来源项目 | 关系 | 许可证 |
|-----------|---------|------|--------|
| `app/.../xposed/hook/ClipboardWhitelistHook.kt` | [Clipboard Whitelist](https://github.com/Tehcneko/ClipboardWhitelist) | 移植+修改 | GPL-3.0 |
| `app/.../clipboard/ClipboardWhitelistBridge.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../clipboard/ClipboardWhitelistContract.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../overlay/animation/GestureAnimationState.kt` | [SideGesture](https://github.com/aaronzzx/gulugulu) | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimation.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationTriggerDirection.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationButton.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationOverlay.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../util/ShortcutUtils.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../util/LauncherShortcutInfo.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../util/PackageManagerCompat.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/OverlayWindowTypes.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../overlay/EdgeOverlayHost.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../overlay/OverlayScreenMetrics.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../gesture/GestureZoneLayout.kt`（exclusion / TOP 几何） | [SideGesture](https://github.com/aaronzzx/SideGesture) | 改编 | Apache-2.0 |
| `app/.../overlay/EdgeSystemGestureExclusionView.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../search/ImageHostUploader.kt` | [Circle To Search](https://github.com/AKS-Labs/CircleToSearch) | 改编 | GPL-3.0 |
| `app/.../search/ImageSearchUrlBuilder.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../search/ImageSearchBitmapUtils.kt` | 同上 | 移植 | GPL-3.0 |
| `app/.../search/ImageSearchEngine.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../overlay/FloatBallImageSearchPanel.kt` | 同上 | 思路+架构参考 | GPL-3.0 |
| `app/.../overlay/FloatBallOverlay.kt` 等悬浮球取词 | [Nova Text](https://github.com/CashewTeam/BigBang_NovaText) | 交互思路 | GPL-3.0 |
| `app/.../xposed/hook/SmsHandlerHook.kt` | [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) | 改编 | GPL-3.0 |
| `app/.../xposed/hook/SmsProviderHook.kt` | 同上 | 补充（Telephony Provider 捕获） | GPL-3.0 |
| `app/.../xposed/hook/SmsCaptureForwarder.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../xposed/hook/PermissionGranterHook.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../xposed/hook/SystemInputInjectorHook.kt` | 同上 | 改编（`InputHelper`） | GPL-3.0 |
| `app/.../receiver/OtpSmsBridgeReceiver.kt` | 同上 | 架构参考 | GPL-3.0 |
| `core/common/.../otp/VerificationCodeExtractor.kt` | 同上 | 改编（`SmsCodeUtils`） | GPL-3.0 |
| `core/common/.../otp/OtpKeywords.kt` | 同上 | 参考 | GPL-3.0 |
| `core/common/.../otp/OtpRulesParser.kt` | 同上 | 格式兼容（`RuleImporter` v1） | GPL-3.0 |
| `feature/otp/.../OtpOfficialRulesLoader.kt` | 同上 | 衍生 | GPL-3.0 |
| `feature/otp/src/main/assets/smscode-rules.json` | 同上 | 官方规则数据（smscode-rules 格式） | GPL-3.0 |
| `app/.../stash/*` | — | 本项目自有 | AGPL-3.0 |
| `app/.../clipboard/monitor/*` | [ClipboardListener](https://github.com/aa2013/ClipboardListener) | 移植+改编 | MIT |
| `app/src/main/aidl/.../monitor/IClipboardListenerService.aidl` | 同上 | 移植 | MIT |
| `app/src/main/aidl/.../monitor/IOnClipboardChanged.aidl` | 同上 | 移植 | MIT |
| `app/src/main/assets/listener.zip` | 同上 | 衍生资产（监听 DEX） | MIT |
| `app/.../clipboard/ClipboardFocusReader.kt` | [ClipShare](https://github.com/aa2013/ClipShare) | 改编 | GPL-3.0 |
| `app/.../search/ral/*` | [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) | 移植+改编 | GPL-3.0 |
| `app/.../search/NonExportedActivityLauncher.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../activity/ActivityShortcutLauncher.kt`（未导出 Activity 分支） | 同上 | 集成调用 | GPL-3.0 |
| `app/.../search/SearchEngineLauncher.kt`（未导出 Activity 分支） | 同上 | 集成调用 | GPL-3.0 |
| `app/.../overlay/HoneycombGeometry.java` | [FanFreeform](https://github.com/oxohang/FanFreeform) | 移植+扩展 | GPL-3.0 |
| `app/.../overlay/HoneycombOverlayView.java` | 同上 | 移植+修改 | GPL-3.0 |
| `app/.../overlay/HoneycombOverlayController.java` | 同上 | 移植+修改 | GPL-3.0 |
| `app/.../overlay/BlurredWallpaperCache.kt` | 同上 | 改编（截图捕获路径） | GPL-3.0 |
| `app/.../overlay/HoneycombRuntimeTarget.kt` | 同上 | 改编（`RuntimeTarget`） | GPL-3.0 |
| `app/.../overlay/HoneycombAppPickerOverlayWindow.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../overlay/HoneycombCorner.kt` | 同上 | 移植（`GestureGeometry.Corner`） | GPL-3.0 |
| `app/.../ui/HoneycombLayoutEditorScreen.kt` | 同上 | 部分移植（布局编辑触摸） | GPL-3.0 |
| `feature/settings/.../HoneycombDisplaySettings.kt` | 同上 | 默认值对齐 `ConfigContract` | GPL-3.0 |
| `app/.../overlay/HoneycombDisplayConfig.kt` | 同上 | 衍生（设置映射） | GPL-3.0 |
| `app/.../overlay/HoneycombTargetResolver.kt` | 同上 | 衍生（`QuickLauncher` 解析） | GPL-3.0 |
| `app/.../search/files/FileSearchIndex.kt` | [Quick Search](https://github.com/teja2495/quick-search) | 移植（`FileSearchRepository` 核心） | MIT |
| `app/.../search/files/FileSearchFilter.kt` | 同上 | 移植 | MIT |
| `app/.../search/files/FileType.kt` | 同上 | 移植（`search/models/FileType`） | MIT |
| `app/.../search/files/FileClassifier.kt` | 同上 | 移植（`search/utils/FileClassifier`） | MIT |
| `app/.../search/files/FolderPathPatternMatcher.kt` | 同上 | 移植 | MIT |
| `app/.../search/files/DeviceFileEntry.kt` | 同上 | 衍生（`DeviceFile` 模型） | MIT |
| `app/.../search/calculator/CalculatorUtils.kt` | 同上 | 移植 | MIT |
| `app/.../search/websuggestions/WebSuggestionsUtils.kt` | 同上 | 移植 | MIT |
| `app/.../search/contacts/ContactSearchLauncher.kt` | 同上 | 行为对齐（`Intent.ACTION_VIEW`） | MIT |
| `app/.../search/contacts/ContactSearchIndex.kt` | 同上 | 思路参考（联系人索引） | MIT |
| Gradle `top.yukonga.miuix.kmp:*` | [Miuix](https://github.com/compose-miuix-ui/miuix) | Maven 依赖 | Apache-2.0 |
| `app/.../ui/miuix/bottombar/liquid/Lens.kt` | 同上（官方示例） | vendoring | Apache-2.0 |
| `app/.../ui/miuix/GroupedCardItems.kt` | [Mishka](https://github.com/YuKongA/Mishka) | 移植+修改 | GPL-3.0 |
| `app/.../ui/miuix/WindowSize.kt` | 同上 | 改编（`WideContentBox`） | GPL-3.0 |
| `app/.../ui/settings/components/SettingsCardLazyGroup.kt` | 同上 | 改编（`groupedCardItems` 集成） | GPL-3.0 |
| `app/.../ui/miuix/MiuixScaffold.kt` | 同上 | 架构参考（Lazy 设置脚手架） | GPL-3.0 |
| `app/.../ui/MainMiuixNavigationRail.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../ui/MainMiuixFloatingNavBar.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../ui/M3eSettingsUi.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../overlay/volumepanel/*`（扩展面板） | [EdgeGesture](https://github.com/evilgodxu/EdgeGesture) | 移植+重构 | GPL-3.0 |
| `app/.../remind/*`（N分钟后闹钟提醒） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../copy/UniversalCopy*`（全局复制） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../translate/overlay/ScreenTranslationOverlayManager.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../backtap/BackTap*`（背面敲击手势） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../freezer/*`（应用冻结室） | [EdgeX](https://github.com/oxohang/EdgeX) | 移植+重构 | GPL-3.0 |
| `feature/apps/.../FreezerAppsScreen.kt` | 同上 | 界面重写 | GPL-3.0 |
| `app/.../search/settings/SystemSettingsSearch*.kt` | — | 本项目自有（Android `indexables/raw`） | AGPL-3.0 |

更新借鉴代码时请同步修改 `THIRD_PARTY_NOTICES.md` 与本表。

