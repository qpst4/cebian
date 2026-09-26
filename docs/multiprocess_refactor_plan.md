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
