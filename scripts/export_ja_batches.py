#!/usr/bin/env python3
"""Export string entries into JSON batches for AI translation."""

import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
OUT = ROOT / "scripts" / "ja_batches"
BATCH_SIZE = 180


def parse_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {c.attrib["name"]: (c.text or "") for c in root if c.tag == "string" and "name" in c.attrib}


def main() -> None:
    en = parse_strings(RES / "values-en" / "strings.xml")
    zh = parse_strings(RES / "values" / "strings.xml")
    names = list(en.keys()) + [n for n in zh if n not in en]
    entries = []
    for name in names:
        if name in en:
            entries.append({"name": name, "text": en[name], "source": "en"})
        else:
            entries.append({"name": name, "text": zh[name], "source": "zh"})

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("batch_*.json"):
        old.unlink()

    for i in range(0, len(entries), BATCH_SIZE):
        chunk = entries[i : i + BATCH_SIZE]
        idx = i // BATCH_SIZE + 1
        (OUT / f"batch_{idx:02d}.json").write_text(
            json.dumps(chunk, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    meta = {"total": len(entries), "batches": (len(entries) + BATCH_SIZE - 1) // BATCH_SIZE, "batch_size": BATCH_SIZE}
    (OUT / "meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    print(meta)


if __name__ == "__main__":
    main()
