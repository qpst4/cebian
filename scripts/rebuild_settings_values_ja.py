#!/usr/bin/env python3
"""Rebuild feature/settings values-ja from preset_search_strings_i18n.json."""

import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRESET = ROOT / "scripts" / "ja_batches" / "preset_search_strings_i18n.json"
RES = ROOT / "feature" / "settings" / "src" / "main" / "res"
OUT = RES / "values-ja" / "strings.xml"


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    order, _, _ = [], {}, {}
    tree = ET.parse(RES / "values" / "strings.xml")
    for child in tree.getroot():
        if child.tag == "string":
            order.append(child.attrib["name"])

    ja_map: dict[str, str] = {}
    for item in json.loads(PRESET.read_text(encoding="utf-8")):
        for suffix in ("name", "desc"):
            key = item.get(f"{suffix}Key")
            ja = item.get(f"ja_{suffix}")
            if key and ja:
                ja_map[key] = ja

    ja_map["default_engine_bilibili"] = "bilibili（公式）"
    ja_map["default_engine_wechat"] = "WeChat"
    ja_map["default_engine_taobao"] = "淘宝"
    ja_map["default_engine_weibo"] = "微博"
    ja_map["default_engine_zhihu"] = "知乎"
    ja_map["default_engine_douyin"] = "抖音"
    ja_map["default_engine_meituan"] = "美团"
    ja_map["default_engine_xhs"] = "小红书"

    zh = {c.attrib["name"]: c.text or "" for c in tree.getroot() if c.tag == "string"}
    en_tree = ET.parse(RES / "values-en" / "strings.xml")
    en = {c.attrib["name"]: c.text or "" for c in en_tree.getroot() if c.tag == "string"}

    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name in order:
        ja = ja_map.get(name)
        if not ja:
            ja = zh.get(name, en.get(name, ""))
        if not ja:
            raise SystemExit(f"Missing ja for {name}")
        lines.append(f'    <string name="{name}">{escape_xml(ja)}</string>')
    lines.append("</resources>")
    lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT} ({len(order)} strings)")


if __name__ == "__main__":
    main()
