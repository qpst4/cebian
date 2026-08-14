#!/usr/bin/env python3
"""Migrate remember/emit settings card API to Mishka inline naming."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app" / "src" / "main" / "java" / "com" / "slideindex" / "app" / "ui"

REPLACEMENTS = [
    ("rememberSettingsRadioGroup", "settingsCardItems"),
    ("rememberSettingsCardGroup", "settingsCardItems"),
    ("rememberSettingsCardItems", "settingsCardItems"),
]

EMIT_NAMED_RE = re.compile(
    r"emitSettingsCardItems\(\s*"
    r"keyPrefix\s*=\s*\"([^\"]+)\"\s*,\s*"
    r"group\s*=\s*(\w+)"
    r"(?:\s*,\s*selectableGroup\s*=\s*(true|false))?"
    r"\s*\)",
)

EMIT_POS_RE = re.compile(
    r"emitSettingsCardItems\(\s*\"([^\"]+)\"\s*,\s*(\w+)(?:\s*,\s*selectableGroup\s*=\s*(true|false))?\s*\)",
)

EMIT_GROUP_POS_RE = re.compile(
    r"emitSettingsCardGroup\(\s*(\w+)\s*,\s*\"([^\"]+)\"(?:\s*,\s*selectableGroup\s*=\s*(true|false))?\s*\)",
)

IMPORTS_REMOVE = [
    "import com.slideindex.app.ui.settings.components.emitSettingsCardItems\n",
    "import com.slideindex.app.ui.settings.components.rememberSettingsCardItems\n",
    "import com.slideindex.app.ui.settings.components.emitSettingsCardGroup\n",
    "import com.slideindex.app.ui.settings.components.rememberSettingsCardGroup\n",
    "import com.slideindex.app.ui.settings.components.rememberSettingsRadioGroup\n",
]

IMPORTS_ADD = [
    "import com.slideindex.app.ui.settings.components.settingsCardItems",
    "import com.slideindex.app.ui.settings.components.settingsGroupedCardItems",
]


def migrate_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text

    def emit_named_fix(match: re.Match[str]) -> str:
        prefix = match.group(1)
        group = match.group(2)
        selectable = match.group(3)
        if selectable:
            return f'settingsGroupedCardItems("{prefix}", {group}, selectableGroup = {selectable})'
        return f'settingsGroupedCardItems("{prefix}", {group})'

    def emit_pos_fix(match: re.Match[str]) -> str:
        prefix = match.group(1)
        group = match.group(2)
        selectable = match.group(3)
        if selectable:
            return f'settingsGroupedCardItems("{prefix}", {group}, selectableGroup = {selectable})'
        return f'settingsGroupedCardItems("{prefix}", {group})'

    text = EMIT_NAMED_RE.sub(emit_named_fix, text)
    text = EMIT_POS_RE.sub(emit_pos_fix, text)
    text = EMIT_GROUP_POS_RE.sub(
        lambda m: (
            f'settingsGroupedCardItems("{m.group(2)}", {m.group(1)}, selectableGroup = {m.group(3)})'
            if m.group(3)
            else f'settingsGroupedCardItems("{m.group(2)}", {m.group(1)})'
        ),
        text,
    )

    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    text = text.replace(".coordinator.RenderRows()", ".RenderRows()")

    for imp in IMPORTS_REMOVE:
        text = text.replace(imp, "")

    needs_items = "settingsCardItems(" in text
    needs_grouped = "settingsGroupedCardItems(" in text
    if needs_items or needs_grouped:
        lines = text.splitlines(keepends=True)
        pkg_idx = next((i for i, line in enumerate(lines) if line.startswith("package ")), None)
        if pkg_idx is None:
            return text != original
        insert_at = pkg_idx + 1
        while insert_at < len(lines) and lines[insert_at].strip() == "":
            insert_at += 1
        while insert_at < len(lines) and lines[insert_at].startswith("import "):
            insert_at += 1
        existing = set(line.strip() for line in lines if line.startswith("import "))
        to_add = []
        if needs_items and IMPORTS_ADD[0] not in existing:
            to_add.append(IMPORTS_ADD[0] + "\n")
        if needs_grouped and IMPORTS_ADD[1] not in existing:
            to_add.append(IMPORTS_ADD[1] + "\n")
        if to_add:
            lines[insert_at:insert_at] = to_add
            text = "".join(lines)

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main() -> None:
    changed = 0
    for path in UI.rglob("*.kt"):
        if migrate_file(path):
            changed += 1
            print(path.relative_to(ROOT))
    print(f"Updated {changed} files")


if __name__ == "__main__":
    main()
