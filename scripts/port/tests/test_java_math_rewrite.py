"""Keep Java ceiling division intact when source machine clocks are synchronized."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "rewrite_1201.py"
spec = importlib.util.spec_from_file_location("port_math_rewrite", SCRIPT)
rewrite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rewrite)


class JavaMathRewriteTest(unittest.TestCase):
    def test_ceiling_division_owners_and_idempotence(self):
        for owner in ("Math", "StrictMath", "java.lang.Math", "java.lang.StrictMath"):
            with self.subTest(owner=owner):
                source = "class Clock { long migrated = " + owner + ".ceilDiv(minutes * 1600L, 1260); }"
                converted = rewrite.rewrite(source, [])
                self.assertIn("com.stardew.craft.port.PortJava.ceilDiv(minutes * 1600L, 1260)", converted)
                self.assertNotIn("Math.ceilDiv", converted)
                self.assertEqual(rewrite.rewrite(converted, []), converted)

    def test_unrelated_owners_are_not_rewritten(self):
        source = "class Clock { long value = custom.Math.ceilDiv(x, y); }"
        self.assertEqual(rewrite.rewrite(source, []), source)


if __name__ == "__main__":
    unittest.main()
