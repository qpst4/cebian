#!/usr/bin/env python3
"""Apply preset search i18n to feature/settings res and regenerate catalog references."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
I18N = ROOT / "scripts/ja_batches/preset_search_strings_i18n.json"
CATALOG = ROOT / "feature/settings/src/main/java/com/slideindex/app/settings/PresetSearchEngineCatalog.kt"
RES = ROOT / "feature/settings/src/main/res"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def write_strings(folder: str, key: str, entries: list[dict]) -> None:
    path = RES / folder / "strings.xml"
    if not path.exists():
        path.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n', encoding="utf-8")
    text = path.read_text(encoding="utf-8")
    existing = set(re.findall(r'<string name="([^"]+)"', text))
    lines = []
    for e in entries:
        for suffix in ("name", "desc"):
            name = e[f"{suffix}Key"] if suffix == "name" else e["descKey"]
            if name in existing:
                continue
            if folder == "values":
                val = e["zh_name"] if suffix == "name" else e["zh_desc"]
            elif folder == "values-en":
                val = e["en_name"] if suffix == "name" else e["en_desc"]
            else:
                val = e["ja_name"] if suffix == "name" else e["ja_desc"]
            lines.append(f'    <string name="{name}">{escape_xml(val)}</string>')
    if lines:
        path.write_text(text.replace("</resources>", "\n".join(lines) + "\n</resources>"), encoding="utf-8")
        print(f"{folder}: +{len(lines)}")


def patch_catalog(entries: list[dict]) -> None:
    text = CATALOG.read_text(encoding="utf-8")
    if "@StringRes" not in text:
        text = text.replace(
            "package com.slideindex.app.settings\n",
            "package com.slideindex.app.settings\n\nimport android.content.Context\nimport androidx.annotation.StringRes\nimport com.slideindex.app.settings.R\n",
        )
        text = text.replace(
            "data class PresetSearchEngine(\n    val presetId: String,\n    val name: String,\n    val category: PresetSearchCategory,\n    val description: String,",
            "data class PresetSearchEngine(\n    val presetId: String,\n    @StringRes val nameRes: Int,\n    val category: PresetSearchCategory,\n    @StringRes val descriptionRes: Int,",
        )
        text = text.replace(
            "        name = name,\n",
            "        name = context.getString(nameRes),\n",
        )
        text = text.replace(
            "    fun toSearchEngineConfig(sortOrder: Int): SearchEngineConfig = SearchEngineConfig(",
            "    fun localizedName(context: Context): String = context.getString(nameRes)\n\n    fun localizedDescription(context: Context): String = context.getString(descriptionRes)\n\n    fun toSearchEngineConfig(context: Context, sortOrder: Int): SearchEngineConfig = SearchEngineConfig(",
        )
    for e in entries:
        pid = e["presetId"]
        text = re.sub(
            rf'(presetId = "{pid}",\s*)name = "[^"]*",',
            rf'\1nameRes = R.string.{e["nameKey"]},',
            text,
        )
        text = re.sub(
            rf'(presetId = "{pid}",\s*nameRes = R\.string\.\w+,\s*category = PresetSearchCategory\.\w+,\s*)description = "[^"]*",',
            rf'\1descriptionRes = R.string.{e["descKey"]},',
            text,
        )
    CATALOG.write_text(text, encoding="utf-8")
    print("Patched PresetSearchEngineCatalog.kt")


def main() -> None:
    entries = json.loads(I18N.read_text(encoding="utf-8"))
    for folder in ("values", "values-en", "values-ja"):
        write_strings(folder, folder, entries)
    patch_catalog(entries)


if __name__ == "__main__":
    main()
