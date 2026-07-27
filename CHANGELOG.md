# Changelog

All notable changes to Cebian are documented in this file.

## [Unreleased]

### Added
- **P3 测试：** ViewModel 写入路径扩展（`ExtensionSettings`、`OtpSettings`、`OtpRecords.deleteRecord`）；`TaskSwitcherLongPressHandler` / `QuickLauncherScrollHandler` / `QuickLauncherManagementTouchHandler` 纯逻辑单测；`TaskManagerTaskOperations` 边界单测。

### Changed
- **文档：** `README.md` 补充主题与界面说明，设置备份章节同步 format v3、`search_icons` 目录及敏感/默认嵌入 JSON 范围；`RELEASE_NOTES.md` 同步备份与主题说明。

### Changed
- 项目对外品牌统一为 **Cebian**（英文）/ **边栏**（中文）；GitHub 仓库更名为 `cebian`。
- 项目许可证由 MIT 改为 **GNU General Public License v3.0（GPLv3）**，后升级为 **GNU Affero General Public License v3.0（AGPLv3）**。
- **P3 维护成本：** `TaskShellParser` 拆为 `Patterns` / `Titles` / `Entries` / `Lookup` / `Support`（门面 83 行）；`NotificationHistoryIntentCapture` 拆 `Serialization` + `Extraction`；`AdjustLevelIndicator` 拆 `Types` / `Layout` / `Renderer`；`WidgetCanvasLayout` 拆 `TouchHandler` + `Geometry`；`QuickLauncherEditorScreen` / `QuickLauncherGridEditor` 拆 `ui/quicklauncher/*`；`QuickLauncherPanelController` 拆 `Toolbar` + `ManagementHandler`。
- CI `instrumentation` job 改为阻断失败，并上传 `instrumentation-results` artifact。

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
