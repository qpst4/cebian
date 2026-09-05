<div align="center">

<img src="art/logo.svg" width="96" alt="边栏 (Cebian)" />

# 📱 边栏（Cebian）

**Android 边缘手势与全能系统增强工具**  
*侧滑面板 · 悬浮球取词搜图 · 晃动 / 扣桌 / 背面手势 · 通知与 OTP 管理 · 悬浮指针 · 应用冻结 · 自由小窗*

**简体中文** | [English](README.md)

[![Release](https://img.shields.io/github/v/release/qpst4/cebian?style=flat-square&color=6340e6)](https://github.com/qpst4/cebian/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2012%2B-brightgreen?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-purple?style=flat-square)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.3.2-blue?style=flat-square)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-blue?style=flat-square)](https://gradle.org)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.07.01-blue?style=flat-square)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-31-orange?style=flat-square)](https://developer.android.com)
[![targetSdk](https://img.shields.io/badge/targetSdk-37-orange?style=flat-square)](https://developer.android.com)

<br />

<img src="art/screenshots/hero_showcase.webp" width="96%" alt="边栏 (Cebian) 全景预览" />

</div>

---

**边栏（Cebian）** 是一款基于 Android 无障碍服务、Shizuku 及可选 LSPosed 框架的系统级手势与生产力增强工具。通过屏幕边缘滑动、多功能悬浮球、摇晃或敲击机身手势，轻松触发 50+ 种系统动作；深度整合本地多引擎 OCR、分词与以图搜图聚合，并在任意应用之上提供高效率的悬浮启动面板、应用冻结室、OTP 验证码提取、通知管理、自由小窗与悬浮指针控制。

- **应用包名：** `com.slideindex.app`
- **当前版本：** 1.9.20（versionCode 45）
- **系统要求：** Android 12+（API 31+）
- **开源协议：** [AGPL-3.0 License](LICENSE)

---

## 📥 下载安装

<div align="center">

[![Download Full APK](https://img.shields.io/badge/下载%20Full%20完整包-内置离线引擎-238636?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/cebian/releases/latest)
[![Download Lite APK](https://img.shields.io/badge/下载%20Lite%20轻量包-体积小巧-0969DA?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/cebian/releases/latest)

</div>

| 产物版本 | 适用场景 | 说明 |
| :--- | :--- | :--- |
| **Full 完整包** (`cebian-*-full.apk`) | **新用户首选** | 开箱即用，已内置完整离线 OCR、Jieba 分词与离线翻译 Native 引擎 |
| **Lite 轻量包** (`cebian-*-lite.apk`) | 追求小体积 / 在线热更新 | 仅保留核心手势与基础功能，体积更小，可按需在线下载扩展引擎 |

> [!TIP]
> 两个版本的 `applicationId` 均为 `com.slideindex.app`，支持直接相互覆盖安装，配置无缝保留。

---

## 🌟 核心特性

应用底部设有四个主要模块：🏠 **首页** · 📳 **晃动** · 🔔 **通知** · 🧩 **扩展**。

### 🏠 首页 — 边缘手势、悬浮球与个性化

#### 1. 边缘手势
- **触发与外观**：左右边缘与顶部/底部高灵敏度触发条，支持气泡、胶囊、波浪等多种动画样式与触钮震动反馈。
- **自定义布局与横屏专属**：自由调节触发条位置、高度、粗细、角度与分段；支持为**横屏状态**独立配置专属触钮把手，游戏与观影防误触更彻底。
- **智能防误触**：横屏隐藏、锁屏隐藏、桌面隐藏以及基于前台应用使用情况访问权限的精确按 App 排除名单与前台切换黑名单。
- **边角轮盘**：从左下或右下角滑出扇形径向菜单，支持实时壁纸高斯模糊与手势动作分组。
- **自由小窗深度适配**：支持 Android 原生 Freeform 自由窗口、小米 MIUI/HyperOS 小窗、魅族 Flyme 小窗等各 OEM 厂商专用小窗策略与规则。

#### 2. 50+ 可配置快捷手势动作

| 动作分类 | 支持操作 |
| :--- | :--- |
| **系统导航** | 返回、Home 键、多任务（系统多任务）、上一个应用、锁屏（含静音变体）、电源菜单、分屏 |
| **截屏与视效** | 全屏截图、区域截图、全屏截图取词、区域截图取词、屏幕录制、手电筒 |
| **OCR 与搜图** | 悬浮球取词、以图搜图聚合、即时悬浮翻译、全局屏幕复制、钉图暂存面板、二维码识别 |
| **面板与启动** | 快速启动器、应用索引、圆环启动器（FV 风格）、蜂窝启动器、全息启动器、任务切换器（OHO 风格）、应用冻结室、扩展面板（音量/亮度快速调节）、Widget 浮窗面板 |
| **媒体与控制** | 媒体上一曲 / 下一曲 / 播放暂停、音量调节、亮度调节、切换输入法 |
| **工具与历史** | 悬浮指针、剪贴板历史面板、快速工具面板（OHO 风格）、暂停悬浮窗、暂停手势 |
| **高级与扩展** | 执行 Shell 命令（Shizuku / Root）、启动指定 Activity / 快捷方式、直达特定应用、N 分钟后闹钟提醒、重新冻结 |

#### 3. 🔮 悬浮球（取词、搜图与多功能手势）
*与「悬浮指针」独立运行，集摇杆操作与屏幕取词指针于一体。*

- **无障碍与本地多引擎 OCR**：优先通过无障碍节点读取文字，失败自动平滑降级至本地离线 OCR 引擎（**ML Kit / Tesseract / PaddleOCR ONNX**）。
- **取词面板**：一键搜索、翻译、点词分词（CppJieba）、全选、去空格、复制，支持区域划词与图片分享 OCR 历史记录。
- **文字搜索聚合**：支持自定义搜索引擎列表、网格排序、搜索历史、前缀别名与深链跳转；支持从 GestureEVO / SearchEVO 独立导入。
- **以图搜图聚合**：区域截图后呼出多引擎搜图面板（Google、Yandex、TinEye、SauceNAO、IQDB、3D-IQDB、ASCII2D、trace.moe、AnimeTrace、Copyseeker），支持并行搜索与内置 WebView 预览。
- **钉图暂存**：将截屏内容或图文块钉在屏幕顶层，支持双指缩放、拖拽与控制条隐藏。
- **外观与微调**：支持预设配色、自定义图片、GIF 动图与幻灯片；可自定义加号指针纵向灵敏度与取词容差。

#### 4. 主题与界面
- **Miuix 视觉与组件体系**：主应用全面采用 Miuix UI（HyperOS 风格）设计语言，支持自适应折叠 TopAppBar、虚拟化分组卡片、平滑回弹阻尼与触觉振动反馈。
- **动态取色（Material You）**：Android 12+ 基于 MaterialKolor 自动提取壁纸色调，生成 9 种调色风格并无缝注入 Miuix 主题。
- **毛玻璃与性能优化**：底部导航栏与悬浮面板支持高斯模糊（Gaussian Blur）与渐进式毛玻璃（Progressive Blur）；支持关闭毛玻璃以完全消除列表滚动时的屏幕采样开销。

---

### 📳 晃动与机身手势 — 摇一摇、扣桌与背面敲击

- **六方向晃动识别**：精准区分左/右翻转、前/后翻转、左/右快速甩动；支持针对特定应用配置独立灵敏度与覆盖专属动作。
- **扣桌静音手势**：亮屏时手机朝下平放至桌面静止，自动触发锁屏与响铃静音（支持自定义联动动作与音频反馈）。
- **背面敲击手势（BackTap）**：基于加速度传感器的后盖敲击识别，支持双击触发 50+ 种系统动作，支持亮屏/息屏/始终触发策略、灵敏度调节与充电防误触。
- **场景规则**：支持亮屏/锁屏激活策略、应用独立黑白名单、独立灵敏度阈值与震动动画反馈。

---

### 🔔 通知 — 消息提醒、历史与 OTP

- **多形态消息提醒**：拦截系统通知并以灵动卡片、悬浮通知、侧边气泡或屏幕弹幕等样式悬浮展示；支持「解锁后进入最新消息」（锁屏收到消息时解锁后自动打开，支持始终允许与询问确认规则）、防打扰应用黑白名单与通知专属手势。
- **通知历史与过滤规则**：按活跃、历史、已隐藏分类管理通知，支持多维度正则表达式与关键词自动归档/屏蔽。
- **OTP 验证码中心**：智能识别短信与应用通知中的验证码，自动正则匹配提取、剪贴板写入与自动填充；提供成功率统计；可选 LSPosed 模块实现系统底层短信注入增强。

---

### 🧩 扩展 — 增强工具与备份

| 功能模块 | 路径入口 | 核心能力说明 |
| :--- | :--- | :--- |
| **应用索引** | 扩展 → 应用索引 | 侧滑呼出拼音首字母索引应用列表，可自由调节列数与面板透明度 |
| **快速启动器** | 扩展 → 快速启动器 | 侧滑网格启动器，支持多面板切换、分页、文件夹拖拽合并与快捷方式库 |
| **圆环启动器** | 手势动作「圆环启动器」 | FV 风格半圆/同心圆环布局，支持自定义层数、圆环间距、图标形状与槽位应用 |
| **蜂窝启动器** | 扩展 → 蜂窝启动器 | 六边形蜂窝网格布局，按住向外圈滑动直达应用与快捷方式 |
| **全息启动器** | 扩展 → 全息启动器 | 全屏 3D 空间球形启动器，拖拽旋转 3D 球体，点击图标启动应用 |
| **任务切换器** | 手势动作「任务切换器」 | OHO 风格后台最近运行任务面板，支持滑动切换、单独关闭、一键清理全部后台任务与小窗启动 |
| **快速工具面板** | 手势动作「快速工具面板」 | OHO 风格快捷工具面板，聚合常用系统开关、快捷操作与实用工具直达 |
| **应用冻结室** | 扩展 → 冻结室 | 批量冻结后台顽固应用与一键解冻（依赖 Shizuku / Root），支持手势面板快捷重新冻结 |
| **搜索面板** | 扩展 → 搜索面板 | 本地应用、联系人、文件、系统设置项与网络文字/以图搜图聚合搜索 |
| **Activity 快捷方式** | 扩展 → Activity 快捷方式 | 系统隐藏设置、未导出 Activity 提取、App Shortcuts 快捷方式与 URI 深链 |
| **外部调用** | 扩展 → 外部调用 | `cebian://` Deeplink 与 Intent Action，供 Tasker / MacroDroid 等搭配使用 |
| **Shell 命令** | 扩展 → Shell 命令 | 命令面板、模板变量替换与自定义图标；依赖 Shizuku / Root 执行 |
| **Widget 面板** | 扩展 → Widget 面板 | 将桌面小部件悬浮化绑定展示，支持可调模糊背景与快捷多选 |
| **悬浮指针** | 扩展 → 悬浮指针 | 跟手虚拟摇杆控制环形指针，支持悬停框选、径向功能环与手势录制回放 |
| **剪贴板历史** | 手势动作「剪贴板面板」 | 图文历史搜索、贴边浮窗与分页加载；支持 Shizuku 后台监听与 `cebian://` 外部协议 |
| **设置备份** | 扩展 → 设置备份 | 将全量配置与资产导出为 ZIP 或一键导入；敏感数据独立加密保护 |

#### 📸 核心界面与交互预览

<table>
  <tr>
    <th width="33.33%" align="center">边角轮盘（二级快捷菜单）</th>
    <th width="33.33%" align="center">圆环启动器（FV 风格）</th>
    <th width="33.33%" align="center">快速启动器（网格多面板）</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/01_circle_launcher_framed.webp" width="100%" alt="边角轮盘" /></td>
    <td align="center"><img src="art/screenshots/03_honeycomb_launcher_framed.webp" width="100%" alt="圆环启动器" /></td>
    <td align="center"><img src="art/screenshots/06_quick_launcher_framed.webp" width="100%" alt="快速启动器" /></td>
  </tr>
  <tr>
    <th align="center">悬浮指针（虚拟摇杆）</th>
    <th align="center">Shell 命令面板（Shizuku）</th>
    <th align="center">剪贴板历史 / 暂存收纳</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/08_floating_pointer_framed.webp" width="100%" alt="悬浮指针" /></td>
    <td align="center"><img src="art/screenshots/07_shell_panel_framed.webp" width="100%" alt="Shell 命令面板" /></td>
    <td align="center"><img src="art/screenshots/10_clipboard_panel_framed.webp" width="100%" alt="剪贴板历史" /></td>
  </tr>
  <tr>
    <th align="center">应用索引（拼音导轨）</th>
    <th align="center">Widget 悬浮小部件</th>
    <th align="center">钉图暂存浮窗</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/05_app_index_framed.webp" width="100%" alt="应用索引" /></td>
    <td align="center"><img src="art/screenshots/09_widget_panel_framed.webp" width="100%" alt="Widget 悬浮面板" /></td>
    <td align="center"><img src="art/screenshots/11_pin_image_framed.webp" width="100%" alt="钉图暂存" /></td>
  </tr>
</table>

---

## 🔗 外部调用

从其他应用、Tasker、MacroDroid 或 `adb` 唤起边栏面板。应用内可在 **扩展 → 快捷操作 → 外部调用** 查看并一键复制。

> **前置条件：** 搜索面板、收纳夹、剪贴板面板需已开启边栏与无障碍服务；通知滤盒需已授予通知监听权限。

### Deeplink（推荐）

统一格式：`cebian://open/<path>?q=<可选关键词>`

| 功能 | URI | 说明 |
| :--- | :--- | :--- |
| 通知滤盒 | `cebian://open/notification-history` | 打开通知滤盒 |
| 通知滤盒（预填搜索） | `cebian://open/notification-history?q=关键词` | 打开并预填搜索词 |
| 收纳夹 | `cebian://open/stash` | 打开收纳夹面板 |
| 收纳夹（预填搜索） | `cebian://open/stash?q=关键词` | 打开并预填搜索词 |
| 剪贴板 | `cebian://open/clipboard` | 打开剪贴板面板 |
| 剪贴板（预填搜索） | `cebian://open/clipboard?q=关键词` | 打开并预填搜索词 |
| 搜索面板 | `cebian://open/search-panel` | 打开搜索面板 |
| 搜索面板（预填关键词） | `cebian://open/search-panel?q=关键词` | 打开并预填搜索词 |

示例：

```bash
# 打开搜索面板
adb shell am start -a android.intent.action.VIEW -d "cebian://open/search-panel"

# 打开搜索面板并预填关键词
adb shell am start -a android.intent.action.VIEW -d "cebian://open/search-panel?q=天气"
```

### Intent Action（高级）

无 URI 对应能力、或需显式指定组件时使用。包名均为 `com.slideindex.app`。

| 功能 | Action | 组件 | 可选 extra |
| :--- | :--- | :--- | :--- |
| 通知滤盒 | `com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY` | `.MainActivity` | —（预填搜索请用 Deeplink） |
| 收纳夹 | `com.slideindex.app.action.OPEN_STASH_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| 剪贴板 | `com.slideindex.app.action.OPEN_CLIPBOARD_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| 搜索面板 | `com.slideindex.app.action.OPEN_SEARCH_PANEL` | `.service.SearchPanelTrampolineActivity` | `q` |
| 切换手势开关 | `com.slideindex.app.action.TOGGLE_GESTURE` | `.service.ToggleGestureTrampolineActivity` | — |
| Shell 命令面板 | `com.slideindex.app.action.OPEN_SHELL_PANEL` | `.service.ShellCommandPanelTrampolineActivity` | —（未对外导出，仅供应用内快捷方式） |

示例：

```bash
# 打开搜索面板并预填关键词
adb shell am start -a com.slideindex.app.action.OPEN_SEARCH_PANEL \
  -n com.slideindex.app/.service.SearchPanelTrampolineActivity \
  --es q "天气"

# 切换边缘手势总开关
adb shell am start -a com.slideindex.app.action.TOGGLE_GESTURE \
  -n com.slideindex.app/.service.ToggleGestureTrampolineActivity
```

---

## 🛠️ 技术栈

| 层次 | 核心技术 / 依赖组件 |
| :--- | :--- |
| **核心语言** | Kotlin 2.4.0 + C++17（NDK / CMake） |
| **UI 体系与规范** | Miuix UI (KMP 0.9.4 / HyperOS 风格) + Jetpack Compose (BOM 2026.07.01) |
| **色彩与动效** | MaterialKolor 5.0.0（壁纸动态取色） + Haze 1.7.2（高斯模糊） |
| **架构与 DI** | MVVM + UDF（单向数据流） + Dagger Hilt 2.60.1 |
| **异步与状态** | Kotlin Coroutines 1.11.0 + StateFlow / SharedFlow + DataStore Preferences 1.2.1 |
| **OCR 与 AI** | ML Kit Text Recognition 16.0.1 + Tesseract4Android 4.9.0 + PaddleOCR (ONNX Runtime 1.28.0 + OpenCV 4.12.0) |
| **NLP 与分词** | CppJieba（JNI Native 分词引擎） + ML Kit Translate / Language ID |
| **系统特权扩展** | Shizuku API 13.1.5 + LibSuperuser 1.1.1 + HiddenApiBypass 6.1 + LibXposed API 102.0.0 |
| **网络与解析** | OkHttp 5.4.0 + ZXing Core 3.5.4 + kotlinx.serialization 1.11.0 + Markdown Renderer M3 |

---

## 📐 架构设计

应用采用 **多模块（Multi-Module）分层架构**，遵循 **MVVM + 单向数据流（UDF）** 规范：

```
                              ┌─────────────────────────────┐
                              │          :app (顶层装配)     │
                              └──────────────┬──────────────┘
                                             │
                      ┌──────────────────────┼──────────────────────┐
                      ▼                      ▼                      ▼
           ┌──────────────────────┐┌──────────────────────┐┌──────────────────────┐
           │   :feature:settings  ││     :feature:otp     ││   :feature:shake     │
           │   :feature:apps      ││ :feature:notification││   :feature:message   │
           └──────────┬───────────┘└──────────┬───────────┘└──────────┬───────────┘
                      │                       │                       │
                      └──────────────────────┼───────────────────────┘
                                             ▼
           ┌──────────────────────────────────────────────────────────────────────┐
           │ :core:gesture       :core:ocr          :core:translate  :core:autofill│
           │ :core:overlay-layout:core:native-engine:core:monitoring :core:common │
           └──────────────────────────────────┬───────────────────────────────────┘
                                             ▼
                                  ┌───────────────────────┐
                                  │   :vendor:ppocr-sdk   │
                                  └───────────────────────┘
```

1. **服务协调与 Overlay 渲染**：`SlideIndexAccessibilityService` 负责拦截全局手势与系统事件派发，悬浮层由 `OverlayLayout` 与各个独立的 WindowManager 统一管理。
2. **多引擎动态加载机制**：OCR、翻译与分词引擎支持 Full 内置包与 Lite 轻量包模式，Native `.so` 与模型资源支持运行时按需提取解压。
3. **系统级特权通道**：Shizuku IPC（无 Root 提权）、Root 降级执行器（LibSu）与 LSPosed 模块互补协同，实现无缝的后台剪贴板监听与进程级管控。

---

## 📂 项目结构

```
.
├── app/                                 # 宿主应用：全局 DI 装配、JNI C++ 桥接与主页面
│   └── src/main/
│       ├── cpp/                         # C++17 JNI 源码（CppJieba 分词桥接）
│       └── java/com/slideindex/app/
│           ├── activity/                # 顶层 Activity 与透明交互层
│           ├── backtap/                 # 背面手势
│           ├── clipboard/               # 剪贴板历史与 Shizuku 后台监听服务
│           ├── gesture/                 # 核心手势服务与悬浮触发条
│           ├── overlay/                 # 系统级 Overlay 窗口管理器
│           ├── search/                  # 文字与以图搜图聚合引擎
│           ├── shell/                   # Shell 命令执行器
│           ├── ui/                      # Compose 界面、主题与动态色彩
│           └── xposed/                  # LSPosed 模块入口与 Hook 逻辑
├── core/                                # 核心基础库与引擎
│   ├── autofill/                        # OTP 自动填充框架
│   ├── common/                          # 基础扩展、公共模型与通用工具
│   ├── gesture/                         # 手势识别算法与事件总线
│   ├── monitoring/                      # 性能追踪与内存监控
│   ├── native-engine/                   # 离线 Native 引擎包分发与解压管理
│   ├── notification/                    # 系统通知拦截与处理
│   ├── ocr/                             # 多引擎 OCR 调度器（ML Kit / Tesseract / ONNX）
│   ├── overlay-layout/                  # 悬浮窗布局渲染核心
│   └── translate/                       # 多语种即时翻译与语言检测
├── feature/                             # 业务功能模块
│   ├── apps/                            # 应用扫描、图标缓存与启动器
│   ├── message/                         # 悬浮通知提醒与弹幕渲染
│   ├── notification/                    # 通知历史归档与过滤
│   ├── otp/                             # 验证码正则提取与统计面板
│   ├── settings/                        # 设置项、偏好备份与 DataStore
│   └── shake/                           # 传感器摇晃与扣桌静音算法
├── vendor/
│   └── ppocr-sdk/                       # PaddleOCR SDK 封装与 ONNX 绑定
├── gradle/libs.versions.toml             # 统一依赖与版本管理
└── RELEASE_NOTES.md                     # 版本更新日志
```

---

## 🚀 编译与构建

### 环境要求
- **JDK 21**
- **Android Studio**（建议 Ladybug 或更新版本）
- **Android SDK**（API 37 `compileSdk`）与 **NDK 28+**

### 构建命令
```bash
# 克隆仓库
git clone https://github.com/qpst4/cebian.git
cd cebian

# 编译 Full Debug 包（含内置离线引擎）
./gradlew assembleFullDebug

# 编译 Lite Release 包（轻量包）
./gradlew assembleLiteRelease
```

---

## 💬 社区与交流

欢迎加入社区交流群或参与讨论，提出新功能建议与 Bug 反馈：

<div align="center">

[![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/cebian/discussions)
[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues%20反馈-EA4AAA?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/cebian/issues)
[![QQ Group](https://img.shields.io/badge/QQ%20交流群-点击加入-12B7F5?style=for-the-badge&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/jHZTMmiZ9K)

</div>

---

## 💖 赞赏与支持

如果您觉得「边栏（Cebian）」对您的日常使用有所帮助，欢迎赞赏请作者喝杯咖啡 ☕，您的支持是项目持续迭代与积极适配的最大动力！

<div align="center">
  <img src="art/sponsor.png" width="220" alt="微信赞赏码" />
</div>

---

## 📜 许可证

本项目采用 [GNU Affero General Public License v3.0](LICENSE)（AGPLv3）开源。

---

## 🤝 致谢

在开发过程中参考或借鉴了以下优秀开源项目，特此鸣谢（详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)）：

- [SideGesture](https://github.com/aaronzzx/SideGesture) — 边缘手势与 overlay 架构参考
- [EdgeGesture](https://github.com/evilgodxu/EdgeGesture) — 扩展面板、闹钟提醒、屏幕翻译、全局复制与背面手势参考
- [EdgeX](https://github.com/oxohang/EdgeX) & [FanFreeform](https://github.com/oxohang/FanFreeform) — 蜂窝启动器与应用冻结室（Freezer）架构参考
- [ClipboardListener](https://github.com/aa2013/ClipboardListener) & [ClipShare](https://github.com/aa2013/ClipShare) — Android 10+ 剪贴板后台监听架构
- [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) — 未导出 Activity 启动方案
- [Circle To Search](https://github.com/AKS-Labs/CircleToSearch) — 以图搜图多引擎集成策略
- [Nova Text](https://github.com/CashewTeam/BigBang_NovaText) — 悬浮球取词与点词交互思路
- [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) — OTP 短信 Hook 与验证码提取规则
- [Miuix](https://github.com/compose-miuix-ui/miuix) — 优美的 Material & Miuix 风格 Compose 组件库

---

## 📝 更新日志

完整历史版本日志请参见 [RELEASE_NOTES.md](RELEASE_NOTES.md) 与 [CHANGELOG.md](CHANGELOG.md)。

