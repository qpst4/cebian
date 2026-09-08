#!/usr/bin/env python3
"""Extract PresetSearchEngine catalog strings for i18n."""

import json
import re
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / (
    "feature/settings/src/main/java/com/slideindex/app/settings/PresetSearchEngineCatalog.kt"
)
OUT = Path(__file__).resolve().parents[1] / "scripts/ja_batches/preset_search_strings.json"


def slug(preset_id: str) -> str:
    return preset_id.removeprefix("preset-").replace("-", "_")


def main() -> None:
    text = CATALOG.read_text(encoding="utf-8")
    blocks = re.findall(
        r'PresetSearchEngine\(\s*presetId = "([^"]+)",\s*name = "([^"]*)",\s*'
        r'category = PresetSearchCategory\.\w+,\s*description = "([^"]*)"',
        text,
    )
    entries = []
    for preset_id, name, desc in blocks:
        s = slug(preset_id)
        entries.append(
            {
                "presetId": preset_id,
                "nameKey": f"preset_se_{s}_name",
                "descKey": f"preset_se_{s}_desc",
                "zh_name": name,
                "zh_desc": desc,
            }
        )
    OUT.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")
    print(len(entries))


if __name__ == "__main__":
    main()
