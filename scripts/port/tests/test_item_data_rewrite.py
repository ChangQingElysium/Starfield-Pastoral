"""Static CustomData helpers are not ItemStack instance component calls."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "rewrite_1201.py"
spec = importlib.util.spec_from_file_location("port_item_data_rewrite", SCRIPT)
rewrite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rewrite)


class ItemDataRewriteTest(unittest.TestCase):
    def test_static_custom_data_update_and_set_are_preserved(self):
        for owner in ("CustomData", "net.minecraft.world.item.component.CustomData",
                      "com.stardew.craft.port.net.minecraft.world.item.component.CustomData"):
            for method in ("update", "set"):
                with self.subTest(owner=owner, method=method):
                    source = owner + "." + method + "(DataComponents.CUSTOM_DATA, stack, updater);"
                    self.assertEqual(rewrite.rewrite_item_data(source), source)

    def test_stack_component_methods_are_still_rewritten(self):
        for method in ("get", "set", "has", "remove", "getOrDefault", "update"):
            with self.subTest(method=method):
                source = "stack." + method + "(DataComponents.CUSTOM_DATA, value);"
                converted = rewrite.rewrite_item_data(source)
                self.assertIn("PortItemData." + method + "(stack, DataComponents.CUSTOM_DATA, value);", converted)
                self.assertEqual(rewrite.rewrite_item_data(converted), converted)


if __name__ == "__main__":
    unittest.main()
