# Changelog

All notable changes to Cebian are documented in this file.

## [Unreleased]

## [1.6.6] - 2026-07-28

### Added
- 取词/搜索面板横屏双列布局，宽屏下文本搜索与图片搜索并排展示

### Changed
- 抽取 `OverlayBottomPanelMetrics`，统一底部面板高度与内边距度量

## [1.5.0] - 2026-07-28

### Added
- **悬浮球：** 多引擎 OCR、取词面板重构（文本优先/折叠/自绘工具条）、聚合以图搜图、可配置搜索引擎、外部分享图片 OCR、钉图暂存与贴屏
- **剪贴板：** 图文历史、后台监听、LSPosed 白名单、截图监听入库
- **手势：** 底边触钮与底边手势、扣桌手势、侧边角度按边独立配置
- **消息：** 提醒浮层重构、快捷回复、多种展示样式
- **OTP：** 自动填充状态追踪与成功率统计
- **备份：** ZIP 设置备份 v3，可选敏感数据导出
- **主题：** 动态取色/种子色、底栏毛玻璃、9 种配色风格
- **其他：** OCR 模型前台下载、Shell 模板变量、许可证 Markdown 页、引导页分步权限说明

### Changed
- 品牌统一为 **Cebian**（边栏）；许可证升级为 **AGPLv3**
- `minSdk` 提升至 31（Android 12）
- 悬浮球/Overlay 架构拆分（SceneState、Chrome、TouchHost、面板 Host、history 子模块）
- 设置页统一区块组件与乐观更新；Hub 设置改 LazyColumn 脚手架

### Fixed
- 取词面板布局、键盘遮挡、系统返回与层级冲突等多项体验问题
- 剪贴板列表性能、复制去重与 Word 粘贴兼容
- Shell/OTP Hub Tab 布局与搜索栏焦点问题

## [1.2.0] - 2026-07-11

### Added
- **P1 测试覆盖：** `:core:autofill` 单测（`OtpAutoInputFallbackPolicy`、广播契约、`OtpAutoInputNodeHelper`）；`:feature:settings` Mutator 写入与 `readSnapshot` 异步缓存；`:app` 10 个 ViewModel 初始状态/同步行为单测（`ViewModelTestSupport` + `testSettingsRepository`）；`OtpAutoInputOrchestratorPolicy`、`SmsCaptureForwarder`、`SettingsRepository` 结果路径、`OverlayServiceController`、`OtpAccessibilitySettingsHelper` 等 `:app` 纯逻辑单测；`MainActivityComposeFlowTest` 底部导航 Compose 流程（需设备/模拟器）。
- **P2 测试扩展：** `SlideIndexAccessibilityGestureInjector`、`GestureSessionContinuousPick` / `ThresholdTracker`、`ActionExecutorPolicy`、`TaskManagerShellExecutor`、`ShakeGestureClassifier` / `ShakeGestureDetector` 单测；ViewModel 写入路径（`setPanelOpacity`、`setMessageReminderEnabled`、`setEnabled`）与 `NotificationHistoryViewModel` 错误分支；CI `instrumentation` job（API 30 模拟器跑 `connectedDebugAndroidTest`，`continue-on-error`）。

### Changed
- **P2 维护成本（续）：** `TaskManagerUtil` 拆为 `TaskManagerUtilShell` / `TaskManagerUtilShortcuts` / `TaskManagerUtilFreeWindow`（与 `TaskManagerTaskOperations` 等服务端模块对齐）；`MessageStyleSettingsScreen` 进一步拆出 `MessageStyleFloatIconSettings` / `MessageStyleChip` / `MessageStyleLabels`。
- **P0 维护成本：** `SettingsRepository` 按域拆分为 `Edge` / `Overlay` / `Shake` / `Message` / `Otp` Mutator + `SettingsPreferencesEditor` / `SettingsSnapshotReader`；公共 API 不变。
- `TaskSwitcherOverlayController` 触摸逻辑迁至 `TaskSwitcherTouchHandler`（与 QuickLauncher 同模式）；进一步拆为 `TaskSwitcherScrollHandler` / `TaskSwitcherContextMenuHandler` / `TaskSwitcherLongPressHandler` / `TaskSwitcherPickResolver`。
- 新建 `:core:overlay-layout`，迁出 `QuickLauncherPanelLayoutEngine`、`OverlayGridLayout` 与 `TaskSwitcherLayoutEngine`（`TaskSwitcherRowEntry` / `TaskSwitcherLayoutHost` 抽象）。
- `TaskManagerUserService` 拆为 `TaskManagerShellExecutor`、`TaskManagerTaskOperations`、`TaskManagerShortcutResolver`、`TaskManagerFreeWindowOperations`。
- `GestureSession` 拆为 `GestureSessionContinuousPick`、`GestureSessionThresholdTracker`、`GestureSessionActionDispatch`。
- `AdjustPanelOverlayController` 拆为 `AdjustPanelTouchHandler` + `AdjustPanelRenderer`；悬浮指针输入迁至 `FloatingPointerInputHandler`（由 `FloatingPointerHostLayout` 持有）。
- 巨型 UI 文件拆分：`QuickLauncherAddOverlaySheet`、`GestureActionPickerScreen`、`NotificationHistoryScreen` 迁至子包；`SettingsComponents` 迁至 `ui/settings/components/`（保留薄 re-export）。
- **P2 维护成本：** `SlideIndexAccessibilityService` 拆为 `GestureInjector` / `OtpCoordinator` / `ForegroundTracker` / `Watchdog`；`SideOverlayController` → `SideOverlayWindowManager` + `SideOverlayRenderer`；`QuickLauncherTouchHandler` 拆 scroll/management/pick；`FloatingPointerOverlayWindow` → `WindowLifecycle` + `SettingsSync` + 既有 `InputHandler`；`WidgetPopupOverlayWindow` 拆 touch/renderer；`ActionExecutor` 拆 `executor/Launch|MediaSystem|OverlayPanels`；`TaskManagerShortcutResolver` 拆 XML/dumpsys loader；`MessageStyleSettingsScreen` / `NotificationRuleEditorScreen` 拆子 Composable 包。
- CI 单元测试按模块分批执行（`:app`、`:feature:shake`、`:core:overlay-layout` 与其余模块）以降低 OOM 风险。

## [1.1.0] - 2026-07-10

### Added
- Lightweight unit tests for message filters/swipes, shake action resolution, quick-launcher layout, and app repository helpers.
- Debug performance overlay panel (FPS / jank) when the layout debug monitor is enabled.
- MIT open-source license.
- Additional instrumentation smoke checks for app wiring.

### Changed
- Extracted `resolveShakeAction` as a testable pure function.
- `PerformanceMonitor` now exposes the latest FPS/jank snapshot for the debug overlay.
- Tightened R8 keep rules for Jetpack Compose.
- Incremented `versionCode` to 2.

### Quality
- ProGuard Compose rules no longer keep the entire `androidx.compose.runtime` and `ui.platform` packages.
