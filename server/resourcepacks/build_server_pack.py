"""Builds the one resource pack the server offers on join: Backpacks! textures + the RMF 26.3 fix.

Merges server/resourcepacks/eclipse-backpacks/ and client/resourcepack/rmf-26.3-fix/ (their only
shared path is pack.mcmeta, replaced by PACK_META below) into
server/resourcepacks/dist/ClaudeSMP-server-pack-26.3.zip and prints its sha1.

Every entry gets a fixed timestamp, so an unchanged source tree always rebuilds to the same sha1
and server.properties' resource-pack-sha1 only has to change when the pack really changes.

Then: copy the zip to the website repo's site/downloads/, publish it, and set resource-pack +
resource-pack-sha1 in server/config/server.properties and on the live server
(`kinetic.mjs prop ...`, read at startup, so it applies on the next restart).

Run from anywhere: python server/resourcepacks/build_server_pack.py
"""

import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    ROOT / "server" / "resourcepacks" / "eclipse-backpacks",
    ROOT / "client" / "resourcepack" / "rmf-26.3-fix",
]
OUT = ROOT / "server" / "resourcepacks" / "dist" / "ClaudeSMP-server-pack-26.3.zip"
SKIP = {"pack.mcmeta", "README.md", "PORTED.md"}
FIXED_TIME = (1980, 1, 1, 0, 0, 0)

# min_format 84 comes from the backpacks half; the RMF half's item definitions need 26.3's format
# 97 and are ignored by older clients that ViaBackwards lets in.
PACK_META = {
    "pack": {
        "min_format": 84,
        "max_format": [100, 0],
        "pack_format": 97,
        "description": [
            {"text": "The Claude SMP ", "color": "gold", "italic": False},
            {"text": "- backpacks + food fixes", "color": "gray", "italic": False},
        ],
    }
}


def add(zf, arcname, data):
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    zf.writestr(info, data)


def main():
    files = {}
    for src in SOURCES:
        for path in sorted(src.rglob("*")):
            arcname = path.relative_to(src).as_posix()
            if path.is_file() and arcname not in SKIP:
                if arcname in files:
                    raise SystemExit(f"both packs ship {arcname}; merge it by hand first")
                files[arcname] = path
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w") as zf:
        add(zf, "pack.mcmeta", json.dumps(PACK_META, indent=2) + "\n")
        for arcname in sorted(files):
            add(zf, arcname, files[arcname].read_bytes())
    sha1 = hashlib.sha1(OUT.read_bytes()).hexdigest()
    print(f"wrote {OUT} ({len(files) + 1} files)")
    print(f"sha1 {sha1}")


if __name__ == "__main__":
    main()
