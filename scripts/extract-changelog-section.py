#!/usr/bin/env python3
"""
Extract Markdown section for a given version from CHANGELOG.md (for GitHub Releases).
"""
import argparse
import re
import sys
from pathlib import Path


def extract_section(changelog_text: str, version: str) -> str:
    v = version.lstrip("v")
    if v.lower() == "unreleased":
        raise ValueError("Cannot extract [Unreleased] for release notes.")

    header = f"## [{v}]"
    start = changelog_text.find(header)
    if start < 0:
        raise ValueError(f"Section '{header}' not found in CHANGELOG.")

    after_header = start + len(header)
    remainder = changelog_text[after_header:]

    next_match = re.search(r"(?m)^## \[", remainder)
    if next_match:
        section = changelog_text[start : after_header + next_match.start()].rstrip()
    else:
        section = changelog_text[start:].rstrip()

    return section


def lint_section(section: str) -> None:
    # Check if section has at least one bullet point (- or *)
    has_bullets = bool(re.search(r"(?m)^[-*]\s+", section))
    if not has_bullets:
        raise ValueError("Changelog section contains no bullet items.")


def main():
    parser = argparse.ArgumentParser(description="Extract changelog section for a release.")
    parser.add_argument("--version", "-v", required=True, help="Version string (e.g. 1.9.8.2 or v1.9.8.2)")
    parser.add_argument("--changelog", "-c", default="CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument("--out-file", "-o", default="", help="Path to write output markdown")
    parser.add_argument("--lint", action="store_true", help="Perform linting checks on the extracted section")

    args = parser.parse_args()

    changelog_path = Path(args.changelog)
    if not changelog_path.is_file():
        print(f"ERROR: Changelog not found: {changelog_path}", file=sys.stderr)
        sys.exit(1)

    content = changelog_path.read_text(encoding="utf-8")
    try:
        section = extract_section(content, args.version)
        if args.lint:
            lint_section(section)
    except ValueError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

    if args.out_file:
        out_path = Path(args.out_file)
        out_path.write_text(section + "\n", encoding="utf-8")
        print(f"Wrote {out_path}")
    else:
        print(section)


if __name__ == "__main__":
    main()
