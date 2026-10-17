import tempfile
import unittest
from pathlib import Path

import build


class GradleVersionTest(unittest.TestCase):
    def test_reads_the_project_version_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            script = Path(tmp) / "build.gradle.kts"
            script.write_text('plugins {\n    id("x") version "1.18.4"\n}\n\nversion = "1.0.1"\n', encoding="utf-8")
            # The plugin's own `version "1.18.4"` sits indented inside plugins {}, so only the
            # top-level assignment may match.
            self.assertEqual(build.gradle_version(script), "1.0.1")

    def test_the_real_treemod_script_has_a_version(self):
        self.assertRegex(build.gradle_version(build.REPO / "treemod" / "build.gradle.kts"), r"^\d+\.\d+\.\d+$")


if __name__ == "__main__":
    unittest.main()
