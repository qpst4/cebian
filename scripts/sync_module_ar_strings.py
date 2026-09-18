#!/usr/bin/env python3
"""Write values-ar/strings.xml for library modules from community APK strings dump."""
from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRIB = Path(r"d:\Downloads\Telegram Desktop\string边栏_1.10.3.apk.xml")

MODULE_RES_DIRS = [
    ROOT / "feature/settings/src/main/res",
    ROOT / "feature/notification/src/main/res",
    ROOT / "core/ocr/src/main/res",
    ROOT / "core/common/src/main/res",
]


def load_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    out: dict[str, str] = {}
    for el in root.findall("string"):
        parts: list[str] = []
        if el.text:
            parts.append(el.text)
        for child in el:
            parts.append(ET.tostring(child, encoding="unicode", method="xml"))
            if child.tail:
                parts.append(child.tail)
        out[el.attrib["name"]] = "".join(parts)
    return out


def ordered_names(base_strings: Path) -> list[str]:
    root = ET.parse(base_strings).getroot()
    return [el.attrib["name"] for el in root.findall("string")]


def escape_android_xml(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', '\\"')
    )


def write_ar_module(res_dir: Path, contrib: dict[str, str]) -> int:
    base = res_dir / "values/strings.xml"
    names = ordered_names(base)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<resources>",
    ]
    for name in names:
        if name not in contrib:
            raise KeyError(f"Missing in community dump: {name} ({res_dir})")
        value = contrib[name]
        lines.append(f'    <string name="{name}">{escape_android_xml(value)}</string>')
    lines.append("</resources>")
    lines.append("")
    out_path = res_dir / "values-ar/strings.xml"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return len(names)


def main() -> None:
    if not CONTRIB.is_file():
        raise SystemExit(f"Community file not found: {CONTRIB}")
    contrib = load_strings(CONTRIB)
    for res_dir in MODULE_RES_DIRS:
        count = write_ar_module(res_dir, contrib)
        print(f"Wrote {count} strings -> {res_dir / 'values-ar/strings.xml'}")


if __name__ == "__main__":
    main()
