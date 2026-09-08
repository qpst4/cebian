#!/usr/bin/env python3
"""Patch core/ocr VLM provider display names for ja locale."""

import re
from pathlib import Path

TARGET = Path(__file__).resolve().parents[1] / "core" / "ocr" / "src" / "main" / "res" / "values-ja" / "strings.xml"

PATCHES = {
    "vlm_provider_dashscope_name": "Alibaba Cloud 百煉（DashScope）",
    "vlm_provider_zhipu_name": "Zhipu AI（智谱）",
    "vlm_provider_siliconflow_name": "SiliconFlow",
}


def escape_xml(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")
    for name, ja in PATCHES.items():
        pattern = rf'(<string name="{re.escape(name)}"[^>]*>)(.*?)(</string>)'
        text, _ = re.subn(pattern, rf"\1{escape_xml(ja)}\3", text, count=1)
    TARGET.write_text(text, encoding="utf-8")
    print(f"Patched {len(PATCHES)} ocr ja strings")


if __name__ == "__main__":
    main()
