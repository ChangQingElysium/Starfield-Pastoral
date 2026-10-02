"""Forge external automation conversion remains part of the normal future-sync pipeline."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "rewrite_1201.py"
ROOT = SCRIPT.parents[2]
spec = importlib.util.spec_from_file_location("port_rewrite", SCRIPT)
rewrite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rewrite)


class MachineExtensionRewriteTest(unittest.TestCase):
    def source(self, name, braced=True):
        branch = "{ return null; }" if braced else "return null;"
        return ("package example;\nclass " + name + " {\n"
                " public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {\n"
                "  if (state.getValue(PART) == Part.EXTENSION) " + branch + "\n"
                "  return new MachineBlockEntity(pos, state);\n }\n"
                " public Object getTicker() { if (state.getValue(PART) == Part.EXTENSION) return null; }\n}\n")

    def test_factories_only_and_idempotent(self):
        for name in ("AbstractTwoBlockUtilityBlock", "KegBlock", "ReclamationMachineBlock", "DeluxeWormBinBlock", "HeavyFurnaceBlock"):
            for braced in (True, False):
                with self.subTest(name=name, braced=braced):
                    converted = rewrite.rewrite(self.source(name, braced), [])
                    self.assertIn("PortMachineExtensions.createExtension(pos, state)", converted)
                    self.assertIn("return new MachineBlockEntity(pos, state)", converted)
                    self.assertIn("getTicker() { if (state.getValue(PART) == Part.EXTENSION) return null;", converted)
                    self.assertEqual(rewrite.rewrite(converted, []), converted)

    def test_non_registered_entities_unchanged(self):
        for name in ("FarmComputerBlock", "FridgeBlock", "MapDecorStaticBlock", "ShippingBinBlock", "MailboxBlock"):
            source = self.source(name)
            self.assertEqual(rewrite.rewrite_machine_extensions(source), source)

    def test_valid_blocks_are_the_provider_blocks_without_additions(self):
        source = ("package example;\nclass UtilityAutomationCapabilities { void register() {\n"
                  " event.registerBlock(Capabilities.ItemHandler.BLOCK, UtilityAutomationCapabilities::getAutomationFromMultiblock,\n"
                  " ModBlocks.KEG.get(), ModBlocks.LOOM.get());\n }\n}\n")
        converted = rewrite.rewrite(source, [])
        self.assertIn("multiblockAutomationBlocks());", converted)
        self.assertIn("ModBlocks.KEG.get(), ModBlocks.LOOM.get()};", converted)
        self.assertEqual(converted.count("ModBlocks.KEG.get()"), 1)
        self.assertEqual(rewrite.rewrite(converted, []), converted)

    def test_heavy_tapper_upper_bridges_only_existing_owner(self):
        source = (ROOT / "src/main/java/com/stardew/craft/gingerisland/HeavyTapperBlock.java").read_text()
        factory = source.split("public BlockEntity newBlockEntity", 1)[1].split("\n    }", 1)[0]
        ticker = source.split("BlockEntityTicker<T> getTicker", 1)[1].split("\n    }", 1)[0]
        self.assertIn("isMainPart(state) ? super.newBlockEntity(pos, state)", factory)
        self.assertIn("PortMachineExtensions.createExtension(pos, state)", factory)
        self.assertNotIn("new TapperBlockEntity", factory)
        self.assertIn("isMainPart(state) ? super.getTicker(level, state, type) : null", ticker)
        self.assertIn("productionMultiplier() { return 2; }", source)
        self.assertIn("public List<ItemStack> getDrops", source)
        self.assertIn("isMainPart(state) ? super.getDrops(state, params) : List.of()", source)

    def test_separately_registered_heavy_tapper_is_a_valid_upper_proxy(self):
        source = (ROOT / "src/port/java/com/stardew/craft/port/PortMachineExtensions.java").read_text()
        self.assertIn("automationExtensionBlocks()).build(null)", source)
        self.assertIn("UtilityAutomationCapabilities.multiblockAutomationBlocks()", source)
        self.assertIn('supported[multiblocks.length] = GingerIslandBlocks.get("ginger_heavy_tapper")', source)
        detection = source.split("boolean isAutomationExtension", 1)[1].split("\n    }", 1)[0]
        self.assertLess(detection.index("PortCapabilities.isBlockRegistered"),
                        detection.index("instanceof HeavyTapperBlock"))
        self.assertIn("return state.getValue(HeavyTapperBlock.UPPER)", detection)
        self.assertIn('part.getSerializedName().equals("extension")', detection)
        self.assertIn("owner.getCapability(ForgeCapabilities.ITEM_HANDLER, side)", source)
        self.assertIn("!(owner instanceof UtilityAutomationAccess)", source)
        self.assertNotIn("extends TimedProductionBlockEntity", source)
        self.assertNotIn("implements Container", source)

    def test_ostrich_inherits_one_owner_factory_and_ticker_free_extension(self):
        ostrich = (ROOT / "src/main/java/com/stardew/craft/gingerisland/OstrichIncubatorBlock.java").read_text()
        incubator = (ROOT / "src/main/java/com/stardew/craft/block/utility/IncubatorBlock.java").read_text()
        registrations = (ROOT / "src/main/java/com/stardew/craft/blockentity/ModBlockEntities.java").read_text()
        self.assertIn("OstrichIncubatorBlock extends IncubatorBlock", ostrich)
        self.assertNotIn("newBlockEntity(", ostrich)
        self.assertNotIn("getTicker(", ostrich)
        factory = incubator.split("BlockEntity newBlockEntity", 1)[1].split("\n    }", 1)[0]
        ticker = incubator.split("BlockEntityTicker<T> getTicker", 1)[1].split("\n    }", 1)[0]
        self.assertIn("PortMachineExtensions.createExtension(pos, state)", factory)
        self.assertEqual(factory.count("new IncubatorBlockEntity(pos, state)"), 1)
        self.assertRegex(ticker, r"state.getValue\(PART\) == Part.EXTENSION\) \{\s*return null;")
        self.assertIn('GingerIslandBlocks.get("ginger_ostrich_incubator_empty")', registrations)
        self.assertIn("Math.floorDiv(now + minutes + 1599, 1600) * 1600", ostrich)


if __name__ == "__main__":
    unittest.main()
