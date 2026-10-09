"""Checks the stonecutter wood recipes players actually get: MasterCutter's pinned zip with
server/datapacks/mastercutter-fixes laid over it. Every recipe under wood/<wood>/ must name only
that wood's items (the zip alone fails this: dark and pale oak are cherry copies), the overrides
must keep upstream's ids and counts, and the committed pack must match a fresh generate.py run.

Run from the repo root: python -m unittest discover -s server/mastercutter-fixes
"""

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import generate as gen


def committed_overrides():
    return {
        path.relative_to(gen.PACK).as_posix(): path.read_text(encoding="utf-8")
        for path in (gen.PACK / gen.RECIPES).rglob("*.json")
    }


class MasterCutterFixesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(gen.upstream_zip()) as zf:
            cls.upstream = gen.wood_recipes(zf)
            cls.generated = gen.fixes(zf)
        cls.overrides = committed_overrides()
        cls.effective = {**cls.upstream, **cls.overrides}

    def test_every_wood_recipe_names_only_its_own_wood(self):
        wrong = {
            path: sorted(gen.foreign_woods(path, text))
            for path, text in self.effective.items()
            if gen.foreign_woods(path, text)
        }
        self.assertEqual(wrong, {})

    def test_dark_oak_log_cuts_into_dark_oak_planks(self):
        recipe = json.loads(
            self.effective[gen.RECIPES + "dark_oak/dark_oak_planks_from_dark_oak_log.json"]
        )
        self.assertEqual(recipe["ingredient"], "minecraft:dark_oak_log")
        self.assertEqual(recipe["result"], {"id": "minecraft:dark_oak_planks", "count": 4})

    def test_overrides_replace_existing_ids_only(self):
        self.assertTrue(self.overrides)
        self.assertLessEqual(set(self.overrides), set(self.upstream))

    def test_fixed_recipes_match_their_oak_twin(self):
        # Oak is correct upstream and has the same item set as dark and pale oak.
        for path, text in self.overrides.items():
            wood = gen.folder_wood(path)
            oak_path = path.replace(f"/{wood}/", "/oak/").replace(f"{wood}_", "oak_")
            oak_as_wood = gen.fix(path, self.upstream[oak_path])
            self.assertEqual(json.loads(text), json.loads(oak_as_wood), path)

    def test_committed_pack_matches_generator(self):
        with tempfile.TemporaryDirectory() as tmp:
            gen.write(Path(tmp), self.generated)
            fresh = {
                path.relative_to(tmp).as_posix(): path.read_text(encoding="utf-8")
                for path in (Path(tmp) / gen.RECIPES).rglob("*.json")
            }
        self.assertEqual(fresh, self.overrides)


if __name__ == "__main__":
    unittest.main()
