"""Exercise the public-file gate against a real temporary Git index."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from check_repository_contents import check


class RepositoryContentsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)

    def add(self, path, content="fixture"):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        subprocess.run(["git", "add", "-f", "--", path], cwd=self.root, check=True)

    def test_force_added_private_note_is_rejected(self):
        self.add(".gitignore", "docs/*\n!docs/public.md\n")
        self.add("docs/public.md")
        self.add("docs/private.md")
        self.assertEqual(check(self.root), ["docs/private.md: excluded by repository ignore policy"])

    def test_outputs_are_rejected_even_without_ignore_rules(self):
        for path in ("javac.123.args", "fabric/build/report.json", "fabric/run-client/options.txt",
                     "libs/unused.jar", "scripts/data/forest_farm_report.json"):
            self.add(path)
        problems = check(self.root)
        self.assertEqual(len(problems), 5)

    def test_required_assets_and_untracked_local_work_are_preserved(self):
        for path in ("assets-src/npc/example/model.bbmodel", "src/main/resources/pregen/world/region/r.0.0.mca",
                     "compatibility/fixtures/result.json", "gradle/wrapper/gradle-wrapper.jar",
                     "libs/Jade-1.21.1-NeoForge-15.10.4.jar"):
            self.add(path)
        (self.root / "local.log").write_text("untracked")
        self.assertEqual(check(self.root), [])
        self.assertTrue((self.root / "local.log").exists())


if __name__ == "__main__":
    unittest.main()
