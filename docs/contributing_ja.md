# Cebian（边栏）への貢献

**Cebian（边栏）** の改善にご協力いただきありがとうございます。翻訳とコード貢献の手順です。

**言語：** [English](../CONTRIBUTING.md) · [简体中文](contributing_zh.md) · **日本語**

---

## アプリ UI の翻訳（Weblate 推奨）

**アプリ内 UI テキスト** の貢献は [Weblate](https://hosted.weblate.org/engage/cebian/) が最も簡単です：

[![翻訳状況](https://hosted.weblate.org/widget/cebian/app-strings/svg-badge.svg)](https://hosted.weblate.org/engage/cebian/)

1. Weblate のプロジェクトページを開く（Git の知識不要）。
2. 言語を選ぶ（English、日本語、または新規言語のリクエスト）。
3. Web UI で文字列を翻訳。
4. 送信後、メンテナーが Weblate からの Pull Request をマージ。

### Weblate の対象範囲

| 含む | 現時点で含まない |
|------|------------------|
| `app/src/main/res/values*/strings.xml` | `preset_shortcuts.json`（プリセット名） |
| メインアプリ UI | README（GitHub PR で） |
| | `core/*` / `feature/*` モジュール（将来コンポーネント追加の可能性） |

### 翻訳ルール

- string の **`name` は変更しない**。`<string>` 内の本文のみ翻訳。
- **プレースホルダを維持**：`%1$s`、`%1$d` など、型を間違えない。
- **HTML/XML エンティティ** と `\n` を保持。
- **固有名詞**（Cebian、Shizuku、LSPosed、Miuix 等）は原則そのまま（ソースが既にローカライズされていればそれに従う）。
- UI 向けに自然で簡潔な表現を優先。

### Pull Request で翻訳（代替）

```text
app/src/main/res/values/strings.xml       # ソース（中国語）
app/src/main/res/values-en/strings.xml    # 英語
app/src/main/res/values-ja/strings.xml    # 日本語
app/src/main/res/values-xx/strings.xml    # 新言語：values-en をコピーして翻訳
```

1. リポジトリを Fork してブランチ作成。
2. 該当 `values-xx/strings.xml` を編集または新規作成。
3. ソースの key と揃える。
4. PR を作成。CI の `lintLiteDebug` に注意。

---

## コード貢献

1. Fork & clone: `git clone https://github.com/qpst4/cebian.git`
2. `main` から機能ブランチを作成。
3. 変更は小さく、既存の Kotlin / Compose スタイルに合わせる。
4. ローカル: `.\gradlew.bat compileLiteDebugKotlin`（UI/文字列変更時は `lintLiteDebug` 推奨）。
5. PR に変更内容とテスト手順を記載。

### コード内の文字列

- **Compose:** `stringResource(R.string.xxx)` を使用（`@Composable` 内で `context.getString()` は不可）。
- **非 Compose:** `context.getString(R.string.xxx)`。
- 新規 key は **values / values-en / values-ja** へ可能な限り追加。

---

## コミュニティ

- [GitHub Issues](https://github.com/qpst4/cebian/issues)
- [GitHub Discussions](https://github.com/qpst4/cebian/discussions)
- QQ グループ：**1042783385**

---

## ライセンス

貢献は [AGPL-3.0](../LICENSE) と同じライセンスで公開されることに同意したものとみなします。
