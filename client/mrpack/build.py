"""Builds client/Los-Pollos-MineHermanos-26.3.mrpack from modrinth.index.json.

Downloads every pinned file first and checks its size, sha1 and sha512 against the index, so a
pack never ships with a hash the Modrinth App or Prism would reject at import time.

Anything under mrpack/overrides/ is zipped in verbatim under overrides/ at the root of the
.mrpack (the Modrinth App / Prism copy that tree straight into the instance folder on import).

Before that, client/resourcepack/rmf-26.3-fix/ (our own RMF carrier-item fix, see its README.md)
is zipped fresh into overrides/resourcepacks/rmf-26.3-fix.zip, so the source folder stays the only
thing committed and the zip is always rebuilt from current contents.

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
OVERRIDES = HERE / "overrides"
OUT = HERE.parent / "Los-Pollos-MineHermanos-26.3.mrpack"
RESOURCEPACK_SRC = HERE.parent / "resourcepack" / "rmf-26.3-fix"
RESOURCEPACK_OUT = OVERRIDES / "resourcepacks" / "rmf-26.3-fix.zip"
# Modrinth asks API/CDN clients for a descriptive User-Agent.
USER_AGENT = "SirBepy/mc-plugins client-kit"


def build_resourcepack_zip():
    RESOURCEPACK_OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(RESOURCEPACK_OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(RESOURCEPACK_SRC.rglob("*")):
            if path.is_file():
                arcname = path.relative_to(RESOURCEPACK_SRC).as_posix()
                zf.write(path, arcname)
    print(f"built {RESOURCEPACK_OUT} from {RESOURCEPACK_SRC}")


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
    build_resourcepack_zip()
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(INDEX, "modrinth.index.json")
        if OVERRIDES.is_dir():
            for path in sorted(OVERRIDES.rglob("*")):
                if path.is_file():
                    arcname = "overrides/" + path.relative_to(OVERRIDES).as_posix()
                    zf.write(path, arcname)
                    print(f"{arcname}: added")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
