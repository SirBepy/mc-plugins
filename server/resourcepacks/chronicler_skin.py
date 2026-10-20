"""Draws Thessa the Chronicler's skin (the minibosses plugin's spawn NPC, claudesmp:entity/chronicler)
into claudesmp-items/assets/claudesmp/textures/entity/chronicler.png, and optionally renders previews.

She is painted the way guild_skins.py paints its staff: character grids, one char per pixel, in the
standard 64x64 skin layout with the slim 3-pixel arms. The helpers (Skin, the part boxes, the
preview renderer) are imported from guild_skins rather than copied.

    python server/resourcepacks/chronicler_skin.py            # writes the pack texture
    python server/resourcepacks/chronicler_skin.py <out dir>  # also writes front/back previews there
"""

import sys
from pathlib import Path

from guild_skins import BODY, HEAD, LARM, LLEG, RARM, RLEG, Skin, render, rgb
from PIL import Image

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "claudesmp-items" / "assets" / "claudesmp" / "textures" / "entity"


# Thessa: silver hair tied back in a long tail with a gold ribbon, pale weathered skin with tired
# grey-blue eyes, a quill tucked behind her right ear; a burgundy scholar's robe with gold collar,
# cuffs and hem, a dark belt, an ink-stained parchment apron, a small book hanging at her hip,
# ink-stained fingers on the writing (right) hand, and dark leather boots.

def chronicler():
    s = Skin({
        "h": rgb("D6DAE2"), "H": rgb("AEB4C2"), "L": rgb("F2F4F8"),
        "s": rgb("EBD8C8"), "S": rgb("D3BCAB"), "k": rgb("B79F8E"),
        "e": rgb("5C7D90"), "w": rgb("F4F4F4"), "E": rgb("9496A0"), "m": rgb("B07F78"),
        "r": rgb("6E2F3C"), "R": rgb("4C1E2A"), "v": rgb("86434F"),
        "g": rgb("DDB64E"), "G": rgb("A98429"),
        "a": rgb("C9B68E"), "A": rgb("A08B62"), "i": rgb("232648"),
        "D": rgb("4A3222"), "B": rgb("2E1E14"),
        "b": rgb("5A3A22"), "p": rgb("ECDEB8"),
        "q": rgb("F4F0E6"), "Q": rgb("3C3232"),
    }, slim=True)
    streaks = "hLhhHhLh"
    side = ["hhhhhhhh", "hLLhhhhh", "hhhhhhhh", "hHhhSSsh", "hhhhSksh", "hHhhsssh", "hhhhhsss", "hhhhhsss"]
    s.box(HEAD,
          top=[streaks] * 8,
          bottom=["hhssssHh", "hhssssHh", "hhSSSShh"] + ["hhhhhhhh"] * 5,
          front=["hhhhhhhh",
                 "hLhhhhLh",
                 "hhsssshh",
                 "hEEssEEh",
                 "hwessewh",
                 "hkSssSkh",
                 "sssmmsss",
                 "SsssssSs"],
          right=side,
          left=[r[::-1] for r in side],
          back=[streaks] * 4 + ["hhgGGghh", "hhhHHhhh", "hhhhhhhh", "hhhhhhhh"])
    # Overlay: the quill behind her right ear, and the start of the tail standing off the nape.
    s.box(HEAD, overlay=True,
          right=["........", "......q.", ".....qq.", "....qQ..", "...Q....", "........", "........", "........"],
          back=["........"] * 4 + ["..gGGg..", "..hLHh..", "..hHhh..", "...hh..."])
    s.box(BODY,
          top=["rrrrrrrr"] * 3 + ["rRggggRr"],
          bottom=["RRRRRRRR"] * 4,
          front=["rRggggRr",
                 "rvrGGrvr",
                 "rvrrrrvr",
                 "rvrrrrvr",
                 "rvrrrrvr",
                 "rRrrrrRr",
                 "DDDggDDD",
                 "rAaaaaAr",
                 "rAaiaaAr",
                 "rAaaiiAr",
                 "rAaaaaAr",
                 "GGGggGGG"],
          right=["rvrr"] * 6 + ["DDDD"] + ["rvrr"] * 4 + ["GGGG"],
          left=["rrvr"] * 6 + ["DDDD"] + ["rrvr"] * 4 + ["GGGG"],
          back=["rRggggRr"] + ["rvrrrrvr"] * 5 + ["DDDDDDDD"] + ["rvrrrrvr"] * 4 + ["GGGGGGGG"])
    # Overlay: the long tail down her back, knotted again with gold near its end, and the book on her belt.
    s.box(BODY, overlay=True,
          front=["........"] * 7 + ["......bb", "......bp", "......bb"] + ["........"] * 2,
          back=["..hhLh..", "..hLhh..", "..hhHh..", "..hLhh..", "..hhHh..", "..hLhh..", "..hhHh..",
                "..gGGg..", "...hLh..", "...hHh..", "...hh...", "........"])
    sleeve = ["rvrr", "rrrr", "rvrr", "rvrr", "rvrr", "rvrr", "rvrr", "rvrr", "GgGG", "ssss", "ssSs", "sSSs"]
    arm_top = ["rrr", "rvr", "rrr", "rrr"]
    arm_bottom = ["sss", "sSs", "sSs", "sss"]
    # Her writing hand is the right one: ink on the fingertips.
    s.limb(RARM, sides=sleeve, top=arm_top, bottom=arm_bottom,
           front=["rvr", "rrr", "rvr", "rvr", "rvr", "rvr", "rvr", "rvr", "GgG", "sss", "sis", "sii"])
    s.limb(LARM, sides=sleeve, mirror=True, top=arm_top, bottom=arm_bottom)
    leg = ["rvrr"] * 9 + ["GGGG", "DDDD", "BBBB"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["rrrr"] * 4, bottom=["BBBB"] * 4)
    return s


def write(out_dir=OUT):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    skin = chronicler()
    path = out_dir / "chronicler.png"
    skin.img.save(path)
    return skin, path


def preview(skin, scale=12):
    """Front and back side by side on a flat backdrop, so the transparent texels read."""
    views = [render(skin.img, None, skin.slim, yaw, 0, scale) for yaw in (0, 180)]
    gap = 40
    W = sum(v.width for v in views) + gap * 3
    H = max(v.height for v in views) + gap * 2
    sheet = Image.new("RGBA", (W, H), rgb("5A5E66"))
    x = gap
    for v in views:
        sheet.alpha_composite(v, (x, H - gap - v.height))
        x += v.width + gap
    return sheet


def main():
    skin, path = write()
    if len(sys.argv) > 1:
        previews = Path(sys.argv[1])
        previews.mkdir(parents=True, exist_ok=True)
        preview(skin).save(previews / "chronicler_preview.png")
    print(f"chronicler: {path}")


if __name__ == "__main__":
    main()
