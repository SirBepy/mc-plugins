"""Writes server/datapacks/mastercutter-fixes/: corrected copies of MasterCutter's broken wood
stonecutter recipes, under the same recipe ids so they replace the upstream ones.

MasterCutter v1.8.0 (mc26.3) ships every recipe in wood/dark_oak/ and wood/pale_oak/ as a copy of
the cherry one (dark_oak_planks_from_dark_oak_log.json cuts a cherry log into cherry planks), so
dark and pale oak can't be cut at all (ImMijo's report, 2026-10-09). 1.8.0 is the newest release
(Modrinth, checked 2026-10-09), and the zip is a pinned download that's never edited, so the fix is
this override pack. A recipe in a higher-priority pack replaces the one with the same id, and a
folder pack named in lowercase sorts after "MasterCutter+..." and is appended last on a world that
already has the zip, so it always wins.

Any recipe under wood/<wood>/ that names an item of a different wood gets that wood swapped for
<wood>; the file name already says what the recipe should be. Counts stay upstream's, which match
the oak recipes for both broken woods. When a fixed MasterCutter is pinned, rerun this: it then
writes no recipes, and the empty pack can go.

Run from the repo root: python server/mastercutter-fixes/generate.py
"""

import re
import shutil
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARTIFACTS = ROOT / "buildSrc/src/main/kotlin/ServerArtifacts.kt"
PACK = ROOT / "server/datapacks/mastercutter-fixes"
RECIPES = "data/master_cutter/recipe/wood/"

WOODS = [
    "acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle", "mangrove", "oak",
    "pale_oak", "poplar", "spruce", "warped",
]
# Longest first so "dark_oak_log" is dark oak, not oak.
WOOD_RE = re.compile(
    r"(?<![a-z])(" + "|".join(sorted(WOODS, key=len, reverse=True)) + r")(?=_)"
)
ITEM_RE = re.compile(r'"(?:#?minecraft):([a-z0-9_/]+)"')


def wood_of(item_id):
    """The wood an item id belongs to, or None (sticks, generic tags)."""
    match = WOOD_RE.search(item_id)
    return match.group(1) if match else None


def pinned_url():
    text = ARTIFACTS.read_text(encoding="utf-8")
    match = re.search(r'"(https://[^"]*MasterCutter[^"]*\.zip)"', text)
    if not match:
        raise SystemExit(f"No MasterCutter URL in {ARTIFACTS}")
    return match.group(1)


def upstream_zip():
    """The pinned zip: an assembled server's copy if there is one, else a cached download."""
    url = pinned_url()
    name = urllib.parse.unquote(url.rsplit("/", 1)[1])
    for folder in ("server/dist/world/datapacks", "server/local/world/datapacks"):
        if (ROOT / folder / name).is_file():
            return ROOT / folder / name
    cached = Path(tempfile.gettempdir()) / "mastercutter-fixes" / name
    if not cached.is_file():
        cached.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url) as response, open(cached, "wb") as out:
            shutil.copyfileobj(response, out)
    return cached


def wood_recipes(zf):
    """{path: text} for every recipe under wood/<wood>/."""
    return {
        name: zf.read(name).decode("utf-8")
        for name in zf.namelist()
        if name.startswith(RECIPES) and name.endswith(".json")
    }


def folder_wood(path):
    return path[len(RECIPES):].split("/", 1)[0]


def foreign_woods(path, text):
    wood = folder_wood(path)
    return {w for w in map(wood_of, ITEM_RE.findall(text)) if w not in (wood, None)}


def fix(path, text):
    """The recipe with every other wood's items renamed to the folder's wood."""
    wood = folder_wood(path)

    def rename(match):
        return match.group(0).replace(match.group(1), WOOD_RE.sub(wood, match.group(1)))

    return ITEM_RE.sub(rename, text)


def fixes(zf):
    """{path: fixed text} for every wood recipe that names another wood's items."""
    return {
        path: fix(path, text)
        for path, text in wood_recipes(zf).items()
        if foreign_woods(path, text)
    }


def write(out_dir, fixed):
    data = out_dir / "data"
    if data.exists():
        shutil.rmtree(data)
    for path, text in fixed.items():
        target = out_dir / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")


def main():
    with zipfile.ZipFile(upstream_zip()) as zf:
        fixed = fixes(zf)
    write(PACK, fixed)
    per_wood = {}
    for path in fixed:
        per_wood[folder_wood(path)] = per_wood.get(folder_wood(path), 0) + 1
    print(f"Wrote {len(fixed)} recipes to {PACK}: {per_wood}")


if __name__ == "__main__":
    main()
