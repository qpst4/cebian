# XposedSmsCode-beta 移植分析（面向边栏 / Cebian）

本文档记录 `D:\AndroidDev\Projects\XposedSmsCode-beta` 的架构勘察结论、与
`D:\AndroidDev\Projects\cebian` 现有 OTP 能力的差距，以及分阶段移植方案。

## 1. 结论摘要

1. **本地这份 XposedSmsCode-beta 不完整**：它是没有初始化 submodule 的源码副本，
   缺失 5 个共享库（`smscode/core/*`、`magisk-xposed-kit`、`magisk-ui-kit`、
   `build-logic`、`smscode/rules`）。hook 侧大量类只是"薄适配层"，真正的逻辑
   （`io.github.magisk317.smscode.rule.*`、`runtime.verification.*` 等）都在缺失的
   共享库里。因此**逐文件 1:1 抄源码无法完成**，可行做法是"按能力重实现并适配边栏架构"。
2. **边栏已经做过一轮同源移植**：`core/common/.../otp/*`、`app/.../xposed/hook/Sms*`、
   `feature/otp/*` 里已带 `Portions derived from XposedSmsCode` 头注释。已有能力：
   LSPosed 短信捕获、关键字 + 官方/用户规则提取、记录、剪贴板、无障碍与 Xposed 注
   入自动填充、统计、规则编辑。**这些不需要重做**。
3. **真正缺口集中在三块**：验证码提醒（Toast / 系统通知）、短信安全策略（拦截屏蔽 /
   标记已读 / 删除短信）、官方规则资产（`smscode-rules.json` 未打包）。

## 2. 目标项目（XposedSmsCode-beta）现状核查

| 目录 | 角色 | 本地是否可用 |
| :--- | :--- | :--- |
| `app` | 模块 App（UI/接收器/服务，32 个 kt / 103 KB） | 可用 |
| `hook` | Xposed hook（42 个 kt / 176 KB） | 可用（大量逻辑依赖共享库） |
| `runtime` | 数据/偏好/工具/桥接（73 个 kt / 280 KB） | 可用（部分依赖共享库） |
| `core` | 主 UI 与设置（67 个 kt / 596 KB） | 可用 |
| `smscode/core/{hook,domain,runtime,contract,rule,verification}` | 共享核心（规则引擎、验证流程、契约） | **缺失（空 submodule）** |
| `smscode/rules` | 官方验证码规则数据 | **缺失（空 submodule）** |
| `magisk-xposed-kit/{logging,diagnostics,permission}` | Xposed 基础库 | **缺失** |
| `magisk-ui-kit` | Miuix 风格 UI 组件 | **缺失** |
| `build-logic` | 约定插件（`magisk.android.*`） | **缺失** |

可直接读取的源码规模：179 个 `.kt`、约 22.5k 行；`.xml` 约 2k 行。
该副本也不是 Git 仓库（无 `.git`），无法用 submodule 命令补齐。

### 2.1 依赖缺失造成的影响（举例）

- `hook/.../SmsBlockEvaluator.kt` 只是 `SharedSmsBlockEvaluator` 的包装；
- `hook/.../CodeWorker.kt` 依赖 `SmsCodePostParseCoordinator`、`SmsParseActionRunner`；
- `hook/.../NotifyAction.kt` 依赖 `PhoneOwnedNotificationDispatcher`、`NotifyActionHelper`；
- `runtime/.../SmsCodeUtils.kt` 依赖 `RuntimeSmsCodeAdapter`、`SmsCodeRuleProvider`、规则目录仓库；
- `hook/.../SmsHandlerHook.kt`（34 KB）依赖 `io.github.magisk317.smscode.verification.*`。

结论：这些类的**行为**可以从调用点、参数、注释完整读出，但**代码不能照搬**；
在边栏里应按同样行为重新实现，并复用边栏已有的桥接/设置/通知体系。

## 3. XposedSmsCode-beta 功能清单与实现位置

| 功能 | 主要实现位置 | 边栏现状 |
| :--- | :--- | :--- |
| 验证码短信拦截与解析（关键字 + 官方/用户规则） | `hook/.../SmsHandlerHook.kt`、`CodeWorker.kt`、`runtime/.../SmsCodeUtils.kt` | ✅ 已有（`VerificationCodeExtractor` + 规则） |
| 复制验证码到剪贴板 | `hook/.../CopyToClipboardAction.kt` | ✅ 已有（`OtpClipboardHelper`） |
| 验证码 Toast 提示 | `hook/.../ToastAction.kt`、`helper/InputHelper.kt` | ❌ 缺失（仅复制时有提示） |
| 验证码系统通知（含自动取消 / 驻留时长 / 点击复制） | `hook/.../NotifyAction.kt`、`CopyCodeReceiver.kt`、`AutoCancelReceiver.kt` | ❌ 缺失 |
| 自动输入验证码 / 自动回车 | `hook/.../AutoInputAction.kt`、`app/.../AutoInputAccessibilityService.kt` | ✅ 已有（无障碍 + 系统注入两条路） |
| 记录验证码短信（含普通短信/应用通知分类、历史上限） | `hook/.../RecordSmsAction.kt`、`runtime/.../SmsMsg.kt` | ⚠️ 部分（仅验证码记录，无分类/上限配置） |
| 标记验证码短信为已读 | `hook/.../OperateSmsAction.kt` | ❌ 缺失 |
| 提取后自动删除该短信 | `hook/.../OperateSmsAction.kt`（`FORCE_DELETE`） | ❌ 缺失 |
| 拦截 / 屏蔽特定验证码短信 | `hook/.../SmsBlockEvaluator.kt`、`runtime/.../SmsBlacklistUtils.kt`、`SmsHandlerHook` 的分发门禁 | ❌ 缺失 |
| 短信黑名单（号码 / 前缀 / 内容 / 正则，动作：删除 / 拦截） | `runtime/.../SmsBlacklistUtils.kt` + `PrefConst` 黑名单键 | ❌ 缺失 |
| 验证码短信去重（同一短信只处理一次） | `SmsHookRuntimeContext`、`SmsInboxObserver`、共享去重 | ✅ 已有（`OtpCaptureDeduplicator`） |
| MMS 验证码解析 | `hook/.../mms/MmsMessagesHook.kt` | ❌ 缺失 |
| 短信入库观察 / 路由修复（`SmsInboxObserver`） | `hook/.../SmsInboxObserver.kt` | ❌ 缺失（无对应需求） |
| 应用通知验证码提取 | 通知监听链路 | ✅ 已有（`NotificationHistoryRecorder`） |
| 官方规则只读库 + 用户规则导入导出 | `core` 规则页 + `smscode/rules` | ⚠️ 规则 UI 有，**官方规则资产未打包** |
| KillMe（自动输入后结束宿主进程） | `hook/.../KillMeAction.kt` | ⛔ 不建议移植（与本项目目标无关） |
| 模块冲突仲裁（与信驿 Relay 等共存） | `hook/.../ModuleConflictArbiter.kt` | ⛔ 不适用 |

## 4. 关键架构差异（决定移植方式）

| 维度 | XposedSmsCode-beta | 边栏 Cebian |
| :--- | :--- | :--- |
| Hook API | LibXposed API 102（`XposedModule` 子类 + `XposedRuntimeInstaller`） | LibXposed API 102（`SlideIndexLibXposedModule`）**一致** |
| Hook 进程 | `system_server` + `com.android.phone` + `providers.telephony` | 同样三个（分别装输入/短信 hook） |
| 设置下发 | Xposed 远程偏好（`REMOTE_PREFS_GROUP` / `HookPrefsReader`） | **JSON 快照文件 + 广播**（`ModuleHookSnapshot` / `HookConfigReader`），带 TTL 与反向请求 |
| hook 与 App 通信 | 数据库 Provider + 广播 + 自有契约类 | 广播契约（`OtpAutoInputBroadcastContract` 等）+ AIDL 桥 |
| 设置存储 | SharedPreferences + DataStore | DataStore（`SettingsPreferenceKeys` + 8 处映射样板） |
| UI | Compose + Miuix/Material 双实现 | Compose + Miuix + `SettingsLazyScreenScaffold` |
| 数据库 | Room（短信记录、规则、转发规则） | 无 Room，JSON 文件仓储（`OtpRecordsRepository`） |

因此移植原则：

1. **行为对齐、结构不照搬**：沿用边栏的 Hilt / DataStore / 广播契约 / Miux 组件。
2. **Hook 侧策略走既有快照通道**：需要电话进程"同步决策"的开关（拦截 / 已读 / 删除），
   应扩展 `ModuleHookSnapshot`（app 写、hook 读），而不是引入 Xposed 远程偏好。
3. **纯逻辑下沉到 `:core:common`**：可单测、不依赖 Android 运行时的策略（黑名单匹配、
   通知驻留归一、动作决策）放进 `core/common`，与现有 `VerificationCodeExtractor` 同级。
4. **不引入 Room / 不搬运 UI**：规则、记录继续用现有 JSON 仓储与设置页。

## 5. 差距清单与优先级

| 优先级 | 缺口 | 价值 | 风险 |
| :--- | :--- | :--- | :--- |
| P0 | 验证码系统通知（点击复制 / 自动取消 / 驻留时长） | 高（模块最直观的产出） | 低（纯 App 侧） |
| P0 | 验证码 Toast 提示开关 | 中 | 低 |
| P1 | 短信黑名单拦截（号码 / 前缀 / 内容 / 正则，动作：拦截 / 删除） | 高 | 中（需 hook 侧同步决策 + 设置下发） |
| P1 | 标记已读 / 提取后删除短信 | 高 | 中（需在电话进程操作短信库） |
| P2 | 官方规则资产 `smscode-rules.json` 打包（现读不到 → 官方规则形同虚设） | 高 | 低（但数据源需自备，见 §7） |
| P2 | 记录分类与历史上限（验证码 / 普通短信 / 应用通知） | 中 | 低 |
| P3 | MMS 验证码解析 | 低 | 中 |

实施状态（第二轮完成后）：

- P0 两项 **已完成**：`OtpCodeAlertPresenter` 负责通知与 Toast，驻留时长 0 = 常驻不自动取消。
- P1 两项 **已完成**：策略随配置快照 `otp` 段下发，电话进程拦截 / 短信存储进程执行已读与删除。
  已知近似见 §6.1。
- P2 官方规则：**仍缺数据**，但已在规则页显式标注"未内置（0 条）"，不再制造"官方规则在用"的错觉。
- P2 记录分类与上限 **已完成**：四类（验证码 / 普通短信 / 应用通知 / 测试），每类独立开关与保留条数。
- P3 MMS **决定不做**（上游实现依赖缺失共享库，收益低）。

## 6. 实施记录（第二轮：短信安全 + 状态行 + 记录分类）

### 6.1 短信安全策略（含已知近似）

- 契约：`ModuleHookSnapshot.otp`（`capture` / `block` / `mark_read` / `delete` / `keywords` / `blacklist`），
  `SNAPSHOT_VERSION = 3`、`MODULE_CODE_VERSION = 3`（hook 代码有实质改动，因此本次更新必须重启）。
- 纯逻辑：`core/common/.../SmsBlacklist.kt` —— 匹配顺序固定为 号码 → 前缀 → 内容 → 正则，
  动作 `actionBlock` / `actionDelete` 独立；正则非法时跳过而不是抛错。带单测。
- 执行位置：`SmsHandlerHook`（电话进程，命中拦截即 `returnEarly`）、`SmsProviderHook`
  （短信存储进程，`insert` 之后按返回的 Uri 执行删除 / 置已读）。App 侧没有短信库写权限，
  这两件事只能在 hook 侧做。
- **近似 1**：hook 侧判定"是否验证码短信"只用快照里的关键词正则（屏蔽）与"关键词 + 提取到 4~8 位码"
  （已读/删除），不复制官方/用户规则引擎。仅被规则命中、不含关键词的短信不会被屏蔽/已读/删除，
  但 App 侧仍会照常提取与记录。
- **近似 2**：`bulkInsert` 不返回逐条 id，策略跳过；部分 ROM 不经过 `insert` 时同样跳过并记日志。
- **近似 3**：拦截发生在短信分发（`dispatchIntent`）阶段，被拦截的短信不入库；黑名单"删除"作用于
  入库后的行，二者语义与上游一致，但匹配顺序无法与上游逐字核对（共享库缺失），已固定写死并单测。

### 6.2 OTP 页 LSPosed 状态行

- 与「剪贴板后台监听」「屏蔽系统手势」两页共用 `StatusRow` / `StatusPill`（`StatusRows.kt`）。
- 三条状态：LSPosed 模块（系统框架）、LSPosed 系统注入、LSPosed 短信拦截通道（电话进程）。
- 状态通道按进程分槽：`EXTRA_STATUS_CHANNEL` = `system` / `phone` / `telephony`；
  旧模块不带该字段，一律按 `system` 处理，兼容既有缓存。
- 每行点击即重新探测（系统模块走 `ModuleBridgeStatusProbe`，注入走 `LsposedInjectorProbe`，
  电话通道为新增的 `SmsPolicyRuntime` 状态响应）。

### 6.3 记录分类与上限

- `OtpRecordCategory`（code / plain_sms / app_notify / test）+ 每类保留条数（0 = 不记录，上限 200）。
- 仓库在 `:feature:otp`，不依赖设置模块，上限由 `OtpRecordLimitsInstaller` 从设置单向推入。
- 普通短信记录可为空码，`OtpRecordCodec` 相应放宽（仅普通短信允许空码）。

### 6.4 hook 侧链路对照：动作管线与自动填充 / 系统注入

这一节补的是"链路层面"的分析（此前的 §3 只做到功能清单层面）。

**上游（XposedSmsCode-beta）的动作管线**

入口：`hook/.../hook/code/SmsHandlerHook.kt` hook `InboundSmsHandler.dispatchIntent`（第 272 行附近），
并额外挂了一批同类方法（第 204/213 行的 `listOf(...)`）与多接收器索引（`receiverIndex`）。
解析后由 `SmsCodeActionDispatcher.dispatchParsedSmsActions` 在**电话进程内**按序执行：

1. `CopyToClipboardAction`（第 108 行）与 `ToastAction`（第 116 行）——UI 动作；
2. `AutoInputAction`（第 138 / 165 行）——立即或按 `delayMs` 延迟执行自动填充；
3. `RecordSmsAction`（第 183 / 202 行）——写记录；
4. `NotifyAction`（第 228 行）——发验证码通知（优先 phone-owned 路径）；
5. `OperateSmsAction`（第 254 行）——标记已读 / 删除；
6. 派发前有 `claimAutoInputDispatch` / `claimNotificationDispatch`（跨进程领取代币防重复），
   破坏性动作还要过 `mobileAutomationAllowed(context)`（entitlement）。

自动填充链路：`AutoInputAction` → `AutoInputActionHelper`（共享库）→ `InputHelper.sendText`
→ 上游 `SystemInputInjectorHook.resolveActionAutoInput()`（共享库）→ system_server 侧注入；
结果经 `AutoInputResultBroadcastContract`（共享库）回传，`KillMeAction` 与通知自动取消都挂在它上面。

**边栏（cebian）的对应链路**

- 入口：`SmsHandlerHook` 只 hook `InboundSmsHandler` 三个类的 `dispatchIntent`；不做多接收器索引处理。
- 拦截 / 已读 / 删除：本轮下沉到电话进程与短信存储进程（`SmsPolicyRuntime` + `SmsProviderHook`）。
- 其余动作（提取、复制、Toast、通知、记录）在 **App 进程**执行：hook 只把 body/sender/slot 广播过来
  （`SmsCaptureForwarder`），由 `OtpSmsBridgeReceiver` 走既有管线。
- 自动填充：App 进程 `OtpAutoInputOrchestrator` 发**有序广播**（`OtpAutoInputBroadcastContract`，
  priority 2000 = system_server 注入器，-500 = 无障碍回退），`SystemInputInjectorHook` 在 system_server
  校验发送方 uid（hook `BroadcastQueue.enqueueBroadcastLocked` 取 callerUid）后注入按键，结果回传记统计；
  失败按 `OtpAutoInputFallbackPolicy` 回退无障碍 SET_TEXT（focused_node / best_editable_node / group_nodes）。

**结论：链路结构不一致。** 差异与性质：

| 环节 | 上游 | 边栏 | 性质 |
| :--- | :--- | :--- | :--- |
| 动作执行宿主 | 全部在电话进程（hook） | 判定与副作用在 App 进程，仅拦截/已读/删除在 hook | **明确差异**（架构） |
| 自动填充触发方 | 电话进程解析完直接触发 | App 进程收到短信广播后再请求注入 | **明确差异**，可靠性差一档：App 进程被冻结/强停时上游仍能填，边栏只能靠广播唤醒 + 无障碍兜底 |
| 按前台应用屏蔽自动填充 | `AutoInputAction.packageBlockedChecker` → `AppInfo.blocked` | **无**（只有"跳过自身包"） | **明确差异** |
| 填充事件记录维度 | 写 `auto_input_event`：前台应用包名 / 码长 / 时间 | 统计只有总数、成功失败、最近策略与原因 | **明确差异** |
| 通知自动取消与结果联动 | 由自动输入结果驱动（并联动 KillMe） | 通知用 `setTimeoutAfter` 独立计时；KillMe 不移植 | 换实现（KillMe 不做） |
| 注入器防伪 | 实现位于共享库 | 自己实现：callerUid 校验（仅放行 system/phone/自身）+ probe 自检 | 无法核对等价性 |
| 策略集与重试细节 | `AutoInputActionHelper`（共享库） | 上述策略名与优先级为边栏自有定义 | 无法核对等价性 |

可核对范围受限于缺失共享库：`AutoInputActionHelper`、`AutoInputBroadcastHelper`、
`AutoInputBlockedPackageHelper`、上游 `SystemInputInjectorHook`、`AutoInputResultBroadcastContract`、
`SmsCodePostParseCoordinator`、`SmsParseActionRunner`、`PhoneOwnedNotificationDispatcher` 等均不在本地副本中，
因此"逐行一致"这一结论**无法给出**，只能给出上表的差异与不可核对项。

**事实声明**：本轮（含上一轮）**没有改动任何自动填充逻辑**，只改了包含它的两个 UI 文件里的状态行与入口。
边栏的自动填充是更早那轮移植的产物。

## 6. 分阶段实施计划

| 阶段 | 内容 | 验证方式 |
| :--- | :--- | :--- |
| 阶段 1（本次） | 验证码提醒：系统通知 + Toast + 设置 + UI + 单测 | `.\gradlew.bat :core:common:testDebugUnitTest :app:compileFullDebugKotlin` |
| 阶段 2 | 短信安全策略：黑名单规则引擎（纯逻辑 + 单测）→ 快照下发 → 电话进程拦截 / 已读 / 删除 | 单测 + 编译 + 真机验证 |
| 阶段 3 | 官方规则资产与记录分类、历史上限 | 单测 + 编译 |
| 阶段 4（可选） | MMS 解析 | 编译 + 真机验证 |

## 7. 风险与许可

- **许可**：XposedSmsCode 为 GPL-3.0，边栏为 AGPL-3.0，二者兼容（结论文件保持 AGPL-3.0）。
  已移植文件沿用现有做法，在文件头标注来源与许可。
- **数据缺口**：官方规则库来自缺失的 `smscode/rules` submodule，本地无副本；
  只能①由上游仓库补齐后导入，或②沿用现有 `OtpKeywords.DEFAULT_KEYWORDS_REGEX` 与
  用户规则，不伪造官方规则数据。
- **真机验证不可替代**：电话进程的拦截 / 已读 / 删除行为依赖具体 ROM 的短信分发路径，
  阶段 2 完成后必须真机（含厂商 ROM）验证，编译通过不代表行为正确。
- **不移植项**：KillMe、模块冲突仲裁、内购/Updater、Magisk 打包脚本等与本项目目标无关，
  保持不移植。
