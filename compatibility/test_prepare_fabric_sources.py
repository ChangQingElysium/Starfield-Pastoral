"""Regression checks for source completeness and protecting maintained files."""

import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "prepare_fabric_sources",
    Path(__file__).resolve().parents[1] / "scripts/port/prepare_fabric_sources.py",
)
PORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PORT)


class PrepareFabricSourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "src/main/java/demo"
        self.source.mkdir(parents=True)
        self.output = self.root / "fabric/build/generated/sources"
        self.report = self.root / "fabric/build/reports/port.json"

    def write_override(self, body):
        override = self.root / "fabric/src/overrides/java/demo/A.java"
        override.parent.mkdir(parents=True, exist_ok=True)
        fingerprint = hashlib.sha256((self.source / "A.java").read_bytes()).hexdigest()
        override.write_text(f"// Upstream-SHA256: {fingerprint}\n" + body, encoding="utf-8")
        return override

    def test_complete_overlay_preserves_upstream_and_unsupported_code(self):
        original = (
            "import net.neoforged.neoforge.registries.DeferredRegister;\n"
            "import net.neoforged.neoforge.example.UnreviewedApi;\n"
            "class Example { void play() { originalGameplay(); } }\n"
        )
        path = self.source / "Example.java"
        path.write_text(original, encoding="utf-8")
        (self.source / "Other.java").write_text("class Other {}\n", encoding="utf-8")
        result = PORT.prepare(self.root, self.output, self.report)
        self.assertEqual(2, result["source_files"])
        self.assertEqual(2, len(list(self.output.rglob("*.java"))))
        self.assertEqual(original, path.read_text(encoding="utf-8"))
        generated = (self.output / "demo/Example.java").read_text(encoding="utf-8")
        self.assertIn("com.stardew.craft.platform.fabric.registry.DeferredRegister", generated)
        self.assertIn("import net.neoforged.neoforge.example.UnreviewedApi;", generated)
        self.assertIn("originalGameplay();", generated)
        self.assertIn("net.neoforged.neoforge.example.UnreviewedApi", result["remaining_neoforge_references"])
        self.assertEqual(result, json.loads(self.report.read_text(encoding="utf-8")))

    def test_idempotence_and_removal_only_in_owned_output(self):
        (self.source / "A.java").write_text("class A {}", encoding="utf-8")
        first = PORT.prepare(self.root, self.output, self.report)
        second = PORT.prepare(self.root, self.output, self.report)
        self.assertEqual(first, second)
        (self.source / "A.java").unlink()
        (self.source / "B.java").write_text("class B {}", encoding="utf-8")
        third = PORT.prepare(self.root, self.output, self.report)
        self.assertFalse((self.output / "demo/A.java").exists())
        self.assertNotEqual(first["source_fingerprint"], third["source_fingerprint"])

    def test_refuses_to_overwrite_maintained_or_unowned_files(self):
        (self.source / "A.java").write_text("class A {}", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "inside"):
            PORT.prepare(self.root, self.source, self.report)
        self.output.mkdir(parents=True)
        (self.output / "Handwritten.java").write_text("preserve me", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unowned"):
            PORT.prepare(self.root, self.output, self.report)
        self.assertEqual("preserve me", (self.output / "Handwritten.java").read_text(encoding="utf-8"))

    def test_empty_upstream_is_an_error(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            PORT.prepare(self.root, self.output, self.report)

    def test_native_override_keeps_coverage_and_records_its_source(self):
        upstream = self.source / "A.java"
        upstream.write_text("class A { int old; }", encoding="utf-8")
        override = self.write_override("class A { int nativeFabric; }")
        result = PORT.prepare(self.root, self.output, self.report)
        self.assertEqual("class A { int old; }", upstream.read_text(encoding="utf-8"))
        self.assertTrue((self.output / "demo/A.java").read_text(encoding="utf-8").endswith("class A { int nativeFabric; }"))
        self.assertEqual("fabric/src/overrides/java/demo/A.java", result["files"][0]["override"])
        (self.source / "A.java").rename(self.source / "B.java")
        with self.assertRaisesRegex(ValueError, "existing upstream"):
            PORT.prepare(self.root, self.output, self.report)

    def test_environment_side_mapping_preserves_dedicated_server_guard(self):
        result, _ = PORT.convert(
            "import net.neoforged.api.distmarker.Dist;\n"
            "import net.neoforged.api.distmarker.OnlyIn;\n"
            "import net.neoforged.fml.loading.FMLEnvironment;\n"
            "@OnlyIn(Dist.CLIENT) class A { boolean client = FMLEnvironment.dist.isClient(); "
            "boolean server = FMLEnvironment.dist == Dist.DEDICATED_SERVER; }"
        )
        self.assertIn("@Environment(EnvType.CLIENT)", result)
        self.assertIn("getEnvironmentType() == EnvType.SERVER", result)
        self.assertNotIn("FMLEnvironment", result)

    def test_persistent_data_rewrite_is_scoped_and_ignores_non_code(self):
        source = (
            "class A { void tick() { player.getPersistentData(); this.getPersistentData(); "
            "getPersistentData(); event.getEntity().getPersistentData(); owner.player.getPersistentData(); } "
            'String text = "player.getPersistentData()"; /* player.getPersistentData() */ }'
        )
        converted, changes = PORT.convert(source, "com/stardew/craft/monster/StardewMonsterEntity.java")
        self.assertEqual(3, changes["Entity.getPersistentData"])
        self.assertIn("PersistentEntityData.get(player)", converted)
        self.assertEqual(2, converted.count("PersistentEntityData.get(this)"))
        self.assertIn("event.getEntity().getPersistentData()", converted)
        self.assertIn("owner.player.getPersistentData()", converted)
        self.assertIn('"player.getPersistentData()"', converted)
        self.assertIn("/* player.getPersistentData() */", converted)
        other, _ = PORT.convert("getPersistentData();", "example/Unknown.java")
        self.assertEqual("getPersistentData();", other)

    def test_update_checker_queries_and_reads_the_fabric_loader(self):
        source = (
            'import net.neoforged.fml.ModList;\n'
            'String filter = "[\\"neoforge\\"]";\n'
            'ModList.get().getModContainerById("stardewcraft")'
            '.map(container -> container.getModInfo().getVersion().toString());'
        )
        generated, _ = PORT.convert(source, "com/stardew/craft/player/ModUpdateChecker.java")
        self.assertIn(r'[\"fabric\"]', generated)
        self.assertIn('FabricLoader.getInstance().getModContainer(', generated)
        self.assertIn('getMetadata().getVersion().getFriendlyString()', generated)

    def test_concurrent_override_edit_fails_instead_of_claiming_a_coherent_overlay(self):
        (self.source / "A.java").write_text("class A {}", encoding="utf-8")
        override = self.write_override("class A { int before; }")
        original_convert = PORT.convert

        def editing_convert(source, relative=""):
            converted = original_convert(source, relative)
            override.write_text("class A { int after; }", encoding="utf-8")
            return converted

        with patch.object(PORT, "convert", editing_convert):
            with self.assertRaisesRegex(ValueError, "overrides changed"):
                PORT.prepare(self.root, self.output, self.report)

    def test_changed_upstream_under_an_override_requires_review(self):
        upstream = self.source / "A.java"
        upstream.write_text("class A {}", encoding="utf-8")
        self.write_override("class A { int fabric; }")
        upstream.write_text("class A { int newUpstreamFeature; }", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "review it first"):
            PORT.prepare(self.root, self.output, self.report)

    def test_saved_data_factories_keep_arguments_and_unrelated_factories(self):
        source = (
            'class Data extends SavedData { Factory<Data> a = new Factory<>(Data::new, Data::load); '
            'Object b = new SavedData.Factory<>(Data::new, (tag, lookup) -> new Data(tag), null); '
            'String text = "new SavedData.Factory<>(wrong)"; }'
        )
        converted, changes = PORT.convert(source)
        self.assertEqual(2, changes["SavedData.Factory.nullableDataFixType"])
        self.assertIn('SavedDataFactories.create(Data::new, Data::load)', converted)
        self.assertIn('SavedDataFactories.create(Data::new, (tag, lookup) -> new Data(tag), null)', converted)
        self.assertIn('"new SavedData.Factory<>(wrong)"', converted)
        unrelated, _ = PORT.convert('class Renderer { Object a = new Factory<>(Renderer::new); }')
        self.assertIn('new Factory<>(Renderer::new)', unrelated)

    def test_item_ability_conversion_preserves_dynamic_overrides_and_only_audited_receivers(self):
        source = ('public class HoeItem extends Item implements IStardewItem { '
                  'HoeItem(Properties properties) { super(properties.setNoRepair()); } '
                  '@Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) { '
                  'return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability); } }')
        converted, _ = PORT.convert(source, 'com/stardew/craft/item/tool/HoeItem.java')
        self.assertIn('implements IStardewItem, com.stardew.craft.platform.fabric.extension.ItemAbilityProvider', converted)
        self.assertIn('@Override public boolean canPerformAction(', converted)
        self.assertIn('ItemRepairs.setNoRepair(properties)', converted)
        source = ('tool.canPerformAction(ItemAbilities.AXE_DIG); main.canPerformAction(action); '
                  'holder.stack.canPerformAction(action); getTool().canPerformAction(action); '
                  'String note = "stack.canPerformAction(action)";')
        converted, changes = PORT.convert(source, 'com/stardew/craft/event/WildTreeChopEvents.java')
        self.assertEqual(2, changes['ItemAbility.stackSensitiveQueries'])
        self.assertIn('ItemActions.canPerformAction(tool, ItemAbilities.AXE_DIG)', converted)
        self.assertIn('holder.stack.canPerformAction(action)', converted)
        self.assertIn('"stack.canPerformAction(action)"', converted)
        untouched, _ = PORT.convert(source, 'demo/Unknown.java')
        self.assertEqual(source, untouched)

    def test_tool_modification_distinguishes_state_queries_from_super_dispatch(self):
        source = ('public class TerrainDirtBlock extends Block { '
                  'return super.getToolModifiedState(state, context, ability, simulate); '
                  'return Blocks.DIRT.getToolModifiedState(Blocks.DIRT.defaultBlockState(), context, ability, simulate); }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/block/terrain/TerrainDirtBlock.java')
        self.assertEqual(3, changes['BlockState.cancellableToolModification'])
        self.assertIn('implements com.stardew.craft.platform.fabric.extension.ToolModificationProvider', converted)
        self.assertIn('BlockActions.defaultModifiedState(state, context, ability, simulate)', converted)
        self.assertIn('BlockActions.defaultModifiedState(Blocks.DIRT.defaultBlockState(), context, ability, simulate)', converted)
        source = ('level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, true); '
                  'String info = "state.getToolModifiedState(context, action, true)";')
        converted, changes = PORT.convert(source, 'com/stardew/craft/gametest/SandySoilGameTests.java')
        self.assertEqual(1, changes['BlockState.cancellableToolModification'])
        self.assertIn('BlockActions.getModifiedState(level.getBlockState(pos), context, ItemAbilities.HOE_TILL, true)', converted)
        self.assertIn('"state.getToolModifiedState(context, action, true)"', converted)

    def test_data_maps_use_exact_holder_receivers_without_changing_other_get_data(self):
        source = ('return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())\n'
                  '  .getData(StardewDataMaps.FOOD_EFFECTS); data.getData();')
        converted, changes = PORT.convert(source, 'com/stardew/craft/api/v1/item/StardewFoodEffects.java')
        self.assertEqual(1, changes['Holder.ownerScopedDataMapQueries'])
        self.assertIn('DataMaps.get(BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()), StardewDataMaps.FOOD_EFFECTS)', converted)
        self.assertIn('data.getData()', converted)

    def test_block_entity_load_conversion_preserves_super_and_inherited_methods(self):
        source = ('public class WoodenChestBlockEntity extends BlockEntity implements Container { '
                  '@Override public void onLoad() { super.onLoad(); maintainContents(); } }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/blockentity/WoodenChestBlockEntity.java')
        self.assertEqual(1, changes['BlockEntity.deferredOnLoad'])
        self.assertIn('extends com.stardew.craft.platform.fabric.blockentity.FabricBlockEntity implements Container', converted)
        self.assertIn('super.onLoad(); maintainContents();', converted)

    def test_manual_floor_capture_retains_the_body_and_deferred_assertions(self):
        source = '''
int snapshots = level.capturedBlockSnapshots.size();
level.captureBlockSnapshots = true;
try {
    originalWrites(); assertFloorStillPresent();
} finally {
    level.captureBlockSnapshots = false;
    level.capturedBlockSnapshots.subList(snapshots,level.capturedBlockSnapshots.size()).clear();
}
helper.runAfterDelay(2, () -> { assertCommittedFloorRemoved(); });
'''
        converted, changes = PORT.convert(source, 'com/stardew/craft/gametest/SurfaceFloorGameTests.java')
        self.assertEqual(1, changes['SurfaceFloorGameTests.scopedPlacementCapture'])
        self.assertIn('PlacementTransactions.beginCapture(level)', converted)
        self.assertIn('originalWrites(); assertFloorStillPresent();', converted)
        self.assertIn('helper.runAfterDelay(2, () -> { assertCommittedFloorRemoved(); });', converted)
        self.assertNotIn('capturedBlockSnapshots', converted)
        with self.assertRaisesRegex(ValueError, 'Review the changed SurfaceFloor'):
            PORT.convert('class Changed {}', 'com/stardew/craft/gametest/SurfaceFloorGameTests.java')

    def test_surface_floor_early_use_keeps_the_existing_item_contract(self):
        source = ('public final class SurfaceFloorItem extends Item implements IStardewItem { '
                  '@Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { original(); } }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/floor/SurfaceFloorItem.java')
        self.assertEqual(1, changes['SurfaceFloorItem.earlyBlockInteraction'])
        self.assertIn('implements IStardewItem, com.stardew.craft.platform.fabric.event.entity.player.ItemUseFirstProvider', converted)
        self.assertIn('onItemUseFirst(ItemStack stack, UseOnContext context) { original(); }', converted)

    def test_erased_bus_listeners_gain_explicit_types_without_changing_reload_listeners(self):
        source = ('bus.addListener(TownDoorNetwork::register); '
                  'NeoForge.EVENT_BUS.addListener(TownDoorRuntime::tick); '
                  'NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> logout(event)); '
                  'event.addListener(new DataLoader());')
        converted, changes = PORT.convert(source, 'com/stardew/craft/interior/door/TownDoorRuntime.java')
        self.assertEqual(3, changes['EventBus.explicitListenerTypes'])
        self.assertIn('bus.addListener(com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent.class, TownDoorNetwork::register)', converted)
        self.assertIn('FabricEvents.GAME.addListener(LevelTickEvent.Post.class, TownDoorRuntime::tick)', converted)
        self.assertIn('FabricEvents.GAME.addListener(PlayerEvent.PlayerLoggedOutEvent.class, (PlayerEvent.PlayerLoggedOutEvent event) -> logout(event))', converted)
        self.assertIn('event.addListener(new DataLoader())', converted)

    def test_dynamic_model_dispatch_handles_nested_receivers_and_preserves_native_calls(self):
        source = ('class Model { Object quads = parts.get(index()).getQuads(state, side, random, data, type); '
                  'Object nativeQuads = model.getQuads(state, side, random); '
                  'Object itemTypes = model.getRenderTypes(stack, false); '
                  'Object particle = current(null).getParticleIcon(data); '
                  'Object quad = new BakedQuad(vertices, -1, Direction.UP, sprite, true, source.hasAmbientOcclusion()); '
                  'Object base = super.getQuads(state, side, random, data, type); '
                  'String note = "model.getQuads(state, side, random, data, type)"; }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/client/model/Example.java')
        self.assertEqual(5, changes['BakedModel.explicitExtendedDispatch'])
        self.assertIn('BakedModels.getQuads(parts.get(index()), state, side, random, data, type)', converted)
        self.assertIn('model.getQuads(state, side, random)', converted)
        self.assertIn('BakedModels.getRenderTypes(model, stack, false)', converted)
        self.assertIn('BakedModels.getParticleIcon(current(null), data)', converted)
        self.assertIn('com.stardew.craft.platform.fabric.client.model.ModelQuad.create(', converted)
        self.assertIn('ModelQuad.ambientOcclusion(source)', converted)
        self.assertIn('super.getQuads(state, side, random, data, type)', converted)
        self.assertIn('"model.getQuads(state, side, random, data, type)"', converted)
        unrelated, _ = PORT.convert(source, 'demo/Unknown.java')
        self.assertEqual(source, unrelated)

    def test_capability_calls_keep_nested_positions_and_the_surrounding_assertion(self):
        source = ('h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, pos.offset(1, 0, 2), Direction.UP) '
                  '== be.getAutomationItemHandler(), "Extension automation lost owner");')
        converted, changes = PORT.convert(source, 'com/stardew/craft/gametest/Example.java')
        self.assertEqual(1, changes['GameTest.FabricBlockCapabilities'])
        self.assertIn('BlockCapabilities.get(Capabilities.ItemHandler.BLOCK, level, pos.offset(1, 0, 2), Direction.UP)', converted)
        self.assertTrue(converted.endswith('== be.getAutomationItemHandler(), "Extension automation lost owner");'))

    def test_native_registry_buffers_keep_real_payload_roundtrip_assertions(self):
        source = ('import net.neoforged.neoforge.network.connection.ConnectionType;\n'
                  'var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE); '
                  'PetScreenPayload.CODEC.encode(wire, payload); var decoded = PetScreenPayload.CODEC.decode(wire); '
                  'h.assertTrue(!wire.isReadable(), "Unread bytes");')
        converted, changes = PORT.convert(source, 'com/stardew/craft/gametest/PetInitialAdoptionGameTests.java')
        self.assertEqual(1, changes['GameTest.nativeRegistryCodecBuffers'])
        self.assertNotIn('ConnectionType', converted)
        self.assertIn('new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess())', converted)
        self.assertIn('PetScreenPayload.CODEC.encode(wire, payload)', converted)
        self.assertIn('PetScreenPayload.CODEC.decode(wire)', converted)
        self.assertIn('h.assertTrue(!wire.isReadable(), "Unread bytes")', converted)

    def test_contextual_light_queries_preserve_static_getters_and_building_identity(self):
        source = ('public class LargeFireplaceDecorBlock extends MapDecorStaticBlock implements EntityBlock { '
                  '@Override public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) { return 15; } '
                  'int light = level.getBlockState(pos.above()).getLightEmission(level, pos.above()); '
                  'Object manager = level.getAuxLightManager(pos); int statik = state.getLightEmission(); '
                  'String note = "state.getLightEmission(level, pos)"; }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/block/decor/LargeFireplaceDecorBlock.java')
        self.assertEqual(3, changes['LightEmission.contextualAndSnapshotQueries'])
        self.assertIn('implements EntityBlock, com.stardew.craft.platform.fabric.light.LightEmissionProvider', converted)
        self.assertIn('LightEmissionHooks.getLightEmission(level.getBlockState(pos.above()), level, pos.above())', converted)
        self.assertIn('AuxiliaryLights.getManager(level, pos)', converted)
        self.assertIn('int statik = state.getLightEmission()', converted)
        self.assertIn('"state.getLightEmission(level, pos)"', converted)
        self.assertIn('getLightEmission(BlockState state, BlockGetter level, BlockPos pos) { return 15; }', converted)

    def test_effect_amount_functions_and_folded_qualified_damage_types_are_preserved(self):
        source = ('this.addAttributeModifier(Attributes.MOVEMENT_SPEED, id, '
                  'AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> 0.10D); '
                  'effect.addAttributeModifier(Attributes.ATTACK_DAMAGE, furyId, '
                  'AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> 0.10D * (amplifier + 1)); '
                  'var reduction = net.neoforged.neoforge.common.damagesource\n   .DamageContainer.Reduction.ARMOR;')
        converted, changes = PORT.convert(source, 'com/stardew/craft/effect/ModMobEffects.java')
        self.assertEqual(2, changes['MobEffect.amplifierFunctions'])
        self.assertIn('MobEffectAttributeFunctions.add(this, Attributes.MOVEMENT_SPEED, id,', converted)
        self.assertIn('amplifier -> 0.10D)', converted)
        self.assertIn('amplifier -> 0.10D * (amplifier + 1)', converted)
        self.assertIn('com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer.Reduction.ARMOR', converted)

    def test_contextual_block_queries_keep_native_static_properties(self):
        source = ('class PlaygroundBlock extends Block { '
                  'Object a = level.getBlockState(pos.above()).getSoundType(level, pos, player); '
                  'Object b = placed.getBlock().getSoundType(placed, level, pos, player); '
                  'float f = state.getFriction(level, pos, player); '
                  'float r = state.getExplosionResistance(level, pos, explosion); '
                  'boolean ladder = block.isLadder(state, level, pos, player); '
                  'Object ordinary = state.getSoundType(); float staticR = block.getExplosionResistance(); }')
        converted, changes = PORT.convert(source, 'com/stardew/craft/block/decor/PlaygroundBlock.java')
        self.assertEqual(6, changes['BlockBehavior.contextualQueries'])
        self.assertIn('implements com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider', converted)
        self.assertIn('BlockBehaviors.getSoundType(level.getBlockState(pos.above()), level, pos, player)', converted)
        self.assertIn('BlockBehaviors.getSoundType(placed.getBlock(), placed, level, pos, player)', converted)
        self.assertIn('BlockBehaviors.getFriction(state, level, pos, player)', converted)
        self.assertIn('BlockBehaviors.getExplosionResistance(state, level, pos, explosion)', converted)
        self.assertIn('BlockBehaviors.isLadder(block, state, level, pos, player)', converted)
        self.assertIn('ordinary = state.getSoundType()', converted)
        self.assertIn('staticR = block.getExplosionResistance()', converted)

    def test_key_accessors_do_not_rewrite_translation_or_registry_keys(self):
        source = ('var a = ModKeyMappings.SKILL_MINOR.getKey(); '
                  'var b = mc.options.keyUse.getKey(); '
                  'var c = translatable.getKey(); var d = BuiltInRegistries.ITEM.getKey(item); '
                  'box.setTextShadow(false);')
        converted, changes = PORT.convert(source, 'com/stardew/craft/client/ModClientEvents.java')
        self.assertEqual(3, changes['Client.nativeKeyAndWidgetExtensions'])
        self.assertIn('FabricKeyMappings.getKey(ModKeyMappings.SKILL_MINOR)', converted)
        self.assertIn('FabricKeyMappings.getKey(mc.options.keyUse)', converted)
        self.assertIn('translatable.getKey()', converted)
        self.assertIn('BuiltInRegistries.ITEM.getKey(item)', converted)
        self.assertIn('EditBoxExtensions.setTextShadow(box, false)', converted)

    def test_wildcard_gametest_imports_use_the_namespace_aware_annotation(self):
        source = ('import net.minecraft.gametest.framework.*;\n'
                  'import net.neoforged.neoforge.gametest.*;\n'
                  '@GameTestHolder("demo") @PrefixGameTestTemplate(false) class Tests { '
                  '@GameTest(templateNamespace="demo") public static void test(GameTestHelper helper) { original(); } }')
        converted, changes = PORT.convert(source)
        self.assertEqual(2, changes['GameTest.wildcardMetadata'])
        self.assertIn('import com.stardew.craft.platform.fabric.gametest.GameTest;', converted)
        self.assertIn('import com.stardew.craft.platform.fabric.gametest.GameTestHolder;', converted)
        self.assertIn('import net.minecraft.gametest.framework.*;', converted)
        self.assertNotIn('net.neoforged.neoforge.gametest', converted)
        self.assertIn('test(GameTestHelper helper) { original(); }', converted)


if __name__ == "__main__":
    unittest.main()
