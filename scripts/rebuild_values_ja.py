#!/usr/bin/env python3
"""Rebuild app values-ja from AI batch JSON sources (no machine translation APIs)."""

from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
BATCH_DIR = ROOT / "scripts" / "ja_batches"
OUT_STRINGS = RES / "values-ja" / "strings.xml"
OUT_PLURALS = RES / "values-ja" / "plurals.xml"
GAPS_JSON = ROOT / "scripts" / "ja_gaps.json"

SKIP_NAMES = {"app_name"}


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def parse_strings_with_meta(path: Path) -> tuple[list[str], dict[str, str], dict[str, bool]]:
    tree = ET.parse(path)
    order: list[str] = []
    values: dict[str, str] = {}
    non_translatable: dict[str, bool] = {}
    for child in tree.getroot():
        if child.tag != "string" or "name" not in child.attrib:
            continue
        name = child.attrib["name"]
        order.append(name)
        values[name] = child.text or ""
        non_translatable[name] = child.attrib.get("translatable") == "false"
    return order, values, non_translatable


def parse_plain_strings(path: Path) -> dict[str, str]:
    _, values, _ = parse_strings_with_meta(path)
    return values


def collect_batch_ja() -> dict[str, str]:
    out: dict[str, str] = {}
    for path in sorted(BATCH_DIR.glob("batch_*_ja.json")):
        for item in json.loads(path.read_text(encoding="utf-8")):
            name = item["name"]
            ja = item.get("ja") or item.get("text", "")
            if ja:
                out[name] = ja
    return out


def collect_misc_ja() -> dict[str, str]:
    out: dict[str, str] = {}
    data = json.loads((BATCH_DIR / "misc_hardcoded_i18n.json").read_text(encoding="utf-8"))
    for item in data.get("entries", []):
        ja = item.get("ja")
        if ja:
            out[item["name"]] = ja
    return out


def collect_keyevent_ja() -> dict[str, str]:
    out: dict[str, str] = {}
    data = json.loads((BATCH_DIR / "keyevent_strings_i18n.json").read_text(encoding="utf-8"))
    for item in data.get("entries", []):
        ja = item.get("ja")
        if ja:
            out[item["name"]] = ja
    for item in data.get("items", []):
        for key in ("labelRes", "descRes"):
            res = item.get(key)
            ja_key = key.replace("Res", "_ja")
            if res and item.get(ja_key):
                out[res] = item[ja_key]
    # entries list already has name/ja for label and desc resources
    return out


def collect_preset_search_ja() -> dict[str, str]:
    out: dict[str, str] = {}
    data = json.loads((BATCH_DIR / "preset_search_strings_i18n.json").read_text(encoding="utf-8"))
    for item in data:
        for suffix in ("name", "desc"):
            key_field = f"{suffix}Key"
            ja_field = f"ja_{suffix}"
            name_key = item.get(key_field)
            ja_val = item.get(ja_field)
            if name_key and ja_val:
                out[name_key] = ja_val
    return out


def collect_plurals_ja() -> dict[str, str]:
    path = BATCH_DIR / "plurals_ja.json"
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for item in json.loads(path.read_text(encoding="utf-8")):
        ja = item.get("ja")
        if ja:
            out[item["name"]] = ja
    return out


def is_untranslated(name: str, ja: str, en: str, zh: str) -> bool:
    if name in SKIP_NAMES:
        return False
    if not ja.strip():
        return True
    if ja == en and en != zh:
        return True
    return False


def main() -> None:
    order, zh, non_translatable = parse_strings_with_meta(RES / "values" / "strings.xml")
    en = parse_plain_strings(RES / "values-en" / "strings.xml")
    current_ja = parse_plain_strings(OUT_STRINGS) if OUT_STRINGS.exists() else {}

    sources: dict[str, str] = {}
    for collector in (
        collect_batch_ja,
        collect_misc_ja,
        collect_keyevent_ja,
        collect_preset_search_ja,
    ):
        sources.update(collector())

    # Prefer curated JSON over current file when current looks like English copy.
    ja_map: dict[str, str] = {}
    for name in order:
        if non_translatable.get(name):
            ja_map[name] = current_ja.get(name) or zh.get(name) or en.get(name, "")
            continue
        candidates = [
            sources.get(name),
            current_ja.get(name) if current_ja.get(name) and current_ja.get(name) != en.get(name) else None,
            sources.get(name),
        ]
        picked = None
        for c in candidates:
            if c and not is_untranslated(name, c, en.get(name, ""), zh.get(name, "")):
                picked = c
                break
        if picked is None:
            picked = sources.get(name) or current_ja.get(name) or en.get(name, zh.get(name, ""))
        if name == "app_name":
            picked = "Cebian"
        ja_map[name] = picked

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    gaps = []
    for name in order:
        ja = ja_map[name]
        if is_untranslated(name, ja, en.get(name, ""), zh.get(name, "")):
            gaps.append(
                {
                    "name": name,
                    "zh": zh.get(name, ""),
                    "en": en.get(name, ""),
                    "ja": ja,
                }
            )
        attr = ' translatable="false"' if non_translatable.get(name) else ""
        lines.append(f'    <string name="{name}"{attr}>{escape_xml(ja)}</string>')
    lines.append("</resources>")
    lines.append("")
    OUT_STRINGS.parent.mkdir(parents=True, exist_ok=True)
    OUT_STRINGS.write_text("\n".join(lines), encoding="utf-8")

    plurals_ja = collect_plurals_ja()
    if plurals_ja:
        plines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        for name, ja in plurals_ja.items():
            plines.append(f'    <plurals name="{name}">')
            plines.append(f'        <item quantity="other">{escape_xml(ja)}</item>')
            plines.append("    </plurals>")
        plines.append("</resources>")
        plines.append("")
        OUT_PLURALS.write_text("\n".join(plines), encoding="utf-8")

    GAPS_JSON.write_text(json.dumps(gaps, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT_STRINGS} ({len(order)} strings)")
    print(f"Collected source translations: {len(sources)}")
    print(f"Remaining gaps: {len(gaps)} -> {GAPS_JSON}")


if __name__ == "__main__":
    main()
