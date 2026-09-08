# Weblate 初次配置（维护者）

本文档说明如何在 [hosted.weblate.org](https://hosted.weblate.org) 为 **Cebian（边栏）** 开通社区翻译。贡献者无需阅读本文。

## 前置条件

- GitHub 仓库：`https://github.com/qpst4/cebian`
- 仓库根目录已包含 [`weblate.yml`](../weblate.yml)
- 源语言为 **简体中文**（`app/src/main/res/values/strings.xml`）

## 步骤

### 1. 申请托管项目

1. 使用 GitHub 登录 [hosted.weblate.org](https://hosted.weblate.org)
2. **Manage** → **Create project**（开源项目可申请 Libre hosting）
3. 项目信息建议：
   - **Name**：Cebian
   - **Slug**：`cebian`（与 `weblate.yml` 中 `project` 一致）
   - **Website**：`https://github.com/qpst4/cebian`
   - **Source language**：Chinese (Simplified) / `zh_Hans`

### 2. 导入组件

**方式 A（推荐）：从 weblate.yml 导入**

1. 在项目中 **Add new translation component**
2. Repository：`https://github.com/qpst4/cebian.git`，Branch：`main`
3. 若界面提供 **Import from weblate.yml**，选用根目录配置

**方式 B：手动创建 App strings 组件**

| 字段 | 值 |
|------|-----|
| Name | App strings |
| Slug | `app-strings` |
| File format | Android String Resource |
| File mask | `app/src/main/res/values-*/strings.xml` |
| Monolingual base language file | `app/src/main/res/values/strings.xml` |
| Source language | Chinese (Simplified) |

当前 **仅** 覆盖主应用 `app` 模块字符串。`core/*`、`feature/*` 与 `preset_shortcuts.json` 未纳入；需要时可后续增加 linked component。

### 3. 版本控制与合并

在组件 **Settings → Version control** 中建议：

| 选项 | 建议 |
|------|------|
| **Push on commit** | 开启 |
| **Merge style** | Rebase 或 Merge |
| **Repository push branch** | 独立 `weblate` 分支 + **Pull Request**（勿直接推 `main`） |

推荐工作流：Weblate → `weblate` 分支 → GitHub PR → review → 合并 `main` → CI `lintLiteDebug`。

### 4. GitHub 集成

**Settings → Integrations** 连接 GitHub：

- 仓库有新字符串时自动 pull
- 翻译提交后自动开 PR

### 5. 验证

1. 打开 https://hosted.weblate.org/projects/cebian/
2. 选择 **English** / **Japanese**，确认加载 `values-en`、`values-ja`
3. 试译一条并提交，检查 GitHub 是否出现 PR
4. README 徽章：`https://hosted.weblate.org/widget/cebian/app-strings/svg-badge.svg`

## 语言代码对照

| 目录 | 语言 |
|------|------|
| `values/` | 简体中文（源） |
| `values-en/` | English |
| `values-ja/` | Japanese |

## 故障排查

- **源语言冲突**：monolingual base 必须是 `values/strings.xml`，源语言设为简体中文。
- **Lint 失败**：占位符 `%1$s` / `%1$d` 类型须与源字符串一致。
- **缺 key**：Repository → **Update** 同步最新 `strings.xml`。

## 贡献者文档

- [CONTRIBUTING.md](../CONTRIBUTING.md)
- [contributing_zh.md](contributing_zh.md)
- [contributing_ja.md](contributing_ja.md)
