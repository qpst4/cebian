#!/usr/bin/env python3
"""Remove lint-reported unused resources after verifying no code references."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LINT_XML = ROOT / "app/build/reports/lint-results-fullDebug.xml"
RES_DIR = ROOT / "app/src/main/res"
SCAN_ROOTS = [ROOT / "app/src", ROOT / "feature", ROOT / "core"]


def load_sources() -> dict[Path, str]:
    files: dict[Path, str] = {}
    for base in SCAN_ROOTS:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.suffix not in {".kt", ".java", ".xml"}:
                continue
            if "build" in path.parts:
                continue
            try:
                files[path] = path.read_text(encoding="utf-8")
            except OSError:
                pass
    return files


def parse_unused() -> list[tuple[str, str]]:
    text = LINT_XML.read_text(encoding="utf-8")
    return re.findall(r'message="The resource `R\.(\w+)\.([^`]+)`', text)


def build_patterns(res_type: str, name: str) -> list[re.Pattern[str]]:
    escaped = re.escape(name)
    patterns = [
        re.compile(rf"R\.{res_type}\.{escaped}\b"),
        re.compile(rf"@{res_type}/{escaped}(?=[\"'\s/>])"),
    ]
    if res_type == "string":
        patterns.extend(
            [
                re.compile(rf"stringResource\(R\.string\.{escaped}\b"),
                re.compile(rf"getString\(R\.string\.{escaped}\b"),
            ]
        )
    elif res_type == "plurals":
        patterns.append(re.compile(rf"pluralStringResource\(R\.plurals\.{escaped}\b"))
    elif res_type == "array":
        patterns.append(re.compile(rf"stringArrayResource\(R\.array\.{escaped}\b"))
    return patterns


def is_used(name: str, res_type: str, sources: dict[Path, str]) -> bool:
    patterns = build_patterns(res_type, name)
    for path, content in sources.items():
        for line in content.splitlines():
            if f'name="{name}"' in line and "res" in path.parts:
                continue
            for pattern in patterns:
                if pattern.search(line):
                    return True
    return False


def write_file(path: Path, text: str) -> None:
    path.write_bytes(text.encode("utf-8"))


def prune_strings(names: set[str]) -> int:
    if not names:
        return 0
    alt = "|".join(re.escape(name) for name in sorted(names, key=len, reverse=True))
    pattern = re.compile(
        rf'^[ \t]*<string\b[^>]*\bname="(?:{alt})"[^>]*>.*?</string>[ \t]*\r?\n?',
        re.MULTILINE,
    )
    removed = 0
    for path in RES_DIR.rglob("strings.xml"):
        text = path.read_text(encoding="utf-8")
        new_text, count = pattern.subn("", text)
        if count:
            write_file(path, new_text)
            removed += count
    return removed


def prune_plurals(names: set[str]) -> int:
    if not names:
        return 0
    alt = "|".join(re.escape(name) for name in sorted(names, key=len, reverse=True))
    pattern = re.compile(
        rf'^[ \t]*<plurals\b[^>]*\bname="(?:{alt})"[^>]*>.*?</plurals>[ \t]*\r?\n?',
        re.MULTILINE | re.DOTALL,
    )
    removed = 0
    for path in RES_DIR.rglob("plurals.xml"):
        text = path.read_text(encoding="utf-8")
        new_text, count = pattern.subn("", text)
        if count:
            write_file(path, new_text)
            removed += count
    return removed


def prune_arrays(names: set[str]) -> int:
    if not names:
        return 0
    alt = "|".join(re.escape(name) for name in sorted(names, key=len, reverse=True))
    pattern = re.compile(
        rf'^[ \t]*<string-array\b[^>]*\bname="(?:{alt})"[^>]*>.*?</string-array>[ \t]*\r?\n?',
        re.MULTILINE | re.DOTALL,
    )
    removed = 0
    for path in RES_DIR.rglob("*.xml"):
        if not path.parent.name.startswith("values"):
            continue
        text = path.read_text(encoding="utf-8")
        new_text, count = pattern.subn("", text)
        if count:
            write_file(path, new_text)
            removed += count
    return removed


def prune_drawables(names: set[str]) -> int:
    removed = 0
    for name in names:
        for path in RES_DIR.rglob(f"{name}.xml"):
            if path.parent.name.startswith("drawable"):
                path.unlink()
                removed += 1
    return removed


def main() -> int:
    apply = "--apply" in sys.argv
    sources = load_sources()
    items = parse_unused()
    safe: list[tuple[str, str]] = []
    skipped: list[tuple[str, str]] = []
    for res_type, name in items:
        if is_used(name, res_type, sources):
            skipped.append((res_type, name))
        else:
            safe.append((res_type, name))

    print(f"lint_unused={len(items)} safe_to_remove={len(safe)} skipped_in_use={len(skipped)}")
    for res_type, name in skipped:
        print(f"SKIP {res_type}/{name}")

    if not apply:
        print("Dry run only. Pass --apply to delete.")
        return 0

    by_type: dict[str, set[str]] = {}
    for res_type, name in safe:
        by_type.setdefault(res_type, set()).add(name)

    total = 0
    total += prune_strings(by_type.get("string", set()))
    total += prune_plurals(by_type.get("plurals", set()))
    total += prune_arrays(by_type.get("array", set()))
    total += prune_drawables(by_type.get("drawable", set()))
    unknown = {t for t in by_type if t not in {"string", "plurals", "array", "drawable"}}
    for res_type in sorted(unknown):
        for name in sorted(by_type[res_type]):
            print(f"UNKNOWN {res_type}/{name}")
    print(f"removed_entries={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
