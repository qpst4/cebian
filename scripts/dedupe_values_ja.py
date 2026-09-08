#!/usr/bin/env python3
"""Remove duplicate <string> entries from values-ja/strings.xml (keep first)."""
import re
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/res/values-ja/strings.xml"
text = path.read_text(encoding="utf-8")
pattern = re.compile(r'    <string name="([^"]+)">.*?</string>\n', re.DOTALL)
seen: set[str] = set()
out: list[str] = []
removed = 0
for m in pattern.finditer(text):
    name = m.group(1)
    if name in seen:
        removed += 1
        continue
    seen.add(name)
    out.append(m.group(0))

header = text[: text.index("    <string")]
footer = "</resources>\n"
path.write_text(header + "".join(out) + footer, encoding="utf-8")
print(f"kept {len(seen)}, removed {removed} duplicates")
