"""Draws the Adventurer's Guild staff skins (the guild plugin's Mannequins, see Staff.java) into
claudesmp-items/assets/claudesmp/textures/entity/guild/, and optionally renders previews.

Each skin is painted face by face from character grids (one char per pixel, looked up in the
character's palette, '.' transparent), in the standard 64x64 skin layout. Slim characters use the
3-pixel arm layout. The examiner also gets a cape (64x32).

    python server/resourcepacks/guild_skins.py            # writes the pack textures
    python server/resourcepacks/guild_skins.py <out dir>  # also writes previews there
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "claudesmp-items" / "assets" / "claudesmp" / "textures" / "entity" / "guild"


def rgb(hexcode):
    return tuple(int(hexcode[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# Box UV origins and sizes (w, h, d) in the 64x64 layout; overlays sit at their own origins.
HEAD = ((0, 0), (32, 0), (8, 8, 8))
BODY = ((16, 16), (16, 32), (8, 12, 4))
RARM = ((40, 16), (40, 32), (4, 12, 4))
LARM = ((32, 48), (48, 48), (4, 12, 4))
RLEG = ((0, 16), (0, 32), (4, 12, 4))
LLEG = ((16, 48), (0, 48), (4, 12, 4))


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


class Skin:
    def __init__(self, palette, slim=False):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.palette = dict(palette, **{".": (0, 0, 0, 0)})
        self.slim = slim

    def grid(self, x, y, w, h, rows, where):
        if isinstance(rows, str) and len(rows) == 1:
            rows = [rows * w] * h
        if len(rows) != h or any(len(r) != w for r in rows):
            raise SystemExit(f"{where}: expected {w}x{h}, got {[len(r) for r in rows]}")
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                self.px[x + i, y + j] = self.palette[ch]

    def box(self, part, overlay=False, **faces):
        (base, over, (w, h, d)) = part
        if part in (RARM, LARM) and self.slim:
            w = 3
        u, v = over if overlay else base
        rects = face_rects(u, v, w, h, d)
        for name, rows in faces.items():
            x, y, fw, fh = rects[name]
            self.grid(x, y, fw, fh, rows, f"{part}/{name}")

    def limb(self, part, overlay=False, sides=None, mirror=False, **faces):
        """Paints all four vertical faces of an arm or leg from one column pattern, then any overrides.

        The pattern is drawn 4 wide; on a slim arm's front and back the last column is dropped.
        """
        w = 3 if part in (RARM, LARM) and self.slim else 4
        if sides is not None:
            rows = [r[::-1] for r in sides] if mirror else sides
            for name in ("right", "left"):
                faces.setdefault(name, rows)
            for name in ("front", "back"):
                faces.setdefault(name, [r[:w] for r in rows])
        self.box(part, overlay, **faces)


# --------------------------------------------------------------------------------------------
# Lyra Fennwick, Guild Receptionist: honey-brown hair with a rose ribbon and a quill behind her
# ear, the Guild's navy uniform (vest, ribbon tie, gold badge, pleated skirt), white blouse sleeves.

def lyra():
    s = Skin({
        "h": rgb("B07040"), "H": rgb("8C542E"), "L": rgb("C8864E"),
        "s": rgb("F6D6BC"), "S": rgb("E2BA9E"), "k": rgb("C89C80"),
        "e": rgb("3E8C5C"), "E": rgb("2A2830"), "w": rgb("FAFAF8"), "W": rgb("DCDEE4"),
        "b": rgb("F0B0B0"), "m": rgb("D48484"),
        "n": rgb("2E3A60"), "N": rgb("1F2846"), "v": rgb("3A4874"),
        "g": rgb("E8BE54"), "G": rgb("B88C34"),
        "r": rgb("F2B8C6"), "R": rgb("D6809A"),
        "t": rgb("3A3040"), "T": rgb("2E2636"), "K": rgb("6A4428"), "B": rgb("462C1C"),
        "q": rgb("F4F0E6"), "Q": rgb("3C3232"),
    }, slim=True)
    streaks = "hLhhHhLh"
    side = ["hhhhhhhh", "LLLLhhhh", "hhhhhhhh", "HHhhhSsh", "hhhhSksh", "hhhhhssh", "LLLhhhhh", "hhhhhhhh"]
    s.box(HEAD,
          top=[streaks] * 8,
          bottom=["hhssssHh", "hhssssHh", "hhSSSShh"] + ["hhhhhhhh"] * 5,
          front=["hhhhhhhh",
                 "hhLhhhLh",
                 "hsHhhsHh",
                 "hEEssEEh",
                 "hwessewh",
                 "hbsSSsbh",
                 "hsssmmsh",
                 "hhsssshh"],
          right=side,
          left=[r[::-1] for r in side],
          back=[streaks] * 3 + ["hhrRRrhh", "hLhrrhLh"] + [streaks] * 3)
    # Overlay: a gold hairpin, the quill tucked behind her right ear, and the ribbon bow standing out.
    s.box(HEAD, overlay=True,
          front=["........", "......g.", "........", "........", "........", "........", "........", "........"],
          right=["........", "......q.", ".....qq.", "....qQ..", "........", "........", "........", "........"],
          back=["........", "........", "........", "..rRRr..", "...rr...", "........", "........", "........"])
    s.box(BODY,
          top=["nnnnnnnn", "nnnnnnnn", "nnnnnnnn", "nnwwwwnn"],
          bottom=["NNNNNNNN"] * 4,
          front=["nvwrrwvn",
                 "nvwRRwvn",
                 "nvwrrwgn",
                 "nvvwwvGn",
                 "nvnggnvn",
                 "nvnnnnvn",
                 "GGGggGGG",
                 "NnnnnnnN",
                 "nnvnnvnn",
                 "nnvnnvnn",
                 "nnvnnvnn",
                 "NNNNNNNN"],
          right=["nnvn"] * 6 + ["GGGG", "nnnn", "nvnn", "nvnn", "nvnn", "NNNN"],
          left=["nvnn"] * 6 + ["GGGG", "nnnn", "nnvn", "nnvn", "nnvn", "NNNN"],
          back=["nvnnnnvn"] * 6 + ["GGGGGGGG", "NnnnnnnN", "nnvnnvnn", "nnvnnvnn", "nnvnnvnn", "NNNNNNNN"])
    # Her hair falls past her shoulders at the back, and the skirt flares over the hips.
    skirt = ["NnnnnnnN", "nnvnnvnn", "nnvnnvnn", "nnvnnvnn", "NNNNNNNN"]
    s.box(BODY, overlay=True,
          front=["........"] * 7 + skirt,
          right=["...."] * 7 + ["nnnn", "nvnn", "nvnn", "nvnn", "NNNN"],
          left=["...."] * 7 + ["nnnn", "nnvn", "nnvn", "nnvn", "NNNN"],
          back=[".hLhhLh.", ".hLhhLh.", "..hhLh..", "..hLhh..", "...hh...", "........", "........"] + skirt)
    sleeve = ["vnnv", "nnnn", "wwWw", "wwWw", "wwWw", "wwWw", "wwWw", "wwWw", "rrRr", "ssss", "ssss", "sSSs"]
    for arm, mirror in ((RARM, False), (LARM, True)):
        s.limb(arm, sides=sleeve, mirror=mirror,
               top=["nnn", "nvn", "nnn", "nnn"], bottom=["sss", "sSs", "sSs", "sss"])
    leg = ["nvnn", "nvnn", "nvnn", "nvnn", "NNNN", "tTtt", "tTtt", "tTtt", "tTtt", "KKKK", "KKKK", "BBBB"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["nnnn"] * 4, bottom=["BBBB"] * 4)
    return s


# --------------------------------------------------------------------------------------------
# Bram Ashgrove, Quest Clerk: grizzled, receding grey hair, bushy brows, a grey beard, an old scar
# over his left eye and a pencil behind his ear; a tan leather vest over a linen shirt with rolled sleeves
# and leather sleeve garters, and his left hand is a finger short (the Breeze).

def bram():
    s = Skin({
        "h": rgb("A8A8A6"), "H": rgb("8C8C8A"), "L": rgb("C8C8C4"),
        "s": rgb("D6A27C"), "S": rgb("BC8864"), "k": rgb("9E6C4E"), "x": rgb("EAB0A0"), "X": rgb("5A3426"),
        "e": rgb("28343E"), "m": rgb("6E4438"),
        "t": rgb("E6DAC0"), "T": rgb("C8BA9C"), "u": rgb("F4ECDA"),
        "l": rgb("B07438"), "D": rgb("6E4422"),
        "p": rgb("4E4840"), "P": rgb("44403A"),
        "b": rgb("3E2C20"), "B": rgb("281E16"), "g": rgb("C4A050"),
        "y": rgb("E8C448"), "Y": rgb("C89664"),
    })
    side = ["hHhhhhhh", "hHhhhsss", "hHhSksss", "hHhkSsss", "hHhhssss", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh"]
    s.box(HEAD,
          top=["hHhhhHhh", "hHhhhHhh", "hHhhhHhh", "hhssssHh", "hssssssh", "hssssssh", "hssssssh", "hhssssSh"],
          bottom=["hhhhhhhh"] * 4 + ["hhssssHh", "hhssssHh", "hhSSSShh", "hhhhhhhh"],
          front=["hhsssshh",
                 "hssssssh",
                 "sHHssHxs",
                 "sesSSexs",
                 "sssSSsxk",
                 "sHhhhhHs",
                 "hhhmmhhh",
                 "hhhhhhhh"],
          right=side,
          left=[r[::-1] for r in side],
          back=["hHhhhHhh"] * 8)
    # Overlay: the pencil behind his right ear, and the beard standing off the chin.
    s.box(HEAD, overlay=True,
          right=["........", "........", "...y....", "...yY...", "....Y...", "........", "........", "........"],
          front=["........", "........", "........", "........", "........", "........", "h......h", "hhhhhhhh"])
    s.box(BODY,
          top=["llllllll", "lltttlll", "lltttlll", "llttttll"],
          bottom=["PPPPPPPP"] * 4,
          front=["lDtuutDl",
                 "lltTttll",
                 "llttttll",
                 "lltTttll",
                 "llttttll",
                 "lltTttll",
                 "llttttll",
                 "DDDggDDD",
                 "ppPppPpp",
                 "ppPppPpp",
                 "ppPppPpp",
                 "PPPPPPPP"],
          right=["llll"] * 7 + ["DDDD", "pPpp", "pPpp", "pPpp", "PPPP"],
          left=["llll"] * 7 + ["DDDD", "ppPp", "ppPp", "ppPp", "PPPP"],
          back=["lllDllll"] * 7 + ["DDDDDDDD", "ppPppPpp", "ppPppPpp", "ppPppPpp", "PPPPPPPP"])
    s.box(BODY, overlay=True,
          front=["lD....Dl"] * 7 + ["........"] * 5,
          right=["llll"] * 7 + ["...."] * 5,
          left=["llll"] * 7 + ["...."] * 5,
          back=["lllDllll"] * 7 + ["........"] * 5)
    sleeve = ["tttt", "tttt", "DgDD", "tttt", "uuuu", "TuuT", "ssss", "ssss", "ssss", "ssss", "ssss", "sSSs"]
    s.limb(RARM, sides=sleeve, top=["tttt"] * 4, bottom=["ssss", "sSSs", "sSSs", "ssss"])
    # The missing finger: a dark gap and a pale stub on the left hand's front, and a gap underneath.
    s.limb(LARM, sides=sleeve, mirror=True,
           front=["tttt", "tttt", "DDgD", "tttt", "uuuu", "TuuT", "ssss", "ssss", "ssss", "ssss", "sxss", "sXss"],
           top=["tttt"] * 4, bottom=["ssss", "sXSs", "sSSs", "ssss"])
    leg = ["pPpp"] * 8 + ["DDDD", "bbbb", "bbbb", "BBBB"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["pppp"] * 4, bottom=["BBBB"] * 4)
    return s


# --------------------------------------------------------------------------------------------
# Silas Coinwright, Guild Treasurer: neat black hair with a side part, round gold spectacles and a
# thin moustache; a charcoal frock coat over a gold waistcoat with a watch chain, white cravat with a
# gold pin, gold cufflinks and one ink-stained finger.

def silas():
    s = Skin({
        "h": rgb("28221F"), "H": rgb("3C3530"), "d": rgb("141010"),
        "s": rgb("F0D4BE"), "S": rgb("DCBAA2"), "k": rgb("C4A088"),
        "e": rgb("323C5A"), "w": rgb("FAFAF6"),
        "g": rgb("F4D35E"), "G": rgb("C4A038"), "y": rgb("FCECA0"),
        "W": rgb("D6D6D4"),
        "c": rgb("2C2C34"), "C": rgb("1E1E24"), "v": rgb("3E3E4A"),
        "q": rgb("24242A"), "Q": rgb("1A1A1F"),
        "b": rgb("141416"), "B": rgb("464650"),
        "i": rgb("28305A"), "m": rgb("B07A6E"),
    })
    side = ["hhhhhhhh", "hhhhhhhs", "hhhhsSss", "hhhSksss", "hhhkSGGG", "hhhhssss", "hhhhsSss", "hhhhssss"]
    s.box(HEAD,
          top=["hHhdhhHh"] * 8,
          bottom=["hhhhhhhh"] * 4 + ["hhssssHh", "hhssssHh", "hhSSSShh", "hhhhhhhh"],
          front=["hhhdhhhh",
                 "hssssssh",
                 "shhsshhs",
                 "sGGSSGGs",
                 "GweGGewG",
                 "sGGssGGs",
                 "sshhhhss",
                 "sssmmssk"],
          right=side,
          left=[r[::-1] for r in side],
          back=["hHhhhhHh"] * 7 + ["hhhhhhhh"])
    s.box(BODY,
          top=["cccccccc", "cccccccc", "ccwwwwcc", "ccwwwwcc"],
          bottom=["QQQQQQQQ"] * 4,
          front=["cvWwwWvc",
                 "cvwgwwvc",
                 "cvgwwgvc",
                 "cvgGggvc",
                 "cvggggvc",
                 "cvgGyyvc",
                 "cvggggvc",
                 "cvgGggvc",
                 "cvqqQqvc",
                 "cvqqQqvc",
                 "cvqqQqvc",
                 "CCqqQqCC"],
          right=["cccc"] * 11 + ["CCCC"],
          left=["cccc"] * 11 + ["CCCC"],
          back=["cccccccc"] * 6 + ["ccGccGcc", "cccCcccc", "cccCcccc", "cccCcccc", "cccCcccc", "CCCCCCCC"])
    # The frock coat's lapels and tails stand off the body.
    s.box(BODY, overlay=True,
          front=["cv....vc"] * 11 + ["CC....CC"],
          right=["...."] * 7 + ["cccc"] * 4 + ["CCCC"],
          left=["...."] * 7 + ["cccc"] * 4 + ["CCCC"],
          back=["........"] * 7 + ["cccCcccc", "ccc..ccc", "ccc..ccc", "ccc..ccc", "CCC..CCC"])
    sleeve = ["cccc"] * 8 + ["wwwg", "ssss", "ssss", "sSSs"]
    s.limb(RARM, sides=sleeve, top=["cccc"] * 4, bottom=["ssss", "sSSs", "sSSs", "ssss"],
           front=["cccc"] * 8 + ["wwwg", "ssss", "sssi", "sSSi"])
    s.limb(LARM, sides=sleeve, mirror=True, top=["cccc"] * 4, bottom=["ssss", "sSSs", "sSSs", "ssss"])
    leg = ["qqQq"] * 10 + ["bbbb", "bBbb"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["qqqq"] * 4, bottom=["bbbb"] * 4)
    return s


# --------------------------------------------------------------------------------------------
# Captain Maren Holt, Trial Examiner: short ash-blonde hair in a braid, a scar through her left brow,
# steel breastplate and pauldrons under the Guild's blue tabard with a gold sword, leather belt and
# gloves, steel greaves, and a blue captain's cape with a gold edge and the same sword.

def maren():
    s = Skin({
        "h": rgb("C4B084"), "H": rgb("A8956A"), "L": rgb("D8C69C"),
        "s": rgb("E8C4A6"), "S": rgb("CEA88A"), "k": rgb("B08A6E"), "x": rgb("D68076"),
        "e": rgb("466078"), "w": rgb("F0F0F0"), "E": rgb("786446"), "m": rgb("A0605A"),
        "a": rgb("C0C8D0"), "A": rgb("8C94A0"), "i": rgb("E2E8EE"), "j": rgb("646C78"),
        "n": rgb("284078"), "N": rgb("1A2C58"), "g": rgb("DCB450"), "G": rgb("A8842E"),
        "l": rgb("644630"), "D": rgb("463020"), "t": rgb("3C3A42"),
    }, slim=True)
    streaks = "hLhhHhLh"
    side = ["hhhhhhhh", "LLLLhhhh", "hhhhhhhs", "hHhhSsss", "hhhhkSss", "hHhhssss", "hhhsssss", "hhhsSsss"]
    s.box(HEAD,
          top=[streaks] * 8,
          bottom=["hhhhhhhh"] * 4 + ["hhssssHh", "hhssssHh", "hhSSSShh", "hhhhhhhh"],
          front=["hhhhhhhh",
                 "hLLLhhhh",
                 "hEEssxEh",
                 "hwessewh",
                 "hsssSxsh",
                 "hssSSssh",
                 "sssmmsss",
                 "SsssssSs"],
          right=side,
          left=[r[::-1] for r in side],
          back=[streaks] * 4 + ["hLhllhLh"] + [streaks] * 3)
    # A short braid knotted at the back of the head.
    s.box(HEAD, overlay=True,
          back=["........", "........", "........", "........", "...hH...", "...Lh...", "...hH...", "...l...."])
    s.box(BODY,
          top=["aaaaaaaa", "aiaaaaia", "aiaaaaia", "aAaaaaAa"],
          bottom=["tttttttt"] * 4,
          front=["AaiiiiaA",
                 "aiannaia",
                 "aianGnia",
                 "aaggggaa",
                 "aiaggaia",
                 "jAnggnAj",
                 "DlDggDlD",
                 "tAnggnAt",
                 "tanggnat",
                 "tanggnat",
                 "tNnGGnNt",
                 "ttNNNNtt"],
          right=["aiaa"] * 5 + ["jjjj", "DlDl"] + ["tttt"] * 5,
          left=["aaia"] * 5 + ["jjjj", "lDlD"] + ["tttt"] * 5,
          back=["aiaaaaia"] * 5 + ["jjjjjjjj", "DlDDDDlD", "tnnnnnnt", "tnnnnnnt", "tnnnnnnt", "tNnnnnNt", "ttNNNNtt"])
    # The tabard hangs loose below the belt.
    s.box(BODY, overlay=True,
          front=["........"] * 7 + [".Anggna.", ".anggna.", ".anggna.", ".NnGGnN.", "..NNNN.."])
    sleeve = ["aiaa", "aiaa", "jjjj", "tttt", "tttt", "tttt", "AAAA", "aiaa", "aiaa", "llll", "llll", "DDDD"]
    pauldron = ["aiaa", "aiaa", "AAAA"] + ["...."] * 9
    for arm, mirror in ((RARM, False), (LARM, True)):
        s.limb(arm, sides=sleeve, mirror=mirror, top=["aaa", "aia", "aaa", "aaa"], bottom=["lll", "lDl", "lll", "lll"])
        s.limb(arm, overlay=True, sides=pauldron, mirror=mirror, top=["aia", "aaa", "aaa", "aia"])
    leg = ["tttt"] * 4 + ["AAAA", "aiaa", "aiaa", "aiaa", "AAAA", "llll", "llll", "DDDD"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["tttt"] * 4, bottom=["DDDD"] * 4)
    return s


def maren_cape():
    n, ndark, gold = rgb("284078"), rgb("1A2C58"), rgb("DCB450")
    cape = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    cp = cape.load()
    emblem = ["..........",
              "..........",
              "....gg....",
              "....gg....",
              "..gggggg..",
              "....gg....",
              "....gg....",
              "....gg....",
              "....gg....",
              "....gg....",
              "..........",
              "..........",
              "..........",
              "..........",
              "..........",
              ".........."]
    for x0 in (1, 12):
        for j in range(16):
            for i in range(10):
                edge = i in (0, 9) or j == 15
                cp[x0 + i, 1 + j] = gold if edge else (ndark if i in (3, 6) else n)
                if x0 == 1 and emblem[j][i] == "g":
                    cp[x0 + i, 1 + j] = gold
    for j in range(16):
        cp[0, 1 + j] = ndark
        cp[11, 1 + j] = ndark
    for i in range(10):
        cp[1 + i, 0] = ndark
        cp[11 + i, 0] = ndark
        cp[11 + i, 17] = gold
    return cape


# --------------------------------------------------------------------------------------------
# Pip Quillfeather, Guild Archivist: a mop of copper curls with a big quill stuck in them,
# freckles and an ink smudge; a green scholar's robe with a darker trim and sash, a satchel strap
# across the chest to a parchment-stuffed satchel on the back, and ink-stained fingers.

def pip():
    s = Skin({
        "h": rgb("C46232"), "H": rgb("A04C26"), "L": rgb("DC7C40"),
        "s": rgb("F4D4BA"), "S": rgb("E0B89C"), "k": rgb("C4987C"), "f": rgb("D69676"),
        "e": rgb("4A6A3C"), "w": rgb("FAFAF6"), "i": rgb("242848"), "m": rgb("C47A6E"),
        "g": rgb("9CC9A1"), "G": rgb("84B48A"), "D": rgb("4C7656"), "y": rgb("D8ECD8"),
        "l": rgb("785030"), "o": rgb("966840"), "b": rgb("583A22"),
        "p": rgb("ECDEB8"), "P": rgb("CEBC92"),
        "q": rgb("F6F4EC"), "Q": rgb("78AABE"), "c": rgb("5A3C28"),
    })
    curls = ["hLhhHhLh", "hhLhhLhh", "HhhLhhhL", "hLhhHhLh", "hhLhhLhh", "HhhLhhhL", "hLhhHhLh", "hhLhhLhh"]
    side = ["hLhhHhLh", "hhLhhLhh", "HhhLhhhs", "hLhhkSss", "hhLhSsss", "Hhhhhsss", "hLhhssss", "hhhhsSss"]
    s.box(HEAD,
          top=curls,
          bottom=["hhhhhhhh"] * 4 + ["hhssssHh", "hhssssHh", "hhSSSShh", "hhhhhhhh"],
          front=["hLhhHhLh",
                 "hhLhhLhh",
                 "hsHssHsh",
                 "hweSSweh",
                 "sfsSSsfs",
                 "ssfssfis",
                 "sssmmsss",
                 "SsssssSk"],
          right=side,
          left=[r[::-1] for r in side],
          back=curls)
    # Curls standing off the head at the fringe and crown, and the quill pushed into them.
    s.box(HEAD, overlay=True,
          top=["h......L", "........", "........", "........", "........", "........", "........", "L.h..h.h"],
          front=["L.h..h.h", "........", "........", "........", "........", "........", "........", "........"],
          left=["QQ......", "qQq.....", ".qq.....", "..q.....", "........", "........", "........", "........"],
          back=["h......L", "........", "........", "........", "........", "........", "........", "........"])
    robe = "ggDyyDgg"
    front = [robe] * 6 + ["DDDDDDpD", "gGDyyDGg", "gGDyyDGg", "gGDyyDGg", "gGDyyDGg", "GGDDDDGG"]
    # The satchel strap runs from the right shoulder down across the chest.
    front = [row[:j] + "l" + row[j + 1:] if j < 6 else row for j, row in enumerate(front)]
    back = ["gGggggGg"] * 6 + ["DDDDDDDD", "gGggggGg", "gGggggGg", "gGggggGg", "gGggggGg", "GGGGGGGG"]
    back = [row[:7 - j] + "l" + row[8 - j:] if j < 3 else row for j, row in enumerate(back)]
    s.box(BODY,
          top=["gggggggg", "gggggggg", "gggggggg", "ggDyyDgg"],
          bottom=["GGGGGGGG"] * 4,
          front=front,
          right=["gGgg"] * 6 + ["DDDD"] + ["gGgg"] * 4 + ["GGGG"],
          left=["ggGg"] * 6 + ["DDDD"] + ["ggGg"] * 4 + ["GGGG"],
          back=back)
    # The satchel on the back, stuffed with parchment, and the robe's skirt flaring out.
    s.box(BODY, overlay=True,
          back=["........", "........", "........", "..pPpP..", "..lllll.", "..lbbol.", "..lllll.", "..lllll.",
                "........", "........", "........", "........"],
          right=["...."] * 8 + ["gGgg", "gGgg", "gGgg", "GGGG"],
          left=["...."] * 8 + ["ggGg", "ggGg", "ggGg", "GGGG"])
    sleeve = ["gGgg"] * 8 + ["DDDD", "ssss", "ssss", "sSSs"]
    s.limb(RARM, sides=sleeve, top=["gggg"] * 4, bottom=["ssss", "sSSs", "sSSs", "ssss"],
           front=["gGgg"] * 8 + ["DDDD", "ssss", "ssis", "sSii"])
    s.limb(LARM, sides=sleeve, mirror=True, top=["gggg"] * 4, bottom=["ssss", "sSSs", "sSSs", "ssss"])
    leg = ["gGgg"] * 9 + ["GGGG", "cccc", "bbbb"]
    for lg, mirror in ((RLEG, False), (LLEG, True)):
        s.limb(lg, sides=leg, mirror=mirror, top=["gggg"] * 4, bottom=["bbbb"] * 4)
    return s


# Keyed by Staff.id() in the guild plugin; the textures are claudesmp:entity/guild/<id>.
STAFF = {
    "receptionist": lyra,
    "clerk": bram,
    "treasurer": silas,
    "examiner": maren,
    "archivist": pip,
}
CAPES = {"examiner": maren_cape}


# --------------------------------------------------------------------------------------------
# Preview: a small painter's-algorithm renderer of the player model, one quad per texel.

def boxes(slim, has_cape):
    aw = 3 if slim else 4
    parts = [
        (HEAD, (-4, 24, -4), (8, 8, 8)),
        (BODY, (-4, 12, -2), (8, 12, 4)),
        (RARM, (-4 - aw, 12, -2), (aw, 12, 4)),
        (LARM, (4, 12, -2), (aw, 12, 4)),
        (RLEG, (-4, 0, -2), (4, 12, 4)),
        (LLEG, (0, 0, -2), (4, 12, 4)),
    ]
    out = []
    for (base, over, _), pos, size in parts:
        out.append(("skin", base, pos, size, 0.0))
        out.append(("skin", over, pos, size, 0.5 if base == HEAD[0] else 0.25))
    if has_cape:
        out.append(("cape", (0, 0), (-5, 8, -3.2), (10, 16, 1), 0.0))
    return out


def render(skin, cape, slim, yaw, pitch, scale=10):
    import math
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p
        xr = x * cy + z * sy
        zr = -x * sy + z * cy
        return xr, y * cp - zr * sp, y * sp + zr * cp

    quads = []
    imgs = {"skin": skin.load(), "cape": cape.load() if cape else None}
    for tex, (u, v), (x0, y0, z0), (w, h, d), inf in boxes(slim, cape is not None):
        px = imgs[tex]
        X0, Y0, Z0 = x0 - inf, y0 - inf, z0 - inf
        X1, Y1, Z1 = x0 + w + inf, y0 + h + inf, z0 + d + inf
        sx, sy_, sz = (X1 - X0) / w, (Y1 - Y0) / h, (Z1 - Z0) / d
        rects = face_rects(u, v, w, h, d)
        if tex == "cape":
            # The cape's outer face is the 10x16 at (1,1); it faces away from the body (-z).
            rects = {"back": (1, 1, 10, 16), "front": (12, 1, 10, 16)}
        for name, (fx, fy, fw, fh) in rects.items():
            for j in range(fh):
                for i in range(fw):
                    c = px[fx + i, fy + j]
                    if c[3] == 0:
                        continue
                    if name == "front":
                        pts = [(X0 + a * sx, Y1 - b * sy_, Z1) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (0, 0, 1)
                    elif name == "back":
                        pts = [(X1 - a * sx, Y1 - b * sy_, Z0) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (0, 0, -1)
                    elif name == "right":
                        pts = [(X0, Y1 - b * sy_, Z0 + a * sz) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (-1, 0, 0)
                    elif name == "left":
                        pts = [(X1, Y1 - b * sy_, Z1 - a * sz) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (1, 0, 0)
                    elif name == "top":
                        pts = [(X0 + a * sx, Y1, Z0 + b * sz) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (0, 1, 0)
                    else:
                        pts = [(X0 + a * sx, Y0, Z1 - b * sz) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                        n = (0, -1, 0)
                    if project(n)[2] <= 1e-6:
                        continue
                    proj = [project(p) for p in pts]
                    depth = sum(p[2] for p in proj) / 4
                    shade = {"top": 1.0, "front": 0.92, "back": 0.92, "right": 0.8, "left": 0.8, "bottom": 0.6}[name]
                    col = tuple(int(ch * shade) for ch in c[:3]) + (255,)
                    quads.append((depth, [(p[0], p[1]) for p in proj], col))
    quads.sort(key=lambda q: q[0])
    pad = 2
    xs = [p[0] for q in quads for p in q[1]]
    ys = [p[1] for q in quads for p in q[1]]
    minx, maxx, miny, maxy = min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad
    W, H = int((maxx - minx) * scale), int((maxy - miny) * scale)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for _, pts, col in quads:
        draw.polygon([((x - minx) * scale, (maxy - y) * scale) for x, y in pts], fill=col, outline=col)
    return img


def preview(skin, cape, slim):
    views = [render(skin, cape, slim, yaw, 12) for yaw in (25, -35, 180 + 25)]
    W = sum(v.width for v in views) + 20 * (len(views) - 1)
    H = max(v.height for v in views)
    sheet = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = 0
    for v in views:
        sheet.alpha_composite(v, (x, H - v.height))
        x += v.width + 20
    return sheet


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    previews = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    for staff, draw in STAFF.items():
        skin = draw()
        skin.img.save(OUT / f"{staff}.png")
        cape = CAPES[staff]() if staff in CAPES else None
        if cape:
            cape.save(OUT / f"{staff}_cape.png")
        if previews:
            previews.mkdir(parents=True, exist_ok=True)
            preview(skin.img, cape, skin.slim).save(previews / f"{staff}_preview.png")
        print(f"{staff}: {OUT / (staff + '.png')}")


if __name__ == "__main__":
    main()
