#!/usr/bin/env python3
"""Append i18n strings and regenerate KeyEventPreset.kt."""

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
KEYEVENT_I18N = ROOT / "scripts/ja_batches/keyevent_strings_i18n.json"
PRESET_KT = ROOT / "app/src/main/java/com/slideindex/app/gesture/KeyEventPreset.kt"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def append_strings(path: Path, new_entries: list[dict]) -> None:
    text = path.read_text(encoding="utf-8")
    existing = set(re.findall(r'<string name="([^"]+)"', text))
    lines = []
    for e in new_entries:
        if e["name"] in existing:
            continue
        locale_key = {"values": "zh", "values-en": "en", "values-ja": "ja"}[path.parent.name]
        value = e.get(locale_key) or e.get("zh", "")
        lines.append(f'    <string name="{e["name"]}">{escape_xml(value)}</string>')
    if not lines:
        return
    updated = text.replace("</resources>", "\n".join(lines) + "\n</resources>")
    path.write_text(updated, encoding="utf-8")
    print(f"Appended {len(lines)} to {path.name}")


def generate_keyevent_kt(data: dict) -> str:
    categories = {
        "SYSTEM": "keyevent_category_system",
        "MEDIA": "keyevent_category_media",
        "NAVIGATION": "keyevent_category_navigation",
        "INPUT_EDIT": "keyevent_category_input_edit",
        "HARDWARE": "keyevent_category_hardware",
        "TV_REMOTE": "keyevent_category_tv_remote",
        "GAMEPAD": "keyevent_category_gamepad",
        "FUNCTION_KEYS": "keyevent_category_function_keys",
    }
    cat_lines = "\n".join(
        f"    {name}(R.string.{res})," for name, res in categories.items()
    ).rstrip(",") + ";"
    item_lines = []
    for item in data["items"]:
        item_lines.append(
            f"""        KeyEventPresetItem(
            keyCode = {item['keyCodeExpr']},
            constantName = "{item['constantName']}",
            labelRes = R.string.{item['labelRes']},
            category = KeyEventCategory.{item['category']},
            descriptionRes = R.string.{item['descRes']},
        ),"""
        )
    items_block = "\n".join(item_lines)
    return f'''package com.slideindex.app.gesture

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.StringRes
import com.slideindex.app.R

data class KeyEventPresetItem(
    val keyCode: Int,
    val constantName: String,
    @StringRes val labelRes: Int,
    val category: KeyEventCategory,
    @StringRes val descriptionRes: Int,
) {{
    fun label(context: Context): String = context.getString(labelRes)

    fun description(context: Context): String = context.getString(descriptionRes)
}}

enum class KeyEventCategory(@StringRes val titleRes: Int) {{
{cat_lines}
    fun title(context: Context): String = context.getString(titleRes)
}}

object KeyEventPresets {{
    val presets: List<KeyEventPresetItem> = listOf(
{items_block}
    )

    private val presetByCode: Map<Int, KeyEventPresetItem> = presets.associateBy {{ it.keyCode }}

    fun findByCode(code: Int): KeyEventPresetItem? = presetByCode[code]

    fun getDisplayName(context: Context, code: Int, customName: String = ""): String {{
        if (customName.isNotBlank()) return customName
        val preset = findByCode(code)
        return if (preset != null) {{
            context.getString(R.string.keyevent_display_name, preset.label(context), preset.keyCode)
        }} else {{
            context.getString(R.string.keyevent_fallback_name, code)
        }}
    }}
}}
'''


def main() -> None:
    if not KEYEVENT_I18N.exists():
        raise SystemExit(f"Missing {KEYEVENT_I18N}")
    data = json.loads(KEYEVENT_I18N.read_text(encoding="utf-8"))
    entries = data["entries"]
    # display name format string
    extra = [
        {"name": "keyevent_display_name", "zh": "%1$s (%2$d)", "en": "%1$s (%2$d)", "ja": "%1$s (%2$d)"},
        {"name": "keyevent_fallback_name", "zh": "KeyCode %1$d", "en": "KeyCode %1$d", "ja": "KeyCode %1$d"},
    ]
    all_entries = entries + extra
    for folder in ("values", "values-en", "values-ja"):
        append_strings(RES / folder / "strings.xml", all_entries)
    PRESET_KT.write_text(generate_keyevent_kt(data), encoding="utf-8")
    print("Regenerated KeyEventPreset.kt")


if __name__ == "__main__":
    main()
