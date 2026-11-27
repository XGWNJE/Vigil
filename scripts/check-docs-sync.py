"""Local CI check: verify README, ROADMAP, release notes, and secret isolation against Kotlin source."""
import argparse
import pathlib
import re
import subprocess
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


def read_text(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_gradle(root: pathlib.Path) -> dict:
    src = read_text(root / "app/build.gradle.kts")
    vname = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"', src, re.MULTILINE)
    vcode = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', src, re.MULTILINE)
    min_sdk = re.findall(r'^\s*minSdk\s*=\s*(\d+)', src, re.MULTILINE)
    compile_sdk = re.findall(r'^\s*compileSdk\s*=\s*(\d+)', src, re.MULTILINE)
    if not (len(vname) == 1 and len(vcode) == 1 and len(min_sdk) == 1 and len(compile_sdk) == 1):
        raise ValueError("Could not uniquely parse versionName/versionCode/minSdk/compileSdk in app/build.gradle.kts")
    return {
        "versionName": vname[0],
        "versionCode": int(vcode[0]),
        "minSdk": int(min_sdk[0]),
        "compileSdk": int(compile_sdk[0]),
    }


def parse_int_const(src: str, name: str) -> int:
    m = re.search(rf'\bconst\s+val\s+{re.escape(name)}\s*=\s*(\d+)\b', src)
    if not m:
        raise ValueError(f"Constant {name} not found")
    return int(m.group(1))


def parse_leading_factor(src: str, name: str) -> int:
    m = re.search(rf'\bconst\s+val\s+{re.escape(name)}\s*=\s*(\d+)\s*\*', src)
    if not m:
        raise ValueError(f"Constant {name} expression not found")
    return int(m.group(1))


def count_builtin_presets(root: pathlib.Path) -> int:
    src = read_text(root / "app/src/main/java/com/example/vigil/alert/RingtoneLibrary.kt")
    m = re.search(r'val\s+PRESETS\s*:[^=]*=\s*listOf\((.*?)\n\s*\)', src, re.DOTALL)
    if not m:
        raise ValueError("PRESETS list not found in RingtoneLibrary.kt")
    return len(re.findall(r'PresetRingtone\(', m.group(1)))


def check_readme(root: pathlib.Path, gradle: dict, errors: list[str]) -> None:
    readme_path = root / "README.md"
    readme = read_text(readme_path)

    prefs_src = read_text(root / "app/src/main/java/com/example/vigil/data/SharedPreferencesHelper.kt")
    svc_src = read_text(root / "app/src/main/java/com/example/vigil/MyNotificationListenerService.kt")

    max_queue = parse_int_const(prefs_src, "MAX_ALERT_QUEUE_SIZE")
    max_sched = parse_int_const(prefs_src, "MAX_SCHEDULED_ALERT_SIZE")
    max_loop = parse_int_const(prefs_src, "MAX_LOOP_COUNT")
    max_history = parse_int_const(prefs_src, "MAX_ALERT_HISTORY")
    ttl_min = parse_leading_factor(svc_src, "PENDING_ALERT_TTL_MS")
    overdue_hr = parse_leading_factor(svc_src, "MAX_OVERDUE_FIRE_MS")
    presets_count = count_builtin_presets(root)

    expected_facts = [
        (f"{max_queue} 项" if max_queue == max_sched else f"{max_queue} 项", "queue/scheduled limit"),
        (f"1–{max_loop} 次", "MAX_LOOP_COUNT"),
        (f"{max_history} 条", "MAX_ALERT_HISTORY"),
        (f"{ttl_min} 分钟", "PENDING_ALERT_TTL_MS"),
        (f"{overdue_hr} 小时", "MAX_OVERDUE_FIRE_MS"),
        (f"{presets_count} 条语音预设", "RingtoneLibrary.PRESETS"),
        (f"API {gradle['minSdk']}", "minSdk"),
        (f"API {gradle['compileSdk']}", "compileSdk"),
    ]
    for needle, label in expected_facts:
        if needle not in readme:
            errors.append(f"README.md missing expected fact '{needle}' ({label})")

    stale_terms = [
        ("开启「系统浮窗」", "use exact UI term '系统悬浮窗'"),
        ("一键预览", "use exact UI term '点选即预览'"),
        ("查看全部历史", "use bounded history description '最近 100 条'"),
    ]
    for term, hint in stale_terms:
        if term in readme:
            errors.append(f"README.md contains stale term '{term}' ({hint})")

    # Header <div align="center"> must close before first ## heading
    first_h2 = re.search(r'^## ', readme, re.MULTILINE)
    div_open = readme.find('<div align="center">')
    div_close = readme.find('</div>')
    if div_open == -1 or div_close == -1 or not first_h2 or not (div_open < div_close < first_h2.start()):
        errors.append("README.md header <div align=\"center\"> must open and close before first ## heading")

    if "## 路线规划" not in readme or "[路线规划](#路线规划)" not in readme:
        errors.append("README.md must include '## 路线规划' and header nav '[路线规划](#路线规划)'")

    # Anchor & local link validation
    headings = {
        re.sub(r'\s+', '-', h.strip().lower())
        for h in re.findall(r'^#{2,6}\s+(.+)$', readme, re.MULTILINE)
    }
    raw_headings = {h.strip() for h in re.findall(r'^#{2,6}\s+(.+)$', readme, re.MULTILINE)}
    for anchor in re.findall(r'\[[^\]]+\]\(#([^)]+)\)', readme):
        if anchor not in raw_headings and anchor.lower() not in headings:
            errors.append(f"README.md broken anchor '#{anchor}'")

    for target in re.findall(r'\[[^\]]+\]\(([^)#]+)\)', readme):
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        if not (root / target).exists():
            errors.append(f"README.md broken local link '{target}'")

    for img_src in re.findall(r'<img\b[^>]*\bsrc="([^"]+)"', readme):
        if img_src.startswith(("http://", "https://")):
            continue
        if not (root / img_src).exists():
            errors.append(f"README.md broken image src '{img_src}'")


def check_roadmap_and_tag(root: pathlib.Path, gradle: dict, tag: str | None, errors: list[str]) -> None:
    roadmap = read_text(root / "ROADMAP.md")
    expected_ver = f"当前版本：v{gradle['versionName']}（versionCode {gradle['versionCode']}）"
    if expected_ver not in roadmap:
        errors.append(f"ROADMAP.md must contain '{expected_ver}'")

    if tag:
        if not re.fullmatch(r'v\d+\.\d+\.\d+', tag) or tag != f"v{gradle['versionName']}":
            errors.append(f"Tag '{tag}' does not match app/build.gradle.kts versionName 'v{gradle['versionName']}'")
        notes_path = root / "release-notes" / f"{tag}.md"
        if not notes_path.exists():
            errors.append(f"Missing release notes file: release-notes/{tag}.md")
        else:
            notes = read_text(notes_path).strip()
            if not notes.startswith("## "):
                errors.append(f"release-notes/{tag}.md must start with '## <one-line theme>'")
            if "{{" in notes or "}}" in notes:
                errors.append(f"release-notes/{tag}.md contains unreplaced template placeholders")
            if "\n- " not in notes:
                errors.append(f"release-notes/{tag}.md must contain at least one bullet item")


def check_tracked_secrets(root: pathlib.Path, errors: list[str]) -> None:
    res = subprocess.run(
        ["git", "ls-files"],
        cwd=root,
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    )
    forbidden = re.compile(r'(\.keystore|\.jks|\.p12|\.pfx|keystore\.properties|release\.properties)$', re.IGNORECASE)
    for line in res.stdout.splitlines():
        if forbidden.search(line.strip()):
            errors.append(f"Tracked secret/keystore file detected in git: {line.strip()}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", default="", help="Optional release tag vX.Y.Z to validate")
    args = parser.parse_args()

    root = pathlib.Path(__file__).resolve().parent.parent
    errors: list[str] = []

    gradle = parse_gradle(root)
    check_readme(root, gradle, errors)
    check_roadmap_and_tag(root, gradle, args.tag or None, errors)
    check_tracked_secrets(root, errors)

    if errors:
        for err in errors:
            print(f"[FAIL] {err}")
        return 1

    tag_info = f", tag={args.tag}" if args.tag else ""
    print(f"[OK] Docs, README, ROADMAP, and secret checks passed (v{gradle['versionName']} code={gradle['versionCode']}{tag_info})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
