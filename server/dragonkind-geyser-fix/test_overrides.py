"""Checks the Dragonkind Evolved enchantments players actually get: the committed zip with
server/datapacks/dragonkind-geyser-fix laid over it. Geyser preview pr.6712 (builds 3090 and 3091) can't
read an enchantment whose exclusive_set is a single enchantment id rather than a #tag: resolving
that id needs the enchantment registry it is still building, so the whole registry is left unusable
for the session and every enchanted item fails to translate to Bedrock. Every effective
exclusive_set must therefore be a tag, and each override must otherwise equal upstream, with its
tag holding exactly the id upstream named.

Run from the repo root: python -m unittest discover -s server/dragonkind-geyser-fix
"""

import json
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UPSTREAM = ROOT / "server" / "datapacks" / "dragonkind-evolved-v1.15.zip"
PACK = ROOT / "server" / "datapacks" / "dragonkind-geyser-fix"


def upstream_enchantments():
    with zipfile.ZipFile(UPSTREAM) as zf:
        return {
            name: json.loads(zf.read(name))
            for name in zf.namelist()
            if "/enchantment/" in name and name.endswith(".json") and "/tags/" not in name
        }


def override_enchantments():
    return {
        path.relative_to(PACK).as_posix(): json.loads(path.read_text(encoding="utf-8"))
        for path in (PACK / "data").rglob("enchantment/*.json")
        if "tags" not in path.parts
    }


def tag_values(tag):
    namespace, path = tag.removeprefix("#").split(":", 1)
    file = PACK / "data" / namespace / "tags" / "enchantment" / f"{path}.json"
    return json.loads(file.read_text(encoding="utf-8"))["values"]


class DragonkindGeyserFixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upstream = upstream_enchantments()
        cls.overrides = override_enchantments()
        cls.effective = {**cls.upstream, **cls.overrides}

    def test_every_exclusive_set_is_a_tag(self):
        not_tags = {
            name: data["exclusive_set"]
            for name, data in self.effective.items()
            if "exclusive_set" in data and not str(data["exclusive_set"]).startswith("#")
        }
        self.assertEqual(not_tags, {})

    def test_overrides_only_swap_the_id_for_a_tag_of_that_id(self):
        for name, data in self.overrides.items():
            with self.subTest(name):
                self.assertIn(name, self.upstream, "override for an enchantment upstream doesn't have")
                original = self.upstream[name]
                self.assertEqual({**data, "exclusive_set": None}, {**original, "exclusive_set": None})
                self.assertEqual(tag_values(data["exclusive_set"]), [original["exclusive_set"]])


if __name__ == "__main__":
    unittest.main()
