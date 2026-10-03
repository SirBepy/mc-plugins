"""Builds client/SirBepy-Friends-Server-26.3.mrpack from modrinth.index.json.

Downloads every pinned file first and checks its size, sha1 and sha512 against the index, so a
pack never ships with a hash the Modrinth App or Prism would reject at import time.

Run from anywhere: python client/mrpack/build.py
"""

import hashlib
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
INDEX = HERE / "modrinth.index.json"
OUT = HERE.parent / "SirBepy-Friends-Server-26.3.mrpack"
# Modrinth asks API/CDN clients for a descriptive User-Agent.
USER_AGENT = "SirBepy/mc-plugins client-kit"


def verify(entry):
    req = urllib.request.Request(entry["downloads"][0], headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req) as resp:
        data = resp.read()
    problems = []
    if len(data) != entry["fileSize"]:
        problems.append(f"size {len(data)} != {entry['fileSize']}")
    for algo in ("sha1", "sha512"):
        actual = hashlib.new(algo, data).hexdigest()
        if actual != entry["hashes"][algo]:
            problems.append(f"{algo} {actual} != {entry['hashes'][algo]}")
    return problems


def main():
    index = json.loads(INDEX.read_text(encoding="utf-8"))
    failed = False
    for entry in index["files"]:
        problems = verify(entry)
        status = "OK" if not problems else "FAIL " + "; ".join(problems)
        print(f"{entry['path']}: {status}")
        failed |= bool(problems)
    if failed:
        sys.exit(1)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(INDEX, "modrinth.index.json")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
