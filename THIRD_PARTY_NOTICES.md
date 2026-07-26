# Third-Party Notices

Cebian（`com.slideindex.app`）在 [GNU Affero General Public License v3.0](LICENSE) 下发布。
以下开源项目的代码或思路被参考、移植或复用，特此致谢。

完整文件级映射见 [docs/ATTRIBUTIONS.md](docs/ATTRIBUTIONS.md)。

---

## SideGesture (gulugulu / SideGesture)

- **Copyright:** aaronzzx and contributors
- **License:** [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- **Source:** https://github.com/aaronzzx/SideGesture (formerly https://github.com/aaronzzx/gulugulu)
- **Used in:** 边缘手势触发动画（`GestureAnimation*`）、快捷方式解析（`ShortcutUtils`）、无障碍覆盖层窗口类型与系统手势排除区域、屏幕尺寸度量、顶部触钮几何与窗口布局等
- **Modifications:** 以 Kotlin 重写并集成至 Cebian；架构拆分为 `EdgeOverlayHost`、`OverlayWindowTypes` 等模块；v1.6 顶部触钮按 Bottom 镜像适配

---

## Clipboard Whitelist

- **Copyright:** Tehcneko
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/Tehcneko/ClipboardWhitelist
- **Used in:** LSPosed 剪贴板白名单 Hook（`ClipboardWhitelistHook`）及 App 侧同步桥接（`ClipboardWhitelistBridge`）
- **Modifications:** 集成至 SlideIndex Xposed 模块；prefs 命名修正；增加 Compose 管理界面与设置备份联动

---

## Circle To Search

- **Copyright:** AKS-Labs and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/AKS-Labs/CircleToSearch
- **Used in:** 以图搜图图床上传（Litterbox/Catbox）、Google/Yandex/TinEye hosted-URL 构建、多引擎 WebView 预加载面板
- **Modifications:** 拆分为 `ImageHostUploader` / `ImageSearchUrlBuilder` 等模块；扩展 IQDB、SauceNAO、ASCII2D 等 direct-POST 引擎；集成至悬浮球取词面板

---

## Nova Text (BigBang)

- **Copyright:** CashewTeam and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/CashewTeam/BigBang_NovaText
- **Used in:** 悬浮球取词、无障碍文本提取与 OCR 降级等交互思路（无直接代码移植）
- **Note:** Cebian 悬浮球主实现参考 GestureEVO / FooView 路线；与 Nova Text 属同类产品思路借鉴

---

## 许可证全文

- Apache License 2.0：`app/src/main/assets/licenses/Apache-2.0.txt`
- GNU General Public License v3.0：`app/src/main/assets/licenses/GPL-3.0.txt`
