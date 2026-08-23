#!/usr/bin/env python3
"""
Update update.json manifest after release and purge jsDelivr cache.
Cross-platform, UTF-8 safe script for CI and local usage.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path


def parse_version_code_from_gradle(gradle_path: Path) -> int:
    content = gradle_path.read_text(encoding="utf-8")
    m = re.search(r"versionCode\s*=\s*(\d+)", content)
    if not m:
        raise ValueError(f"Could not parse versionCode from {gradle_path}")
    return int(m.group(1))


def get_changelog_bullet_notes(
    version: str, changelog_path: Path, max_items_per_group: int = 8
) -> str:
    v = version.lstrip("v")
    content = changelog_path.read_text(encoding="utf-8")

    header = f"## [{v}]"
    start = content.find(header)
    if start < 0:
        raise ValueError(f"Section '{header}' not found in {changelog_path}")

    after_header = start + len(header)
    remainder = content[after_header:]
    next_match = re.search(r"(?m)^## \[", remainder)
    if next_match:
        section = content[start : after_header + next_match.start()].rstrip()
    else:
        section = content[start:].rstrip()

    header_to_title = {
        "added": "新增",
        "changed": "变更",
        "fixed": "修复",
    }

    groups = []
    current_title = ""
    current_items = []
    plain_bullets = []
    saw_group_header = False

    for line in section.splitlines():
        trimmed = line.strip()
        m_group = re.match(r"^###\s+(.+)$", trimmed)
        if m_group:
            hdr = m_group.group(1).strip().lower()
            title = header_to_title.get(hdr)
            if title:
                saw_group_header = True
                if current_items:
                    groups.append({"title": current_title, "items": list(current_items)})
                current_title = title
                current_items = []
            continue

        if trimmed.startswith("- "):
            item = trimmed[2:].strip().replace("**", "").replace("`", "")
            if current_title:
                if max_items_per_group <= 0 or len(current_items) < max_items_per_group:
                    current_items.append(item)
            else:
                plain_bullets.append(item)

    if current_items:
        groups.append({"title": current_title, "items": list(current_items)})

    if not saw_group_header:
        return "\n".join(plain_bullets)

    out_lines = []
    out_lines.extend(plain_bullets)
    for group in groups:
        if not group["items"]:
            continue
        out_lines.append(f"## {group['title']}")
        for item in group["items"]:
            out_lines.append(f"- {item}")

    return "\n".join(out_lines)


def purge_jsdelivr(repo: str = "qpst4/cebian") -> None:
    purge_url = f"https://purge.jsdelivr.net/gh/{repo}@main/update.json"
    try:
        req = urllib.request.Request(
            purge_url, headers={"User-Agent": "Cebian-Release-Bot"}
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"Purged jsDelivr cache: {purge_url} (status: {data.get('status')})")
    except Exception as e:
        print(f"WARNING: jsDelivr cache purge failed (non-fatal): {e}", file=sys.stderr)


def verify_remote(
    version: str, apk_size: int, repo: str = "qpst4/cebian"
) -> None:
    urls = [
        f"https://raw.githubusercontent.com/{repo}/main/update.json",
        f"https://cdn.jsdelivr.net/gh/{repo}@main/update.json",
    ]
    for url in urls:
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "Cebian-Release-Bot", "Cache-Control": "no-cache"}
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                remote_version = data.get("version")
                remote_size = int(data.get("apkSize", 0))
                if remote_version != version:
                    raise ValueError(
                        f"Remote version mismatch at {url}: {remote_version} (expected {version})"
                    )
                if remote_size != apk_size:
                    raise ValueError(
                        f"Remote apkSize mismatch at {url}: {remote_size} (expected {apk_size})"
                    )
                print(f"Verified {url} (version={remote_version}, apkSize={remote_size})")
        except Exception as e:
            print(f"ERROR: Failed to verify {url}: {e}", file=sys.stderr)
            raise


def main():
    parser = argparse.ArgumentParser(description="Update update.json manifest.")
    parser.add_argument("--version", "-v", required=True, help="Release version (e.g. 1.9.8.2)")
    parser.add_argument("--version-code", type=int, help="Version code integer (default: parse from build.gradle.kts)")
    parser.add_argument("--apk-size", type=int, help="Exact byte size of lite release APK")
    parser.add_argument("--apk-file", help="Path to lite release APK to calculate size automatically")
    parser.add_argument("--apk-file-name", default="", help="Custom APK file name in release URL")
    parser.add_argument("--notes", default="", help="Custom update notes text")
    parser.add_argument("--notes-file", default="", help="File containing update notes text")
    parser.add_argument("--from-changelog", action="store_true", default=True, help="Generate notes from CHANGELOG.md")
    parser.add_argument("--max-items-per-group", type=int, default=8, help="Max items per changelog group")
    parser.add_argument("--changelog", default="CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument("--manifest", default="update.json", help="Path to update.json")
    parser.add_argument("--gradle-file", default="app/build.gradle.kts", help="Path to build.gradle.kts")
    parser.add_argument("--repo", default="qpst4/cebian", help="GitHub repo in owner/name format")
    parser.add_argument("--purge-jsdelivr", action="store_true", help="Purge jsDelivr cache")
    parser.add_argument("--verify-remote", action="store_true", help="Verify raw & jsDelivr remote manifests")

    args = parser.parse_args()

    v = args.version.lstrip("v")

    # Resolve APK size
    apk_size = args.apk_size
    if apk_size is None and args.apk_file:
        apk_path = Path(args.apk_file)
        if not apk_path.is_file():
            print(f"ERROR: APK file not found: {apk_path}", file=sys.stderr)
            sys.exit(1)
        apk_size = apk_path.stat().st_size

    if apk_size is None or apk_size <= 0:
        print("ERROR: --apk-size or valid --apk-file must be provided (> 0).", file=sys.stderr)
        sys.exit(1)

    # Resolve versionCode
    version_code = args.version_code
    if version_code is None:
        version_code = parse_version_code_from_gradle(Path(args.gradle_file))

    # Resolve notes
    if args.notes_file:
        resolved_notes = Path(args.notes_file).read_text(encoding="utf-8").strip()
    elif args.notes:
        resolved_notes = args.notes.replace("；", "\n").strip()
    else:
        resolved_notes = get_changelog_bullet_notes(
            version=v,
            changelog_path=Path(args.changelog),
            max_items_per_group=args.max_items_per_group,
        )

    if not resolved_notes:
        print("ERROR: Resolved update notes are empty.", file=sys.stderr)
        sys.exit(1)

    apk_file_name = args.apk_file_name or f"cebian-{v}-lite.apk"
    apk_url = f"https://github.com/{args.repo}/releases/download/v{v}/{apk_file_name}"

    manifest_data = {
        "version": v,
        "versionCode": version_code,
        "apkUrl": apk_url,
        "apkSize": apk_size,
        "notes": resolved_notes,
    }

    manifest_path = Path(args.manifest)
    manifest_path.write_text(
        json.dumps(manifest_data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Updated {manifest_path}:")
    print(json.dumps(manifest_data, ensure_ascii=False, indent=2))

    if args.purge_jsdelivr:
        purge_jsdelivr(repo=args.repo)

    if args.verify_remote:
        verify_remote(version=v, apk_size=apk_size, repo=args.repo)


if __name__ == "__main__":
    main()
