"""Builds client/Claude-SMP-26.3.mrpack from modrinth.index.json.

Downloads every pinned file first and checks its size, sha1 and sha512 against the index, so a
pack never ships with a hash the Modrinth App or Prism would reject at import time.

Anything under mrpack/overrides/ is zipped in verbatim under overrides/ at the root of the
.mrpack (the Modrinth App / Prism copy that tree straight into the instance folder on import).

Before that, each source folder in RESOURCEPACKS is zipped fresh into overrides/resourcepacks/, so
the source folders stay the only thing committed and the zips are always rebuilt from current
contents: client/resourcepack/rmf-26.3-fix/ (our own RMF carrier-item fix, see its README.md) and
server/resourcepacks/eclipse-backpacks/ (the 26.3-patched Backpacks! textures, see its PORTED.md).

Our own mods aren't on Modrinth, so they can't be index entries: each Gradle project in OWN_MODS is
built and its jar copied into overrides/mods/ (treemod/, the Classes talent-tree screen).

Run from anywhere: python client/mrpack/build.py
"""

import hashlib
import json
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
INDEX = HERE / "modrinth.index.json"
OVERRIDES = HERE / "overrides"
OUT = HERE.parent / "Claude-SMP-26.3.mrpack"
# The backpack zip name matches the copy the server offers on join (resource-pack= in
# server.properties), so a player who has both sees one recognisable name.
RESOURCEPACKS = [
    (HERE.parent / "resourcepack" / "rmf-26.3-fix", "rmf-26.3-fix.zip"),
    (HERE.parent.parent / "server" / "resourcepacks" / "eclipse-backpacks",
     "Eclipse-Backpacks-textures-26.3.zip"),
]
REPO = HERE.parent.parent
# (Gradle project, jar it builds under <project>/build/libs/)
OWN_MODS = [
    ("treemod", "classes-tree-1.0.0.jar"),
]
# Modrinth asks API/CDN clients for a descriptive User-Agent.
USER_AGENT = "SirBepy/mc-plugins client-kit"


def zip_tree(zf, src_dir, arc_prefix=""):
    arcnames = []
    for path in sorted(src_dir.rglob("*")):
        if path.is_file():
            arcname = arc_prefix + path.relative_to(src_dir).as_posix()
            zf.write(path, arcname)
            arcnames.append(arcname)
    return arcnames


def build_resourcepack_zips():
    for src, name in RESOURCEPACKS:
        out = OVERRIDES / "resourcepacks" / name
        out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
            zip_tree(zf, src)
        print(f"built {out} from {src}")


def build_own_mods():
    gradlew = REPO / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    mods = OVERRIDES / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    for project, jar in OWN_MODS:
        subprocess.run([str(gradlew), f":{project}:build", "-q"], cwd=REPO, check=True)
        shutil.copyfile(REPO / project / "build" / "libs" / jar, mods / jar)
        print(f"built {mods / jar} from {project}/")


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
    build_resourcepack_zips()
    build_own_mods()
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(INDEX, "modrinth.index.json")
        if OVERRIDES.is_dir():
            for arcname in zip_tree(zf, OVERRIDES, "overrides/"):
                print(f"{arcname}: added")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
