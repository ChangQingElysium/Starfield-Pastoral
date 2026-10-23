"""The Fabric index must retain tests outside the conventional gametest package."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "fabric_gametest_index", Path(__file__).resolve().parents[1] / "scripts/port/fabric_gametest_index.py")
INDEX = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INDEX)


class FabricGameTestIndexTests(unittest.TestCase):
    def write(self, root, relative, source):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        return path

    def test_indexes_every_source_package_without_counting_holders_comments_or_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "src/main/java/demo/gametest/MainTests.java", '''
package demo.gametest;
@GameTestHolder("demo") @PrefixGameTestTemplate(false)
public class MainTests {
 private MainTests() {}
 @GameTest(templateNamespace="demo", template="empty") public static void first(GameTestHelper h) {}
 @GameTest(template="empty") public static void second(GameTestHelper h) {}
 String ignored = "@GameTest @GameTestHolder";
 // @GameTest void comment() {}
}''')
            self.write(root, "src/main/java/demo/animals/OtherTests.java", '''
package demo.animals; @GameTestHolder("demo_animals") public class OtherTests {
 @net.minecraft.gametest.framework.GameTest(template="empty") public static void third(GameTestHelper h) {}
}''')
            self.write(root, "src/main/java/demo/Plain.java", "package demo; class Plain {}")
            result = INDEX.index_sources(root)
            self.assertEqual(3, result["total_tests"])
            self.assertEqual(["demo.animals.OtherTests", "demo.gametest.MainTests"],
                             [entry["class_name"] for entry in result["classes"]])
            self.assertEqual([1, 2], [entry["test_count"] for entry in result["classes"]])

    def test_source_hash_tracks_a_test_body_change_without_changing_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = self.write(root, "src/main/java/demo/Tests.java", """
package demo; @GameTestHolder("demo") public class Tests {
 @GameTest public static void real(GameTestHelper h) { assertOriginal(); }
} """)
            before = INDEX.index_sources(root)
            path.write_text(path.read_text().replace("assertOriginal()", "assertUpdated()"))
            after = INDEX.index_sources(root)
            self.assertEqual(before["total_tests"], after["total_tests"])
            self.assertNotEqual(before["classes"][0]["source_sha256"], after["classes"][0]["source_sha256"])

    def test_outputs_are_portable_and_confined_to_fabric_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = self.write(root, "src/main/java/demo/Tests.java", """
package demo; @GameTestHolder("demo") public class Tests { @GameTest public static void real(GameTestHelper h) {} }
""")
            output = root / "fabric/build/generated/resources/gametest/META-INF/stardewcraft/fabric-gametest-index.json"
            report = root / "fabric/build/reports/gametest-index.json"
            result = INDEX.write_index(root, output, report)
            self.assertEqual(result, json.loads(output.read_text()))
            self.assertEqual(1, json.loads(report.read_text())["source_classes"])
            self.assertNotIn(str(root), output.read_text())
            with self.assertRaisesRegex(ValueError, "inside fabric/build"):
                INDEX.write_index(root, source)

    def test_empty_suites_and_unheld_test_classes_fail_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "src/main/java/demo/Plain.java", "package demo; class Plain {}")
            with self.assertRaisesRegex(ValueError, "No GameTests"):
                INDEX.index_sources(root)
            self.write(root, "src/main/java/demo/Tests.java",
                       "package demo; class Tests { @GameTest void real(GameTestHelper h) {} }")
            with self.assertRaisesRegex(ValueError, "GameTestHolder"):
                INDEX.index_sources(root)


if __name__ == "__main__":
    unittest.main()
