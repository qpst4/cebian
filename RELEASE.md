# 发版流程（Release Checklist）

> **给 AI 工具：** 用户要求发版时，请严格按本文档执行。引擎 zip 由 Gradle 自动打进 APK，**不要**手动打包或上传引擎 zip。

远程仓库：`qpst4/cebian`（本地目录名可能不同）。

---

## 不必做的事

| 项目 | 说明 |
|------|------|
| 手动打引擎 zip | `assembleRelease` 会自动执行 `packageNativeEnginePacks`，CI 产出约 49MB 胖包 |
| 上传 `ocr-engine` / `translate-engine` / `segmentation-engine` zip 到 Release | 胖包已内置，仅 `cebian-{版本}.apk` 需上传 |
| 改 `native_engine_packs.json` | 瘦包时代在线下载用；胖包发版无需更新 |
| 提交 `.fv_*`、`.tools/`、`MessageThemeCatalog.kt`（若仅有本地改动） | 临时/惯例不提交文件 |

---

## 发版步骤

### 1. 升版本号

同步修改：

- `app/build.gradle.kts` → `versionCode`、`versionName`
- `update.json` → `version`、`versionCode`、`apkUrl`、`notes`（**不要**把 `apkSize: 0` 推到 `main`；`notes` 每条一行，用 `\n` 分隔，**不要**只用中文分号拼成一行）
- `CHANGELOG.md` → 新版本条目
- `README.md` → 顶部版本行；若功能有变，同步功能概览与「设置备份」章节
- `RELEASE_NOTES.md` → 当前版本亮点（可选，大版本或对外说明时更新）

`apkUrl` 格式：

```text
https://github.com/qpst4/cebian/releases/download/v{版本}/cebian-{版本}.apk
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

需 GitHub Secrets 已配置（见 `README.md` CI 章节），否则无签名 `release-apk` artifact。

CI 在签名 `assembleRelease` 后会自动运行 `scripts/verify-release-apk.sh`，校验 APK 内 `versionCode` / `versionName` 与 `app/build.gradle.kts` 一致；不一致则构建失败。

### 4. 创建 GitHub Release

**禁止**使用整份 `CHANGELOG.md` 作为 Release 正文（会带上 `[Unreleased]` 与旧版本历史）。

```powershell
gh run download <run-id> --repo qpst4/cebian -n release-apk -D release-apk
# 上传前再次校验（Windows）
.\scripts\verify-release-apk.ps1 -ApkPath release-apk/cebian-{版本}.apk
# macOS / Linux
bash scripts/verify-release-apk.sh release-apk/cebian-{版本}.apk

# 仅截取当版 Changelog 段落
.\scripts\extract-changelog-section.ps1 -Version "{版本号}" -OutFile release-notes-{版本号}.md
gh release create v{版本号} --repo qpst4/cebian `
  --title "v{版本号}" `
  --notes-file release-notes-{版本号}.md `
  release-apk/cebian-{版本}.apk
```

（`--notes` 也可手写当版 Highlights，不必整份 CHANGELOG。）

### 5. 修正 `update.json` 的 APK 大小（必须）

`apkSize` 必须等于 CI 产物**精确字节数**，否则应用内下载后无法安装（`file.length() == apkSize`）。

**必须使用 `scripts/update-release-manifest.ps1` 更新 manifest 并 purge jsDelivr 缓存**（不要手写 `update.json` 的 `apkSize` 或单独 purge）。

```powershell
$size = (Get-Item release-apk/cebian-{版本}.apk).Length
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

- [ ] GitHub Release 页可下载 `cebian-{版本}.apk`
- [ ] GitHub Release 正文**仅含当版** Changelog（已用 `extract-changelog-section.ps1`）
- [ ] `update.json` 的 `notes` 为多行（`\n` 分隔），非单行分号拼接
- [ ] `verify-release-apk` 校验通过（CI 自动；本地上传前可手动跑脚本）
- [ ] `update.json` 中 `version` / `versionCode` / `apkUrl` / `apkSize` 均正确
- [ ] 已运行 `update-release-manifest.ps1`（`apkSize` 与 jsDelivr purge）
- [ ] 未误提交 `.fv_*` 等临时文件

---

## 相关文件

| 文件 | 用途 |
|------|------|
| `update.json` | 应用内检查更新清单（仓库根目录） |
| `scripts/extract-changelog-section.ps1` | 截取当版 CHANGELOG 段落，供 GitHub Release |
| `scripts/update-release-manifest.ps1` | 生成 `update.json` + purge jsDelivr |
| `scripts/verify-release-apk.sh` | CI / Linux 校验 APK 内版本号 |
| `scripts/verify-release-apk.ps1` | Windows 本地上传前校验 APK 内版本号 |
| `scripts/package-native-engine-packs.ps1` | **仅**单独发布引擎 zip 时用；胖包发版不需要 |
| `.github/workflows/ci.yml` | push 后构建签名 APK 并上传 artifact |
| `RELEASE_NOTES.md` | 面向用户的产品说明（非发版操作手册） |
