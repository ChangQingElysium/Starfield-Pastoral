package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.manager.*;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ArtifactSpotGameTests {
    private ArtifactSpotGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void grassAndOccupiedSurfacesNeverSpawn(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        for (var grass : new net.minecraft.world.level.block.Block[]{Blocks.GRASS_BLOCK,
                ModBlocks.GRASS_BLOCK.get(), ModBlocks.DARK_GRASS_BLOCK.get(), ModBlocks.FARMLAND.get()}) {
            level.setBlock(p.below(), grass.defaultBlockState(), 3);
            helper.assertTrue(!ArtifactSpotSpawnService.place(level, p, false), "Invalid surface accepted: " + grass);
            helper.assertTrue(!ModBlocks.SEED_SPOT.get().defaultBlockState().canSurvive(level, p), "Seed spot survived on grass/farmland");
        }
        level.setBlock(p.below(), ModBlocks.DIRT.get().defaultBlockState(), 3);
        level.setBlock(p, Blocks.SHORT_GRASS.defaultBlockState(), 3);
        helper.assertTrue(!ArtifactSpotSpawnService.place(level, p, true), "Generation overwrote an occupied tile");
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(p.above(3), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!ArtifactSpotSpawnService.place(level, p, true), "Generation ignored overhead cover");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void diggingPreservesEveryGroundVariantAndPaysOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = helper.absolutePos(new BlockPos(5, 2, 5));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArtifactDig"));
        var tool = new ItemStack(Items.IRON_HOE);
        var data = PlayerDataManager.getPlayerData(player);
        int before = data.getStat("ArtifactSpotsDug");
        int xp = data.getSkillExperience(SkillType.FORAGING);
        for (int variant = 0; variant < 5; variant++) {
            var dirt = ModBlocks.DIRT.get().defaultBlockState().setValue(TerrainVariants.DIRT, variant);
            level.setBlock(p.below(), dirt, 3);
            level.setBlock(p, ModBlocks.SEED_SPOT.get().defaultBlockState(), 3);
            withMiningDataLevel(helper, () -> helper.assertTrue(ArtifactSpotDigService.dig(level, p, player, tool), "Hoe failed to dig marker"));
            helper.assertTrue(level.getBlockState(p).isAir(), "Marker remained after hoe hit");
            helper.assertTrue(level.getBlockState(p.below()) == dirt, "Digging changed ground or its variant");
            helper.assertTrue(!ArtifactSpotDigService.dig(level, p, player, tool), "Duplicate click paid twice");
        }
        helper.assertTrue(data.getStat("ArtifactSpotsDug") == before + 5, "Incorrect artifact spot count");
        helper.assertTrue(data.getSkillExperience(SkillType.FORAGING) == xp + 75, "Each dig must give exactly 15 foraging XP");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void collisionIsEmptyAndSupportLossDoesNotDropMarker(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = helper.absolutePos(new BlockPos(5, 2, 5));
        for (var block : new net.minecraft.world.level.block.Block[]{ModBlocks.ARTIFACT_SPOT.get(), ModBlocks.SEED_SPOT.get()}) {
            level.setBlock(p.below(), Blocks.SAND.defaultBlockState(), 3);
            level.setBlock(p, block.defaultBlockState(), 3);
            var state = level.getBlockState(p);
            helper.assertTrue(state.getCollisionShape(level, p).isEmpty(), "Marker blocks walking");
            helper.assertTrue(!state.getShape(level, p).isEmpty(), "Marker cannot be targeted");
            helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(state, level, p, null).isEmpty(), "Marker drops itself");
            level.setBlock(p.below(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(p).isAir(), "Unsupported marker floats");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void seedTimingAndQuantityFollowSource(GameTestHelper helper) {
        helper.assertTrue(ArtifactDropService.seedSeason(0, 23) == 0 && ArtifactDropService.seedSeason(0, 24) == 1,
                "Spring seed cutoff must be after day 23");
        for (int season = 1; season < 4; season++)
            helper.assertTrue(ArtifactDropService.seedSeason(season, 20) == season
                    && ArtifactDropService.seedSeason(season, 21) == (season + 1) % 4, "Other seasons switch after day 20");
        var seeds = new net.minecraft.world.item.Item[]{ModItems.CARROT_SEEDS.get(), ModItems.SUMMER_SQUASH_SEEDS.get(),
                ModItems.BROCCOLI_SEEDS.get(), ModItems.POWDER_MELON_SEEDS.get()};
        for (int season = 0; season < 4; season++)
            helper.assertTrue(ArtifactDropService.rollSeedDrop(season, 1, 0, RandomSource.create(4)).is(seeds[season]), "Wrong seasonal seed");
        var random = RandomSource.create(991);
        int total = 0;
        for (int n = 0; n < 10000; n++) total += ArtifactDropService.rollSeedDrop(0, 1, 0, random).getCount();
        helper.assertTrue(total > 25700 && total < 26500, "Zero-luck quantity must average 2.5 + 1/9");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void lifecycleUsesEntryThresholdsAndExtraDatePasses(GameTestHelper helper) {
        helper.assertTrue(!ArtifactSpotSpawnService.maySpawn(1, true, 0), "Farm threshold is one existing marker");
        helper.assertTrue(ArtifactSpotSpawnService.maySpawn(1, false, 0)
                && !ArtifactSpotSpawnService.maySpawn(2, false, 0), "Outdoor entry threshold changed");
        helper.assertTrue(ArtifactSpotSpawnService.maySpawn(4, true, 3)
                && !ArtifactSpotSpawnService.maySpawn(5, false, 3), "Winter threshold changed");
        helper.assertTrue(ArtifactSpotSpawnService.dailyPasses(7, 7, false) == 3
                && ArtifactSpotSpawnService.dailyPasses(7, 7, true) == 1, "Weekly extra passes apply only outside farms");
        helper.assertTrue(ArtifactSpotSpawnService.dailyPasses(1, 1, false) == 3
                && ArtifactSpotSpawnService.dailyPasses(1, 29, true) == 2, "Opening/monthly passes missing");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void basicHoeAimedAtGroundDigsMarkerInsteadOfTilling(GameTestHelper helper) {
        var level = helper.getLevel();
        var ground = helper.absolutePos(new BlockPos(5, 1, 5));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GroundHoe"));
        var hoe = (HoeItem) ModItems.HOE.get();
        var stack = new ItemStack(hoe);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var dirt = ModBlocks.DIRT.get().defaultBlockState().setValue(TerrainVariants.DIRT, 4);
        level.setBlock(ground, dirt, 3);
        level.setBlock(ground.above(), ModBlocks.SEED_SPOT.get().defaultBlockState(), 3);
        withMiningDataLevel(helper, () -> hoe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false))));
        helper.assertTrue(level.getBlockState(ground) == dirt, "Basic hoe tilled below marker");
        helper.assertTrue(level.getBlockState(ground.above()).isAir(), "Basic hoe missed surface marker");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void chargedHoeTargetsBothMarkerAndGroundRows(GameTestHelper helper) {
        var level = helper.getLevel();
        var ground = helper.absolutePos(new BlockPos(5, 1, 5));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ChargedHoe"));
        var hoe = (HoeItem) ModItems.IRIDIUM_HOE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hoe));
        var center = ground.relative(player.getDirection(), 2);
        var expectedTargets = new java.util.HashSet<BlockPos>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            var p = center.offset(x, 0, z);
            level.setBlock(p, ModBlocks.DIRT.get().defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
            expectedTargets.add(p.equals(ground) ? p.above() : p);
        }
        level.setBlock(ground.above(), ModBlocks.ARTIFACT_SPOT.get().defaultBlockState(), 3);
        var targets = hoe.getAffectedBlocks(level, ground.above(), player, 5);
        helper.assertTrue(targets.size() == 25 && targets.contains(ground.above()) && !targets.contains(ground),
                "Charged hoe must lift only occupied marker cells, leaving ordinary targets at ground height");
        helper.assertTrue(new java.util.HashSet<>(targets).equals(expectedTargets),
                "Charged hoe must preserve every ordinary ground target in its forward-facing 5x5 pattern");
        helper.succeed();
    }
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void generousRepeatsOnlyTheSelectedItemHalfTheTime(GameTestHelper helper) throws Exception {
        var parse = ArtifactDropService.class.getDeclaredMethod("parseDropEntry", com.google.gson.JsonObject.class);
        parse.setAccessible(true);
        var entry = parse.invoke(null, com.google.gson.JsonParser.parseString("""
                {"Id":"(O)330","ItemId":"(O)330","Chance":1,"Precedence":0,"ContinueOnDrop":false,
                 "MinStack":2,"MaxStack":5,"ApplyGenerousEnchantment":true,"OneDebrisPerDrop":false}
                """).getAsJsonObject());
        var field = ArtifactDropService.class.getDeclaredField("locationDrops"); field.setAccessible(true);
        var previous = field.get(null);
        var tool = new ItemStack(ModItems.IRIDIUM_HOE.get());
        tool.enchant(helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(com.stardew.craft.enchantment.StardewEnchantments.GENEROUS), 1);
        try {
            field.set(null, java.util.Map.of("Default", java.util.List.of(entry)));
            int doubled = 0;
            for (int n = 0; n < 2000; n++) {
                var drops = ArtifactDropService.rollDrops(helper.getLevel(), new BlockPos(n, 64, 9876), null, tool);
                helper.assertTrue(drops.size() == 1 || drops.size() == 2, "Generous rerolled the whole table");
                if (drops.size() == 2) doubled++;
                for (var drop : drops) helper.assertTrue(drop.is(ModItems.CLAY.get()) && drop.getCount() >= 2 && drop.getCount() <= 5,
                        "Generous changed selected item or failed to reroll its stack size");
            }
            helper.assertTrue(doubled > 900 && doubled < 1100, "Generous must activate about half of the time");
        } finally { field.set(null, previous); }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void archaeologistRaisesArtifactProbabilityWithoutDuplicatingStacks(GameTestHelper helper) throws Exception {
        var method = ArtifactDropService.class.getDeclaredMethod("rollRandomArtifact", String.class, RandomSource.class, int.class);
        method.setAccessible(true);
        int ordinary = 0, enchanted = 0;
        var random = RandomSource.create(427);
        for (int n = 0; n < 10000; n++) {
            if (method.invoke(null, "Town", random, 1) != null) ordinary++;
            var drop = (ItemStack) method.invoke(null, "Town", random, 2);
            if (drop != null) { enchanted++; helper.assertTrue(drop.getCount() == 1, "Archaeologist duplicated stack"); }
        }
        helper.assertTrue(ordinary > 0 && enchanted > ordinary * 1.5, "Archaeologist did not increase per-artifact chance");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void packagedModelsHaveOnlyTexturedNativeFaces(GameTestHelper helper) throws Exception {
        for (String name : new String[]{"artifact_spot", "seed_spot"}) {
            String path = "assets/stardewcraft/models/block/" + name + ".json";
            try (var stream = ArtifactSpotGameTests.class.getClassLoader().getResourceAsStream(path)) {
                helper.assertTrue(stream != null, "Packaged marker model is missing");
                var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                helper.assertTrue(model.get("render_type").getAsString().equals("minecraft:cutout"), "Marker needs alpha cutout");
                helper.assertTrue(model.getAsJsonArray("elements").size() == 6, "Preview ground leaked into runtime model");
                for (var raw : model.getAsJsonArray("elements")) {
                    var faces = raw.getAsJsonObject().getAsJsonObject("faces");
                    helper.assertTrue(faces.size() == 2, "Zero-area faces must be disabled");
                    for (var face : faces.entrySet()) helper.assertTrue(face.getValue().getAsJsonObject().get("texture")
                            .getAsString().equals("#0"), "Native model references missing texture");
                }
            }
        }
        helper.succeed();
    }

    /** GameTestServer creates only vanilla dimensions; the normal HUD sync also reads mining progress. */
    @SuppressWarnings("unchecked")
    private static void withMiningDataLevel(GameTestHelper helper, Runnable action) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            field.setAccessible(true);
            var levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>) field.get(helper.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
            var previous = levels.put(key, helper.getLevel());
            try { action.run(); }
            finally { if (previous == null) levels.remove(key); else levels.put(key, previous); }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

}
