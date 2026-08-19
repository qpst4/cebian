# 发版流程（Release Checklist）

> **给 AI 工具：** 用户要求发版时，请严格按本文档执行。CI 会产出 **full**（内置引擎）与 **lite**（轻量）两个 APK；引擎 zip 仅在 native 变更时单独上传。

远程仓库：`qpst4/cebian`（本地目录名可能不同）。

---

## 产物说明

| 产物 | 文件名 | 用途 |
|------|--------|------|
| **Full 胖包** | `cebian-{版本}-full.apk` | GitHub Release；新用户装完即用（内置约 49 MB 引擎） |
| **Lite 轻量包** | `cebian-{版本}-lite.apk` | **应用内更新**默认指向此包（~6 MB，不含内置引擎） |
| **引擎 zip** | `*-engine-arm64-vN.zip` | **仅引擎变更时**上传；纯 App 发版不必每次附带 |

`applicationId` 始终为 `com.slideindex.app`，full / lite 可互相覆盖安装。

---

## 不必做的事

| 项目 | 说明 |
|------|------|
| 手动打引擎 zip | `assembleFullRelease` 会自动执行 `packageNativeEnginePacks` 并打入 full APK |
| 每次 Release 都上传引擎 zip | 仅 native 引擎变更时需要；大多数版本只上传两个 APK |
| 改 `native_engine_packs.json` | 仅引擎变更时需要 |
| 提交 `.fv_*`、`.tools/`、`MessageThemeCatalog.kt`（若仅有本地改动） | 临时/惯例不提交文件 |

---

### 0. 收集当版完整日志（必须）

**禁止仅盲目拷贝 `CHANGELOG.md` 中现有的 `[Unreleased]` 暂存文字。** 必须通过 `git log` 与文件 Diff 主动审计自上次 Tag 发版以来的所有真实变更：

```bash
# 查出上一个发版 Tag
last_tag=$(git describe --tags --abbrev=0)

# 1. 输出自上次发版以来的所有 Commit 变更
git log ${last_tag}..HEAD --oneline

# 2. 检查新增文件与配置类（防止遗漏全新 Feature/模式）
git diff ${last_tag}..HEAD --name-only --diff-filter=A
```

**审计铁律：** 
- 凡在两次 Release 之间**新增了 ViewModel/Enum/配置类/组件**（例如 `ClipboardFloatListStyle`），代表引入了全新的功能或模式，**100% 必须作为 `Added` 新功能列出**，绝不可仅作为 Bug 修复简写。
- 汇总审计所有 Commit 与新增文件后，归纳整理出当版完整的 Added / Changed / Fixed 清单，再写入 `CHANGELOG.md` 的新版本章节中。

### 1. 升版本号

同步修改：

- `app/build.gradle.kts` → `versionCode`、`versionName`
- `update.json` → `version`、`versionCode`、`apkUrl`、`notes`（**不要**把 `apkSize: 0` 推到 `main`；`notes` 每条一行，用 `\n` 分隔，**不要**只用中文分号拼成一行）
- `CHANGELOG.md` → 新版本条目
- `README.md` → 顶部版本行；若功能有变，同步功能概览与「设置备份」章节
- `RELEASE_NOTES.md` → 当前版本亮点（可选，大版本或对外说明时更新）

`update.json` 的 `apkUrl` 默认指向 **lite**：

```text
https://github.com/qpst4/cebian/releases/download/v{版本}/cebian-{版本}-lite.apk
```

### 2. 提交并打 tag

```bash
git add app/build.gradle.kts update.json CHANGELOG.md README.md
git commit -m "{版本号}：{简述}"
git tag -a v{版本号} -m "v{版本号}"
git push origin main
git push origin v{版本号}
```

Commit 备注格式：`1.7.0：边角轮盘手势`（版本号 + 中文冒号 + 简述）。

**注意：** 仅改 `*.md` 或 `update.json` 的提交不会触发 CI（`paths-ignore`）。版本号必须在 `build.gradle.kts` 中变更，CI 才会打出对应版本的 APK。

### 3. 等待 CI 完成

```bash
gh run list --repo qpst4/cebian --limit 1
gh run watch <run-id> --repo qpst4/cebian --exit-status
```

需 GitHub Secrets 已配置（见 `README.md` CI 章节），否则无签名 `release-apk-full` / `release-apk-lite` artifact。

CI 在签名 `assembleFullRelease assembleLiteRelease` 后会自动运行 `scripts/verify-release-apk.sh all`，校验两个 APK 的版本号与引擎打包策略。

### 4. 创建 GitHub Release

**禁止**使用整份 `CHANGELOG.md` 作为 Release 正文（会带上 `[Unreleased]` 与旧版本历史）。

```powershell
gh run download <run-id> --repo qpst4/cebian -n release-apk-full -D release-apk-full
gh run download <run-id> --repo qpst4/cebian -n release-apk-lite -D release-apk-lite
# 上传前再次校验（Windows / macOS / Linux）
.\scripts\verify-release-apk.ps1
bash scripts/verify-release-apk.sh all

# 仅截取当版 Changelog 段落
.\scripts\extract-changelog-section.ps1 -Version "{版本号}" -OutFile release-notes-{版本号}.md
gh release create v{版本号} --repo qpst4/cebian `
  --title "v{版本号}" `
  --notes-file release-notes-{版本号}.md `
  release-apk-full/cebian-{版本}-full.apk `
  release-apk-lite/cebian-{版本}-lite.apk
```

若当次有引擎变更，额外附上对应 `*-engine-arm64-vN.zip`，并更新 `native_engine_packs.json`。

（`--notes` 也可手写当版 Highlights，不必整份 CHANGELOG。）

### 5. 修正 `update.json` 的 APK 大小（必须）

`apkSize` 必须等于 **lite APK** 的**精确字节数**，否则应用内下载后无法安装（`file.length() == apkSize`）。

**必须使用 `scripts/update-release-manifest.ps1` 更新 manifest 并 purge jsDelivr 缓存**（默认 `-ApkFileName cebian-{版本}-lite.apk`）。

```powershell
$size = (Get-Item release-apk-lite/cebian-{版本}-lite.apk).Length
# 推荐：从 CHANGELOG 当版条目生成多行 notes（App 弹窗按行显示）
.\scripts\update-release-manifest.ps1 `
  -Version "{版本号}" `
  -VersionCode {整数} `
  -ApkSize $size `
  -FromChangelog `
  -MaxChangelogItems 8 `
  -VerifyRemote
```

也可用 `-NotesFile notes.txt`（每行一条）或 `-Notes`（脚本会自动把 `；` 转为换行）。

然后提交推送：

```bash
git add update.json
git commit -m "{版本号}：修正 update.json APK 大小"
git push origin main
```

**验证：** 应用从以下两源拉取 manifest（同源 `main` 分支；同版本时优先 `apkSize > 0`）：

- `https://raw.githubusercontent.com/qpst4/cebian/main/update.json`
- `https://cdn.jsdelivr.net/gh/qpst4/cebian@main/update.json`

推送后执行 `.\scripts\update-release-manifest.ps1 ... -VerifyRemote` 确认两源 `apkSize` 一致。脚本会自动请求 `purge.jsdelivr.net`。

---

## 发版完成检查

- [ ] GitHub Release 页可下载 `cebian-{版本}-full.apk` 与 `cebian-{版本}-lite.apk`
- [ ] GitHub Release 正文**仅含当版** Changelog（已用 `extract-changelog-section.ps1`）
- [ ] `update.json` 的 `notes` 为多行（`\n` 分隔），非单行分号拼接
- [ ] `verify-release-apk` 校验通过（CI 自动；本地上传前可手动跑 `all`）
- [ ] `update.json` 中 `version` / `versionCode` / `apkUrl`（lite）/ `apkSize`（lite）均正确
- [ ] 已运行 `update-release-manifest.ps1`（`apkSize` 与 jsDelivr purge）
- [ ] 引擎变更时已上传 zip 并更新 `native_engine_packs.json`
- [ ] 未误提交 `.fv_*` 等临时文件

---

## 相关文件

| 文件 | 用途 |
|------|------|
| `update.json` | 应用内检查更新清单（默认 lite APK） |
| `scripts/extract-changelog-section.ps1` | 截取当版 CHANGELOG 段落，供 GitHub Release |
| `scripts/update-release-manifest.ps1` | 生成 `update.json` + purge jsDelivr |
| `scripts/verify-release-apk.sh` | CI / Linux 校验 full + lite APK |
| `scripts/verify-release-apk.ps1` | Windows 本地上传前校验 full + lite APK |
| `scripts/package-native-engine-packs.ps1` | **仅**单独发布引擎 zip 时用 |
| `.github/workflows/ci.yml` | push 后构建签名 full/lite APK 并上传 artifact |
| `RELEASE_NOTES.md` | 面向用户的产品说明（非发版操作手册） |
