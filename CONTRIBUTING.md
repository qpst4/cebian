# Contributing to Cebian

Thank you for helping improve **Cebian (边栏)**! This document covers how to contribute translations and code.

**Languages:** [English](CONTRIBUTING.md) · [简体中文](docs/contributing_zh.md) · [日本語](docs/contributing_ja.md)

---

## Translating the app (Weblate — recommended)

The easiest way to help with **in-app UI text** is [Weblate](https://hosted.weblate.org/engage/cebian/):

[![Translation status](https://hosted.weblate.org/widget/cebian/app-strings/svg-badge.svg)](https://hosted.weblate.org/engage/cebian/)

1. Open the project on Weblate (no Git knowledge required).
2. Pick a language (e.g. English, 日本語, or request a new one).
3. Translate strings in the web UI.
4. Submit; maintainers merge Weblate pull requests into `main`.

### What Weblate covers

| Included | Not included (yet) |
|----------|-------------------|
| `app/src/main/res/values*/strings.xml` | `preset_shortcuts.json` (preset shortcut names) |
| Main app UI | README markdown files (use GitHub PR) |
| | `core/*` / `feature/*` module strings (may be added as extra components later) |

### Translation rules

- **Do not rename** string keys (`name="..."`). Only change the text inside `<string>`.
- **Keep placeholders** exactly as in the source: `%1$s`, `%1$d`, `%2$s`, etc.
- **Keep HTML/XML entities** and `\n` where present.
- **Do not translate** brand names: Cebian, Shizuku, LSPosed, Miuix, OCR engine names unless already localized in the source.
- Prefer natural, concise UI wording over literal word-for-word translation.

### Translating via pull request (alternative)

If you prefer Git:

```text
app/src/main/res/values/strings.xml       # Source (Chinese)
app/src/main/res/values-en/strings.xml    # English
app/src/main/res/values-ja/strings.xml    # Japanese
app/src/main/res/values-xx/strings.xml    # New locale: copy values-en, translate
```

1. Fork the repo and create a branch.
2. Edit or add the appropriate `values-xx/strings.xml`.
3. Ensure every `name` in the source file exists in your locale (or only add missing keys).
4. Open a PR; CI runs `lintLiteDebug` — fix any `StringFormatMatches` / format errors.

---

## Contributing code

1. Fork and clone: `git clone https://github.com/qpst4/cebian.git`
2. Create a feature branch from `main`.
3. Make focused changes; match existing Kotlin / Compose style.
4. Run locally: `.\gradlew.bat compileLiteDebugKotlin` (or `lintLiteDebug` for UI/string changes).
5. Open a PR with a clear description and test notes.

### String resources in code

- **Compose:** use `stringResource(R.string.xxx)`, not `context.getString()` inside `@Composable`.
- **Non-Compose:** use `context.getString(R.string.xxx)`.
- Add new keys to **all** maintained locales (`values`, `values-en`, `values-ja`) when possible.

---

## Community

- [GitHub Issues](https://github.com/qpst4/cebian/issues) — bugs and feature requests
- [GitHub Discussions](https://github.com/qpst4/cebian/discussions) — questions and ideas
- QQ group: **1042783385**

---

## License

By contributing, you agree that your contributions will be licensed under the same [AGPL-3.0](LICENSE) license as the project.
