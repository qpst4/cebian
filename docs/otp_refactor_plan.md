# 验证码功能：逐文件清单 + 精简重构方案

分析对象：`D:\AndroidDev\Projects\XposedSmsCode-beta`（本地可读 179 个 kt / 22,472 行，其中 hook 37、runtime 72、app 23（内含测试 8）、core 47）。
分析目标：①逐文件判定移植状态；②把边栏验证码页精简重构，核心 hook 能力直接照上游移植。

状态含义：✅ 已移植等价能力 ｜ 🔁 换实现（能力在、做法不同）｜ ❌ 未移植 ｜ ⛔ 决定不做 ｜ 🔧 支撑代码（入口/契约/桥接/工具）｜ ❓ 未核对（本次未通读，不猜）

## 1. 逐文件清单

### 1.1 hook（37）

- `xp/LibXposedEntry.kt`、`xp/XposedRuntimeInstaller.kt`（9.6KB）：模块入口与运行时安装 ｜🔧 边栏入口是 `SlideIndexLibXposedModule`；**`XposedRuntimeInstaller` 的首次加载/去重逻辑未通读** ❓
- `xp/CorePrefsBridge.kt`、`code/SmsCodeVerificationPrefs.kt`、`code/SmsHookRuntimeContext.kt`、`code/SmsVerificationBridge.kt`、`code/ParseResult.kt`：偏好桥与共享库适配 ｜🔁 边栏用配置快照 + 自有广播契约（`ModuleHookSnapshot` / `OtpAutoInputBroadcastContract`）
- `xp/hook/code/SmsHandlerHook.kt`（34KB，主 hook）：挂 `dispatchIntent` + 一批同类方法与多接收器索引 ｜🔁 边栏只挂三个类的 `dispatchIntent` before；**细节未通读** ❓
- `code/SmsHookConstructorInitializer.kt`：hook 构造期初始化/取 context ｜❓ 未通读（边栏用 `LibXposedReflect` 从 `mContext` 取）
- `code/SmsDispatchIntentHandler.kt`、`code/SmsDispatchIntentProcessor.kt`：分发放行判定 + 黑名单/解析组合 ｜🔁 边栏在 hook before 里做"策略→returnEarly"；无冲突仲裁/entitlement 门禁
- `code/CodeWorker.kt`、`code/SmsCodeActionDispatcher.kt`：解析编排 + **动作派发管线** ｜❌ 结构未移植：边栏没有动作管线，动作分散在 App 进程；本轮只把拦截/已读/删除下沉到 hook
- `code/SmsBlockEvaluator.kt`、`runtime/common/utils/SmsBlacklistUtils.kt`：拦截判定 ｜✅ 本轮已移植（`SmsPolicyRuntime.shouldBlock` + `SmsBlacklistMatcher`）
- `code/action/Action.kt`、`CallableAction.kt`、`RunnableAction.kt`：动作抽象 ｜❌ 无对应（动作框架本身未移植）
- `code/action/impl/AutoInputAction.kt`：自动填充动作 ｜🔁 边栏由 App 进程触发；**缺按前台应用屏蔽**（`AppInfo.blocked`）
- `code/action/impl/CopyToClipboardAction.kt` ｜✅ `OtpClipboardHelper`
- `code/action/impl/ToastAction.kt` ｜✅ 本轮 `OtpCodeAlertPresenter`
- `code/action/impl/NotifyAction.kt`、`code/AutoCancelReceiver.kt`、`code/CopyCodeReceiver.kt`、`code/CodeNotificationBroadcastContract.kt` ｜🔁 边栏 `OtpCodeAlertPresenter` + `OtpCodeCopyReceiver`（App 侧发通知；**无 phone-owned 路径**）
- `code/action/impl/OperateSmsAction.kt` ｜✅ 本轮已移植（provider 侧置已读/删除）
- `code/action/impl/RecordSmsAction.kt` ｜🔁 本轮补了分类记录，但写入在 App 侧
- `code/action/impl/SmsParseAction.kt` ｜🔁 `VerificationCodeExtractor` + `OtpCaptureDeduplicator`（App 侧）
- `code/action/impl/CancelNotifyAction.kt` ｜🔁 用 `setTimeoutAfter` 代替独立取消动作
- `code/action/impl/KillMeAction.kt` ｜⛔ 不做
- `code/helper/InputHelper.kt` ｜🔁 `OtpAutoInputOrchestrator` + `SystemInputInjectorHook`
- `code/SmsInboxObserver.kt`、`code/ObservedSmsHandler.kt`（14KB）｜❌ 未移植（收件箱兜底扫描 + 路由修复）；`ObservedSmsHandler` 细节 ❓
- `xp/hook/mms/MmsMessagesHook.kt` ｜⛔ 不做（可选并入兜底扫描的文本源）
- `xp/hook/telephony/SmsProviderHook.kt` ｜🔧 上游此文件只做诊断/心跳；边栏 provider hook 承担采集与策略
- `xp/hook/me/ModuleUtilsHook.kt` ｜❓ 行为在缺失共享库中
- `xp/helper/ModuleConflictArbiter.kt`、`RelayConflictNoticeHelper.kt` ｜⛔ 不做

### 1.2 app（15 个 main）

- `ui/app/SmsCodeApplication.kt`（15KB）、`AppShellRuntimeBridge.kt`、`XposedServiceBridge.kt`、`AppIpcTokenStore.kt` ｜🔧 边栏对应 `SlideIndexApp` + `ModuleHookBridgeReceiver` + injector 的 callerUid 校验
- `ui/app/PhoneProcessRestartCoordinator.kt` ｜❌ 未移植（更新后重启电话/短信相关进程）
- `service/AutoInputAccessibilityService.kt` ｜🔁 边栏 `SlideIndexAccessibilityService` + `OtpAutoInputNodeHelper`
- `receiver/AutoInputResultHandler.kt`（8.5KB）、`AutoInputResultReceiver.kt` ｜🔁 边栏 `OtpAutoInputOrchestrator` 处理结果与统计；无 KillMe/通知取消联动；细节 ❓
- `receiver/CodeNotificationReceiver.kt`、`CodeNotificationReceiverConfig.kt` ｜🔁 边栏 App 自建渠道与文案
- `receiver/KillSelfControlReceiver.kt` ｜⛔ 不做
- `receiver/SecretCodeReceiver.kt` ｜❌ 未移植（秘钥码触发入口）
- `entitlement/MobileEntitlementActivity.kt` ｜⛔ 不做
- `di/AppModule.kt` + 8 个测试类 ｜🔧

### 1.3 runtime（57 个 main）

- 常量：`Const`、`NotificationConst`、`PermConst`、`PrefConst`、`TransitionConst`、`CodeNotificationOwner` ｜🔧 对应边栏 `SettingsPreferenceKeys` / 通知渠道 / `PermissionHelper`
- `PrefRestoreTypeRegistry.kt` ｜🔁 对应边栏备份的域映射（`mapPreferenceKeyToDomain`）；**恢复类型注册的完整语义未核对** ❓
- 偏好与桥：`HookPrefsReader.kt`（19KB）、`HookPreferenceMirror.kt`、`HookCacheInvalidator.kt`、`AppPreferences.kt`、`AppPreferenceTransactions.kt`、`XscPreferenceHooks.kt`（10KB）、`data/prefs/PrefsProvider.kt`、`runtime/AppPrefsFacade.kt` ｜🔁 边栏用 `ModuleHookSnapshot` 快照 + 广播；❓ 上游跨进程偏好语义细节未通读
- 工具：`SmsCodeUtils.kt` ｜✅（提取/规则合并已移植）；`SmsBlacklistUtils.kt` ｜✅；`TtlValueCache.kt`、`XLog.kt`、`ModuleUtils.kt`、`ProviderCallerGuard.kt`、`RuntimeDiagnosticsBridge.kt` ｜🔧/🔁（`ProviderCallerGuard` 的调用方校验语义 ❓）
- 数据层：`data/db/AppDatabase.kt`（20KB）、`DBManager.kt`（16KB）、`DBProvider.kt`（**40KB**）、`dao/RoomDaos.kt`（11KB）｜🔁 边栏用 JSON 仓储；❓ DBProvider 的跨进程读写契约未通读
- 实体：`SmsCodeRule.kt` ｜✅；`SmsMsg.kt` ｜🔁（记录模型）；`AppInfo.kt` ｜❌（按应用配置：blocked/forwarding/notifyTemplate）；`AutoInputEvent.kt` ｜❌（填充事件记录）；`NotifyRouteRule.kt` ｜❓ 全仓无调用方（预留表）
- `data/log/RuntimeLogProvider.kt` ｜🔁 边栏有自己的诊断日志；"运行时日志保留天数"未移植
- `data/update/*`（4 个）｜⛔/🔁 边栏有自有更新器
- `feature/backup/BackupManager.kt`、`BackupTypes.kt` ｜🔁 边栏有设置备份；**独立的规则表导入/导出未移植** ❌
- `feature/store/EntityStoreManager.kt`、`EntityType.kt` ｜🔁/❌（文件化实体存储；`CODE_RULE_TEMPLATE` 规则模板 ❌、`PREV_SMS_MSG` 上一条短信 ❌、`BLOCKED_APP` ❌）
- `forwarder/*`（7 个）｜⛔ 不做（转发已分流到"信驿 Relay"）
- `runtime/*Facade*.kt`（10 个）｜🔧 内部胶水层，边栏架构不同

### 1.4 core（32 个 main）

- `billing/BillingProvider.kt` ｜⛔ 不做
- `common/utils/PackageUtils.kt` ｜🔧
- `ui/block/AppInfoHelper.kt`、`SortType.kt` + `ui/home/AppConfigScreen.kt`（17KB）、`AppConfigViewModel.kt`（17KB）、`appconfig/*`（2 个）｜❌ 未移植（按应用配置页）
- `ui/home/ComposeSettingsScreen.kt`（**92KB**）、`settings/*`（2 个）｜❓ 设置面全集未按 UI 行为核对（此前只按偏好键核对）
- `ui/home/SettingsViewModel.kt`（31KB）、`MainActivity.kt`（44KB）、`MainScreen.kt`（34KB）、`OverviewScreen.kt`（19KB）、`overview/*`（2 个）、`LauncherActivity.kt` ｜🔁 边栏自有主页/设置；❓ 行为细节未核对
- `ui/record/CodeRecordScreen.kt`（53KB）、`CodeRecordScreenMaterial/Miuix.kt`、`CodeRecordViewModel.kt` ｜🔁 边栏有记录页；上游的**四类筛选/清空/批量操作**是否齐 ❓
- `ui/smscoderule/SmsCodeRuleScreens.kt`（25KB）、`SmsCodeRuleScreensMaterial/Miuix.kt` ｜🔁 边栏有规则页；上游存在 `builtinRuleEditorId`（**内置规则可编辑**）与导入导出 ❓/❌
- `ui/privacy/PrivacyPolicyPage.kt`、`ui/faq/FaqScreen.kt`、`ui/theme/*`（3 个）、`ui/nav/*`（2 个）、`ui/performance/*`（2 个）｜🔁/🔧（隐私页、FAQ、主题；性能埋点不适用）

### 1.5 十二个"未核对"文件的核对结果（二次核对）

（方法：小文件全读；大 UI / DB 文件提取对外动作、暴露 URI 与字符串键。）

| 文件 | 核对结论 |
| :--- | :--- |
| `SmsHookConstructorInitializer.kt`（全读） | 电话进程初始化编排：runtime、冲突仲裁、通知渠道初始化、**CopyCodeReceiver 注册**、模块激活标记、心跳、**收件箱观察者注册**。边栏对应 `SmsPolicyRuntime.register` + 各 hook install；缺心跳与收件箱观察者 |
| `PrefRestoreTypeRegistry.kt`（全读） | 备份恢复的偏好类型表 = 上游全部可恢复设置。逐项比对后新增两个小缺口：**短信去重开关**（`KEY_DEDUPLICATE_SMS`，边栏固定去重无开关）、**运行时日志保留天数** |
| `ProviderCallerGuard.kt`（全读） | 上游 DBProvider 的调用方白名单。边栏不用 provider 通道 → **不适用**（等价保护是 injector 的 callerUid 校验） |
| `XposedRuntimeInstaller.kt`（提取） | 安装运行时：CoreRuntime、HookPolicy、匿名安装 ID、HookBridge（含**跨进程门 `claimRuntimeGate`**、心跳、content URI）、日志 sink 与**日志脱敏**。缺：跨进程门（已决定不做）、心跳、日志脱敏/保留 |
| `SmsHandlerHook.kt`（提取） | 除 `dispatchIntent` 外另挂一批同类方法；引入 **`SmsDispatchChainBlockDeduplicator`（分发链去重）** 与 **`InboundSmsBlocker`（专用入库拦截器）**，并在电话进程初始化通知渠道、注册 CopyCodeReceiver 与收件箱观察者。边栏缺：hook 侧去重、专用入库拦截器、收件箱观察者 |
| `ObservedSmsHandler.kt`（提取） | 收件箱记录处置：去重、**回填短信库 sim_slot/sub_id（多卡归属修复）**、解析 company/package。这补全了"路由修复"的确切含义：修的是**已入库短信的卡槽归属**；边栏只把槽位用于显示 |
| `HookPrefsReader.kt`（提取） | hook 读取的偏好全集（开关/自动输入/延迟间隔/提醒/记录四类/通知开关与归属/日志/entitlement）。与快照 `otp` 段比对：**缺 `deduplicateSms`、`codeNotificationOwner`**，其余已有或已决定不做 |
| `DBProvider.kt`（提取） | 暴露 `sms_msg`、`sms_code_rule`、`app_info`（含按包名）、`auto_input_event`，另有 `prefs_cache`、`rules_cache` 供 hook 读偏好与规则。边栏**不适用**（改快照+广播）；也印证上游"规则下发"走 `rules_cache` |
| `SettingsViewModel.kt`（提取） | 主题/UI/图标/测试/备份恢复/更新等模块自身管理，无新验证码能力 |
| `ComposeSettingsScreen.kt`（提取） | 设置面与 `PrefConst` 一致，未发现清单外能力 |
| `CodeRecordScreen.kt`（提取动作） | 记录页有**多选批量删除（带撤销）、清空、导出记录到文件（SAF）、类型筛选、复制**；边栏只有搜索/排序/单条删除/复制 → 新增缺口：**批量删除 + 清空 + 导出记录** |
| `SmsCodeRuleScreens.kt`（提取动作） | 内置规则可编辑（`builtinRuleEditorId`）、复制、刷新；边栏以"复制为我的规则"覆盖其意图 |

**二次核对新增的小缺口**：①记录页 批量删除/清空/导出记录；②短信去重开关；③运行时日志保留天数与日志脱敏；④hook 侧 分发链去重 + 专用入库拦截器；⑤收件箱观察 + 短信库卡槽回填。前三项属 App 侧、成本低；后两项属 hook 侧，只在需要"更彻底兜住漏掉的短信"时才值得做。

## 2. 目标功能面（按你列的需求收敛）

| 需求 | 落层 | 上游对应实现 | 现状 |
| :--- | :--- | :--- | :--- |
| 用户自写规则 + 内置规则 | App（编辑）/ hook（执行） | `SmsCodeRule` + `SmsCodeUtils` 合并用户/官方规则 | 用户规则 ✅；**内置规则数据缺**（`smscode-rules.json` 未并入） |
| 规则测试 | App | 上游设置页 `KEY_SMSCODE_TEST` | ✅ 已有一键测试弹窗 |
| 验证码记录 | App 展示 / hook 回写 | `RecordSmsAction` + `SmsMsg` + 记录页 | ✅ 本轮已有（分类与上限可按需砍掉） |
| 自动填充 + 自动确认/回车 | hook 触发 → system_server 注入 | `AutoInputAction` + 上游 `SystemInputInjectorHook` | 🔁 功能有，触发方在 App；**要按你的要求改成 hook 触发** |
| 拦截短信 | hook | `SmsBlockEvaluator` + 黑名单 | ✅ 本轮已移植 |
| 通知 / Toast 提醒 | hook（通知建议下沉） | `NotifyAction`（phone-owned 优先）+ `ToastAction` | 🔁 现为 App 侧发 |
| LSPosed 状态 | App | 上游无（边栏自研） | ✅ 已有三行，可按需收敛成一行 |
| 输入延迟 / 间隔 | hook 下发、注入器执行 | `KEY_AUTO_INPUT_CODE_DELAY/INTERVAL` | ✅ 已有 |
| 标记已读 / 提取后删除 | hook | `OperateSmsAction` | ✅ 本轮已移植 |

## 3. 页面重构方案（精简）

现状问题：一个 OTP 页面里塞了「运行状态文本行 ×3、自动填充分组、提醒分组、短信与记录入口、LSPosed 分组、状态行 ×3、诊断分组、时序分组」，层级和语义混在一起。

**目标结构：一个入口，三个 Tab（规则 / 记录 / 设置）**，每个 Tab 只放该职责的东西：

- **规则**：关键词（收进"高级"折叠）+ 内置规则列表（开关，可行时支持复制成用户规则再编辑）+ 用户规则列表（增删改）+ 顶部「测试」按钮（输入文本 → 显示命中规则与提取结果）。
- **记录**：列表 + 搜索 + 单条删除（可选清空）；不再分四类 tab（分类只在行内以小标签显示）。
- **设置**：四组，全部是"开关 / 滑块"这类原子项：
  1. 提醒：系统通知（含驻留时长）、Toast；
  2. 自动填充：总开关、自动确认/回车、输入延迟、输入间隔；
  3. 拦截：屏蔽验证码短信总开关（黑名单收进子页，保留"号码/前缀/内容/正则 + 拦截/删除"）；
  4. LSPosed：**一行**状态行（模块/短信通道合并）+ 「重新检测」+ 「重启电话进程」。
- **删除**：运行状态多行文本提示、诊断分组、填充统计页（你没要）、记录分类与上限（简化为固定上限）、SIM 槽位展示、官方规则"刷新"按钮（内置规则随包发布，改为显示版本）、`OtpAutoInputSettingsScreen` 这个重复入口。

## 4. hook 侧"直接照上游移植"的设计与代价

**做法**：把动作从 App 进程整体搬到电话进程，形成上游那样的动作管线：

1. 快照 `otp` 段从"策略"扩展成"运行方案"：`actions{clipboard,toast,notification(+驻留/自动取消),autoInput(+autoEnter,delayMs,intervalMs),markRead,delete,block}` + `rules{keywordsRegex,userRules,builtinRules}` + `blacklist` + 通知文案。
2. 规则下发：规则数量较多时，改写"由 App 写入设备保护目录的规则文件、hook 侧读取并带 TTL 缓存"（上游 `EntityStoreManager` 的做法），避免快照过大。
3. 电话进程新增运行期（替代现有只做转发的 `SmsCaptureForwarder`）：解析 → 拦截判定 → 动作管线（复制/Toast/通知/自动填充/已读/删除）→ 记录回写。
4. 记录回写：hook 侧通过广播把记录交给 App 落 `OtpRecordsRepository`；App 不在时按"丢弃或补拉"策略（若要更可靠，需要 App 提供带 token 校验的 ContentProvider，工作量大一档）。
5. 通知下沉：用 hook 模块自己的资源发 phone-owned 通知（上游做法），图标/文案随 hook 打包。
6. 自动填充：hook 侧直接广播给现有 system_server 注入器（其 callerUid 放行列表已包含 `1001`/`1000`，无需改校验），结果仍回 App 记统计。

**代价与风险**：

- hook 侧代码量显著上升（规则缓存、动作管线、通知渠道、记录回写、异常兜底），必须真机验证；
- hook 代码每次改动都需要重启才能生效 → **必须同时做"重启电话相关进程"的协调器**（上游 `PhoneProcessRestartCoordinator`），否则体验反而变差；
- 快照/规则文件的下发时机与体积需要压测；规则文件要保证 hook 侧可读（设备保护目录 + 放宽读权限，已有先例）；
- 迁移期要保证"App 侧旧管线"与"hook 侧新管线"不会重复动作（例如双份通知/双份 Toast）——需要一个总开关或版本门控。

**收益**：App 被冻结/未启动时也能完成全部动作（上游可靠性水平）；请求你"核心 hook 直接移植"的本意即此。

## 5. 待你拍板

1. **内置规则的数据来源**：把上游 `smscode/rules`（或 `smscode-core`）给我 → 直接并入；否则我自建一份最小集（运营商/银行/常见服务的关键词 + 正则，约 30~60 条），并标注自建。
2. **"拦截短信"的范围**：只留"屏蔽验证码短信"总开关，还是保留黑名单子页（号码/前缀/内容/正则 + 拦截/删除）？建议后者（现有实现直接搬）。
3. **确认删除项**：统计页、记录分类与上限、SIM 槽位、官方规则刷新按钮、`OtpAutoInputSettingsScreen` 重复入口 —— 是否都可以删？（我建议全删）
4. **是否接受 hook 侧动作管线的代价**：接受的话，我会把"重启电话进程"协调器与管线一起做（一次重启、一次真机验证）。
5. **Tab 结构**：规则 / 记录 / 设置 三 Tab 是否 OK？

## 6. 建议执行顺序

1. 先把"❓ 未核对"的 12 个文件读完并回填本清单（纯分析，不影响代码）；
2. 页面重构（纯 UI，App 侧，装完即生效）；
3. 内置规则接入（等你确认数据来源）；
4. hook 侧动作管线 + 重启电话进程协调器（一次重启，真机验证）；
5. 收尾：删除废弃分组/页面、更新本文档与 `xposed_smscode_port_analysis.md`。

## 7. 执行进度（按批次回填）

### 已完成（第一批：页面重构 + 状态行 + 重启电话进程）

- **三 Tab 结构落地**：`OtpHubTab = Settings | Records | Rules`，默认进「设置」；FAB（新增规则）只在「规则」Tab 出现。
- **设置 Tab**（`OtpSettingsTabItems.kt`）四组：
  1. 提醒：系统通知 + 驻留时长 + **提取后自动复制** + Toast；
  2. 自动填充：**无障碍服务状态行**（已开启/未开启，点击去开启）+ 总开关 + 自动确认/回车 + 输入延迟 + 输入间隔；
  3. 拦截：屏蔽验证码短信 / 标记已读 / 提取后删除 + 「短信黑名单」入口；
  4. LSPosed：**单行状态**（模块 / 短信通道 / 系统注入三段摘要，点击重测）+ 短信拦截开关 + 系统注入开关 + 「重启电话进程」。
- **删除的页面与路由**：`OtpSettingsScreen`（改为 `OtpSharedSections.kt` 只保留共用段落）、`OtpAutoInputSettingsScreen`、`OtpAutoInputLazyItems`、`OtpAutoFillStatsScreen`、`OtpRecordSettingsScreen`、`OtpAutoFillStatsViewModel`，以及 `AppNavKey` 里的 `OtpSettings / OtpRecords / OtpRulesList / OtpAutoInput / OtpRecordSettings / OtpAutoFillStats`；黑名单页从 `OtpSmsPolicyScreen` 收敛为 `OtpSmsBlacklistScreen`（只留黑名单，三个开关上移到设置 Tab）。
- **重启电话进程**：新增 `ACTION_RESTART_PROCESS` 契约 + `SmsPolicyRuntime` 自我重启（`Process.killProcess`，带确认串防误触发）+ `PhoneProcessRestartRequester`；点按后 2.5 秒自动重测状态。
  注意：system_server 仍无法用这种方式刷新，手势/剪贴板相关仍需重启整机；该按钮只解决电话与短信存储进程。
- 统计数据的**采集与备份保留**（`OtpAutoFillStatsRepository` 仍写入、仍在设置备份里），只是移除了它的 UI 页面。

### 未完成（第二批：hook 侧动作管线）

仍未做的就是你要求的"核心 hook 直接照上游移植"：把 复制 / Toast / 通知 / 自动填充 / 记录的**执行**从 App 进程搬到电话进程。原因与风险记录在第 4 节，关键三点：

1. 需要**跨进程去重门**（上游用 provider 的 `claimRuntimeGate`，本地对应实现缺失），否则同一条验证码会被 hook 与 App 两条管线处理两次（双份通知 / 双份填充）；
2. 通知下沉到 phone 进程要自带资源与渠道、失败回退；
3. 必须与「重启电话进程」配套做真机验证（本批次已把重启通道准备好，正好作为它的前置条件）。

建议下一批按此顺序：规则下发（App 写规则文件 + hook TTL 读）→ hook 侧动作管线（先接管 SMS 来源，通知来源保留在 App）→ 通知下沉 → 记录回写 → 真机验证。

### 已完成（第二批：小项 2 / 3 / 4 / 5）

- **2 规则表导入/导出**：新增 `OtpUserRulesCodec`（JSON；单测覆盖往返、非法输入、按 id 与内容指纹去重合并）。规则 Tab 顶部两个按钮走系统文件选择器（导出 `CreateDocument`、导入 `OpenDocument`），结果用应用内消息提示条数或失败原因。
- **3 按应用屏蔽**：新增设置 `otpBlockedPackages` + 「设置 → 拦截 → 不处理这些应用」子页（复用现有应用黑名单页与应用选择器）。
  - 通知链路：名单内应用的验证码**不提取**；
  - 自动填充：请求前用无障碍服务取前台包名，命中名单则**整条跳过**（不注入、不回退），结果记为 `blocked_app`。
- **4 小项**：①短信转发自带的**卡槽写入记录并在记录/通知上显示「卡1/卡2」**（`OtpRecord.simSlot`，旧数据兼容）；②内置规则行新增**「复制为我的规则」**，复制后即为可编辑用户规则。
- **5 十二个未核对文件全部回填**（见 §1.5），并据此记录五个新的小缺口。

### 已取消（原第二批剩余）

hook 侧动作管线（把复制 / Toast / 通知 / 自动填充 / 记录搬到电话进程）：按用户判断**取消**——边栏是常驻手势应用，App 被冻结的场景基本不存在，而搬迁会引入"同一条验证码被处理两次"与跨进程去重门的成本。保留的能力是「重启电话进程」按钮（更新后免整机重启）。

### 已完成（第三批：验证码页由 Tab 改为一页四入口）

- **一级「验证码」页**（`OtpHubScreen`）：顶部两行状态（无障碍自动填充状态、LSPosed 状态，均可点击去开启/重测）+ 四个入口行（验证码提取、验证码自动填充、验证码提取规则、验证码记录）。原来的 `TabRow` 与设置分组全部下线。
- **验证码提取页**（`OtpExtractionScreen`）：提醒（系统通知+驻留时长、Toast、提取后自动复制）+ 拦截与安全（屏蔽验证码短信、标记已读、提取后删除、短信黑名单、不处理的应用）。
- **验证码自动填充页**（`OtpAutoFillScreen`）：自动填充开关、自动确认/回车、输入延迟、输入间隔 + LSPosed 系统注入开关；未开无障碍时顶部给一行引导。
- **验证码提取规则页**（`OtpRulesScreen`）：顶部「短信测试」+「⋮」（导出/导入/刷新内置规则）+ 统一规则表（我的 / 内置 行内标签 + 计数）+ 高级「兜底识别词表」；新增规则走"贴一条真实短信自动生成"（`OtpRuleInference`）。
- **验证码记录页**（`OtpRecordsPage`）：独立子页（搜索、排序、单条删除、复制）。
- 新增导航：`OtpExtraction` / `OtpAutoFill` / `OtpRules` / `OtpRecords`；删除 `OtpSettingsTabItems`（其内容拆分到上面两个子页）。
