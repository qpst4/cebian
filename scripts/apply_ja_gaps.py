#!/usr/bin/env python3
"""Apply AI-filled gap translations to values-ja/strings.xml."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res" / "values-ja" / "strings.xml"
GAPS = ROOT / "scripts" / "ja_gaps_filled.json"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    patches = {item["name"]: item["ja"] for item in json.loads(GAPS.read_text(encoding="utf-8"))}
    text = RES.read_text(encoding="utf-8")
    for name, ja in patches.items():
        pattern = rf'(<string name="{re.escape(name)}"[^>]*>)(.*?)(</string>)'
        repl = rf"\1{escape_xml(ja)}\3"
        new_text, n = re.subn(pattern, repl, text, count=1)
        if n:
            text = new_text
        else:
            print(f"WARN missing key: {name}")
    RES.write_text(text, encoding="utf-8")
    print(f"Applied {len(patches)} gap patches")


if __name__ == "__main__":
    main()
