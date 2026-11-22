#!/usr/bin/env python3
"""latest.json pro vydání (zadání kap. 9, port tool/release/latest_json.dart z Deskovek).

Použití (release.yml):  python3 tool/release/make_latest_json.py <verze> <apk> <výstup.json>

Sekce verze v CHANGELOG.md:
    ## 0.1.1
    Minimální verze: 0.1.0      (volitelné – starší verze musí aktualizovat)
    ### cs
    …
    ### en
    …
    ### es
    …
"""
import hashlib
import json
import re
import sys


def build(version: str, apk: bytes, changelog: str) -> dict:
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not m:
        raise SystemExit(f"verze {version!r}: čekám major.minor.patch")
    major, minor, patch = map(int, m.groups())
    if minor > 99 or patch > 99:
        raise SystemExit("minor a patch musí být 0–99 (versionCode)")
    sections = re.split(r"^## ", changelog, flags=re.M)
    section = next((s for s in sections if s.split("\n", 1)[0].strip() == version), None)
    if section is None:
        raise SystemExit(f'CHANGELOG.md nemá sekci "## {version}"')
    minimum = re.search(r"^Minimální verze: (\d+\.\d+\.\d+)", section, flags=re.M)

    def notes(lang: str) -> str:
        lines = section.split("\n")
        try:
            start = [l.strip() for l in lines].index(f"### {lang}")
        except ValueError:
            raise SystemExit(f'CHANGELOG.md {version} nemá poznámky "### {lang}"')
        out = []
        for l in lines[start + 1:]:
            if l.startswith("### "):
                break
            out.append(l)
        return "\n".join(out).strip()

    return {
        "version": version,
        "versionCode": major * 10000 + minor * 100 + patch,
        "sha256": hashlib.sha256(apk).hexdigest(),
        "minSupportedVersion": minimum.group(1) if minimum else "0.0.0",
        "notes_cs": notes("cs"),
        "notes_en": notes("en"),
        "notes_es": notes("es"),
    }


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: make_latest_json.py <verze> <apk> <out.json>")
    data = build(sys.argv[1], open(sys.argv[2], "rb").read(), open("CHANGELOG.md", encoding="utf-8").read())
    with open(sys.argv[3], "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"latest.json: {data['version']} ({data['versionCode']}) sha256 {data['sha256']}")
