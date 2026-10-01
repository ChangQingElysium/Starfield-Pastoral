package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainGrassGameTests {
    private TerrainGrassGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void terrainGrassIsAFullCubeInItsOwnCatalogAndKeepsGrassDrops(GameTestHelper helper) {
        terrainGrassIsAFullCubeInItsOwnCatalogAndKeepsGrassDrops(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_terrainGrassIsAFullCubeInItsOwnCatalogAndKeepsGrassDrops(GameTestHelper helper) {
        terrainGrassIsAFullCubeInItsOwnCatalogAndKeepsGrassDrops(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void terrainGrassIsAFullCubeInItsOwnCatalogAndKeepsGrassDrops(GameTestHelper helper, Block grassBlock) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var grass = grassBlock.defaultBlockState();
        level.setBlock(pos, grass, 3);
        helper.assertTrue(grass.isCollisionShapeFullBlock(level, pos), "Terrain grass must be a full cube");
        helper.assertTrue(grass.getDestroySpeed(level, pos) == Blocks.GRASS_BLOCK.defaultBlockState().getDestroySpeed(level, pos),
                "Grass hardness changed");
        helper.assertTrue(grass.is(BlockTags.DIRT) && grass.is(BlockTags.MINEABLE_WITH_SHOVEL)
                        && grass.is(BlockTags.ANIMALS_SPAWNABLE_ON) && grass.is(BlockTags.SNIFFER_DIGGABLE_BLOCK)
                        && grass.is(BlockTags.VALID_SPAWN), "Grass soil/spawn/mining tags missing");
        helper.assertTrue(StardewItemCatalog.tabForItem(grassBlock.asItem()) == StardewCatalogTab.NATURE,
                "Terrain grass is in the wrong catalog");
        var normal = Block.getDrops(grass, level, pos, null, null, new ItemStack(Items.IRON_SHOVEL));
        helper.assertTrue(normal.size() == 1 && com.stardew.craft.port.PortJava.getFirst(normal).is(grassBlock.asItem()), "Ordinary mining must drop the grass itself without Silk Touch");
        var silk = new ItemStack(Items.DIAMOND_SHOVEL);
        silk.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = Block.getDrops(grass, level, pos, null, null, silk);
        helper.assertTrue(silkDrops.size() == 1 && com.stardew.craft.port.PortJava.getFirst(silkDrops).is(grassBlock.asItem()),
                "Silk Touch lost the terrain texture variant");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void terrainToolsRespectPermanentGrass(GameTestHelper helper) {
        terrainToolsRespectPermanentGrass(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_terrainToolsRespectPermanentGrass(GameTestHelper helper) {
        terrainToolsRespectPermanentGrass(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void terrainToolsRespectPermanentGrass(GameTestHelper helper, Block grassBlock) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var state = grassBlock.defaultBlockState();
        level.setBlock(pos, state, 3);
        var player = player(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        var context = context(player, pos);
        var tilled = state.getToolModifiedState(context, ToolActions.HOE_TILL, true);
        helper.assertTrue(grassBlock == ModBlocks.DIRT.get()
                        ? tilled != null && tilled.is(ModBlocks.FARMLAND.get()) : tilled == null,
                "Only authored dirt should become authored farmland");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        var path = state.getToolModifiedState(context(player, pos), ToolActions.SHOVEL_FLATTEN, true);
        helper.assertTrue(grassBlock == ModBlocks.DIRT.get()
                        ? path != null && path.is(Blocks.DIRT_PATH) : path == null,
                "Permanent grass must not become a path; authored dirt must still allow paths");
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        helper.assertTrue(state.getToolModifiedState(context(player, pos), ToolActions.HOE_TILL, true) == null,
                "Covered grass incorrectly allowed tilling");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void grassSpreadsItsVariantAndKeepsExpectedCoveredState(GameTestHelper helper) {
        grassSpreadsItsVariantAndKeepsExpectedCoveredState(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_grassSpreadsItsVariantAndKeepsExpectedCoveredState(GameTestHelper helper) {
        grassSpreadsItsVariantAndKeepsExpectedCoveredState(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void grassSpreadsItsVariantAndKeepsExpectedCoveredState(GameTestHelper helper, Block grassBlock) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var state = grassBlock.defaultBlockState();
        level.setBlock(pos, state, 3);
        level.setBlock(pos.east(), Blocks.DIRT.defaultBlockState(), 3);
        level.setBlock(pos.above(3), Blocks.GLOWSTONE.defaultBlockState(), 3);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getMaxLocalRawBrightness(pos.above()) >= 9,
                "Waiting for light propagation")).thenExecute(() -> {
            var random = RandomSource.create(414);
            for (int i = 0; i < 200; i++) state.randomTick(level, pos, random);
            helper.assertTrue(level.getBlockState(pos.east()).is(grassBlock),
                    "Spreading grass lost its texture variant");
            level.setBlock(pos.above(), Blocks.SNOW.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).getValue(SnowyDirtBlock.SNOWY), "Snow did not update grass state");
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            state.randomTick(level, pos, random);
            helper.assertTrue(level.getBlockState(pos).is(grassBlock == Blocks.GRASS_BLOCK ? Blocks.DIRT : grassBlock), "Covered authored grass changed block, or vanilla decay stopped working");
        }).thenSucceed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void everyGrassTextureSurvivesCoverFluidsAndTreeSoilReplacement(GameTestHelper helper)
            throws ReflectiveOperationException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var random = RandomSource.create(414);
        var tree = (net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration) level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(net.minecraft.data.worldgen.features.TreeFeatures.OAK)
                .value().config();
        var setDirt = net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer.class.getDeclaredMethod(
                "setDirtAt", net.minecraft.world.level.LevelSimulatedReader.class, java.util.function.BiConsumer.class,
                RandomSource.class, BlockPos.class, net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.class);
        setDirt.setAccessible(true);
        java.util.function.BiConsumer<BlockPos, net.minecraft.world.level.block.state.BlockState> rejectSoilChange =
                (target, replacement) -> { throw new AssertionError("Tree attempted to replace permanent grass"); };
        for (Block block : java.util.List.of(ModBlocks.GRASS_BLOCK.get(), ModBlocks.DARK_GRASS_BLOCK.get())) {
            for (var initial : block.getStateDefinition().getPossibleStates()) {
                var variant = com.stardew.craft.block.terrain.TerrainVariants.property(initial);
                for (var cover : java.util.List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                        Blocks.LAVA.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 8))) {
                    level.setBlock(pos, initial, 3);
                    level.setBlock(pos.above(), cover, 3);
                    for (int tick = 0; tick < 8; tick++) level.getBlockState(pos).randomTick(level, pos, random);
                    var surviving = level.getBlockState(pos);
                    helper.assertTrue(surviving.is(block), "Cover or fluid degraded permanent grass");
                    helper.assertTrue(variant == null || surviving.getValue(variant).equals(initial.getValue(variant)),
                            "Cover or fluid changed the grass texture variant");
                    setDirt.invoke(null, level, rejectSoilChange, random, pos, tree);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void grassRemainsEligibleForFarmEcology(GameTestHelper helper) throws ReflectiveOperationException {
        grassRemainsEligibleForFarmEcology(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_grassRemainsEligibleForFarmEcology(GameTestHelper helper) throws ReflectiveOperationException {
        grassRemainsEligibleForFarmEcology(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void grassRemainsEligibleForFarmEcology(GameTestHelper helper, Block grassBlock) throws ReflectiveOperationException {
        var grass = grassBlock.defaultBlockState();
        helper.assertTrue(StardewCropBlock.isNaturalSoil(grass), "Crop soil logic rejected terrain grass");
        for (Class<?> service : new Class<?>[]{com.stardew.craft.manager.PastureGrassGrowthManager.class,
                com.stardew.craft.farm.FarmDebrisDailyService.class}) {
            var predicate = service.getDeclaredMethod("isDiggableFarmGround", Block.class);
            predicate.setAccessible(true);
            helper.assertTrue((boolean) predicate.invoke(null, grass.getBlock()), "Farm ecology rejected grass: " + service.getSimpleName());
        }
        var forage = com.stardew.craft.manager.ForageSpawnService.class.getDeclaredMethod("isNaturalForageSurface", net.minecraft.world.level.block.state.BlockState.class);
        forage.setAccessible(true);
        helper.assertTrue((boolean) forage.invoke(null, grass), "Forage spawning rejected terrain grass");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void sheepCanGrazeTerrainGrass(GameTestHelper helper) {
        sheepCanGrazeTerrainGrass(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_sheepCanGrazeTerrainGrass(GameTestHelper helper) {
        sheepCanGrazeTerrainGrass(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void sheepCanGrazeTerrainGrass(GameTestHelper helper, Block grassBlock) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        level.setBlock(pos, grassBlock.defaultBlockState(), 3);
        var sheep = EntityType.SHEEP.create(level);
        sheep.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        sheep.setSheared(true);
        helper.assertTrue(sheep.getWalkTargetValue(pos.above(), level) == 10.0F, "Animals lost their grass path preference");
        var goal = new EatBlockGoal(sheep);
        sheep.getRandom().setSeed(86);
        boolean canGraze = false;
        for (int i = 0; i < 10000 && !canGraze; i++) canGraze = goal.canUse();
        helper.assertTrue(canGraze, "Sheep cannot start eating terrain grass");
        goal.start();
        for (int i = 0; i < 40; i++) goal.tick();
        helper.assertTrue(level.getBlockState(pos).is(grassBlock == Blocks.GRASS_BLOCK ? Blocks.DIRT : grassBlock) && !sheep.isSheared(), "Grazing changed authored grass or failed to regrow wool");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void bonemealGrowsPlantsAcrossBothGrassVariants(GameTestHelper helper) {
        bonemealGrowsPlantsAcrossBothGrassVariants(helper, ModBlocks.GRASS_BLOCK.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void darkGrass_bonemealGrowsPlantsAcrossBothGrassVariants(GameTestHelper helper) {
        bonemealGrowsPlantsAcrossBothGrassVariants(helper, ModBlocks.DARK_GRASS_BLOCK.get());
    }

    private static void bonemealGrowsPlantsAcrossBothGrassVariants(GameTestHelper helper, net.minecraft.world.level.block.GrassBlock grassBlock) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var grass = grassBlock;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlock(pos.offset(x, 0, z), (x % 2 == 0 ? grass : Blocks.GRASS_BLOCK).defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < 4; i++) grass.performBonemeal(level, RandomSource.create(741 + i), pos, grass.defaultBlockState());
        int onCustom = 0;
        int onVanilla = 0;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (level.getBlockState(pos.offset(x, 1, z)).getBlock() instanceof net.minecraft.world.level.block.BushBlock) {
                    if (x % 2 == 0) onCustom++;
                    else onVanilla++;
                }
            }
        }
        helper.assertTrue(onCustom > 0 && onVanilla > 0, "Bonemeal failed across a mixed grass surface");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void terrainDirtKeepsPlainDirtPropertiesAndFarmRules(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var dirt = ModBlocks.DIRT.get().defaultBlockState();
        level.setBlock(pos, dirt, 3);
        helper.assertTrue(dirt.isCollisionShapeFullBlock(level, pos)
                        && dirt.getDestroySpeed(level, pos) == Blocks.DIRT.defaultBlockState().getDestroySpeed(level, pos),
                "Terrain dirt must retain the vanilla dirt shape and hardness");
        helper.assertTrue(dirt.is(BlockTags.DIRT) && dirt.is(BlockTags.MINEABLE_WITH_SHOVEL)
                        && dirt.is(BlockTags.CONVERTABLE_TO_MUD) && dirt.is(BlockTags.SNIFFER_DIGGABLE_BLOCK),
                "Dirt soil/mining/mud conversion tags missing");
        helper.assertTrue(!dirt.is(ModBlocks.YELLOW_DIRT.get()) && !dirt.is(BlockTags.ANIMALS_SPAWNABLE_ON),
                "Plain dirt incorrectly gained yellow dirt or grass behavior");
        helper.assertTrue(StardewItemCatalog.tabForItem(ModItems.DIRT.get()) == StardewCatalogTab.NATURE,
                "Dirt is in the wrong catalog");
        var drops = Block.getDrops(dirt, level, pos, null, null, new ItemStack(Items.IRON_SHOVEL));
        helper.assertTrue(drops.size() == 1 && com.stardew.craft.port.PortJava.getFirst(drops).is(ModItems.DIRT.get()),
                "Mining dirt lost its terrain texture");
        helper.assertTrue(StardewCropBlock.isNaturalSoil(dirt), "Natural soil logic rejected terrain dirt");
        for (Class<?> service : new Class<?>[]{com.stardew.craft.manager.PastureGrassGrowthManager.class,
                com.stardew.craft.farm.FarmDebrisDailyService.class}) {
            var predicate = service.getDeclaredMethod("isDiggableFarmGround", Block.class);
            predicate.setAccessible(true);
            helper.assertTrue((boolean) predicate.invoke(null, dirt.getBlock()),
                    "Terrain dirt rejected by farm ecology: " + service.getSimpleName());
        }
        var forage = com.stardew.craft.manager.ForageSpawnService.class.getDeclaredMethod("isNaturalForageSurface", net.minecraft.world.level.block.state.BlockState.class);
        forage.setAccessible(true);
        helper.assertTrue((boolean) forage.invoke(null, dirt), "Forage spawning rejected terrain dirt");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void terrainDirtUsesVanillaTools(GameTestHelper helper) {
        terrainToolsRespectPermanentGrass(helper, ModBlocks.DIRT.get());
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void grassAndMyceliumNeverSpreadOntoAuthoredDirt(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        level.setBlock(pos.above(3), Blocks.GLOWSTONE.defaultBlockState(), 3);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getMaxLocalRawBrightness(pos.above()) >= 9,
                "Waiting for light propagation")).thenExecute(() -> {
            for (Block spreader : new Block[]{Blocks.GRASS_BLOCK, ModBlocks.GRASS_BLOCK.get(),
                    ModBlocks.DARK_GRASS_BLOCK.get(), Blocks.MYCELIUM}) {
                var state = spreader.defaultBlockState();
                level.setBlock(pos, state, 3);
                level.setBlock(pos.east(), ModBlocks.DIRT.get().defaultBlockState(), 3);
                level.setBlock(pos.east().above(), Blocks.SNOW.defaultBlockState(), 3);
                level.setBlock(pos.north(), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(pos.west(), ModBlocks.YELLOW_DIRT.get().defaultBlockState(), 3);
                var random = RandomSource.create(414);
                for (int i = 0; i < 200; i++) state.randomTick(level, pos, random);
                helper.assertTrue(level.getBlockState(pos.east()).is(ModBlocks.DIRT.get()), "Spreading overwrote authored dirt");
                helper.assertTrue(level.getBlockState(pos.north()).is(spreader), "Spreading onto vanilla dirt changed unexpectedly");
                helper.assertTrue(level.getBlockState(pos.west()).is(ModBlocks.YELLOW_DIRT.get()), "Spread overwrote special yellow dirt");
            }
        }).thenSucceed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void vanillaGrassStillSpreadsAndDecaysToVanillaDirt(GameTestHelper helper) {
        grassSpreadsItsVariantAndKeepsExpectedCoveredState(helper, Blocks.GRASS_BLOCK);
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void vanillaGrassStillBecomesVanillaDirtWhenGrazed(GameTestHelper helper) {
        sheepCanGrazeTerrainGrass(helper, Blocks.GRASS_BLOCK);
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void fixedTerrainCopiesKeepEveryVariantThroughPlacementAndStateSerialization(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var player = player(level);
        player.getAbilities().instabuild = true;
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        for (Block block : java.util.List.of(ModBlocks.GRASS_BLOCK.get(), ModBlocks.DIRT.get())) {
            var property = com.stardew.craft.block.terrain.TerrainVariants.property(block.defaultBlockState());
            for (int variant : property.getPossibleValues()) {
                var source = block.defaultBlockState().setValue(property, variant);
                var ordinary = new ItemStack(block);
                var fixed = com.stardew.craft.block.terrain.TerrainVariants.fixedCopy(ordinary, source);
                helper.assertTrue(!PortItemData.has(ordinary, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE),
                        "Ctrl-copy mutated the normal random-placement item");
                player.setItemInHand(InteractionHand.MAIN_HAND, fixed);
                for (int repeat = 0; repeat < 5; repeat++) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    var placement = new net.minecraft.world.item.context.BlockPlaceContext(context(player, pos));
                    var result = ((net.minecraft.world.item.BlockItem) fixed.getItem()).place(placement);
                    helper.assertTrue(result.consumesAction(), "Fixed terrain item failed placement");
                    var placed = level.getBlockState(pos);
                    helper.assertTrue(placed.is(block) && placed.getValue(property) == variant,
                            "Fixed copy rerolled variant " + variant);
                    var encoded = net.minecraft.world.level.block.state.BlockState.CODEC
                            .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, placed).getOrThrow();
                    var decoded = net.minecraft.world.level.block.state.BlockState.CODEC
                            .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
                    helper.assertTrue(decoded.equals(placed), "Serialized terrain lost the variant");
                }
            }
        }
        helper.assertTrue(com.stardew.craft.block.terrain.TerrainVariants.property(
                ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState()) == null, "Dark grass gained random variants");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void terrainVariantsSurviveSnowAndOrdinaryItemsRemainRandom(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var property = com.stardew.craft.block.terrain.TerrainVariants.GRASS;
        for (int variant : property.getPossibleValues()) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.GRASS_BLOCK.get().defaultBlockState().setValue(property, variant), 3);
            level.setBlock(pos.above(), Blocks.SNOW.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).getValue(SnowyDirtBlock.SNOWY)
                    && level.getBlockState(pos).getValue(property) == variant, "Snow changed the grass variant");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!level.getBlockState(pos).getValue(SnowyDirtBlock.SNOWY)
                    && level.getBlockState(pos).getValue(property) == variant, "Snow removal rerolled the variant");
        }
        var player = player(level);
        for (Block block : java.util.List.of(ModBlocks.GRASS_BLOCK.get(), ModBlocks.DIRT.get())) {
            var ordinary = new ItemStack(block);
            player.setItemInHand(InteractionHand.MAIN_HAND, ordinary);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            var placement = new net.minecraft.world.item.context.BlockPlaceContext(context(player, pos));
            var terrainProperty = com.stardew.craft.block.terrain.TerrainVariants.property(block.defaultBlockState());
            var seen = new java.util.HashSet<Integer>();
            for (int i = 0; i < 10000; i++) seen.add(block.getStateForPlacement(placement).getValue(terrainProperty));
            helper.assertTrue(seen.equals(java.util.Set.copyOf(terrainProperty.getPossibleValues())),
                    "Ordinary placement cannot reach every terrain variant");
            helper.assertTrue(!PortItemData.has(ordinary, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE),
                    "Random placement permanently fixed the remaining stack");
        }
        helper.succeed();
    }

    private static ServerPlayer player(ServerLevel level) {
        return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Grass test"));
    }

    private static UseOnContext context(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
