#!/usr/bin/env python3
"""Sync feature/settings values-ja preset search strings from preset_search_strings_i18n.json."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET = ROOT / "scripts" / "ja_batches" / "preset_search_strings_i18n.json"
TARGET = ROOT / "feature" / "settings" / "src" / "main" / "res" / "values-ja" / "strings.xml"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    patches: dict[str, str] = {}
    for item in json.loads(PRESET.read_text(encoding="utf-8")):
        for suffix in ("name", "desc"):
            key = item.get(f"{suffix}Key")
            ja = item.get(f"ja_{suffix}")
            if key and ja:
                patches[key] = ja
    patches["default_engine_bilibili"] = "bilibili（公式）"
    patches["default_engine_wechat"] = "WeChat"

    text = TARGET.read_text(encoding="utf-8")
    applied = 0
    for name, ja in patches.items():
        pattern = rf'(<string name="{re.escape(name)}"[^>]*>)(.*?)(</string>)'
        new_text, n = re.subn(pattern, rf"\1{escape_xml(ja)}\3", text, count=1)
        if n:
            text = new_text
            applied += 1
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched {applied} settings ja strings")


if __name__ == "__main__":
    main()
