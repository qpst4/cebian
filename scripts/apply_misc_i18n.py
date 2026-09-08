#!/usr/bin/env python3
"""Append misc hardcoded i18n entries to strings.xml files."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
MISC = ROOT / "scripts/ja_batches/misc_hardcoded_i18n.json"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def main() -> None:
    data = json.loads(MISC.read_text(encoding="utf-8"))
    entries = data["entries"]
    for folder, key in [("values", "zh"), ("values-en", "en"), ("values-ja", "ja")]:
        path = RES / folder / "strings.xml"
        text = path.read_text(encoding="utf-8")
        existing = set(re.findall(r'<string name="([^"]+)"', text))
        lines = []
        for e in entries:
            if e["name"] in existing:
                continue
            value = e.get(key, e.get("zh", ""))
            lines.append(f'    <string name="{e["name"]}">{escape_xml(value)}</string>')
        if lines:
            path.write_text(text.replace("</resources>", "\n".join(lines) + "\n</resources>"), encoding="utf-8")
            print(f"{folder}: appended {len(lines)}")
        else:
            print(f"{folder}: nothing new")

    # screenshot keywords array
    keywords = [e for e in entries if e["name"].startswith("screenshot_keyword_")]
    if keywords:
        for folder, key in [("values", "zh"), ("values-en", "en"), ("values-ja", "ja")]:
            arr_path = RES / folder / "arrays.xml"
            if not arr_path.exists():
                arr_path.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n', encoding="utf-8")
            text = arr_path.read_text(encoding="utf-8")
            if "screenshot_monitor_keywords" in text:
                continue
            items = "\n".join(f'        <item>{escape_xml(k.get(key, k["zh"]))}</item>' for k in keywords)
            block = f'    <string-array name="screenshot_monitor_keywords">\n{items}\n    </string-array>\n'
            arr_path.write_text(text.replace("</resources>", block + "</resources>"), encoding="utf-8")
            print(f"{folder}/arrays.xml: added screenshot_monitor_keywords")


if __name__ == "__main__":
    main()
