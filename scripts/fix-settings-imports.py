#!/usr/bin/env python3
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java"
fix_named = re.compile(
    r'settingsGroupedCardItems\(\s*keyPrefix\s*=\s*"([^"]+)"\s*,\s*group\s*=\s*(\w+)'
    r'(?:\s*,\s*selectableGroup\s*=\s*(true|false))?\s*\)'
)

for path in root.rglob("*.kt"):
    text = path.read_text(encoding="utf-8", errors="replace")
    original = text

    def repl(match: re.Match[str]) -> str:
        if match.group(3):
            return (
                f'settingsGroupedCardItems("{match.group(1)}", {match.group(2)}, '
                f"selectableGroup = {match.group(3)})"
            )
        return f'settingsGroupedCardItems("{match.group(1)}", {match.group(2)})'

    text = fix_named.sub(repl, text)
    text = text.replace(
        "import com.slideindex.app.settings.AppSettings\n"
        "import com.slideindex.app.settings.AppSettings\n",
        "import com.slideindex.app.settings.AppSettings\n",
    )
    if (
        "SettingsCardItems" in text
        and "import com.slideindex.app.ui.settings.components.SettingsCardItems" not in text
    ):
        lines = text.splitlines(keepends=True)
        pkg_idx = next((i for i, line in enumerate(lines) if line.startswith("package ")), None)
        if pkg_idx is not None:
            insert_at = pkg_idx + 1
            while insert_at < len(lines) and lines[insert_at].strip() == "":
                insert_at += 1
            while insert_at < len(lines) and lines[insert_at].startswith("import "):
                insert_at += 1
            lines.insert(
                insert_at,
                "import com.slideindex.app.ui.settings.components.SettingsCardItems\n",
            )
            text = "".join(lines)
    if text != original:
        path.write_text(text, encoding="utf-8")
        print(path.relative_to(root.parent.parent.parent))
