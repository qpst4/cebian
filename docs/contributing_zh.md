# 参与贡献 Cebian（边栏）

感谢你愿意帮助改进 **边栏（Cebian）**！本文说明如何贡献翻译与代码。

**语言：** [English](../CONTRIBUTING.md) · **简体中文** · [日本語](contributing_ja.md)

---

## 翻译 App 界面（推荐 Weblate）

贡献 **应用内 UI 文案** 最简单的方式是使用 [Weblate](https://hosted.weblate.org/engage/cebian/)：

[![翻译状态](https://hosted.weblate.org/widget/cebian/app-strings/svg-badge.svg)](https://hosted.weblate.org/engage/cebian/)

1. 打开 Weblate 项目页（无需会 Git）。
2. 选择语言（如 English、日本語，或申请新语言）。
3. 在网页上逐条翻译。
4. 提交后由维护者合并 Weblate 发起的 Pull Request。

### Weblate 覆盖范围

| 包含 | 暂不包含 |
|------|----------|
| `app/src/main/res/values*/strings.xml` | `preset_shortcuts.json`（预设快捷方式名称） |
| 主应用界面 | README（请直接 GitHub PR） |
| | `core/*`、`feature/*` 模块字符串（后续可能增加组件） |

### 翻译注意事项

- **不要修改** string 的 `name` 属性，只改 `<string>` 内的正文。
- **占位符必须保留**：`%1$s`、`%1$d`、`%2$s` 等，类型不能改错。
- 保留原文中的 **HTML/XML 实体** 和 `\n`。
- **品牌名一般不译**：Cebian、Shizuku、LSPosed、Miuix 等（若源文已本地化则跟随源文）。
- 界面文案宜简洁自然，避免生硬直译。

### 通过 Pull Request 翻译（备选）

```text
app/src/main/res/values/strings.xml       # 源语言（中文）
app/src/main/res/values-en/strings.xml    # 英文
app/src/main/res/values-ja/strings.xml    # 日文
app/src/main/res/values-xx/strings.xml    # 新语言：可复制 values-en 再翻译
```

1. Fork 仓库并建分支。
2. 编辑或新建对应 `values-xx/strings.xml`。
3. 尽量与源文件 key 对齐。
4. 提 PR；CI 会跑 `lintLiteDebug`，注意修复格式类 lint。

---

## 贡献代码

1. Fork 并克隆：`git clone https://github.com/qpst4/cebian.git`
2. 从 `main` 创建功能分支。
3. 改动尽量聚焦；风格与现有 Kotlin / Compose 一致。
4. 本地执行：`.\gradlew.bat compileLiteDebugKotlin`（改 UI/字符串建议跑 `lintLiteDebug`）。
5. 提 PR，写清改动与测试说明。

### 代码中的字符串

- **Compose：** 使用 `stringResource(R.string.xxx)`，不要在 `@Composable` 里用 `context.getString()`。
- **非 Compose：** 使用 `context.getString(R.string.xxx)`。
- 新增 key 时尽量同步 **values / values-en / values-ja**。

---

## 社区

- [GitHub Issues](https://github.com/qpst4/cebian/issues) — Bug 与功能建议
- [GitHub Discussions](https://github.com/qpst4/cebian/discussions) — 讨论与提问
- QQ 群：**1042783385**

---

## 许可证

贡献即表示你同意在 [AGPL-3.0](../LICENSE) 许可下发布你的贡献。
