package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewRegion;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.ArtifactDropService;
import com.stardew.craft.manager.ArtifactSpotSpawnService;
import com.stardew.craft.manager.ArtifactSpotCandidatePool;
import com.stardew.craft.manager.QuarrySpawnService;
import com.stardew.craft.world.WorldRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ArtifactParityGameTests {
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void samplingUsesUnionAndDoesNotDoubleWeightOverlaps(GameTestHelper helper) {
        var boxes = List.of(
                new StardewRegion.Box(new BlockPos(0, 0, 0), new BlockPos(9, 5, 0)),
                new StardewRegion.Box(new BlockPos(5, 0, 0), new BlockPos(14, 5, 0)),
                new StardewRegion.Box(new BlockPos(1000, 0, 0), new BlockPos(1004, 5, 0)));
        int[] counts = new int[20];
        var random = RandomSource.create(136);
        for (int i = 0; i < 40000; i++) {
            var p = new ArtifactSpotCandidatePool(boxes, random).drawColumn();
            helper.assertTrue(p.getZ() == 0 && (p.getX() >= 0 && p.getX() <= 14
                    || p.getX() >= 1000 && p.getX() <= 1004), "Sample fell in the gap between mapped areas");
            counts[p.getX() < 15 ? p.getX() : p.getX() - 985]++;
        }
        for (int count : counts) helper.assertTrue(count > 1800 && count < 2200, "Overlapping rectangles biased the sampling");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void invisibleCeilingsDoNotHideSoilButRealCoverStillDoes(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(p.below(), ModBlocks.DIRT.get().defaultBlockState(), 3);
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(p.above(3), Blocks.BARRIER.defaultBlockState(), 3);
        helper.assertTrue(ArtifactSpotSpawnService.surfaceMarker(level, p.getX(), p.getZ()).equals(p), "Invisible map ceiling became the ground");
        helper.assertTrue(ArtifactSpotSpawnService.place(level, p, false), "Valid soil under map barrier never spawns");
        level.removeBlock(p, false);
        for (var cover : List.of(Blocks.STONE, Blocks.GLASS, Blocks.OAK_LEAVES, Blocks.WATER)) {
            level.setBlock(p.above(3), cover.defaultBlockState(), 3);
            helper.assertTrue(!ArtifactSpotSpawnService.canPlace(level, p), "Real cover allowed: " + cover);
        }
        level.removeBlock(p.above(3), false);
        level.setBlock(p.below(), ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
        helper.assertTrue(!ArtifactSpotSpawnService.canPlace(level, p), "Barrier fix allowed grass");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void lowestWalkingHeightKeepsItsLocationAndDropPool(GameTestHelper helper) throws Exception {
        var field = WorldRegionRegistry.class.getDeclaredField("catalog"); field.setAccessible(true);
        var previous = field.get(null);
        var publish = WorldRegionRegistry.class.getDeclaredMethod("publish", Map.class); publish.setAccessible(true);
        var ground = helper.absolutePos(new BlockPos(5, 1, 5));
        var id = new ResourceLocation(StardewCraft.MODID, "town");
        var region = new StardewRegion(id, helper.getLevel().dimension().location(), id,
                List.of(new StardewRegion.Box(ground.above(), ground.above(3))), List.of(), Set.of(), 0);
        try {
            publish.invoke(null, Map.of(id, region));
            helper.assertTrue(ArtifactDropService.resolveLocation(helper.getLevel(), ground).equals("Town"),
                    "Soil one below the walking-height bounds lost its regional drop table");
            helper.assertTrue(ArtifactDropService.resolveLocation(helper.getLevel(), ground.below()).equals("Default"),
                    "Underground soil escaped the vertical boundary");
        } finally { field.set(null, previous); }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void quarryInitializationUsesTenSourceDays(GameTestHelper helper) {
        helper.assertTrue(QuarrySpawnService.initialAttempts(1) == 70, "Year-one quarry used map-area density instead of 10 x 7 attempts");
        helper.assertTrue(QuarrySpawnService.initialAttempts(6) == 160 && QuarrySpawnService.dailyAttempts(6) == 16,
                "Source quarry attempt cap changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void packagedDropsRetainSourceFields(GameTestHelper helper) throws Exception {
        try (var stream = ArtifactParityGameTests.class.getClassLoader()
                .getResourceAsStream("data/stardewcraft/artifact_spots/vanilla.json")) {
            var root = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            // Source-audited 48 entries / 11 behavioral fields, Farm = Farm_Standard. Formatting is ignored.
            var hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            helper.assertTrue(java.util.HexFormat.of().formatHex(hash).equals("d0c95e1c0c3fae5536e2bbe1582c525f441e7ec26b07deb460919bf7859ce085"),
                    "Packaged artifact table diverged from the source-audited baseline; review the source before updating it");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void winterForageUsesGenerousAndClaySplitsDebris(GameTestHelper helper) throws Exception {
        var parse = ArtifactDropService.class.getDeclaredMethod("parseDropEntry", com.google.gson.JsonObject.class); parse.setAccessible(true);
        var field = ArtifactDropService.class.getDeclaredField("locationDrops"); field.setAccessible(true);
        var previous = field.get(null);
        var tool = new ItemStack(ModItems.IRIDIUM_HOE.get());
        com.stardew.craft.port.PortItemStacks.enchant(tool, helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(com.stardew.craft.enchantment.StardewEnchantments.GENEROUS), 1);
        try (var stream = ArtifactParityGameTests.class.getClassLoader()
                .getResourceAsStream("data/stardewcraft/artifact_spots/vanilla.json")) {
            var rows = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("Default");
            for (var raw : rows) {
                var row = raw.getAsJsonObject().deepCopy();
                String id = row.get("Id").getAsString();
                if (!Set.of("(O)412", "(O)416", "(O)330").contains(id)) continue;
                row.addProperty("Chance", 1); row.add("Condition", com.google.gson.JsonNull.INSTANCE);
                field.set(null, Map.of("Default", List.of(parse.invoke(null, row))));
                int doubles = 0;
                for (int i = 0; i < 1000; i++) {
                    var drops = ArtifactDropService.rollDrops(helper.getLevel(), new BlockPos(i, 64, 137), null, tool);
                    for (var stack : drops) helper.assertTrue(stack.getCount() == 1, "OneDebrisPerDrop was ignored");
                    if (drops.size() == 2) doubles++;
                    helper.assertTrue(!drops.isEmpty(), "Existing source item could not resolve: " + id);
                }
                if (!id.equals("(O)330")) helper.assertTrue(doubles > 400 && doubles < 600, "Winter forage lost Generous duplicates");
            }
        } finally { field.set(null, previous); }
        helper.succeed();
    }
}
