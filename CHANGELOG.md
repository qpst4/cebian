# Changelog

All notable changes to Cebian are documented in this file.

## [Unreleased]

## [1.9.9.9] - 2026-09-02

### Added
- **首次引导**：重构为 HyperOS 3 流体背景与 MIUIX 三步向导；全屏沉浸式避免冷启动闪现主页；优化权限请求时机并新增应用列表权限检测
- **手势**：新增「短滑后悬停」五向槽位与可配置停留时长、向内复合模式；定时触发悬停震动并容忍手指微抖
- **手势**：新增前台 Activity Inspector 实时 HUD 与历史面板
- **冰箱**：网格冰箱 UI（Miuix 风格网格页、管理页、Pin 桌面快捷方式）；冻结/解冻对齐雹 PM API 与 shell 路径；手势打开默认进入已冻结列表与底部悬浮窗
- **特权模式**：Root / Shizuku 二选一特权模式与双执行链路；任务管理、RAL、剪贴板监听等按模式分流
- **剪贴板**：新增标准 API 剪贴板监听模式（ClipboardManager 公开监听），无需 Root/Shizuku/悬浮窗
- **通知滤盒**：展示渠道 ID 并支持跳转系统通知设置；默认打开历史标签
- **晃动手势**：灵敏度扩至 20 档（阈值 2–20 rad/s），旧 1–10 刻度 v3 迁移保持手感
- **搜索引擎**：内置文字搜索引擎预设库与自选添加界面
- **主题与图标**：Material You Monet 主题图标与自适应 Launcher mipmaps；关于页新增 Monet 图标主题选项

### Fixed
- **按应用禁用**：横屏下改用无障碍窗口实时识别前台包名；移除 UsageStats 轮询与横屏仅 relayout 路径，修复游戏与全屏视频中触钮/悬浮球仍显示
- **晃动手势**：独立灵敏度路径应用 effectiveThreshold 换算；修正刻度与迁移（数值越高越灵敏，默认 3.0）
- **设置**：悬浮球主开关关闭时子页面仍可进入配置
- **首次引导**：修复冷启动应用列表弹窗拦截与「去授权」跳转系统/MIUI 权限管理页
- **诊断**：诊断导出移至后台线程，避免主线程 ANR
- **ML Kit**：修复翻译语言包下载崩溃；OCR 增加错误防护
- **冰箱**：修复 overlay 长按菜单崩溃；手势打开时减少首页闪屏

### Changed
- **架构**：启动器切页动画优化；100% 纯 ViewModel UDF 架构迁移；消除 runBlocking 与阻塞 IO
- **设置与稳定性**：加固 AppSettings slices；自适应二维网格；onTrimMemory 与本地崩溃上报
- **UI 规范**：Material3 Snackbar 全面迁移为 MIUIX 原生 SnackbarHost；背部双击设置页重构为统一 MIUIX 卡片规范
- **UI 细节**：MiuixTabRowWithContour 浅色底槽与滑块对比度优化；搜索引擎编辑器顶栏与预设选择器简化
- **文档**：README 截图表格统一布局与列宽规范

## [1.9.9.8] - 2026-08-31

### Added
- **手势动作**：新增「短滑进入并原路返回」手势触发支持，具备高精度识别与动效/触感一致性反馈
- **手势动作**：新增边角 L 型滑动扩展槽位与提示动效，完善对角线回退机制，动作选择器置顶「无」动作
- **手势动作**：新增「伪息屏」、「钉到屏幕」与「独立应用切换器」手势动作（支持左右对称手势）
- **手势动作**：新增切换自动旋转、锁定竖屏、锁定横屏手势动作
- **手势动作**：新增系统助手、语音搜索与语音助手动作，并统一对齐 Bootstrap 矢量图标规范

### Fixed
- **蜂窝启动器**：修复遮罩浓度调节失效、渲染时锁死在固定浓度值的问题
- **快速启动器**：修复快捷 Shell 命令在悬浮窗模式下的配置崩溃异常；优化软键盘弹出时的底部内边距避让
- **导航交互**：强化导航栈防击穿与防连击保护机制，解决快速连击返回导致闪退的问题

### Changed
- **手势动作体系**：全面重构手势动作分类为 5 大核心模块（基础控制、导航与窗口、面板与启动器、生产力与工具、系统与设备），优化检索与选择体验
- **界面与动效**：对齐快速启动器 TabRow 水平边距与 Miuix 卡片规范
- **工程与依赖**：升级 Compose BOM、Material3、OkHttp 等核心库，CI 构建流程全面升级至 Node 24 运行时环境

## [1.9.9.7] - 2026-08-29

### Fixed
- **应用字母索引**：修复无应用匹配时的空字母过滤与面板裁剪范围，解决极端场景下气泡越界与无响应问题
- **应用字母索引**：修复右侧面板单列或不满整行时的视觉列计算与布局偏移问题

### Changed
- **开源合规与文档**：严格补齐 EdgeGesture 与 EdgeX 开源归属声明与文件头部版权注释
- **官方文档与视觉**：重构 README 与英文文档 README_en，新增真机带壳渲染 Hero 全景海报展区、完善手势动作与扩展功能说明

## [1.9.9.6] - 2026-08-29

### Fixed
- **悬浮球**：修复重负载与掉帧场景下拖出悬浮球时球与手指脱节、手指出现在球与准星中间的位移累加问题
- **悬浮球**：锁定会话级初始贴边侧，防止大距离拖拽时锚点突变反转到屏幕对侧
- **悬浮球**：优化 Slop 阶段平滑晋级到取词拖拽时的会话衔接；解耦拖拽过程中的 Compose 状态重组，提升 120Hz 跟手度与流畅度
- **圆环启动器**：修复持续触发手势时半透明快捷槽位与启动器面板显示问题
- **晃动手势**：倒扣手势引入武装状态机，防止来电/通知亮屏时误触发倒扣动作

## [1.9.9.4] - 2026-08-28

### Added
- **手势动作**：新增「N 分钟后闹钟」，合并原 1/3/5/10/15 分钟五档延时提醒；触发后弹窗自选 1–120 分钟
- **手势动作**：新增「自动亮度」开关
- **快速启动器**：文件夹内分页与拖拽排序
- **全息启动器**：背景模糊与遮罩选项

### Changed
- **延时提醒**：文案统一为「闹钟」语义，新增闹钟图标
- **快速启动器**：右侧面板镜像翻页与末行图标排列
- **圆环启动器**：外观弹窗横屏布局与可滚动内容区
- **冰箱 / 重冻**：更新动作图标

### Fixed
- **快速启动器**：文件夹编辑模式翻页与拖拽换位预览；图标溢出不可点击；绑定面板后无法选择持续触发模式
- **圆环启动器**：触摸交付与跟手松手；持续触发松手进入编辑时的面板关闭与闪屏
- **全息启动器**：高斯模糊首帧不生效
- **悬浮球**：拖出跟手优化与按下闪没；统一原生球体视图
- **取词面板**：手势返回时退场动画被重复 dismiss 打断

## [1.9.9.2] - 2026-08-27

### Added
- **搜索引擎**：新增文字分享到指定 App 类型（编辑器 Tab「分享」配置目标，取词面板与搜索面板网格点击直达目标应用）

### Changed
- **搜索引擎编辑器**：执行方式 Tab 使用短标签（链接 / Activity / 分享），便于三列完整显示

## [1.9.9.1] - 2026-08-27

### Fixed
- **搜索引擎**：修复图片分享目标选择后编辑器未回写（编辑器与选择页共享 Activity 级 draft）
- **搜索引擎**：修复图标预览不更新、保存后列表仍显示旧图（图标缓存 key、相册 Uri 即时预览）
- **搜索引擎**：修复图片分享应用无法保存（`SHARE_IMAGE_TO_APP` 未写入 targetPackage/Activity）
- **搜索引擎**：修复应用图标选择后未等待落盘即返回导致图标未生效

## [1.9.9] - 2026-08-27

### Added
- **消息提醒**：解锁后打开锁屏消息前增加确认弹窗，支持按应用「始终允许」、解锁规则与确认弹窗自动关闭设置
- **扩展功能**：冰箱、背部双击（实验性）；新增音量扩展面板、屏幕翻译、全局复制、延时提醒、重新冻结等手势动作
- **扩展面板**：快捷槽位长按编辑、槽位标签与清除；毛玻璃浮层、小窗启动策略与深色模式适配

### Changed
- **扩展 Tab**：按功能分组入口
- **扩展面板**：槽位选择器分组展示快捷方式，统一列表高度与 Spring 退场动画
- **冰箱**：应用列表增加加载状态
- **搜索引擎**：统一 Miuix 编辑体验
- **设置页**：应用索引、命令面板、指针命名、主题深色背景、悬浮球取词与外观分组
- **搜索面板**：设置布局与计算器精度

### Fixed
- **搜索引擎编辑器**：修复图片分享目标无法发现（SEND / SEND_MULTIPLE、常见图片 MIME）；修复分享目标列表高度为 0
- **主界面**：Tab 切换与底栏指示器同步

### Removed
- **隐藏最近任务**：移除 LSPosed Hook 与相关设置入口

## [1.9.8.9] - 2026-08-25

### Added
- **交互与外观子页**：震动、横移返回、预测性返回与主题/底栏设置集中入口
- **全息启动器**：新增 3D 球 overlay 动作与独立设置页（隐藏应用、旋转手感等）
- **边角轮盘**：槽位二级快捷菜单与统一编辑导航
- **应用切换器**：FV 轴向/布局与悬浮球触发体验优化

### Changed
- **主导航对齐 Mishka**：NavDisplay 默认转场与系统圆角；Tab Pager 外层 Scaffold 铺底修复 overscroll 露色
- **Tab 体验**：切页动画 ~300ms EaseInOut；液态玻璃底栏居中，底部间距对齐 Mishka
- **顶栏毛玻璃**：新安装默认改为渐进模糊

### Fixed
- **悬浮窗毛玻璃**：合并 Hidden API 豁免，避免预测性返回开关覆盖反射调用
- **小组件面板**：pages/滑条与预览模糊状态同步
- **手势与启动器**：直达 Shell 触发、边角轮盘松手误触、快速启动器图标缓存与会话内保留
- **剪贴板浮窗**：chip 与面板位置可靠持久化

## [1.9.8.2] - 2026-08-23

### Fixed
- **剪贴板钉图**：修复剪贴板单图片/单文本钉在屏幕时误作为富文本卡片导致出现大面积白色背景空白的问题，自动解包为纯图片/纯文本贴合悬浮窗
- **触钮手势动画**：修复持续触发蜂窝启动器或圆环启动器后，启动器弹出时边缘手势动画未及时隐藏并在手指滑动选取期间一直残留的问题
- **小组件选择器**：修复小组件面板浮窗的选择小组件列表中 App 分组行误用向左返回箭头的问题，更正为向右前进箭头

## [1.9.8] - 2026-08-22

### Added
- **预设快捷方式库**：新增内置预设快捷方式库，快速启动器/蜂窝启动器编辑器的“快捷方式”页与“应用内直达 → 添加应用快捷方式”均可进入文件夹浏览；列表默认只显示已安装应用，搜索时展示全部并标记「未安装」
- **应用切换器**：新增外观与布局自定义（圆环/蜂窝布局参数、图标形状等），支持实时预览，并过滤系统内部组件
- **快速启动器**：编辑页与悬浮面板支持拖拽图标合并进文件夹；新建文件夹页升级为顶栏模糊、可展开搜索与卡片样式
- **小组件面板**：新增面板“模糊强度”设置（0–150 dp）与快捷添加多选，默认外观调整为模糊强度 19 dp、面板不透明度 25%
- **小组件选择器**：即时加载、实时拖拽重排与触感反馈
- **悬浮球取词**：新增悬停暂停延迟与区域取消容差设置
- **更新弹窗**：更新说明按「新增 / 变更 / 修复」分组展示

### Changed
- **快捷方式列表统一**：快速启动器/蜂窝启动器编辑器“快捷方式”页与“应用内直达 → 添加应用快捷方式”顶部新增“我的直达”“预设快捷方式库”文件夹入口（保留系统快捷方式目录），进出文件夹带滑动过渡，文件夹卡片跟随 miuix 组件与配色
- **剪贴板 / 钉图**：设置拆分为独立子页面
- **快速启动器外观**：默认外观调整为背景不透明度 63%、模糊强度 16 dp
- **搜索设置**：统一 URL / 自定义方案搜索类型，增强无障碍重试
- **构建与 CI**：NDK 版本自动适配本地环境（CI 回退 28.2.13676358）；CI 优化为 full/lite 并行矩阵构建

### Fixed
- **小组件面板设置**：修复模糊强度调整后设置页仍回跳到默认值
- **快捷方式编辑器**：修复从“我的直达/预设快捷方式库”返回后 tab 跳到“动作”；修复文件夹卡片左右贴边
- **小组件弹窗**：弹窗动画对齐 Dialog 样式，修复多按钮点击与堆叠手势
- **剪贴板悬浮窗**：优化窗口交互与图片长按拾取，修复面板标题；修复小组件选择器崩溃
- **快捷方式创建**：从宿主应用创建时保留完整 intent URI 与 extras；创建第三方快捷方式后自动关闭添加面板
- **快速启动器**：启动 Shell 命令面板后自动收起面板
- **悬浮球与 Overlay**：优化点击穿透时机与手势点击参数

## [1.9.7] - 2026-08-19

### Added
- **剪贴板悬浮窗**：新增单行列表显示模式 (`SINGLE_LINE`)，支持高度紧凑排版、点击展开全文与缩略图预览，并支持单行/网格/列表样式切换
- **快速启动器**：重构支持文件夹分组、底部工具栏与多面板快速轮换，网格模式扩展至最大 6列 × 9行，添加面板升级为 BottomSheet 多选
- **应用切换器**：新增应用切换器手势与 Overlay，重构为 FV 风格圆环/蜂窝布局，支持快捷槽位选择器与右侧拖拽重排
- **“关于”页与更新弹窗**：升级 HyperOS 3 动效、三色应用图标切换器（蓝色/绿色/紫罗兰）、Mishka 头部样式与序号化排版更新弹窗
- **原生全屏高斯模糊**：重构小组件悬浮面板为纯原生全屏悬浮架构，支持硬件毛玻璃高斯模糊与 AOSP 局部高斯模糊联动
- **界面宽屏自适应与导航**：主导航迁移至 `miuix-nav`，顶栏支持可配置的高斯/渐进模糊样式，宽屏侧栏支持自适应悬浮叠层
- **切换上一应用**：手势自动排除已启用输入法，并支持自定义应用黑名单
- **消息提醒**：新增“解锁后进入最后一条消息”：锁屏期间收到的消息，解锁后自动打开

### Changed
- **悬浮球与取词**：统一双侧悬浮球线侧与球侧悬停取词逻辑及面板弹出，优化 FV 命中区域与图标底盘
- **Android 16 适配**：对齐 Android 16 对后台剪贴板监听与焦点读取机制
- **UI 细节统一**：规范全应用 Miuix 卡片边距、操作行与空状态间距，扩展可取词链接识别
- **构建系统升级**：升级 Gradle Wrapper 至 9.7.0，拆分 CI full/lite 构建解决编译内存溢出

### Fixed
- **剪贴板悬浮窗**：修复大图片粘贴 TransactionTooLargeException 异常崩溃与 fallback 机制
- **剪贴板悬浮窗**：修复 Telegram 等 App 输入框粘贴自动附带“输入消息”占位符问题
- **剪贴板悬浮窗**：修复纯文本粘贴自动包含 HTML 段落双换行与多余空行
- **剪贴板悬浮窗**：修复图片指纹比对机制，解决打开浮窗生成两条重复卡片及多图片误判覆盖问题
- **剪贴板悬浮窗**：单行模式下图片优雅展示名称或 `[图片]` 标签，不展示冗长 URI
- **Overlay 遮罩与手柄**：修复 Compose 弹窗关闭后恢复边缘触摸捕获窗口与触发手柄，修复选项切换闪烁并保留选中状态

## [1.9.6] - 2026-08-15

### Added
- Full / Lite 双 APK 发版流程；应用内更新默认指向 lite 包
- 剪贴板悬浮窗：IME 跟随、搜索、缩略图、应用黑名单、横竖屏独立位置记忆
- 设置页迁移 Mishka 架构（`settingsCardItem` / `groupedCardItems`）

### Changed
- 设置子页转场后延迟组合重内容，减轻导航卡顿
- 横屏触钮独立配置与编辑体验优化

### Fixed
- 侧栏浏览时不抢焦点，尽量保持底层输入法
- 纯图片剪贴板语义、悬浮窗条目拖拽与粘贴逻辑（避免粘贴提升历史顺序）
- Widget 添加流程浮层、搜索面板计算器回调、overlay 内容面板层级
- 触钮横屏物理旋转锁定；Lint 与 release APK 校验

## [1.9.5] - 2026-08-13

### Added
- 搜索历史、前缀别名与历史深链搜索；候选搜索分类型设置与系统设置 Manifest 索引
- Shell 命令自定义图标与启动器角标；Activity 快捷方式应用内直达目录与自定义图标
- 剪贴历史贴边浮窗与分页加载；暂存夹/剪贴板 `cebian://` 外部快捷入口
- 边角轮盘壁纸模糊与手势动作分组选择
- 悬浮指针持续手势悬停框选、松手点击关闭与触摸层优化
- 悬浮球加号指针独立纵向速度；扣桌/命中提示音频反馈
- 快速启动器背景不透明度独立设置；快速启动器图标形状
- 设置备份补全：`shell_icons/`、`shortcut_icons/`、搜索面板历史

### Changed
- 边缘触钮单窗承载触摸、绘制与系统手势排除
- 设置页滑块统一 Miuix Custom Key Points；多处操作菜单改用 WindowIconDropdownMenu
- 收纳面板背景模糊与卡片样式重构
- OCR 引擎升级至 ORT 1.28.0 并展示 pack 版本
- 统一深色模式主题链路并补齐 UI 对比度

### Fixed
- 悬浮球 chrome 层级、熄屏 overlay 与穿透触摸窗同步
- 悬浮指针截图前 detach、松手单击卡顿与功能环回闪
- 小部件 overlay 系统返回；面板可见性判定与 Back 关闭链
- 边缘区域取词回放悬浮球轨迹；Android 13+ 壁纸模糊权限改 READ_MEDIA_IMAGES
- MiuixFormDialog 键盘弹出滚动；深链搜索预填竞态

## [1.9.0] - 2026-08-09

### Added
- 搜索面板大改版：全屏/底部呈现、搜索栏位置、列表顺序、壁纸模糊背景
- 搜索候选：设备文件搜索与预览、通讯录/电话号码、计算器、网页建议、应用结果卡片
- 搜索面板独立设置页与文件搜索细项配置
- 快速启动器多面板，手势槽位可绑定不同启动面板
- 手势动作「切换输入法」
- Widget 面板拓展：应用/快捷方式/小组件添加与卡片容器
- 蜂窝启动器原生 Overlay 重构与显示/动效设置页
- 侧滑返回优先关闭叠加层（OverlayBackDismissChain）
- 设置页迁移 Miuix UI：Lazy 列表、浮动底栏、统一对话框与底部弹层
- FanFreeform 第三方归属声明

### Changed
- 主界面宽屏 NavigationRail；ManagedAppList 公共化
- Shell 命令 Runner 与输出历史统一；手势/快捷启动器图标完善
- 小组件/蜂窝面板 Android 12+ 原生窗口高斯模糊
- 息屏/锁屏时关闭全局 overlay 并统一抑制策略

### Fixed
- leave-open 面板（快速启动器/任务切换器/Shell）手势状态与 Back 关闭
- 悬浮球熄屏后 overlay 被系统摘掉时的自动重建
- 搜索面板动画、预热窗口触摸穿透与底部面板自适应高度
- 悬浮球穿透时线条触摸窗 passthrough 同步

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
