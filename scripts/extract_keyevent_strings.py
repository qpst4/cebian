#!/usr/bin/env python3
"""Extract KeyEventPreset strings and regenerate Kotlin with @StringRes."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET_FILE = ROOT / "app/src/main/java/com/slideindex/app/gesture/KeyEventPreset.kt"
OUT_JSON = ROOT / "scripts/ja_batches/keyevent_strings.json"

CATEGORY_MAP = {
    "SYSTEM": "系统控制",
    "MEDIA": "媒体与声音",
    "NAVIGATION": "方向与网页导航",
    "INPUT_EDIT": "输入与文本编辑",
    "HARDWARE": "硬件与快捷应用",
    "TV_REMOTE": "电视与智能遥控",
    "GAMEPAD": "游戏与手柄按键",
    "FUNCTION_KEYS": "功能键 (F1-F12)",
}


def res_name(constant: str, kind: str) -> str:
    slug = constant.lower()
    return f"keyevent_{kind}_{slug}"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def main() -> None:
    text = PRESET_FILE.read_text(encoding="utf-8")
    entries: list[dict] = []

    for enum_name, zh in CATEGORY_MAP.items():
        entries.append(
            {
                "name": f"keyevent_category_{enum_name.lower()}",
                "zh": zh,
                "kind": "category",
            }
        )

    item_pattern = re.compile(
        r"KeyEventPresetItem\(\s*"
        r"keyCode = (KeyEvent\.KEYCODE_\w+),\s*"
        r"constantName = \"(KEYCODE_\w+)\",\s*"
        r"labelZh = \"([^\"]*)\",\s*"
        r"category = KeyEventCategory\.(\w+),\s*"
        r"description = \"([^\"]*)\",\s*"
        r"\)",
        re.MULTILINE,
    )
    items = []
    for match in item_pattern.finditer(text):
        constant = match.group(2)
        label = match.group(3)
        category = match.group(4)
        desc = match.group(5)
        label_name = res_name(constant, "label")
        desc_name = res_name(constant, "desc")
        entries.append({"name": label_name, "zh": label, "kind": "label", "constant": constant})
        entries.append({"name": desc_name, "zh": desc, "kind": "desc", "constant": constant})
        items.append(
            {
                "keyCodeExpr": match.group(1),
                "constantName": constant,
                "category": category,
                "labelRes": label_name,
                "descRes": desc_name,
            }
        )

    OUT_JSON.write_text(json.dumps({"entries": entries, "items": items}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"entries={len(entries)} items={len(items)}")


if __name__ == "__main__":
    main()
