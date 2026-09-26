# 多进程重构方案（常驻交互 / 主 UI / 引擎三层隔离）

> 状态：方案（P0）。本文只定义边界、状态归属与迁移顺序，不改行为。
> 目标读者：后续参与本次重构的人。

## 0. 一句话目标

把「常驻交互」做成一个自给自足、可独立存活的进程：**主进程被杀、冷启动、崩溃，都不影响手势与浮层的响应和动画**；
并把最吃内存的识别引擎再单独一间，避免 OOM 连带拖死手势。

非目标：不改功能语义、不改 UI、不重命名公开设置项。重构期间每一阶段都必须可回滚。

## 1. 现状（已核查，含实测数据）

### 1.1 单进程

`app/src/main/AndroidManifest.xml` 中 `android:process` 出现次数为 **0**：
无障碍服务、`OverlayService`、`ModuleGestureBridgeService`、`ClipboardMonitorForegroundService`、
`MediaNotificationListener`、所有浮层与全部 Activity 都在默认进程。

### 1.2 静态状态规模（只统计 `overlay` / `service` / `widget` 三个包）

| 项 | 数量 |
| --- | --- |
| `object` 单例 | 187 |
| `@Volatile` / `companion object` 声明点 | 128 |
| 关键静态引用点（`SlideIndexAccessibilityService.instance` / `accessibilityInstance()` / `OverlayService.foregroundPackage` / `TriggerEnvironmentState.*`） | 39 |

按「被多少文件引用」排序（拆进程时每一处都要决定归属）：

| 符号 | 引用文件数 |
| --- | --- |
| `SlideIndexAccessibilityService` | 56 |
| `OverlayCompose` | 51 |
| `OverlayDependencyAccess` | 43 |
| `FloatBallOverlay` | 28 |
| `OverlayService` | 25 |
| `FloatingPointerOverlayWindow` | 16 |
| `OverlaySceneController` | 15 |
| `WidgetPopupHost` | 11 |
| `ClipboardAccess` | 10 |
| `TriggerEnvironmentState` | 4 |
| `ModuleGestureBridgeService` | 3 |

### 1.3 三个硬前置（不解决就别动进程边界）

1. **设置是单进程 DataStore**：`feature/settings/.../SettingsPreferencesEditor.kt:32`
   `private val Context.dataStore by preferencesDataStore(name = "slide_index_settings")`（datastore 1.2.1）。
   默认 DataStore 不支持多进程同时打开：两进程会互相看不到写入，甚至报
   `multiple DataStores active for the same file`。
2. **AppWidgetHost 的 hostId 写死**：`WidgetAppWidgetHost.HOST_ID = 0x534944`。
   面板（overlay 侧）与设置页的小组件编辑器（主进程）共用同一个 host。
   拆开后两进程各持一个同 id host，已绑定 `appWidgetId` 的视图归属会打架。
3. **状态写入面很大**：`SettingsRepository` 有 **806** 个 `suspend fun set*`，
   `AppSettings` 约 **338** 个字段。跨进程不能靠零散同步，必须整包快照 + 版本号。

### 1.4 同类产品的对照（FV 1.6.4，反编译物在 `.fv_apk_extract/`）

`(:fv=20, :guide=9, :circle=2, :download=2, :ftpsvr=2, 默认进程=49)`；
无障碍服务、通知监听、输入法、守护接收器全在 `:fv`，重 UI 在默认进程。
说明该模式可行，但注意 FV 的多进程同时承担**保活/守护**目的，不是纯性能优化。

## 2. 目标架构

| 进程 | 内容 | 权威状态 |
| --- | --- | --- |
| `:overlay` | `SlideIndexAccessibilityService`、`EdgeOverlayHost` 及全部浮层/面板渲染、手势状态机、`ModuleGestureBridgeService`、`ClipboardMonitorForegroundService`、`MediaNotificationListener`、触钮/悬浮球/悬浮指针/取词/剪贴板浮窗/面板；启动后不依赖主进程 | 前台包名、锁屏态、场景、剪贴板历史、OTP、通知历史、消息规则运行态、小组件 host |
| main | `MainActivity` 与全部设置页、编辑器、文件/图片编辑、WebView、导入导出、小组件编辑器 | 设置（唯一写者）、UI 偏好缓存 |
| `:engine` | OCR / 翻译 / 公式识别 / 分词（`vendor/ppocr-sdk`、`core/ocr`、`core/translate`、`core/native-engine`、jieba） | 引擎会话与模型加载状态 |

理由：`:overlay` 需要常驻且对帧敏感；主进程是重 UI、可被杀；引擎是 OOM 高发区，单独隔离后
OOM 只影响引擎，不再连带杀手势（现状已有 3 条 OOM 崩溃记录发生在 `onAccessibilityEvent`）。

## 3. 状态归属表（唯一写者原则）

| 状态 | 唯一写者 | 其他进程如何读 | 备注 |
| --- | --- | --- | --- |
| `AppSettings`（338 字段） | main（DataStore 单写） | overlay 收整包快照（含 `version`），本地只读 | 设置页改动 → 序列化 → `IOverlayHost.applySettingsSnapshot()` |
| 反向设置写（`setShellCommands`、`updateQuickLauncherPanelItems`、小组件面板页增删改） | overlay | overlay 调 `ISettingsWriter`（独立 AIDL，写请求回 main 落盘），成功后 main 回推新快照 | 面板的编辑/拖拽发生在 overlay，这条反向通道必需 |
| 前台包名 / 锁屏 / 场景 | overlay | main 订阅 `IOverlayCallback.onForegroundChanged/onSceneChanged` | `TriggerEnvironmentState`、`OverlaySceneController` 只在 overlay 存在 |
| 剪贴板历史、OTP 记录、通知历史、消息规则运行态 | overlay | main 通过查询 AIDL 拉取 + 订阅变更 | 已有 `IClipboardListenerService` / `IDiagnosticLogService` 先例可复用风格 |
| 小组件实例与预览 | overlay | main 请求 `IOverlayHost.requestWidgetPreview(appWidgetId)` 拿位图 | 见 4.2 |
| 引擎会话 | `:engine` | 调用方传位图/文本，拿结果 | 见 4.3 |
| 诊断日志 | overlay + main 各自写 | `DiagnosticLogService` 聚合 | 跨进程排障的第一依赖，先做 |

## 4. 接口契约（草案）

### 4.1 主 AIDL：`IOverlayHost`（绑定 `:overlay`）

```aidl
interface IOverlayHost {
  void applySettingsSnapshot(in byte[] snapshotJson, long version);
  byte[] getStateSnapshot();                       // 前台包名/锁屏/场景/模块就绪
  void registerCallback(IOverlayCallback cb);
  void unregisterCallback(IOverlayCallback cb);
  boolean requestTriggerTest(int side, float y);   // 设置页「试跑」用
  ParcelFileDescriptor requestWidgetPreview(int appWidgetId);   // 或 ashmem
}
```

要点：

- **快照 + 版本号**：`version` 单调递增，旧版本直接丢弃，避免乱序；
- **死亡重连**：`ServiceConnection.onBindingDied/onServiceDisconnected` 后重新绑定并重发最新快照；
- **fail-open**：overlay 未就绪时，设置页相关能力降级为只读、模块桥按现有「宿主未就绪即放行」语义处理；
- 位图不要走普通 Parcel（1MB 限制），用 `ParcelFileDescriptor`/ashmem。

### 4.2 小组件 hostId 方案（二选一，需拍板）

- **A（推荐）**：小组件实例只在 `:overlay` 创建与渲染；主进程编辑器改为向 `:overlay` 要预览位图。
  改动集中在 `WidgetPanelSettingsScreen` 的网格编辑器。
- **B**：两个进程各用不同 hostId，并把已绑定的 `appWidgetId` 做一次性迁移（复杂、有数据风险）。

### 4.3 引擎调用

`:engine` 暴露 `IOcrEngine` / `ITranslateEngine`，输入输出用文本 + ashmem 位图；
调用方（取词面板、屏幕搜索、图片编辑器）改为异步 + 可取消。
引擎进程独立 Hilt 图，只有引擎相关模块。

## 5. 迁移阶段（每阶段独立可回滚）

| 阶段 | 内容 | 完成判据 |
| --- | --- | --- |
| **P0** | 本文档 + 状态归属表定稿 | 归属表无「待定」项 |
| **P1** | **接口化（不改进程）**：把 overlay 侧对全局静态的读写收敛到 `SettingsPort` / `OverlayStatePort` 接口，单进程内用现有实现 | 全量编译通过；冷启动 + 手势行为与改造前一致（用 6. 的基线对比） |
| **P2** | 打开 `:overlay`：仅搬无障碍 + 浮层 + 模块桥；实现 binder 版 port；小组件按 4.2 方案落地 | 冷启动瞬间触发手势：主线程不再出现秒级帧（对比 6. 基线） |
| **P3** | 引擎进程 `:engine` | OCR/翻译/公式/分词功能回归通过；overlay 进程 PSS 明显下降 |
| **P4** | 收尾：删除跨进程静态兜底与冗余广播；文档与注释更新 | 全量回归 + 基线归档 |

**P1 是安全带**：先在单进程内把调用点全部改成走接口，跑一遍就能暴露「漏网的共享状态」——
这一步不改行为、可整体回滚，风险最低，收益是让 P2 从「大规模猜谜」变成「换实现」。

## 6. 基线与验证方法（沿用本次问题排查已建立的流程）

指标：

1. 冷启动 → 触发手势 → 动画首帧延迟（`Choreographer: Skipped N frames`、`HWUI: Davey! duration=`）；
2. `dumpsys gfxinfo <pkg> framestats` 的 jank% 与 99 分位；
3. 主线程采样（JDWP `suspend` + `where all`）里是否仍出现非 UI 阻塞；
4. 各组件的 PSS（`:overlay` / main / `:engine`）与总内存；
5. 自带崩溃记录分类计数（`files/crashes`，重点是 `ForegroundServiceDidNotStartInTimeException` 与 OOM）。

方法：`adb logcat -b all -v threadtime -f /data/local/tmp/xxx.txt` + `dumpsys gfxinfo ... reset` 前后对比
+ JDWP 栈采样；每阶段结束记录一组数据归档到本文档附录。

## 7. 风险地图

| 风险 | 缓解 |
| --- | --- |
| 漏改共享静态 → 静默失灵 | P1 接口化；禁止在新代码里直接引用跨进程静态；按 3. 的归属表 review |
| DataStore 多进程冲突 | 主进程唯一写 + 快照下发；overlay 侧不直接打开 DataStore |
| 双份 Hilt 图造成启动更慢 / 内存翻倍 | overlay 图裁剪到必要模块；避免在 overlay 初始化 UI 相关依赖；量 PSS 后再决定是否合并部分模块 |
| 广播/前台服务/通知渠道重复注册 | 明确只有 `:overlay` 注册这些系统级监听，主进程只读状态 |
| Xposed 模块宿主就绪语义被破坏 | `ModuleGestureBridgeService` 与 `ACTION_HOST_STATE_CHANGED` 一并迁到 `:overlay`，保持「未就绪即 fail-open」 |
| 跨进程调试困难 | 先把 `DiagnosticLogService` 变成跨进程日志聚合（带进程名前缀） |
| 回归面过大 | 每阶段只做一类搬迁，逐模块回归；P2 后先灰度自用一段时间再谈 P4 |

## 8. 待拍板项

1. 小组件：走 4.2 的 **A**（编辑器向 overlay 要预览，推荐）还是 **B**（分 hostId + 迁移）？
2. 引擎进程是否在 P2 之后立刻做，还是等 overlay 稳定一段时间？
3. 是否接受常驻内存增加约一份（overlay 图 + 引擎图）？如果目标是「App 被杀也不掉手势」，这笔开销是必要成本。

## 附录 A：组件清单（当前单进程，迁移动线参考）

- `:overlay` 候选：`SlideIndexAccessibilityService`、`OverlayService`、`HistoryFloatService`、
  `ClipboardFloatService`、`ModuleGestureBridgeService`、`ClipboardMonitorForegroundService`、
  `ClipboardMonitorUserService`、`MediaNotificationListener`、`GestureToggleTileService`、
  `ScreenCaptureService`、`ScreenRecordService`。
- 保持 main：全部 `*TrampolineActivity`（跨进程 `startActivity` 正常）、`MainActivity`、
  `FreezerPanelActivity`、`SearchPanelImagePickerActivity`、`WallpaperPermissionTrampolineActivity`、
  下载类服务（`OcrModelDownloadService`、`NativeEnginePackDownloadService`）。
- 待定：`DiagnosticLogService`（建议留 main，作为跨进程日志聚合方）。

## 附录 B：本次排查已建立的证据链（供回归时复用）

- 面板卡顿：主线程栈落在 `WidgetCardContainer.<init>` → `PackageManager.getApplicationIcon`
  → `FlymeThemeHelper.cropTransparentSpace` → `Bitmap.getPixel`（逐像素），伴随 979/1011ms 单帧。
- 选择器闪退：`WidgetPickerTrampolineActivity` 缺 `LocalAppDependencies` provider（已修，见提交 `e61906b8`）。
- 崩溃分布：`ForegroundServiceDidNotStartInTimeException` ×13、OOM ×3、`coerceIn` 空区间 ×1（均已归档在设备 `files/crashes`）。

## 附录 C：P2 切进程的实测记录（2026-09-26，MEIZU 21 / Android 16）

已把「常驻交互」一组服务声明为 `android:process=":overlay"`：
`SlideIndexAccessibilityService`、`ModuleGestureBridgeService`、`OverlayService`、`HistoryFloatService`、
`ClipboardFloatService`、`MediaNotificationListener`、`ClipboardMonitorForegroundService`、`ClipboardMonitorUserService`。

实测结果：

1. 进程分布正常：`com.slideindex.app`(main) + `com.slideindex.app:overlay` 各自启动；
   Shizuku 用户服务另有 `:task_manager_v36` 进程（注意 [AppProcess.isMain] 不能简单用「非 overlay/engine」判定）。
2. 触钮/浮层窗口确实由 `:overlay` 持有：
   `dumpsys window windows` 中 `mSession=Session{... 26985:u0a10158}`、`ty=ACCESSIBILITY_OVERLAY`。
3. 无障碍服务在 `:overlay` 被系统重新绑定；通知监听（`NotifHistoryCapture` / `NotifFilterRecorder`）日志出现在 `:overlay`。
4. 内存代价：main ≈ 1.0GB RSS、`:overlay` ≈ 540MB RSS（单进程时约 500MB 量级）→ 双份 Hilt 图/引擎是明显开销，
   后续必须裁剪 overlay 进程的初始化（当前它仍会加载 jieba/onnxruntime/opencv）。
5. 已知缺口（下一步必做）：
   - 跨进程设置变更通知未做（MultiProcessDataStore 只保证读写安全，不保证另一进程的 flow 会主动 emit）→ 需要写入后广播 + 读方强制刷新，否则「设置改了不生效」。
   - 主进程侧读取 overlay 状态的 UI（设置页试跑/预览、小组件预览、按应用禁用列表）目前读到的是空状态 → 需要 `OverlayStatePort`。
   - 小组件 `HOST_ID` 仍被两个进程共用（4.2 的方案 A/B 尚未落地）。

### C.1 后续实测补充

- 跨进程设置生效：实测「设置页改手势动画 → 手势立即按新设置走」成立，说明 MultiProcessDataStore 的变更在本机是可传播的；
  仍建议补广播刷新作为兜底（避免依赖实现细节）。
- 截屏/取词预览丢失：根因是 `ScreenCaptureService` 留在主进程，`:overlay` 侧读它的静态 `sessionActive`/`captureDisplayBitmap`
  永远为 false → 持续触发时画不出悬浮球/加号。已把 `ScreenCaptureService`/`ScreenRecordService` 一并移入 `:overlay`。
- 前台服务启动窗口：`:overlay` 冷启时同步读 DataStore + 写系统 LocaleManager 会挤掉 `startForeground` 的窗口
  （历史 13 次的 `ForegroundServiceDidNotStartInTimeException`）。已改为 `AppLocaleApplier.primeFromStorage`（SharedPreferences）
  并把 Shizuku / 模块同步挪到后台。
- `OverlayStatePort` 已落地（广播镜像 + 命令通道）：服务连接态、前台包名、锁屏态的发布点覆盖无障碍服务连接/断开/重绑/销毁、
  ForegroundTracker、Watchdog；主进程 3 个 UI 点与 `AccessibilityForegroundResolver` 已改走镜像。系统侧可见
  `dumpsys activity broadcasts` 中 `OVERLAY_STATE` 已注册且有持续广播。
- OTP 自动填充同样是跨进程断点（短信接收器在默认进程，注入在 `:overlay`）：已通过 `COMMAND_OTP_AUTOFILL` 转发。
  遗留：OTP 记录仓库若是普通文件，跨进程写仍需按「单写者」原则收口。

### C.2 AppWidget 宿主归属（计划 4.2 方案 A 的落地）

`AppWidgetHost` 的 hostId 全应用共用一个（`0x534944`），而 `startListening()` 是「注册订阅」语义：
两个进程都注册时后注册的一方会抢走 `APPWIDGET_UPDATE`，另一方的视图不再刷新；
`MainActivity.onPause` 里的 `stopListening()` 甚至会把 `:overlay` 的订阅注销掉。

已收口为「AppWidget 只在 `:overlay`」：

- `WidgetPopupHost.startListening/stopListening/createView` 加进程守卫，非 `:overlay` 直接忽略并记日志；
- `WidgetBindTrampolineActivity` 声明 `android:process=":overlay"`，`widgetId` 的分配也从调用方移进该 Activity
  （调用方可能来自主进程），`WidgetPickerTrampoline.startBindFlow` 不再自己分配；
- 主进程的小组件编辑器因此不再渲染真实 widget，改为占位卡（`createView` 返回 null）。

遗留：编辑器的占位卡目前复用 `WidgetLoadingPlaceholder`（文案是「加载中…」），后续可换成「在弹出面板中预览」的图标占位。

### C.3 文件型存储的跨进程收口（进行中）

这些仓库都是「读文件 → 改内存 → 写回」的 JSON 存储，原先只有进程内 Mutex，两进程同时改会互相覆盖。
已加入通用工具 `CrossProcessStore`（core/common）：`mutate` 在跨进程文件锁内重新读盘再计算、
写完后广播通知；接收方按文件路径重载缓存，并跳过自己发出的通知。

已接入：

- **通知历史**（`NotificationHistoryRepository`）：`record` 的落盘改为「磁盘为基准 + 本进程待写合并」，
  `delete/clearAll/applyMaxCountLimit/importRawJson/updateCapture` 全部走锁内读改写；并注册了外部变更重载。
  真机验证：`:overlay` 内 `MediaNotificationListener` 在 live 列表、`NotifHistoryCapture` 正常采集、
  `notification_history.json` 与 `.lock` 在采集后更新、无新增崩溃。

待接入（同一模式，逐个做）：暂存 `StashRepository`、OTP 记录 `OtpRecordsRepository`、
搜索历史 `SearchHistoryRepository`、Shell 输出历史 `ShellOutputHistoryRepository`、
OTP 填充统计 `OtpAutoFillStatsRepository`、通知过滤规则 `NotificationFilterRepository`。

另外两条实测结论：

- 剪贴板历史是 SQLite（`ClipboardHistoryStore`），跨进程本身安全，不需要收口。
- 通知监听在 `:overlay` 能正常被系统绑定；早前一次"不在 live 列表"是因为两个进程当时都被系统回收了。

### C.4 搜索面板"呼出无反应"（已修，含排查方法）

症状：悬浮球侧滑长 / 边角轮盘触发"搜索面板"，毫无反应且无任何报错（短滑的返回动作却正常）。

排查结论（日志实证）：

- `show()` 确实被调用（`caller=ActionExecutorOverlayPanels.showSearchPanel`），视图也被设为 VISIBLE；
- 系统侧出现 `E WindowManager: Couldn't add view: ComposeView ... BadTokenException: token ... is not valid`；
- 之后每次 show 的系统日志是 `W WindowManager: Failed looking up window session=...<overlay pid>`；
- `dumpsys window windows` 里该窗口停在**被动态**：`fl=NOT_FOCUSABLE NOT_TOUCHABLE`、`alpha=0.8`、`mViewVisibility=0x8`。

根因：`warmUp()` 在**无障碍宿主未就绪**时用 `applicationContext` 兜底建窗 → token 无效建窗失败，
但 `composeView` 半成品被留下；随后 `show()` 里 `applyPanelShellActive()` 的 `updateViewLayout`
失败被 `runCatching` 静默吞掉 → 窗口永远停在被动状态，且不报错。

修复：`warmUp()` 宿主未就绪直接返回；`show()` 发现 `composeView` 未附着则 `destroyWindow()` 重建；
`updateViewLayout` 失败改为记日志。顺带修：`SettingsRepository.init` 的修复动作失败会拖死快照收集协程
（导致 `readSnapshot()` 永远默认值），已用 `runCatching` 包住并记日志。

经验：**窗口壳子的建立必须以宿主就绪为前提；任何 `updateViewLayout`/`addView` 失败都不允许静默吞掉**，
否则会变成"状态对、画面没有"的幽灵窗口，排查代价极高。

### C.5 OCR 引擎隔离实测（P3 第一步成果）

截图取词实测后按进程统计 native 库映射（`/proc/<pid>/maps` 中 `libonnxruntime` / `libopencv_java5` / `libtesseract` 的条目数）：

| 进程 | onnxruntime | opencv_java5 | tesseract |
| --- | --- | --- | --- |
| `:overlay`（常驻交互） | 0 | 0 | 0 |
| `:engine` | 3 | 3 | 3 |
| main（UI） | 0 | 0 | 0 |

结论：OCR 推理已完全落在 `:engine`，调用方通过 `IEngineOcr`（AIDL + Bitmap 走 ashmem）拿结果，
失败时回退本地推理。**OCR 的内存峰值与 OOM 不再牵动手势/浮层进程**。

待办：jieba 分词仍在调用方进程（占用不大，可并入 `:engine` 或保持现状）。

### C.6 回归修复：收纳把手 / 剪贴板小窗 / 实时通知 / 悬浮球消失

四条真机回归的根因与修法（均已装机验证）：

1. **收纳面板贴边把手「拖动打不开」**
   把手窗口在屏幕最右侧（贴边），横向拖动一旦被系统的边缘手势（返回）抢走，Compose 只收到取消事件，
   而原实现只在 `onDragEnd` 里判断位移 → 抬手也不打开。
   修复：抽出一个收尾闭包，`onDragEnd` 与 `onDragCancel` 都走它；同时把**单击**也接成打开面板
   （点击永远不会被边缘手势吞掉，作为兜底入口）。

2. **弹出键盘看不到剪贴板小窗入口**
   小窗由 `:overlay` 渲染，但开关（`clipboardFloatEnabled` 等）只被主进程下发给
   `ClipboardFloatImeCoordinator`，overlay 侧恒为 false。
   修复：`SlideIndexApp` 在 overlay 进程自己订阅 settings 并下发（提交 `09d675dc`）。

3. **通知滤盒「实时」tab 空着**
   监听服务（`NotificationListenerService`）随常驻交互搬进了 `:overlay`，而 UI 在主进程：
   `MediaNotificationListenerPort.listenerOrNull()` 在主进程恒为 null → 实时列表永远是空。
   修复：overlay 进程把通知栏快照（key/包名/标题/正文/时间/渠道，JSON）广播出来，
   主进程维护镜像并给 UI 用；`NotificationListenerPort` 新增 `activeNotificationSnapshotsOrNull()`，
   进页面 / 刷新时用 `COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS` 让 overlay 立即重发一帧。

4. **悬浮球消失、手势失效（偶发）** —— 三条独立成因，全部处理：
   - **主进程误判「无障碍掉线」**：`SlideIndexAccessibilityService.isConnected()` 读的是**进程内静态**，
     无障碍实例在 `:overlay`，所以主进程恒为 false → 每次启动都以为掉线，弹
     「边缘手势未连接，请完全关闭后重新打开本应用」（用户实测：**提示出现但手势其实是好的**），
     并且（有 WRITE_SECURE_SETTINGS 时）会真的去抖断/重绑一次系统绑定 → 悬浮球与手势瞬间消失。
     修复：恢复逻辑的连通性判定改用 `OverlayStatePort.isServiceConnected()`（overlay 读本地、其它进程读镜像），
     非 overlay 进程一律不自己重绑，只发 `COMMAND_RECOVER_ACCESSIBILITY` 交给 overlay；
     镜像还没收到时视为「未知」，不许动系统设置。
   - **`CrossProcessStore` 文件锁崩溃**：`FileChannel.lock()` 在同一 JVM 已有线程阻塞/持锁时会抛
     `OverlappingFileLockException`，异常发生在用户协程里没人接 → 直接打死整个进程
     （真机崩溃：`notification_history` 的 `updateCapture` 写盘）。
     修复：改成 `tryLock()` 轮询 + 重试，重叠异常一并吞掉，连续失败才退化为「只进程内互斥」，
     调用方永远不会因为锁而崩溃。
   - **剪贴板监听前台服务启动超时**：`startForegroundService()` 之后系统给的 5~10 秒窗口
     如果被主线程队列里的启动期重活挤掉，就会 `ForegroundServiceDidNotStartInTimeException`
     → 整个 `:overlay` 进程被系统干掉（悬浮球 + 手势一起消失）。
     修复：`ClipboardMonitorStartup.runOnMainWhenCalm()` —— 先等主线程空闲，再投递探测任务量一次
     队列延迟，确实不忙了才发起 `startForegroundService`，否则延后重试（最多 8 次）。

经验：进程拆分后**任何「进程内静态」都不再等于全局状态**。凡是"是否连接/是否运行"的判断，
必须区分「overlay 进程读本地」与「其它进程读镜像」，并且镜像未就绪时要当作未知而不是 false。

### C.7 剪贴板监听拆独立进程 + 常驻守护（P4 前的最后两项结构加固）

**1. 剪贴板监听前台服务移出 `:overlay` → 独占 `:clipboard`**

动机：这条服务是历史上唯一反复踩 `ForegroundServiceDidNotStartInTimeException` 的组件，
和浮层同进程时它一崩就带走悬浮球与全部手势（真机崩溃记录里一天出现 11 次）。

做法：

- 清单：`ClipboardMonitorForegroundService` 改 `android:process=":clipboard"`；
- 启停改由调用方按组件名跨进程做（不再依赖 overlay 广播转发）：
  `ClipboardMonitorController.startMonitorServiceFromOutside()/stopMonitorServiceFromOutside()`；
- **模式解析留在监听进程内**：其它进程只发一个"跟随设置"的 intent
  （`EXTRA_FROM_SETTINGS`），由服务自己解析 Shizuku/Root/LSPosed 可用性
  （只有那边判定得准），再走标准启动路径；
- Shizuku 用户服务进程后缀 `clipboard-monitor` → `clipboarduser`：
  否则会出现两个同名却性质不同的进程，而 `AppProcess` 是按进程名判身份的。

> ⚠️ 踩坑（实测）：`android:process` 的值**不能带连字符**。
> `:clipboard-monitor` / `:clipboard-fg` 都会让安装时报
> `INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION: Failed to read manifest ...
> ParsedServiceImpl cannot be cast to java.lang.String`（Flyme/A16 的 PackageParser 行为），
> 换成 `:clipboard`、`:clipmonitor` 立刻正常。二分过程：无关属性改动可正常安装，
> 仅改该进程名即失败；clean build 后依旧失败，排除增量产物问题。

验证（MEIZU 21 / A16）：进程变为 `main / :overlay / :clipboard / :engine / :clipboarduser`；
`:clipboard` 内 `ClipboardMonitorFg` 正常绑定 Shizuku 用户服务，用户服务日志出现
`try read logs: logcat -T ... ClipboardService:E *:S`（监听通道在工作）。

**2. 常驻守护：`OverlayWatchdogJobService` + `OverlayGuard`**

动机：多进程之后主进程不再是常驻进程，"用户不开 App 就没有任何东西保证手势能回来"。
方案是**互拉**：

- `OverlayStatePort` 增加镜像新鲜度（`isServiceStateFresh/millisSinceServiceState`），
  `OverlayService` 每 25s 心跳广播 + 每次被唤醒（`onStartCommand`）都回一帧状态；
- 主进程注册 `JobScheduler` 周期任务（15 分钟、`PERSISTED`、开机/覆盖安装/启动都会幂等重排）；
- 巡检顺序：健康（connected + 新鲜）→ 直接返回；否则**唤醒 `:overlay`**
  （`startForegroundService(OverlayService)`）并发一条恢复命令；收到新状态帧后若仍掉线，
  或压根没回应，才改写系统无障碍条目强制系统重绑；最后仍失败才发掉线通知。

> ⚠️ 踩坑（实测）：**存活判定不能只靠心跳**。第一版用"等一帧新鲜状态（20s）"判断，
> 而 `OverlayService.onStartCommand` 当时不发状态帧，心跳又是 25s 一次 ——
> 20s 窗口经常等空，于是把一个**健康**的绑定当成掉线去抖断（并弹出误报通知）。
> 修正：唤醒的同时发恢复命令（命令处理完必然回一帧状态），并在 `onStartCommand` 里补发状态帧。
> 修正后连续两次手动触发巡检均返回 `watchdog result=Healthy`，overlay 进程 pid 与绑定都没被扰动。

局限（已知）：真机上无法在不 root 的情况下杀掉 `:overlay` 进程，所以"进程已死"这条分支
只做了代码审查与逻辑推演，未做破坏性验证；健康分支与"任务被调度执行"已实测。
