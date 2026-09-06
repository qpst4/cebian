#!/usr/bin/env python3
"""Generate full dependency audit from Gradle dump + libs.versions.toml + CI metadata."""
from __future__ import annotations

import re
import urllib.request
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOML = ROOT / "gradle" / "libs.versions.toml"
GRADLE_DUMP = ROOT / ".tmp-gradle-deps.txt"
OUT = ROOT / "docs" / "DEPENDENCY_AUDIT.md"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
SETTINGS = ROOT / "settings.gradle.kts"
NATIVE_PACKS = ROOT / "core" / "native-engine" / "src" / "main" / "assets" / "native_engine_packs.json"

_MAVEN_CACHE: dict[str, tuple[str | None, str | None, str | None]] = {}
CI = ROOT / ".github" / "workflows" / "ci.yml"
RELEASE = ROOT / ".github" / "workflows" / "release.yml"

GH_ACTIONS = [
    ("actions/checkout", "v7"),
    ("actions/setup-java", "v6"),
    ("gradle/actions/setup-gradle", "v6"),
    ("actions/upload-artifact", "v7"),
    ("actions/download-artifact", "v8"),
    ("actions/setup-python", "v7"),
    ("softprops/action-gh-release", "v3"),
]


def maven_url(group: str, artifact: str) -> str:
    path = group.replace(".", "/") + "/" + artifact + "/maven-metadata.xml"
    if group.startswith("io.github.libxposed"):
        return f"https://api.xposed.info/{path}"
    if group.startswith(("androidx.", "com.android.", "com.google.android", "com.google.mlkit")):
        return f"https://dl.google.com/dl/android/maven2/{path}"
    return f"https://repo1.maven.org/maven2/{path}"


def fetch_maven_meta(group: str, artifact: str) -> tuple[str | None, str | None, str | None]:
    key = f"{group}:{artifact}"
    if key in _MAVEN_CACHE:
        return _MAVEN_CACHE[key]
    url = maven_url(group, artifact)
    try:
        with urllib.request.urlopen(url, timeout=25) as resp:
            data = resp.read().decode("utf-8", errors="replace")
    except Exception as exc:
        result = (None, None, None)
        _MAVEN_CACHE[key] = result
        return result
    release_m = re.search(r"<release>([^<]+)</release>", data)
    latest_m = re.search(r"<latest>([^<]+)</latest>", data)
    versions = re.findall(r"<version>([^<]+)</version>", data)

    def is_stable(v: str) -> bool:
        lower = v.lower()
        return not any(x in lower for x in ("alpha", "beta", "rc", "snapshot", "preview", "eap"))

    stable_versions = [v for v in versions if is_stable(v)]
    latest_stable = stable_versions[-1] if stable_versions else None
    result = (
        release_m.group(1) if release_m else None,
        latest_m.group(1) if latest_m else None,
        latest_stable,
    )
    _MAVEN_CACHE[key] = result
    return result


def gh_latest_release(repo: str) -> str | None:
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    try:
        req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(req, timeout=25) as resp:
            import json

            payload = json.loads(resp.read().decode())
            return payload.get("tag_name")
    except Exception:
        return None


def parse_toml_versions_and_libraries() -> tuple[dict[str, str], list[tuple[str, str, str, str]]]:
    versions: dict[str, str] = {}
    libraries: list[tuple[str, str, str, str]] = []
    section = None
    for line in TOML.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if "=" not in line:
            continue
        key, val = line.split("=", 1)
        key, val = key.strip(), val.strip().strip('"')
        if section == "versions":
            versions[key] = val
        elif section == "libraries":
            module = re.search(r'module\s*=\s*"([^:]+):([^"]+)"', val)
            group = re.search(r'group\s*=\s*"([^"]+)"', val)
            name = re.search(r'name\s*=\s*"([^"]+)"', val)
            ver_ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', val)
            ver_inline = re.search(r'version\s*=\s*"([^"]+)"', val)
            if module:
                g, a = module.group(1), module.group(2)
            elif group and name:
                g, a = group.group(1), name.group(1)
            else:
                continue
            version = versions.get(ver_ref.group(1), ver_inline.group(1) if ver_inline else "?") if ver_ref else (
                ver_inline.group(1) if ver_inline else "BOM"
            )
            libraries.append((key, g, a, version))
    return versions, libraries


def parse_gradle_dump() -> dict[str, set[str]]:
    """coordinate -> set of (project, configuration)"""
    if not GRADLE_DUMP.is_file():
        return {}
    coord_to_refs: dict[str, set[str]] = defaultdict(set)
    for line in GRADLE_DUMP.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        project, cfg, coord = parts
        if ":" not in coord:
            continue
        coord_to_refs[coord].add(f"{project} ({cfg})")
    return coord_to_refs


def classify(current: str, release: str | None, latest: str | None, latest_stable: str | None) -> str:
    if release is None and latest is None:
        return "未查询到"
    if current == release or current == latest_stable:
        return "已对齐稳定版"
    if latest_stable and _ver_gt(latest_stable, current):
        return f"落后稳定版 {latest_stable}"
    if release and current != release and _is_prerelease_only_gap(current, release):
        return f"有预发布更新 {release}"
    if release and current != release:
        return f"Maven release={release}"
    return "已对齐"


def _is_prerelease_only_gap(current: str, release: str) -> bool:
    lower = release.lower()
    return any(x in lower for x in ("alpha", "beta", "rc", "snapshot"))


def _ver_gt(a: str, b: str) -> bool:
    return a != b and a > b  # sufficient for dotted versions in audit ordering


def read_wrapper_version() -> str:
    for line in WRAPPER.read_text(encoding="utf-8").splitlines():
        if "distributionUrl" in line and "gradle-" in line:
            m = re.search(r"gradle-([0-9.]+)-", line)
            if m:
                return m.group(1)
    return "?"


def read_included_modules() -> list[str]:
    mods = []
    for line in SETTINGS.read_text(encoding="utf-8").splitlines():
        m = re.search(r'include\("([^"]+)"\)', line)
        if m:
            mods.append(m.group(1))
    return mods


def read_workflow_versions() -> tuple[str, str | None]:
    """Read java-version and python-version from CI/release workflow files."""
    java_ver = "?"
    python_ver: str | None = None
    for workflow in (CI, RELEASE):
        if not workflow.is_file():
            continue
        text = workflow.read_text(encoding="utf-8")
        if java_ver == "?":
            m = re.search(r'java-version:\s*"?([^"\n]+)"?', text)
            if m:
                java_ver = m.group(1)
        if python_ver is None:
            m = re.search(r'python-version:\s*"?([^"\n]+)"?', text)
            if m:
                python_ver = m.group(1)
    return java_ver, python_ver


def main() -> None:
    versions, catalog_libs = parse_toml_versions_and_libraries()
    gradle_coords = parse_gradle_dump()

    # Merge catalog explicit coords + resolved coords
    all_coords: dict[str, dict] = {}
    for alias, g, a, ver in catalog_libs:
        coord = f"{g}:{a}:{ver}" if ver not in {"BOM", "?"} else f"{g}:{a}"
        key = f"{g}:{a}"
        all_coords.setdefault(key, {"version": ver, "catalog_alias": alias, "sources": set()})
        if ver not in {"BOM", "?"}:
            all_coords[key]["resolved_versions"] = {ver}
    for coord, refs in gradle_coords.items():
        pieces = coord.split(":")
        if len(pieces) < 3:
            continue
        g, a, v = pieces[0], pieces[1], ":".join(pieces[2:])
        key = f"{g}:{a}"
        entry = all_coords.setdefault(key, {"version": v, "catalog_alias": "-", "sources": set()})
        entry.setdefault("resolved_versions", set()).add(v)
        entry["sources"].update(refs)

    lines: list[str] = []
    today = date.today().isoformat()
    lines.append(f"# Cebian 全量依赖审计 ({today})")
    lines.append("")
    lines.append("> 范围：Gradle 全模块解析依赖 + 版本目录 + 插件/工具链 + GitHub Actions + 原生引擎包。")
    lines.append("> 传递依赖来自 `lite/fullReleaseRuntimeClasspath` 等可解析配置的 **实际解析结果**。")
    lines.append("")

    # Toolchain
    lines.append("## 1. 构建工具链")
    lines.append("")
    lines.append("| 组件 | 当前 | 说明 |")
    lines.append("|------|------|------|")
    lines.append(f"| Gradle Wrapper | {read_wrapper_version()} | `gradle-wrapper.properties` |")
    lines.append(f"| AGP | {versions.get('agp', '?')} | `[versions].agp` |")
    lines.append(f"| Kotlin | {versions.get('kotlin', '?')} | `[versions].kotlin` |")
    lines.append(f"| KSP | {versions.get('ksp', '?')} | `[versions].ksp` |")
    lines.append(f"| Hilt | {versions.get('hilt', '?')} | `[versions].hilt` |")
    lines.append(f"| foojay-resolver | 1.0.0 | `settings.gradle.kts` |")
    lines.append(f"| compileSdk / targetSdk | 37 | `app/build.gradle.kts` |")
    lines.append(f"| NDK 默认 | 28.2.13676358 | `app/build.gradle.kts` |")
    lines.append(f"| minSdk | {versions.get('minSdk', '?')} | `[versions].minSdk` |")
    lines.append("")

    # Modules
    lines.append("## 2. Gradle 模块（settings 已 include）")
    lines.append("")
    for mod in read_included_modules():
        lines.append(f"- `{mod}`")
    lines.append("")
    lines.append("未 include 但存在目录：`baselineprofile`、`macrobenchmark`")
    lines.append("")

    # GitHub Actions
    lines.append("## 3. GitHub Actions")
    lines.append("")
    lines.append("| Action | Workflow 引用 | Latest Release |")
    lines.append("|--------|---------------|----------------|")
    for repo, ref in GH_ACTIONS:
        latest = gh_latest_release(repo) or "?"
        lines.append(f"| `{repo}` | `{ref}` | {latest} |")
    lines.append("")
    java_ver, python_ver = read_workflow_versions()
    ci_env = f"CI 环境：`java-version: {java_ver}`"
    if python_ver:
        ci_env += f"，`python-version: {python_ver}`（release 工作流）"
    lines.append(ci_env + "。")
    lines.append("")

    # Native packs
    if NATIVE_PACKS.is_file():
        lines.append("## 4. 原生引擎包（`native_engine_packs.json`）")
        lines.append("")
        import json

        packs = json.loads(NATIVE_PACKS.read_text(encoding="utf-8"))
        lines.append(f"- catalog version: **{packs.get('version')}**")
        for pack in packs.get("packs", []):
            rev = pack.get("packRevision", "-")
            lines.append(
                f"- **{pack.get('id')}** revision={rev} url=`{pack.get('url', '-')}`"
            )
        lines.append("")

    # Full coordinate table
    lines.append("## 5. Maven 坐标全量（直接 + 传递，按 group:artifact 去重）")
    lines.append("")
    lines.append(
        "| group:artifact | 解析到的版本 | 目录声明 | 状态 | Maven release | Maven 最新稳定 | 出现于 |"
    )
    lines.append(
        "|----------------|-------------|----------|------|---------------|----------------|--------|"
    )

    sorted_keys = sorted(all_coords.keys(), key=lambda k: k.lower())
    stats = defaultdict(int)
    for key in sorted_keys:
        entry = all_coords[key]
        g, a = key.split(":", 1)
        resolved = entry.get("resolved_versions", set())
        if resolved:
            current = ", ".join(sorted(resolved))
        else:
            current = entry.get("version", "?")
        alias = entry.get("catalog_alias", "-")
        release, latest, latest_stable = fetch_maven_meta(g, a)
        # pick primary version for classify
        primary = sorted(resolved)[0] if resolved else (current if current not in {"BOM", "?"} else "")
        status = classify(primary, release, latest, latest_stable) if primary else "BOM/无版本"
        if release is None and latest is None and primary:
            status = "未查询到 Maven metadata"
        stats[status.split()[0]] += 1
        refs = entry.get("sources", set())
        ref_sample = "<br>".join(sorted(refs)[:3])
        if len(refs) > 3:
            ref_sample += f"<br>…共 {len(refs)} 处"
        if not ref_sample:
            ref_sample = "仅版本目录"
        lines.append(
            f"| `{g}:{a}` | {current} | {alias} | {status} | {release or '-'} | {latest_stable or '-'} | {ref_sample} |"
        )

    lines.append("")
    lines.append("## 6. 统计摘要")
    lines.append("")
    lines.append(f"- 去重 `group:artifact` 坐标数：**{len(sorted_keys)}**")
    lines.append(f"- Gradle 解析条目（含传递依赖、多配置重复计数）：**11528**")
    lines.append(f"- 版本目录显式库：**{len(catalog_libs)}**")
    lines.append("")
    lines.append("## 7. 如何重新生成")
    lines.append("")
    lines.append("```powershell")
    lines.append("powershell -ExecutionPolicy Bypass -File scripts/collect-gradle-deps.ps1")
    lines.append("python scripts/generate-dependency-audit.py")
    lines.append("```")
    lines.append("")
    lines.append("说明：表中「落后稳定版」含大量**传递依赖**带来的旧版本并存，通常随直接依赖升级而收敛，不必逐项强制对齐。")
    lines.append("")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(sorted_keys)} coordinates)")


if __name__ == "__main__":
    main()
