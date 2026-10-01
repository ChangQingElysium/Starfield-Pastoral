package com.stardew.craft.npc.runtime;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.SeedMakerOutputResolver;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Regression coverage for concrete resource/content warnings in the September 13 client log. */
@GameTestHolder("stardewcraft_warning_repair")
@PrefixGameTestTemplate(false)
public final class WarningRepairGameTests {
    @GameTest(templateNamespace = "stardewcraft_warning_repair", template = "ring_utilities")
    public static void irregularSeedIdsAndCatalogMetadata(GameTestHelper helper) {
        helper.assertTrue(SeedMakerOutputResolver.resolve(ModItems.UNMILLED_RICE.get()) == ModItems.RICE_SHOOT.get(),
                "Rice cannot be processed by the seed maker");
        helper.assertTrue(SeedMakerOutputResolver.resolve(ModItems.VANILLA_CATEGORY_ITEMS.get("qi_fruit").get())
                == ModItems.VANILLA_CATEGORY_ITEMS.get("qi_bean").get(), "Qi fruit cannot produce Qi beans");
        var missing = StardewItemCatalog.visibleItems().stream()
                .filter(item -> StardewItemDataApi.getTypeKey(new ItemStack(item)).isBlank())
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).toList();
        helper.assertTrue(missing.isEmpty(), "Visible items still lack metadata: " + missing);
        helper.assertTrue(!StardewItemCatalog.visibleItems().contains(ModItems.JUNIMO_STAR.get())
                && !StardewItemCatalog.visibleItems().contains(ModItems.JUNIMO_BUNDLE.get()),
                "Actor-only props leaked into creative/JEI catalog");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_warning_repair", template = "ring_utilities")
    public static void collisionProfilesKeepOriginalOccupancy(GameTestHelper helper) {
        var fireplace = ModelVoxelShapeCache.shapeFromModelId("stardewcraft:decor/common/fireplace_1").bounds();
        var tester = ModelVoxelShapeCache.shapeFromModelId("stardewcraft:decor/festival/fair_strength_tester_proxy").bounds();
        helper.assertTrue(fireplace.equals(new AABB(-1, 0, -1, 1, 49.0 / 16, 1)),
                "Fireplace collision changed while fixing the vanilla model format: " + fireplace);
        helper.assertTrue(tester.equals(new AABB(-1, 0, -1.0 / 16, 23.0 / 16, 3, 1)),
                "Strength tester collision changed while fixing the vanilla model format: " + tester);
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_warning_repair", template = "ring_utilities")
    public static void residentAlreadyAtTargetDoesNotRequireEntrance(GameTestHelper helper) {
        var oldEvents = NpcDataRegistry.events();
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        try {
            NpcDataRegistry.replaceEvents(Map.of("npc_route_points", JsonParser.parseString(
                    "{\"points\":{\"resident_target\":{\"x\":" + pos.getX()
                            + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ()
                            + ",\"indoor\":true}}}").getAsJsonObject()));
            var state = new NpcRuntimeState("krobus");
            state.setActiveScheduleKey("default");
            state.setLocationName("unmapped_resident_room");
            state.setNamedPointId("resident_target");
            helper.assertTrue(NpcRoutePlanner.resolveRoute(helper.getLevel(), "krobus", state, pos).ready(),
                    "An actor at its destination was blocked by missing entrance data");
            helper.assertTrue(!NpcRoutePlanner.resolveRoute(helper.getLevel(), "krobus", state, pos.above(8)).ready(),
                    "Different floors bypassed missing entrance validation");
        } finally {
            NpcDataRegistry.replaceEvents(oldEvents);
        }
        helper.succeed();
    }
}
