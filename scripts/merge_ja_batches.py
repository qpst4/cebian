#!/usr/bin/env python3
"""Merge translated JA batch JSON files into values-ja resources."""

import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
BATCH_DIR = ROOT / "scripts" / "ja_batches"
OUT_DIR = RES / "values-ja"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def merge_strings() -> None:
    items: list[dict] = []
    for path in sorted(BATCH_DIR.glob("batch_*_ja.json")):
        items.extend(json.loads(path.read_text(encoding="utf-8")))
    if not items:
        raise SystemExit("No batch_*_ja.json files found")

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for item in items:
        name = item["name"]
        text = item.get("ja") or item.get("text", "")
        if name == "app_name":
            text = "Cebian"
        lines.append(f'    <string name="{name}">{escape_xml(text)}</string>')
    lines.append("</resources>")
    lines.append("")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "strings.xml").write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote strings.xml ({len(items)} entries)")


def merge_plurals() -> None:
    ja_path = BATCH_DIR / "plurals_ja.json"
    if not ja_path.exists():
        raise SystemExit("Missing plurals_ja.json")
    items = json.loads(ja_path.read_text(encoding="utf-8"))
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for item in items:
        lines.append(f'    <plurals name="{item["name"]}">')
        lines.append(f'        <item quantity="other">{escape_xml(item["ja"])}</item>')
        lines.append("    </plurals>")
    lines.append("</resources>")
    lines.append("")
    (OUT_DIR / "plurals.xml").write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote plurals.xml ({len(items)} entries)")


if __name__ == "__main__":
    merge_strings()
    merge_plurals()
