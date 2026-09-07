# IzzyOnDroid / F-Droid 提交收录指南

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) 是全球最大的第三方 F-Droid 自由开源软件源，全球数十万 FOSS 爱好者通过 F-Droid / Droid-ify / Neo Store 客户端从这里发现和更新开源应用。

---

### 一、 提交收录的好处
1. **自动构建与发版同步**：只要你在 GitHub 发 Release 并上传 APK，IzzyOnDroid 每天自动拉取更新，无需手动维护。
2. **全球曝光**：应用会出现在 IzzyOnDroid 网页版、每日新收录 RSS、以及数十万 F-Droid 客户端的新应用推荐中。
3. **GitHub 反向引流**：应用卡片的主页链接直连 `https://github.com/qpst4/cebian`，是海外自然 Star 最稳定的源头。

---

### 二、 提交步骤（只需 3 分钟提一个 Issue）

1. **访问 IzzyOnDroid 官方收录仓库**：
   👉 https://gitlab.com/IzzyOnDroid/repo/-/issues

2. **点击「New issue」创建收录申请**：
   - Issue 模板选择：`Inclusion Request`
   - Title：`[Inclusion Request] Cebian (com.slideindex.app)`

3. **填入以下预制申请内容（直接复制）**：

```markdown
### Repository URL
https://github.com/qpst4/cebian

### Package Name
com.slideindex.app

### License
AGPL-3.0

### Description
All-in-one open-source Android edge gesture & single-handed productivity suite. An alternative to Samsung OHO+, Quick Cursor reachability pointer, and FooView floating ball with local offline OCR, reverse image search aggregator, and Shizuku integration.

### APK Release Asset Pattern
`cebian-*-lite.apk` (or `cebian-*-full.apk`)

### Trackers & Non-Free Dependencies
- None. 100% open-source, no analytics, no ads, no trackers.
```

4. **提交等待审核**：
   - 维护者（Izzy）通常在 1~3 天内审核通过。
   - 通过后，Cebian 就会正式进入全球 F-Droid 生态。
