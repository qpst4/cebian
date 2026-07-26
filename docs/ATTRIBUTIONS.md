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

更新借鉴代码时请同步修改 `THIRD_PARTY_NOTICES.md` 与本表。
