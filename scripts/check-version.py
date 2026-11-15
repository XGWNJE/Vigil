"""Validate a release tag against the application's declared version."""
import argparse
import pathlib
import re


def validate(tag: str, source: str) -> None:
    versions = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"', source, re.MULTILINE)
    codes = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', source, re.MULTILINE)
    if len(versions) != 1 or len(codes) != 1 or int(codes[0]) < 1:
        raise ValueError("Expected one application versionName and positive versionCode")
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag) or tag != 'v' + versions[0]:
        raise ValueError("Release tag does not match application versionName")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent.parent
    validate(args.tag, (root / 'app/build.gradle.kts').read_text(encoding='utf-8'))
    print('Application version and release tag agree')
