"""Builds the one resource pack the server offers on join: Backpacks! textures, the RMF 26.3 fix
and the server's own custom item looks.

Merges server/resourcepacks/eclipse-backpacks/, client/resourcepack/rmf-26.3-fix/ and
server/resourcepacks/claudesmp-items/ (their only shared path is pack.mcmeta, replaced by PACK_META
below) into server/resourcepacks/dist/ClaudeSMP-server-pack-26.3.zip and prints its sha1.

Every entry gets a fixed timestamp, so an unchanged source tree always rebuilds to the same sha1
and server.properties' resource-pack-sha1 only has to change when the pack really changes.

Then: copy the zip to the website repo's site/downloads/ under the name it prints (the sha1's first
8 characters in the file name), publish it, and set resource-pack (the URL it prints) and
resource-pack-sha1 in server/config/server.properties and on the live server (`kinetic.mjs prop
...`, read at startup, so it applies on the next restart). Never overwrite a published pack: the
running server keeps offering its URL with the old sha1 until that restart, and the 26.3 client
refuses a download whose hash doesn't match, so a replaced file leaves new joiners with no pack.

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
    ROOT / "server" / "resourcepacks" / "claudesmp-items",
]
OUT = ROOT / "server" / "resourcepacks" / "dist" / "ClaudeSMP-server-pack-26.3.zip"
DOWNLOADS = "https://sirbepy.github.io/sirbepys-minecraft-server/downloads/"
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


def build(out):
    files = {}
    for src in SOURCES:
        for path in sorted(src.rglob("*")):
            arcname = path.relative_to(src).as_posix()
            if path.is_file() and arcname not in SKIP:
                if arcname in files:
                    raise SystemExit(f"both packs ship {arcname}; merge it by hand first")
                files[arcname] = path
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w") as zf:
        add(zf, "pack.mcmeta", json.dumps(PACK_META, indent=2) + "\n")
        for arcname in sorted(files):
            add(zf, arcname, files[arcname].read_bytes())
    return len(files) + 1


def published_name(sha1):
    return f"{OUT.stem}-{sha1[:8]}{OUT.suffix}"


def main():
    count = build(OUT)
    sha1 = hashlib.sha1(OUT.read_bytes()).hexdigest()
    print(f"wrote {OUT} ({count} files)")
    print(f"sha1 {sha1}")
    print(f"publish as site/downloads/{published_name(sha1)}")
    print(f"resource-pack={DOWNLOADS}{published_name(sha1)}")


if __name__ == "__main__":
    main()
