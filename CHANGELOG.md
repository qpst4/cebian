# Changelog

All notable changes to Cebian are documented in this file.

## [Unreleased]

## [1.8.5] - 2026-08-02

### Added
- 屏幕钉住文本/富文本面板双指缩放与双击切换控制条
- 更新 manifest 双源校验（`pickBetterManifest`、`-VerifyRemote`）
- UpdateChecker 单元测试

### Changed
- 钉住面板统一内容尺寸计算，文本最大宽度放宽至 55%
- 检查更新 jsDelivr 源改为 `@main`，同版本优先 `apkSize > 0` 的 manifest
- CI 合并为单次构建步骤（debug + lint + release）
- 发版脚本禁止写入 `apkSize: 0`

### Fixed
- 文本/富文本钉住面板尺寸与边缘吸附布局问题

## [1.8.3] - 2026-08-02

### Added
- OverlayCompositor 悬浮层合成器与统一 Z 轴层级协调
- 边缘持续交接启动器（区域取词/悬浮指针）
- 取词/搜索面板 warmUp 预挂载
- Release APK 输出命名为 `cebian-{版本}.apk`
- CI 发版前自动校验 APK 内版本号

### Changed
- 悬浮窗类型统一，面板层级与 chrome 置顶逻辑重构
- 手势动画进度计算与路径识别优化
- 点击穿透改为异步注入，穿透时卸载边缘捕获窗
- 自动亮度切换后防抖同步，避免 observer 抖动
- 悬浮球穿透逻辑简化
- 补充 ClipboardListener、ClipShare、RootActivityLauncher 第三方归属声明

### Fixed
- 服务关闭后悬浮球仍可能显示
- 内容面板可能压在悬浮球/边缘 chrome 上方的问题

## [1.8.0] - 2026-08-01

### Added
- 区域截图&取词手势动作：持续触发从边缘拖出，球体跟手，加号取词或框选区域
- RootActivityLauncher 兼容的特权 Activity 启动策略链（Shizuku/Root）
- 快捷设置磁贴即时开关服务，无需等待 DataStore 同步
- 悬浮层面板系统手势排除，避免与底部导航手势冲突

### Changed
- 非导出 Activity 启动改为异步策略链，避免主线程 ANR
- 边缘手势持续触摸交接支持区域取词覆盖层
- 悬浮球/侧边栏/搜索面板层级与手势穿透优化
- 手势切换磁贴响应与预热逻辑改进

### Fixed
- 服务关闭后悬浮球仍可能显示的问题

## [1.7.9] - 2026-08-01

### Added
- 蜂巢启动器手势与蜂巢应用选择面板
- 剪贴板后台监听改用 Shizuku/Root 前台服务，移除 LSPosed 白名单方案
- 活动快捷方式（Activity Shortcut）选择与启动
- 统一选择器组件，覆盖手势动作、快速启动器、搜索引擎与分享目标

### Changed
- 快速启动器、蜂巢启动器与搜索引擎编辑器 UI 重构

## [1.7.8] - 2026-07-31

### Added
- 新手势动作「暂停悬浮窗」：临时隐藏触钮、边角轮盘与悬浮球 5 秒
- 排除应用支持按触发条/边角轮盘/悬浮球分项屏蔽
- 关于页改版：应用信息分区、QQ 交流群入口

### Changed
- 排除应用添加流程改为先选功能模板再添加，已添加应用可单独调整
- 边角轮盘渐进展开从激活距离起算，内层优先展开

## [1.7.5] - 2026-07-31

### Added
- 设置页拖动滑条时悬浮球外观/线条实时预览
- 布局、触钮设计/外观页拖动时边缘触发区与索引高度实时预览
- 悬浮指针摇杆/指针/径向菜单设置页触发区实时预览
- 底栏导航模糊度滑条实时预览

### Changed
- 第三方许可声明页按组件分区卡片展示
- AGP 9.3.1；Gradle MaxMetaspaceSize 调至 1024m

## [1.7.0] - 2026-07-30

### Added
- **边角轮盘：** 屏幕左下/右下角 L 形触发区滑出扇形径向菜单，槽位可配置手势动作
- 边角轮盘设置页：触发区尺寸、轮盘内外径、气泡大小与左右角独立开关
- 设置滑条调整时底角触发区实时预览

## [1.6.20] - 2026-07-29

### Changed
- 悬浮球预览框取词优先框内 leaf 文本，窄条带/评论展开行等场景优化

### Fixed
- 悬浮球设置归入首页导航栈；从系统无障碍设置返回后刷新权限
- 底栏与侧栏 Tab 切换使用不同时长的过渡动画

## [1.6.10] - 2026-07-29

### Added
- 主界面四个 Tab 切换淡入淡出与位移/缩放过渡

### Changed
- 底栏与侧栏导航选中指示器弹簧动画；图标与文字颜色 Crossfade 过渡

### Fixed
- 检查更新拉取失败时单独提示网络问题；「已是最新」显示当前安装版本

## [1.6.9] - 2026-07-29

### Added
- 宽屏（≥600dp）主界面侧栏导航；设置页大屏内容限宽居中
- 边缘手势提示动画支持「相对手指偏移」调节

### Fixed
- 悬浮球横竖屏切换后球体与线条不可见、需拖动才显示

## [1.6.8] - 2026-07-28

### Fixed
- 修复悬浮球横竖屏切换后球体与线条不可见、需拖动才显示的问题

## [1.6.7] - 2026-07-28

### Added
- Release 构建自动打包内置 OCR / 翻译 / 分词引擎（约 49 MB 完整 APK，安装即可用）

### Fixed
- 检查更新多源取最高版本，jsDelivr 改用 `@latest` 并在发版脚本 purge 缓存

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
