package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineStoneBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.MineStoneMining;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_ground_stone")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class MineStoneGameTests {
    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void timingToolsAndAabb(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Stone timing"));
        player.setGameMode(GameType.SURVIVAL);
        var state = ModBlocks.MINE_STONE_32.get().defaultBlockState();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rotated = state.setValue(MineStoneBlock.FACING, facing);
            var outline = rotated.getShape(level, pos);
            h.assertTrue(outline.toAabbs().size() == 1, "Not one overall AABB");
            h.assertTrue(outline.bounds().minY == 0 && outline.bounds().maxY < 1, "Buried part has collision");
            h.assertTrue(!Shapes.joinIsNotEmpty(outline, rotated.getCollisionShape(level, pos), BooleanOp.NOT_SAME), "Outline/collision differ");
        }
        for (Item item : new Item[]{ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(),
                ModItems.STEEL_PICKAXE.get(), ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get(),
                Items.WOODEN_PICKAXE, Items.NETHERITE_PICKAXE}) {
            var stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(progressTicks(state.getDestroyProgress(player, level, pos)) == 12 -
                    (item instanceof com.stardew.craft.item.tool.StardewPickaxeItem pickaxe ? pickaxe.getStardewTier() : 0),
                    "One-hit stone tier bonus differs with " + item);
        }
        var foreign = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        foreign.enchant(enchantments.getHolderOrThrow(Enchantments.EFFICIENCY), 5);
        com.stardew.craft.port.PortItemStacks.enchant(foreign, enchantments.getHolderOrThrow(StardewEnchantments.SWIFT), 1);
        h.assertTrue(MineStoneMining.pickaxePower(foreign) == 1 && MineStoneMining.breakTicks(1, foreign) == 12,
                "External enchantments bypass normalization");
        var swift = new ItemStack(ModItems.IRIDIUM_PICKAXE.get());
        com.stardew.craft.port.PortItemStacks.enchant(swift, enchantments.getHolderOrThrow(StardewEnchantments.SWIFT), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, swift);
        h.assertTrue(progressTicks(state.getDestroyProgress(player, level, pos)) == 6, "Iridium Swift not 6 ticks");
        h.assertTrue(MineStoneMining.breakTicks(3, 2, 1, false) == 22, "Copper must need two whole swings for 3 HP");
        h.assertTrue(MineStoneMining.energyCost(3, 2, 0) == 4 && MineStoneMining.energyCost(3, 2, 10) == 2,
                "Energy uses fractional swings or wrong skill reduction");
        h.assertTrue(MineStoneMining.energyCost(1, 1, 5) == 1.5F, "Level 5 cost is wrong");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void creativeCatalogAndInheritedItem(GameTestHelper h) throws Exception {
        var items = com.stardew.craft.item.catalog.StardewItemCatalog.visibleItems();
        h.assertTrue(items.contains(ModItems.MINE_STONE_32.get()), "Mine rock is hidden from creative");
        h.assertTrue(com.stardew.craft.item.catalog.StardewItemCatalog.tabForItem(ModItems.MINE_STONE_32.get())
                == com.stardew.craft.item.catalog.StardewCatalogTab.NATURE, "Rock is not in Nature");
        try (var stream = MineStoneGameTests.class.getClassLoader().getResourceAsStream(
                "assets/stardewcraft/models/item/mine_stone_32.json")) {
            var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertTrue(model.get("parent").getAsString().equals("stardewcraft:block/mine/nodes/stone_32")
                    && !model.has("display"), "Item must inherit block model and display transforms");
        }
        h.assertTrue(items.indexOf(ModItems.MINE_EARTH_SOIL.get()) < items.indexOf(ModItems.MINE_EARTH_LOOSE_SOIL.get())
                && items.indexOf(ModItems.MINE_EARTH_WALL.get()) < items.indexOf(ModItems.MINE_EARTH_DARK_SOIL.get())
                && items.indexOf(ModItems.MINE_EARTH_DARK_WALL.get()) < items.indexOf(ModItems.MINE_FROST_SOIL.get()),
                "Mine soil and wall families are split or themes out of order");
        h.assertTrue(items.indexOf(ModItems.ASPHALT_ROAD.get()) + 1 == items.indexOf(ModItems.ROAD_DASH.get())
                && items.indexOf(ModItems.ROAD_DASH.get()) + 1 == items.indexOf(ModItems.ROAD_DOUBLE_LINE.get()),
                "Road and its markings are separated");
        var planks = com.stardew.craft.item.catalog.StardewItemDisplayStacks.stacksForItem(ModItems.MINE_PLANKS.get())
                .stream().sorted(com.stardew.craft.item.catalog.StardewItemComparator.STACK).toList();
        h.assertTrue(planks.size() == 8 && PortItemData.get(planks.get(1), com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE)
                .properties().get("theme").equals("earth_dark"), "Normal/dark variants not adjacent");
        for (var tab : java.util.List.of(com.stardew.craft.item.catalog.StardewCatalogTab.BUILDING,
                com.stardew.craft.item.catalog.StardewCatalogTab.NATURE)) {
            var paths = items.stream().filter(i -> com.stardew.craft.item.catalog.StardewItemCatalog.tabForItem(i) == tab)
                    .map(com.stardew.craft.item.catalog.StardewItemComparator::path).toList();
            com.stardew.craft.StardewCraft.LOGGER.info("[STONE32 CATALOG] {} {}", tab, paths);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void shippedSourceMappingMatchesRuntime(GameTestHelper h) throws Exception {
        h.assertTrue(MineStoneMining.stateForSource("unmapped_source", 1).isEmpty(), "Unimplemented source silently substituted");
        try (var stream = MineStoneGameTests.class.getClassLoader().getResourceAsStream(
                "data/stardewcraft/mine_node_sources/ordinary_stones.json")) {
            var manifest = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertTrue(manifest.getAsJsonArray("entries").size() == 55, "Missing mapped appearance");
            for (var raw : manifest.getAsJsonArray("entries")) {
                var entry = raw.getAsJsonObject();
                String source = entry.get("source_id").getAsString();
                var state = MineStoneMining.stateForSource(source, 5).orElseThrow();
                h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                        .equals(entry.get("block").getAsString()), "Source/block mapping drift");
                h.assertTrue(((MineStoneBlock)state.getBlock()).sourceId().equals(source)
                        && state.getValue(MineStoneBlock.STONE_HEALTH) == 5, "Source identity or explicit health lost");
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var rotated = state.setValue(MineStoneBlock.FACING, facing);
                    var shape = rotated.getShape(h.getLevel(), BlockPos.ZERO);
                    h.assertTrue(shape.toAabbs().size() == 1 && shape.bounds().minY == 0
                            && !Shapes.joinIsNotEmpty(shape, rotated.getCollisionShape(h.getLevel(), BlockPos.ZERO), BooleanOp.NOT_SAME),
                            "Invalid rotated AABB for " + source);
                }
                try (var modelStream = MineStoneGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/stardewcraft/models/item/" + entry.get("item").getAsString().split(":")[1] + ".json")) {
                    var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(modelStream,
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    h.assertTrue(model.get("parent").getAsString().equals(entry.get("model").getAsString())
                            && !model.has("display"), "Mapped item does not inherit its block model");
                }
            }
        }
        h.succeed();
    }

    private static int clientProgressTicks(int duration) {
        for (int tick = 1; tick <= 20000; tick++) {
            if (MineStoneMining.progressAfterTicks(tick, duration) >= 1.0F) return tick;
        }
        return -1;
    }

    private static int serverProgressTicks(float increment) {
        for (int tick = 1; tick <= 20000; tick++) if (increment * tick >= 1.0F) return tick;
        return -1;
    }

    private static int progressTicks(float increment) {
        float progress = 0;
        for (int tick = 1; tick <= 20000; tick++) { progress += increment; if (progress >= 1) return tick; }
        return -1;
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void completedBreakPaysOnceAndKeepsSoil(GameTestHelper h) {
        withMiningDataLevel(h, () -> {
            for (String source : java.util.List.of("32", "38", "40", "42", "668", "670", "751", "8", "10", "44", "34", "36", "48", "50", "52", "54", "290", "6", "14", "2", "56", "58", "760", "762", "764", "4", "12", "46", "765", "CalicoEggStone_0", "CalicoEggStone_1", "CalicoEggStone_2", "75", "76", "77", "95", "343", "450", "25", "816", "817", "818", "819", "843", "844", "845", "846", "847", "849", "850", "VolcanoGoldNode", "VolcanoCoalNode0", "VolcanoCoalNode1", "BasicCoalNode0", "BasicCoalNode1")) checkCompletedBreak(h, source);
        });
    }

    private static void checkCompletedBreak(GameTestHelper h, String source) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Stone break"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        var data = PlayerDataManager.getPlayerData(player);
        var state = MineStoneMining.stateForSource(source, switch (source) { case "668", "670" -> 2; case "75", "751", "48", "50", "52", "54" -> 3; case "817", "818", "816", "290", "56", "58", "760", "762" -> 4; case "VolcanoGoldNode", "819", "25", "764" -> 8; case "BasicCoalNode0", "BasicCoalNode1", "76", "4", "6", "8", "10", "12", "14" -> 5; case "46" -> 12; case "765" -> 16; case "77" -> 7; case "95" -> 25; case "843", "844" -> 12; case "845", "846", "847", "849" -> 6; case "CalicoEggStone_0", "CalicoEggStone_1", "CalicoEggStone_2" -> 8; case "2", "VolcanoCoalNode0", "VolcanoCoalNode1" -> 10; default -> 1; }).orElseThrow();
        level.setBlock(pos.below(), ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
        var soil = level.getBlockState(pos.below());
        level.setBlock(pos, state, 3);
        data.setEnergy(100);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos).is(state.getBlock()), "Non-pickaxe destroyed node");
        h.assertTrue(data.getEnergy() == 100, "Failed break consumed energy");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        for (int i = 0; i < 5; i++) state.getDestroyProgress(player, level, pos);
        h.assertTrue(data.getEnergy() == 100, "Partial mining consumed energy");
        data.setEnergy(0);
        h.assertTrue(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos).is(state.getBlock()), "Exhausted break removed node");
        data.setEnergy(100);
        float cost = MineStoneMining.energyCost(player, state);
        int mysticBefore = data.getMysticStonesCrushed();
        int stonesBefore = data.getMineStonesBroken(), oresBefore = data.getMineOresBroken(), gemsBefore = data.getMineGemOresBroken();
        h.assertTrue(player.gameMode.destroyBlock(pos) && level.isEmptyBlock(pos), "Valid survival break failed");
        h.assertTrue(Math.abs(data.getEnergy() - (100 - cost)) < 0.0001, "Energy was not paid exactly once");
        boolean gemNode = source.equals("4") || source.equals("12") || source.equals("2") || source.equals("14") || source.equals("6") || source.equals("8") || source.equals("10") || source.equals("44");
        boolean oreNode = source.equals("VolcanoCoalNode1") || source.equals("BasicCoalNode0") || source.equals("BasicCoalNode1") || source.equals("850") || source.equals("VolcanoGoldNode") || source.equals("VolcanoCoalNode0") || source.equals("849") || source.equals("843") || source.equals("844") || source.equals("95") || source.equals("751") || source.equals("290") || source.equals("764") || source.equals("46") || source.equals("765") || MineStoneMining.isCalicoStone(source) || gemNode;
        h.assertTrue(data.getMineStonesBroken() == stonesBefore + (oreNode ? 0 : 1)
                && data.getMineOresBroken() == oresBefore + (oreNode ? 1 : 0)
                && data.getMineGemOresBroken() == gemsBefore + (gemNode ? 1 : 0),
                "Ground node mining statistics used the wrong category");
        player.gameMode.destroyBlock(pos);
        h.assertTrue(Math.abs(data.getEnergy() - (100 - cost)) < 0.0001, "Repeated break charged twice");
        h.assertTrue(level.getBlockState(pos.below()).equals(soil), "Mining removed the supporting soil");
        h.assertTrue(data.getMysticStonesCrushed() == mysticBefore + (source.equals("46") ? 1 : 0),
                "Mystic counter changed on ordinary/failed/repeated destruction");
        if (source.equals("46")) {
            var restored = com.stardew.craft.player.PlayerStardewData.fromNBT(data.toNBT(level.registryAccess()),
                    player.getUUID(), level.registryAccess());
            h.assertTrue(restored.getMysticStonesCrushed() == data.getMysticStonesCrushed(), "Mystic count was not persisted");
        }

        for (var entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
            h.assertTrue(!(entity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof MineStoneBlock) && !entity.getItem().is(Items.COAL), "Node/vanilla coal leaked into drops");
            entity.discard();
        }
        var worn = new ItemStack(Items.WOODEN_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, worn);
        level.setBlock(pos, state, 3);
        data.setEnergy(100);
        // A diamond's first 150 XP already raises mining to level one before this next break.
        float wornCost = MineStoneMining.energyCost(player, state);
        if (source.equals("2")) h.assertTrue(data.getSkillLevel(com.stardew.craft.player.SkillType.MINING) == 1
                && wornCost == 19, "Diamond XP did not immediately reduce the next ten-swing energy cost");
        h.assertTrue(player.gameMode.destroyBlock(pos) && player.getMainHandItem().isEmpty()
                && Math.abs(data.getEnergy() - (100 - wornCost)) < 0.0001,
                "Last durability lost its mining settlement for source " + source);
        var efficient = new ItemStack(ModItems.PICKAXE.get());
        com.stardew.craft.port.PortItemStacks.enchant(efficient, level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(StardewEnchantments.EFFICIENT), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, efficient);
        data.setEnergy(0);
        level.setBlock(pos, state, 3);
        h.assertTrue(player.gameMode.destroyBlock(pos) && data.getEnergy() == 0, "Efficient failed at zero energy");
        player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos, state, 3);
        data.setEnergy(100);
        h.assertTrue(player.gameMode.destroyBlock(pos) && data.getEnergy() == 100, "Creative removal consumed energy");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void source32DropBranches(GameTestHelper h) {
        var c = new MineStoneMining.Context(32, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var ore = MineStoneMining.roll(c, new FixedRandom(0.5, 0, 0));
        h.assertTrue(ore.miningExperience() == 5 && ore.drops().size() == 2
                && ore.drops().get(0).is(ModItems.COAL.get()) && ore.drops().get(1).is(ModItems.COPPER_ORE.get()), "Ore/coal branch differs");
        var warm = new MineStoneMining.Context(40, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var ochre = new MineStoneMining.Context(38, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        h.assertTrue(MineStoneMining.roll(warm, new FixedRandom(0.5, 0.05, 0.5)).miningExperience() == 5
                && MineStoneMining.roll(ochre, new FixedRandom(0.5, 0.05)).miningExperience() == 0,
                "Source 40 must use 1.2 ore multiplier; source 38 must use 0.8");
        var stone = MineStoneMining.roll(c, new FixedRandom(0.5, 0.5));
        h.assertTrue(stone.miningExperience() == 0 && stone.drops().size() == 1
                && com.stardew.craft.port.PortJava.getFirst(stone.drops()).is(ModItems.STONE.get()), "Ordinary stone rewards differ");
        var geode = MineStoneMining.roll(c, new FixedRandom(0, 0.5));
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(geode.drops()).is(ModItems.GEODE.get()), "Earth geode missing");
        var deep = new MineStoneMining.Context(32, 81, 0, 0, 0, false, true, false, false, false, false, 0, 0, false);
        var deepDrops = MineStoneMining.roll(deep, new FixedRandom(0, 0, 0.5));
        h.assertTrue(deepDrops.drops().get(0).is(ModItems.MAGMA_GEODE.get()) && deepDrops.drops().get(0).getCount() == 2
                && deepDrops.drops().get(1).is(ModItems.OMNI_GEODE.get()) && deepDrops.drops().get(1).getCount() == 2,
                "Depth/geologist geode branches differ");
        h.succeed();
    }


    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void richStoneAndSource42Rules(GameTestHelper h) {
        var red = new MineStoneMining.Context(42, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        h.assertTrue(MineStoneMining.roll(red, new FixedRandom(0.5, 0.05, 0.5)).miningExperience() == 5,
                "Source 42 lost its 1.2 ore modifier");
        var plain = new MineStoneMining.Context(668, 1, 0, 0, 0, false, false, true, true, true, false, 0, 0, false);
        var base = MineStoneMining.roll(plain, new FixedRandom(0.5, 0.5, 0.08));
        h.assertTrue(base.miningExperience() == 3 && base.drops().size() == 1
                && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.STONE.get()) && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount() == 1,
                "Rich stone must stop before ordinary geode/ore rolls; coal blessings do not change its 8% branch");
        var bonuses = new MineStoneMining.Context(668, 1, 10, 10, 0, false, false, false, false, false, false, 0, 2, false);
        var coal = MineStoneMining.roll(bonuses, new FixedRandom(0.05, 0.05, 0.079));
        h.assertTrue(coal.miningExperience() == 4 && coal.drops().size() == 2
                && coal.drops().get(0).getCount() == 5 && coal.drops().get(1).is(ModItems.COAL.get())
                && coal.drops().get(1).getCount() == 3, "Rich stone profession, statue, skill or coal quantity differs");
        h.assertTrue(ModBlocks.MINE_STONE_668.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 2,
                "Rich stone default health must be 2");
        for (int tier = 0; tier < 5; tier++) {
            h.assertTrue(MineStoneMining.breakTicks(2, tier + 1, tier, false) == (tier == 0 ? 24 : 12 - tier),
                    "Rich stone whole-swing timing differs");
            h.assertTrue(MineStoneMining.energyCost(2, tier + 1, 0) == (tier == 0 ? 4 : 2),
                    "Rich stone energy differs");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void copperAmethystAndRichVariantRules(GameTestHelper h) {
        var rich = new MineStoneMining.Context(670, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var richDrop = MineStoneMining.roll(rich, new FixedRandom(0.5, 0.5, 0.079));
        h.assertTrue(richDrop.miningExperience() == 4 && richDrop.drops().size() == 2
                && richDrop.drops().get(0).is(ModItems.STONE.get()) && richDrop.drops().get(1).is(ModItems.COAL.get()),
                "Source 670 differs from the rich-stone branch");
        var copper = new MineStoneMining.Context(751, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var base = MineStoneMining.roll(copper, new FixedRandom(0.5, 0.5));
        h.assertTrue(base.miningExperience() == 5 && base.drops().size() == 1
                && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.COPPER_ORE.get()) && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount() == 1,
                "Copper minimum or exclusive drop branch differs");
        var max = MineStoneMining.roll(copper, new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { return 0.5; }
        });
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(max.drops()).getCount() == 3, "Copper base range must include three ores");
        var bonus = new MineStoneMining.Context(751, 1, 10, 10, 0, false, false, false, false, false, false, 0, 2, false);
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(MineStoneMining.roll(bonus, new FixedRandom(0.099, 0.099)).drops()).getCount() == 5
                && com.stardew.craft.port.PortJava.getFirst(MineStoneMining.roll(bonus, new FixedRandom(0.1, 0.1)).drops()).getCount() == 3,
                "Copper profession/statue and independent skill chance boundaries differ");
        for (boolean mastery : new boolean[]{false, true}) {
            for (boolean geologist : new boolean[]{false, true}) {
                for (boolean triggered : new boolean[]{false, true}) {
                    var gem = new MineStoneMining.Context(8, 1, 10, 10, 0, false, geologist, false, false, false, false, 0, 2, mastery);
                    var drop = MineStoneMining.roll(gem, new FixedRandom(triggered));
                    boolean extra = geologist && triggered;
                    h.assertTrue(drop.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(drop.drops()).is(ModItems.AMETHYST.get())
                            && com.stardew.craft.port.PortJava.getFirst(drop.drops()).getCount() == (mastery ? 2 : 1) * (extra ? 2 : 1)
                            && drop.miningExperience() == (extra ? 8 : 16), "Amethyst mastery/Geologist branch differs");
                }
            }
        }
        h.assertTrue(MineStoneMining.stateForSource("751", 8).orElseThrow().getValue(MineStoneBlock.STONE_HEALTH) == 8,
                "Explicit volcano copper health was lost");
        int[] copperTicks = {36, 22, 10, 9, 8};
        int[] gemTicks = {60, 33, 20, 18, 8};
        for (int tier = 0; tier < 5; tier++) {
            h.assertTrue(MineStoneMining.breakTicks(3, tier + 1, tier, false) == copperTicks[tier]
                    && MineStoneMining.breakTicks(5, tier + 1, tier, false) == gemTicks[tier],
                    "Copper or amethyst duration lost the whole-swing rule");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void topazAndMixedGemRules(GameTestHelper h) {
        var topaz = new MineStoneMining.Context(10, 1, 10, 10, 0, false, true, false, false, false, false, 0, 2, true);
        var t = MineStoneMining.roll(topaz, new FixedRandom(true));
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(t.drops()).is(ModItems.TOPAZ.get()) && com.stardew.craft.port.PortJava.getFirst(t.drops()).getCount() == 4
                && t.miningExperience() == 8, "Topaz mastery/Geologist differs");
        net.minecraft.world.item.Item[] gems = {ModItems.DIAMOND.get(), ModItems.RUBY.get(), ModItems.JADE.get(),
                ModItems.AMETHYST.get(), ModItems.TOPAZ.get(), ModItems.EMERALD.get(), ModItems.AQUAMARINE.get()};
        int[] normalXp = {150, 80, 40, 16, 16, 80, 40};
        int[] extraXp = {100, 50, 20, 8, 8, 50, 20};
        for (int index = 0; index < 7; index++) {
            final int choice = index;
            for (boolean extra : new boolean[]{false, true}) {
                var c = new MineStoneMining.Context(44, 1, 0, 0, 0, false, extra, false, false, false, false, 0, 0, extra);
                var result = MineStoneMining.roll(c, new LegacyRandomSource(0) {
                    @Override public int nextInt(int bound) { h.assertTrue(bound == 7, "Mixed gem is not a uniform seven-way choice"); return choice; }
                    @Override public boolean nextBoolean() { return true; }
                });
                h.assertTrue(result.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(result.drops()).is(gems[index])
                        && com.stardew.craft.port.PortJava.getFirst(result.drops()).getCount() == (extra ? 4 : 1)
                        && result.miningExperience() == (extra ? extraXp[index] : normalXp[index]), "Mixed gem branch differs");
            }
        }
        var dark = new MineStoneMining.Context(34, 31, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        h.assertTrue(MineStoneMining.roll(dark, new FixedRandom(0.5, 0.5, 0.05)).miningExperience() == 0,
                "Dark stone must keep the ordinary 0.8 ore modifier");
        h.succeed();
    }

    /** Headless GameTest has no custom dimensions, but ordinary HUD sync reads mine progress. */
    @SuppressWarnings("unchecked")
    private static void withMiningDataLevel(GameTestHelper h, Runnable action) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            field.setAccessible(true);
            var levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>) field.get(h.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
            var previous = levels.put(key, h.getLevel());
            try { action.run(); }
            finally { if (previous == null) levels.remove(key); else levels.put(key, previous); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void frostOrdinaryHealthAndFloorDrops(GameTestHelper h) {
        int[] ticks = {36, 22, 10, 9, 8};
        for (int source : new int[]{48, 50, 52, 54}) {
            var mapped = MineStoneMining.stateForSource(Integer.toString(source), 5).orElseThrow();
            h.assertTrue(mapped.getValue(MineStoneBlock.STONE_HEALTH) == 5
                    && mapped.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 3,
                    "Frost node lost its source health or explicit generation override");
            for (int tier = 0; tier < 5; tier++) {
                h.assertTrue(MineStoneMining.breakTicks(3, tier + 1, tier, false) == ticks[tier],
                        "Frost stone tier timings differ from whole source swings");
            }
            var c = new MineStoneMining.Context(source, 41, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
            var geode = MineStoneMining.roll(c, new FixedRandom(0, 0.5, 0.5));
            h.assertTrue(geode.drops().stream().anyMatch(stack -> stack.is(ModItems.FROZEN_GEODE.get()))
                    && geode.miningExperience() == 0, "Frost ordinary stone used the wrong geode or XP branch");
            var iron = MineStoneMining.roll(c, new FixedRandom(0.5, 0.5, 0.039, 0.5, 0.74));
            h.assertTrue(iron.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(iron.drops()).is(ModItems.IRON_ORE.get())
                    && iron.miningExperience() == 5, "Floor 41 iron branch is wrong");
            var edge = MineStoneMining.roll(c, new FixedRandom(0.5, 0.5, 0.04001));
            h.assertTrue(edge.miningExperience() == 0 && com.stardew.craft.port.PortJava.getFirst(edge.drops()).is(ModItems.STONE.get()),
                    "Frost ordinary rock used the rich-stone ore multiplier");
            var dark = new MineStoneMining.Context(source, 71, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
            var gold = MineStoneMining.roll(dark, new FixedRandom(0.5, 0.5, 0.039, 0.5, 0.099));
            h.assertTrue(com.stardew.craft.port.PortJava.getFirst(gold.drops()).is(ModItems.GOLD_ORE.get()), "Deep frost gold branch missing");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void ironAndJadeSourceBranches(GameTestHelper h) {
        h.assertTrue(ModBlocks.MINE_STONE_290.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 4
                && ModBlocks.MINE_STONE_6.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 5,
                "Iron or jade has ordinary stone durability");
        for (int health : new int[]{3, 4, 6, 8}) {
            h.assertTrue(MineStoneMining.stateForSource("290", health).orElseThrow()
                    .getValue(MineStoneBlock.STONE_HEALTH) == health, "Iron source area health was lost");
        }
        int[] ironTicks = {48, 22, 20, 9, 8}, jadeTicks = {60, 33, 20, 18, 8};
        int[] ironHits = {4, 2, 2, 1, 1}, jadeHits = {5, 3, 2, 2, 1};
        for (int tier = 0; tier < 5; tier++) {
            h.assertTrue(MineStoneMining.breakTicks(4, tier + 1, tier, false) == ironTicks[tier]
                    && MineStoneMining.breakTicks(5, tier + 1, tier, false) == jadeTicks[tier],
                    "Iron/jade timing is not derived from source whole hits");
            h.assertTrue(MineStoneMining.energyCost(4, tier + 1, 0) == ironHits[tier] * 2
                    && MineStoneMining.energyCost(5, tier + 1, 0) == jadeHits[tier] * 2,
                    "Iron/jade energy differs from source hits");
        }
        // Two level rolls only: geologist/coal/geode flags must not add a second drop branch.
        var iron = new MineStoneMining.Context(290, 41, 100, 100, 0, true, true, true, true, true, false, 0, 2, true);
        var boosted = MineStoneMining.roll(iron, new FixedRandom(0, 0));
        h.assertTrue(boosted.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).is(ModItems.IRON_ORE.get())
                && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).getCount() == 5 && boosted.miningExperience() == 12,
                "Iron added ores/level bonuses/XP differ from source 290");
        var base = MineStoneMining.roll(new MineStoneMining.Context(290, 121, 0, 0, 0, false, false, false,
                false, false, false, 0, 0, false), new FixedRandom(1, 1));
        h.assertTrue(base.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount() == 1
                && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.IRON_ORE.get()) && base.miningExperience() == 12,
                "Iron node fell through to floor-based ore selection");
        var jade = new MineStoneMining.Context(6, 41, 100, 100, 0, true, true, true, true, true, false, 0, 99, true);
        var extra = MineStoneMining.roll(jade, new FixedRandom(true));
        var noExtra = MineStoneMining.roll(jade, new FixedRandom(false));
        h.assertTrue(extra.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(extra.drops()).is(ModItems.JADE.get())
                && com.stardew.craft.port.PortJava.getFirst(extra.drops()).getCount() == 4 && extra.miningExperience() == 20,
                "Jade mastery/geologist quantities or replacement XP differ");
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(noExtra.drops()).getCount() == 2 && noExtra.miningExperience() == 40,
                "Jade wrongly used added ores or reduced XP without geologist extra");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void aquamarineAndDiamondExactBranches(GameTestHelper h) {
        h.assertTrue(ModBlocks.MINE_STONE_14.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 5
                && ModBlocks.MINE_STONE_2.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 10,
                "Diamond rare mine durability was confused with the quarry's five HP");
        for (int hp : new int[]{5, 10}) h.assertTrue(MineStoneMining.stateForSource("2", hp).orElseThrow()
                .getValue(MineStoneBlock.STONE_HEALTH) == hp, "Diamond context HP was lost");
        int[] diamondTicks = {120, 55, 40, 27, 16};
        int[] diamondHits = {10, 5, 4, 3, 2};
        for (int tier = 0; tier < 5; tier++) {
            h.assertTrue(MineStoneMining.breakTicks(10, tier + 1, tier, false) == diamondTicks[tier]
                    && MineStoneMining.energyCost(10, tier + 1, 0) == diamondHits[tier] * 2,
                    "Diamond mining is not based on ten source HP and whole swings");
        }
        h.assertTrue(MineStoneMining.breakTicks(10, 5, 4, true) == 11, "Swift diamond timing mismatch");
        for (int source : new int[]{14, 2}) {
            var item = source == 14 ? ModItems.AQUAMARINE.get() : ModItems.DIAMOND.get();
            var base = new MineStoneMining.Context(source, 61, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
            var plain = MineStoneMining.roll(base, new FixedRandom());
            h.assertTrue(plain.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(plain.drops()).is(item)
                    && com.stardew.craft.port.PortJava.getFirst(plain.drops()).getCount() == 1 && plain.miningExperience() == (source == 14 ? 40 : 150),
                    "Gem produced wrong item/XP or fell through to ordinary rock drops");
            var boosted = new MineStoneMining.Context(source, 61, 100, 100, 1, true, true, true, true, true, false, 0, 99, true);
            var extra = MineStoneMining.roll(boosted, new FixedRandom(true));
            var noExtra = MineStoneMining.roll(boosted, new FixedRandom(false));
            h.assertTrue(com.stardew.craft.port.PortJava.getFirst(extra.drops()).getCount() == 4 && com.stardew.craft.port.PortJava.getFirst(extra.drops()).is(item)
                    && extra.miningExperience() == (source == 14 ? 20 : 100), "Gem mastery/geologist replacement XP mismatch");
            h.assertTrue(com.stardew.craft.port.PortJava.getFirst(noExtra.drops()).getCount() == 2
                    && noExtra.miningExperience() == (source == 14 ? 40 : 150), "Gem incorrectly used ore bonuses");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void lavaOrdinarySourceRules(GameTestHelper h) {
        int[] ticks = {48, 22, 20, 9, 8}, hits = {4, 2, 2, 1, 1};
        for (int source : new int[]{56, 58, 760, 762}) {
            var state = MineStoneMining.stateForSource(Integer.toString(source), 4).orElseThrow();
            h.assertTrue(state.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 4,
                    "Lava rock used earth's one HP or a dangerous appearance's five HP");
            for (int tier = 0; tier < 5; tier++) h.assertTrue(
                    MineStoneMining.breakTicks(4, tier + 1, tier, false) == ticks[tier]
                            && MineStoneMining.energyCost(4, tier + 1, 0) == hits[tier] * 2,
                    "Lava rock timing/energy do not match four source HP");
            for (int floor : new int[]{91, 115}) {
                var context = new MineStoneMining.Context(source, floor, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
                var geode = MineStoneMining.roll(context, new FixedRandom(0, 0.5, 0.5));
                h.assertTrue(geode.drops().stream().anyMatch(s -> s.is(ModItems.MAGMA_GEODE.get()))
                        && geode.miningExperience() == 0, "Lava ordinary rock used wrong geode/XP branch");
                var gold = MineStoneMining.roll(context, new FixedRandom(0.5, 0.5, 0.039, 0.5, 0.74));
                h.assertTrue(gold.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(gold.drops()).is(ModItems.GOLD_ORE.get())
                        && gold.miningExperience() == 5, "Lava floor-based gold branch missing");
                var edge = MineStoneMining.roll(context, new FixedRandom(0.5, 0.5, 0.04001));
                h.assertTrue(edge.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(edge.drops()).is(ModItems.STONE.get())
                        && edge.miningExperience() == 0, "Lava ordinary stone used a rich ore modifier");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void goldSourceHealthAndRewards(GameTestHelper h) {
        h.assertTrue(ModBlocks.MINE_STONE_764.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 8,
                "Gold node default must retain normal eight HP");
        int[] normalTicks = {96, 44, 30, 18, 16}, dangerTicks = {84, 44, 30, 18, 16};
        int[] normalHits = {8, 4, 3, 2, 2}, dangerHits = {7, 4, 3, 2, 2};
        for (int hp : new int[]{7, 8}) {
            var state = MineStoneMining.stateForSource("764", hp).orElseThrow();
            h.assertTrue(state.getValue(MineStoneBlock.STONE_HEALTH) == hp, "Gold source context HP lost");
            for (int tier = 0; tier < 5; tier++) {
                int hits = (hp == 8 ? normalHits : dangerHits)[tier];
                h.assertTrue(MineStoneMining.breakTicks(hp, tier + 1, tier, false)
                        == (hp == 8 ? normalTicks : dangerTicks)[tier]
                        && MineStoneMining.energyCost(hp, tier + 1, 0) == hits * 2
                        && MineStoneMining.energyCost(hp, tier + 1, 10) == hits,
                        "Gold timing or level-based energy differs from source swings");
            }
        }
        h.assertTrue(MineStoneMining.breakTicks(8, 5, 4, true) == 11, "Swift iridium gold timing drifted");
        for (int floor : new int[]{1, 91, 115, 121}) {
            var base = MineStoneMining.roll(new MineStoneMining.Context(764, floor, 0, 0, 0, false, false, false,
                    false, false, false, 0, 0, false), new FixedRandom(1, 1));
            h.assertTrue(base.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.GOLD_ORE.get())
                    && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount() == 1 && base.miningExperience() == 18,
                    "Gold fell through to ordinary floor-based drops or wrong XP");
        }
        int[] rolls = {0};
        var high = new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { rolls[0]++; return 0; }
        };
        var boosted = MineStoneMining.roll(new MineStoneMining.Context(764, 121, 100, 100, 0,
                true, true, true, true, true, false, 0, 2, true), high);
        h.assertTrue(boosted.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).is(ModItems.GOLD_ORE.get())
                && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).getCount() == 7 && boosted.miningExperience() == 18 && rolls[0] == 2,
                "Gold maximum amount, added ores, level bonuses or exclusive drop branch differs");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void rubyEmeraldSourceRewards(GameTestHelper h) {
        for (int source : new int[]{4, 12}) {
            var state = MineStoneMining.stateForSource(Integer.toString(source), 5).orElseThrow();
            h.assertTrue(state.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 5,
                    "Ruby/emerald default HP must match the five HP gem return");
            int[] ticks = {60, 33, 20, 18, 8}, hits = {5, 3, 2, 2, 1};
            for (int tier = 0; tier < 5; tier++) h.assertTrue(MineStoneMining.breakTicks(5, tier + 1, tier, false) == ticks[tier]
                    && MineStoneMining.energyCost(5, tier + 1, 0) == hits[tier] * 2, "Gem timing/energy drift");
            var context = new MineStoneMining.Context(source, 85, 100, 100, 0, true, true, true, true, true, false, 0, 99, true);
            var extra = MineStoneMining.roll(context, new FixedRandom(true));
            var plain = MineStoneMining.roll(context, new FixedRandom(false));
            var item = source == 4 ? ModItems.RUBY.get() : ModItems.EMERALD.get();
            h.assertTrue(extra.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(extra.drops()).is(item)
                    && com.stardew.craft.port.PortJava.getFirst(extra.drops()).getCount() == 4 && extra.miningExperience() == 50,
                    "Ruby/emerald extra XP or mastery/geologist quantities differ");
            h.assertTrue(plain.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(plain.drops()).is(item)
                    && com.stardew.craft.port.PortJava.getFirst(plain.drops()).getCount() == 2 && plain.miningExperience() == 80,
                    "Ruby/emerald used ore bonuses or wrong base XP");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void mysticContextHealthAndRewards(GameTestHelper h) {
        for (int hp : new int[]{1, 4, 5, 10, 12}) h.assertTrue(MineStoneMining.stateForSource("46", hp).orElseThrow()
                .getValue(MineStoneBlock.STONE_HEALTH) == hp, "Mystic inherited/quarry/volcano HP lost");
        int[] ticks = {144, 66, 40, 27, 24}, hits = {12, 6, 4, 3, 3};
        for (int tier = 0; tier < 5; tier++) h.assertTrue(MineStoneMining.breakTicks(12, tier + 1, tier, false) == ticks[tier]
                && MineStoneMining.energyCost(12, tier + 1, 0) == hits[tier] * 2,
                "Quarry mystic time/energy differs from twelve source HP");
        var context = new MineStoneMining.Context(46, 121, 100, 100, 0, true, true, true, true, true, false, 0, 99, true);
        var miss = MineStoneMining.roll(context, new FixedRandom(0.25));
        h.assertTrue(miss.drops().size() == 2 && miss.drops().get(0).is(ModItems.IRIDIUM_ORE.get())
                && miss.drops().get(0).getCount() == 1 && miss.drops().get(1).is(ModItems.GOLD_ORE.get())
                && miss.drops().get(1).getCount() == 1 && miss.miningExperience() == 150,
                "Mystic minimum ores, exclusive shard boundary or XP differs");
        int[] rolls = {0};
        var high = new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { rolls[0]++; return 0.249999; }
        };
        var hit = MineStoneMining.roll(context, high);
        h.assertTrue(hit.drops().size() == 3 && hit.drops().get(0).is(ModItems.IRIDIUM_ORE.get())
                && hit.drops().get(0).getCount() == 3 && hit.drops().get(1).is(ModItems.GOLD_ORE.get())
                && hit.drops().get(1).getCount() == 4 && hit.drops().get(2).is(ModItems.PRISMATIC_SHARD.get())
                && hit.drops().get(2).getCount() == 1 && hit.miningExperience() == 150 && rolls[0] == 1,
                "Mystic max ores/shard differs or ordinary/additional ore bonuses leaked");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void iridiumSourceRewardsAndDuration(GameTestHelper h) {
        h.assertTrue(ModBlocks.MINE_STONE_765.get().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 16,
                "Iridium must preserve sixteen source HP");
        int[] ticks = {192, 88, 60, 36, 32}, hits = {16, 8, 6, 4, 4};
        for (int tier = 0; tier < 5; tier++) h.assertTrue(MineStoneMining.breakTicks(16, tier + 1, tier, false) == ticks[tier]
                && MineStoneMining.energyCost(16, tier + 1, 0) == hits[tier] * 2, "Iridium duration/energy drift");
        // Include long configured durations: a fixed epsilon can otherwise finish those early.
        for (int duration : new int[]{1, 6, 12, 22, 32, 60, 66, 88, 144, 192, 1196, 1200, 19136, 19200}) {
            h.assertTrue(clientProgressTicks(duration) == duration,
                    "Float accumulation changed configured mining duration " + duration);
        }
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Iridium timing"));
        var state = ModBlocks.MINE_STONE_765.get().defaultBlockState();
        Item[] tools = {ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(), ModItems.STEEL_PICKAXE.get(),
                ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get(), Items.NETHERITE_PICKAXE};
        for (int tier = 0; tier < tools.length; tier++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tools[tier]));
            h.assertTrue(serverProgressTicks(state.getDestroyProgress(player, h.getLevel(), BlockPos.ZERO))
                    == ticks[tier == 5 ? 0 : tier], "Actual server progress differs for iridium tier " + tier);
        }
        h.assertTrue(MineStoneMining.breakTicks(16, 5, 4, true) == 22
                && MineStoneMining.breakTicks(16, 6, 4, false) == 24, "Swift/Powerful iridium advantages drifted");
        var base = new MineStoneMining.Context(765, 121, 0, 0, 0, false, false, false, false, false, true, 999, 0, false);
        var miss = MineStoneMining.roll(base, new FixedRandom(1, 1, 0.035));
        h.assertTrue(miss.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(miss.drops()).is(ModItems.IRIDIUM_ORE.get())
                && com.stardew.craft.port.PortJava.getFirst(miss.drops()).getCount() == 1 && miss.miningExperience() == 50,
                "Iridium base drop or 3.5 percent boundary differs");
        int[] rolls = {0};
        var high = new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { rolls[0]++; return 0.034999; }
        };
        var boosted = MineStoneMining.roll(new MineStoneMining.Context(765, 121, 100, 100, 0, true, true, true,
                true, true, true, 999, 2, true), high);
        h.assertTrue(boosted.drops().size() == 2 && boosted.drops().get(0).is(ModItems.IRIDIUM_ORE.get())
                && boosted.drops().get(0).getCount() == 7 && boosted.drops().get(1).is(ModItems.PRISMATIC_SHARD.get())
                && boosted.drops().get(1).getCount() == 1 && boosted.miningExperience() == 50 && rolls[0] == 3,
                "Iridium bonus ores, independent shard or exclusive branch differs");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void calicoFullSourceIdentityAndRewards(GameTestHelper h) {
        for (String source : java.util.List.of("CalicoEggStone_0", "CalicoEggStone_1", "CalicoEggStone_2")) {
            var state = MineStoneMining.stateForSource(source, 8).orElseThrow();
            h.assertTrue(((MineStoneBlock)state.getBlock()).sourceId().equals(source)
                    && state.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == 8,
                    "Full calico source identity or eight HP lost");
            int[] ticks = {96, 44, 30, 18, 16}, hits = {8, 4, 3, 2, 2};
            for (int tier = 0; tier < 5; tier++) h.assertTrue(MineStoneMining.breakTicks(8, tier + 1, tier, false) == ticks[tier]
                    && MineStoneMining.energyCost(8, tier + 1, 0) == hits[tier] * 2, "Calico duration/energy drift");
            var base = MineStoneMining.roll(new MineStoneMining.Context(source, 1, 0, 0, 0, false, false,
                    false, false, false, false, 0, 99, false), new FixedRandom(1, 1));
            h.assertTrue(base.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.CALICO_EGG.get())
                    && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount() == 1 && base.miningExperience() == 50,
                    "Calico node requires active festival or incorrectly applies added ores");
            int[] rolls = {0};
            var high = new LegacyRandomSource(0) {
                @Override public int nextInt(int bound) { return bound - 1; }
                @Override public double nextDouble() { rolls[0]++; return 0; }
            };
            var boosted = MineStoneMining.roll(new MineStoneMining.Context(source, 999, 100, 100, 0,
                    true, true, true, true, true, true, 999, 99, true), high);
            h.assertTrue(boosted.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).is(ModItems.CALICO_EGG.get())
                    && com.stardew.craft.port.PortJava.getFirst(boosted.drops()).getCount() == 5 && boosted.miningExperience() == 50 && rolls[0] == 2,
                    "Calico max reward has ore/mastery/geologist/shard/festival extras");
        }
        h.assertTrue(!MineStoneMining.stateForSource("CalicoEggStone_0", 8).orElseThrow().getBlock()
                .equals(MineStoneMining.stateForSource("CalicoEggStone_1", 8).orElseThrow().getBlock()),
                "Distinct calico appearances collapsed to one block");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void guaranteedGeodeNodesPreserveSourceRules(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Geode timing"));
        Item[] tools = {ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(), ModItems.STEEL_PICKAXE.get(),
                ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get(), Items.NETHERITE_PICKAXE};
        for (int source : new int[]{75, 76, 77}) {
            int hp = source == 75 ? 3 : source == 76 ? 5 : 7;
            var state = MineStoneMining.stateForSource(Integer.toString(source), hp).orElseThrow();
            h.assertTrue(state.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH) == hp,
                    "Farm source geode HP lost");
            int[] ticks = source == 75 ? new int[]{36, 22, 10, 9, 8} : source == 76 ? new int[]{60, 33, 20, 18, 8} : new int[]{84, 44, 30, 18, 16};
            int[] hits = source == 75 ? new int[]{3, 2, 1, 1, 1} : source == 76 ? new int[]{5, 3, 2, 2, 1} : new int[]{7, 4, 3, 2, 2};
            for (int tier = 0; tier < tools.length; tier++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tools[tier]));
                int effectiveTier = tier == 5 ? 0 : tier;
                h.assertTrue(serverProgressTicks(state.getDestroyProgress(player, h.getLevel(), BlockPos.ZERO))
                        == ticks[effectiveTier] && clientProgressTicks(ticks[effectiveTier]) == ticks[effectiveTier],
                        "Geode long-press timing differs for source " + source + " tier " + tier);
                h.assertTrue(MineStoneMining.energyCost(hp, effectiveTier + 1, 0) == hits[effectiveTier] * 2,
                        "Geode energy changed from whole source hits");
            }
            for (boolean boosted : new boolean[]{false, true}) {
                // No random draw may change the fixed node reward, even with all geode bonuses enabled.
                var noRandom = new LegacyRandomSource(0) {
                    @Override public double nextDouble() { throw new AssertionError("Unexpected geode chance"); }
                    @Override public int nextInt(int bound) { throw new AssertionError("Unexpected geode quantity roll"); }
                    @Override public boolean nextBoolean() { throw new AssertionError("Unexpected geologist extra"); }
                };
                var result = MineStoneMining.roll(new MineStoneMining.Context(source, boosted ? 999 : 1,
                        100, 100, 1, boosted, boosted, boosted, boosted, boosted, boosted, 999, 99, boosted), noRandom);
                h.assertTrue(result.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(result.drops()).getCount() == 1
                        && com.stardew.craft.port.PortJava.getFirst(result.drops()).is(source == 75 ? ModItems.GEODE.get() : source == 76 ? ModItems.FROZEN_GEODE.get() : ModItems.MAGMA_GEODE.get())
                        && result.miningExperience() == (source == 75 ? 8 : source == 76 ? 16 : 32), "Guaranteed geode drop or XP differs");
            }
        }
        h.assertTrue(java.util.stream.Stream.of("CalicoEggStone_0", "CalicoEggStone_1", "CalicoEggStone_2")
                .map(id -> MineStoneMining.stateForSource(id, 8).orElseThrow().getBlock()).distinct().count() == 3,
                "The three original calico appearances share a block identity");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void radioactiveHealthAndRewardBoundaries(GameTestHelper h) {
        var state = ModBlocks.MINE_STONE_95.get().defaultBlockState();
        h.assertTrue(state.getValue(MineStoneBlock.STONE_HEALTH) == 25
                && MineStoneMining.stateForSource("95", 25).orElseThrow().equals(state), "Radioactive 25 HP lost");
        int[] ticks = {300, 143, 90, 63, 40}, hits = {25, 13, 9, 7, 5};
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Radioactive timing"));
        Item[] tools = {ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(), ModItems.STEEL_PICKAXE.get(),
                ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get(), Items.NETHERITE_PICKAXE};
        for (int tier = 0; tier < tools.length; tier++) {
            int effective = tier == 5 ? 0 : tier;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tools[tier]));
            h.assertTrue(serverProgressTicks(state.getDestroyProgress(player, h.getLevel(), BlockPos.ZERO)) == ticks[effective]
                    && clientProgressTicks(ticks[effective]) == ticks[effective]
                    && MineStoneMining.energyCost(25, effective + 1, 0) == hits[effective] * 2,
                    "Radioactive timing/energy differs with " + tools[tier]);
        }
        var c = new MineStoneMining.Context(95, 131, 10, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var miss = MineStoneMining.roll(c, new FixedRandom(1, 0.05));
        var hit = MineStoneMining.roll(c, new FixedRandom(1, 0.049999));
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(miss.drops()).getCount() == 1 && com.stardew.craft.port.PortJava.getFirst(hit.drops()).getCount() == 2,
                "Radioactive mining chance must be mining/200, strictly below boundary");
        var high = new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { return 0; }
        };
        var rich = MineStoneMining.roll(new MineStoneMining.Context(95, 999, 100, 100, 1, true, true, true,
                true, true, true, 999, 2, true), high);
        h.assertTrue(rich.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(rich.drops()).is(ModItems.RADIOACTIVE_ORE.get())
                && com.stardew.craft.port.PortJava.getFirst(rich.drops()).getCount() == 6 && rich.miningExperience() == 18,
                "Radioactive base1..2/added ores/exclusive reward differs");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void surfaceStoneDateAndOutdoorBranches(GameTestHelper h) {
        var c = new MineStoneMining.Context(343, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var dayOne = MineStoneMining.rollSurfaceStone(c, 1, true, true, new FixedRandom(0, 0, 0), new FixedRandom(1, 1));
        h.assertTrue(dayOne.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(dayOne.drops()).is(ModItems.STONE.get())
                && dayOne.miningExperience() == 1, "First day has dated extras or lacks outdoor stone/XP");
        for (int day : new int[]{2, 60, 61, 120, 121}) {
            var early = MineStoneMining.rollSurfaceStone(c, day, false, true,
                    day > 60 ? new FixedRandom(0, 0, 1, 1) : new FixedRandom(0, 1, 1), new FixedRandom());
            h.assertTrue(early.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(early.drops()).is(
                    day > 60 ? ModItems.FROZEN_GEODE.get() : ModItems.GEODE.get()) && early.miningExperience() == 0,
                    "Days60 boundary or indoor fixed stone differs");
        }
        var magma = MineStoneMining.rollSurfaceStone(c, 121, false, true,
                new FixedRandom(0, 0.2, 0.19999, 1, 1), new FixedRandom());
        h.assertTrue(magma.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(magma.drops()).is(ModItems.MAGMA_GEODE.get()),
                "Magma must follow failed frozen roll after day120");
        var neutral = MineStoneMining.rollSurfaceStone(c, 121, false, true,
                new FixedRandom(0, 0.2, 0.2, 1, 1), new FixedRandom());
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(neutral.drops()).is(ModItems.GEODE.get()), "Geode strict20 percent edge differs");
        var boosted = new MineStoneMining.Context(343, 1, 0, 0, 0, true, true, true, true, true, false, 0, 99, true);
        var all = MineStoneMining.rollSurfaceStone(boosted, 2, true, true, new FixedRandom(0.04, 0.09, 0), new FixedRandom(0, 0));
        long coal = all.drops().stream().filter(s -> s.is(ModItems.COAL.get())).mapToInt(ItemStack::getCount).sum();
        long stone = all.drops().stream().filter(s -> s.is(ModItems.STONE.get())).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(all.drops().size() == 6 && coal == 3 && stone == 2
                && com.stardew.craft.port.PortJava.getFirst(all.drops()).is(ModItems.GEODE.get()) && com.stardew.craft.port.PortJava.getFirst(all.drops()).getCount() == 1
                && all.miningExperience() == 6, "Separate dated/profession/outdoor rolls or source XP differ");
        var noPlayer = MineStoneMining.rollSurfaceStone(c, 1, true, false, new FixedRandom(0, 0, 0), new FixedRandom(0));
        h.assertTrue(noPlayer.drops().size() == 2 && noPlayer.miningExperience() == 0, "Unowned outdoor destruction changed rewards");
        var interior = com.stardew.craft.api.v1.world.StardewLocations.all().stream()
                .filter(com.stardew.craft.api.v1.world.StardewLocation::indoor).findFirst().orElseThrow();
        h.assertTrue(!MineStoneMining.surfaceIsOutdoors(interior.dimension(), interior.min(), true),
                "A registered indoor room in a sky-lit dimension received outdoor stone rewards");
        for (var block : java.util.List.of(ModBlocks.MINE_STONE_343.get(), ModBlocks.MINE_STONE_450.get())) {
        var state = block.defaultBlockState();
        h.assertTrue(state.getValue(MineStoneBlock.STONE_HEALTH) == 1, "Surface default source HP changed");
        // Exercise the actual non-mine dispatcher, not only the pure probability helper.
        h.assertTrue(h.getLevel().dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING
                && h.getLevel().dimensionType().hasSkyLight(), "Surface settlement fixture is not outdoors");
        var pos = h.absolutePos(new BlockPos(12, 2, 12));
        for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) e.discard();
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Surface settlement"));
        withMiningDataLevel(h, () -> MineStoneMining.finishDrops(h.getLevel(), player, pos, state, false));
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .anyMatch(e -> e.getItem().is(ModItems.STONE.get())), "Runtime outdoor dispatcher failed guaranteed stone");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void festivalStoneEggEntryPointBoundaries(GameTestHelper h) {
        var zero = new LegacyRandomSource(0) {
            @Override public float nextFloat() { return 0; }
            @Override public int nextInt(int bound) { return 0; }
        };
        var max = new LegacyRandomSource(0) {
            @Override public float nextFloat() { return 0.009999F; }
            @Override public int nextInt(int bound) { return bound - 1; }
        };
        var edge = new LegacyRandomSource(0) { @Override public float nextFloat() { return 0.01F; } };
        var cap = new LegacyRandomSource(0) { @Override public float nextFloat() { return 0.5F; } };
        h.assertTrue(com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(120, zero) == 0
                && com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(121, zero) == 1
                && com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(121, max) == 3
                && com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(121, edge) == 0
                && com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(122, edge) >= 1
                && com.stardew.craft.festival.desert.DesertFestivalMineService.rollStoneEggCount(100000, cap) == 0,
                "Source entry festival eggs miss first skull floor, amount, depth or cap boundary");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void musselAndBoneSourceBranches(GameTestHelper h) {
        var mussel = new MineStoneMining.Context(25, 150, 10, 10, 0.1, true, true, true, true, true, true, 99, 99, true);
        var minimum = MineStoneMining.roll(mussel, new FixedRandom());
        var maximum = MineStoneMining.roll(mussel, new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound - 1; }
            @Override public double nextDouble() { throw new AssertionError("Mussel base reward consumed unrelated bonus roll"); }
        });
        h.assertTrue(minimum.drops().size() == 1 && com.stardew.craft.port.PortJava.getFirst(minimum.drops()).is(ModItems.MUSSEL.get())
                && com.stardew.craft.port.PortJava.getFirst(minimum.drops()).getCount() == 2 && com.stardew.craft.port.PortJava.getFirst(maximum.drops()).getCount() == 4
                && maximum.miningExperience() == 5, "Mussel amount/XP changed with unrelated bonuses");
        for (int boneSource : new int[]{816,817}) {
        var bone = new MineStoneMining.Context(boneSource, 1, 10, 10, 0, false, false, false, false, false, false, 0, 2, false);
        var leg = MineStoneMining.roll(bone, new FixedRandom(0.099999, 0, 0));
        var ribs = MineStoneMining.roll(bone, new FixedRandom(0.1, 0.014999, 0.1, 0.1));
        var artifact = MineStoneMining.roll(bone, new FixedRandom(0.1, 0.015, 0.099999, 1, 1));
        var none = MineStoneMining.roll(bone, new FixedRandom(0.1, 0.015, 0.1, 1, 1));
        h.assertTrue(leg.drops().size() == 2 && com.stardew.craft.port.PortJava.getFirst(leg.drops()).is(ModItems.FOSSILIZED_LEG.get())
                && com.stardew.craft.port.PortJava.getLast(leg.drops()).is(ModItems.BONE_FRAGMENT.get()) && com.stardew.craft.port.PortJava.getLast(leg.drops()).getCount() == 5,
                "Leg branch must stop later fossil rolls and still award fragments");
        h.assertTrue(ribs.drops().size() == 2 && com.stardew.craft.port.PortJava.getFirst(ribs.drops()).is(ModItems.FOSSILIZED_RIBS.get())
                && com.stardew.craft.port.PortJava.getLast(ribs.drops()).getCount() == 3, "Conditional ribs branch or skill boundary changed");
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(artifact.drops()).is(ModItems.PREHISTORIC_SCAPULA.get())
                && none.drops().size() == 1 && none.miningExperience() == 6, "Third rare branch must be conditional");
        var lastArtifact = MineStoneMining.roll(bone, new LegacyRandomSource(0) {
            private int index;
            @Override public double nextDouble() { return index++ == 2 ? 0 : 1; }
            @Override public int nextInt(int bound) { return bound - 1; }
        });
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(lastArtifact.drops()).is(ModItems.TRILOBITE.get())
                && com.stardew.craft.port.PortJava.getLast(lastArtifact.drops()).getCount() == 4, "Artifact579..589 range or fragment1..2 endpoint missing");
        }
        int[][] expectedTicks = {{12,11,10,9,8},{96,44,30,18,16},{48,22,20,9,8},{48,22,20,9,8},{48,22,20,9,8},{96,44,30,18,16}};
        int[][] expectedEnergy = {{2,2,2,2,2},{16,8,6,4,4},{8,4,4,2,2},{8,4,4,2,2},{8,4,4,2,2},{16,8,6,4,4}};
        int[] ids = {450,25,816,817,818,819}; int[] hp = {1,8,4,4,4,8};
        for (int i=0;i<ids.length;i++) {
            h.assertTrue(MineStoneMining.stateForSource(Integer.toString(ids[i]),hp[i]).orElseThrow().getBlock()
                    .defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH)==hp[i], "Source default health mismatch");
            for(int tier=0;tier<5;tier++) h.assertTrue(MineStoneMining.breakTicks(hp[i],tier+1,tier,false)==expectedTicks[i][tier]
                    && MineStoneMining.energyCost(hp[i],tier+1,0)==expectedEnergy[i][tier], "Source swing timing/energy mismatch");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void islandRewardsQuotaPersistenceAndPickup(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(8,2,8));
        String previous=com.stardew.craft.interior.InteriorRegionRegistry.getCachedJson();
        var storage=level.getServer().overworld().getDataStorage();
        var previousWalnuts=com.stardew.craft.mining.GoldenWalnutData.get(level.getServer());
        var walnuts=new com.stardew.craft.mining.GoldenWalnutData();
        storage.set("stardewcraft_golden_walnuts",walnuts);
        try {
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2))) entity.discard();
            // No logical island identity: no random call, no island-only reward.
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"25",true,new FixedRandom());
            com.stardew.craft.mining.IslandStoneRewards.afterNodeDrop(level,pos,"25",new FixedRandom());
            var root=com.google.gson.JsonParser.parseString(previous).getAsJsonObject();
            var location=new com.google.gson.JsonObject();location.addProperty("dimension",level.dimension().location().toString());
            location.addProperty("ledger_id","IslandWest");location.addProperty("priority",100000);
            var min=new com.google.gson.JsonArray();var max=new com.google.gson.JsonArray();
            for(int v:new int[]{pos.getX()-1,pos.getY()-1,pos.getZ()-1}) min.add(v);
            for(int v:new int[]{pos.getX()+1,pos.getY()+1,pos.getZ()+1}) max.add(v);
            location.add("min",min);location.add("max",max);root.add("stardewcraft:test_mussel_island",location);
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(root.toString());
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"25",true,new FixedRandom(0.025));
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"816",true,new FixedRandom(0.01));
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).isEmpty(), "West strict chance boundary changed");
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"25",true,new FixedRandom(0.024999));
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"816",true,new FixedRandom(0.009999));
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream()
                    .mapToInt(e->e.getItem().is(ModItems.SNAKE_VERTEBRAE.get())?e.getItem().getCount():0).sum()==2,
                    "West snake vertebrae must apply2.5% to mussels and1% to other stones");
            com.stardew.craft.mining.IslandStoneRewards.afterNodeDrop(level,pos,"25",new FixedRandom(0.1));
            h.assertTrue(walnuts.musselDrops()==0,"Walnut strict10% boundary changed");
            for(int i=0;i<7;i++) com.stardew.craft.mining.IslandStoneRewards.afterNodeDrop(level,pos,"25",new FixedRandom(0));
            h.assertTrue(walnuts.musselDrops()==5 && walnuts.found()==0 && walnuts.balance()==0,
                    "Lifetime5 quota must be spent at drop time, without crediting uncollected nuts");
            location.addProperty("ledger_id","VolcanoDungeon");
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(root.toString());
            var placer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Source849 placement"));
            level.setBlock(pos.below(),ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(),3);
            var copperItem=new ItemStack(ModItems.MINE_STONE_849.get());
            var hitResult=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
            var placement=new net.minecraft.world.item.context.BlockPlaceContext(level,placer,InteractionHand.MAIN_HAND,copperItem,hitResult);
            h.assertTrue(ModBlocks.MINE_STONE_849.get().getStateForPlacement(placement).getValue(MineStoneBlock.STONE_HEALTH)==1,
                    "849 in declared volcano must retain source default1HP, even on earth-looking soil");
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"843",false,new FixedRandom());
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"843",true,new FixedRandom(0.03));
            h.assertTrue(walnuts.volcanoDrops()==0,"Volcano must require player and strict3% threshold");
            for(int i=0;i<7;i++) com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"843",true,new FixedRandom(0));
            h.assertTrue(walnuts.volcanoDrops()==5 && walnuts.musselDrops()==5 && walnuts.found()==0,
                    "VolcanoMining and MusselStone must have independent lifetime5 quotas");
            location.addProperty("ledger_id","Caldera");
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(root.toString());
            com.stardew.craft.mining.IslandStoneRewards.beforeNodeDrop(level,pos,"843",true,new FixedRandom());
            h.assertTrue(ModBlocks.MINE_STONE_849.get().getStateForPlacement(placement).getValue(MineStoneBlock.STONE_HEALTH)==6,
                    "849 outside VolcanoDungeon must use default dangerous-mine6HP");
            h.assertTrue(!com.stardew.craft.mining.IslandStoneRewards.isVolcano(
                    com.stardew.craft.api.v1.world.StardewLocations.find(level,pos).orElseThrow()),"Caldera is not VolcanoDungeon");
            var tag=walnuts.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            var restored=com.stardew.craft.mining.GoldenWalnutData.load(tag,level.registryAccess());
            h.assertTrue(!restored.reserveMusselDrop() && !restored.reserveVolcanoDrop() && restored.found()==0,"Reload reset quota or credited drops");
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Walnut pickup"));
            for(int i=0;i<player.getInventory().getContainerSize();i++) player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var entity=new ItemEntity(level,pos.getX(),pos.getY(),pos.getZ(),new ItemStack(ModItems.GOLDEN_WALNUT.get(),2));
            level.addFreshEntity(entity);entity.setPickUpDelay(20);
            com.stardew.craft.mining.GoldenWalnutPickup.onPickup(new com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,entity));
            h.assertTrue(walnuts.balance()==0 && entity.isAlive(),"Pickup delay was bypassed");
            entity.setNoPickUpDelay();
            var event=new com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,entity);
            event.setCanPickup(com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState.FALSE);
            com.stardew.craft.mining.GoldenWalnutPickup.onPickup(event);
            h.assertTrue(walnuts.balance()==0,"Prior pickup denial was bypassed");
            var allowed=new com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,entity);
            com.stardew.craft.mining.GoldenWalnutPickup.onPickup(allowed);
            com.stardew.craft.mining.GoldenWalnutPickup.onPickup(new com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(player,entity));
            h.assertTrue(walnuts.balance()==2 && walnuts.found()==2 && !entity.isAlive()
                    && !player.getInventory().contains(new ItemStack(ModItems.GOLDEN_WALNUT.get())),
                    "Currency pickup must bypass full inventory and credit once");
            restored=com.stardew.craft.mining.GoldenWalnutData.load(walnuts.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(restored.balance()==2 && restored.found()==2 && !restored.reserveMusselDrop() && !restored.reserveVolcanoDrop(),"Shared reward state did not persist");
        } finally {
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(previous);
            storage.set("stardewcraft_golden_walnuts",previousWalnuts);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void clayAndOmniGeodeSourceBranches(GameTestHelper h) {
        var plain = new MineStoneMining.Context(818, 1, 0, 0, 0, false, false, false, false, false, false, 0, 0, false);
        var base = MineStoneMining.roll(plain, new FixedRandom(0,0));
        h.assertTrue(base.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(base.drops()).is(ModItems.CLAY.get())
                && com.stardew.craft.port.PortJava.getFirst(base.drops()).getCount()==1 && base.miningExperience()==6,"Base clay reward changed");
        var boosted = new MineStoneMining.Context(818, 150, 10, 10, 0.1, true, true, true, true, true, true, 99, 2, true);
        var edge = MineStoneMining.roll(boosted, new FixedRandom(0.1,0.1));
        var hit = MineStoneMining.roll(boosted, new FixedRandom(0.099999,0.099999));
        var maximum = MineStoneMining.roll(boosted, new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound-1; }
            @Override public double nextDouble() { return 0; }
        });
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(edge.drops()).getCount()==3 && com.stardew.craft.port.PortJava.getFirst(hit.drops()).getCount()==5
                && com.stardew.craft.port.PortJava.getFirst(maximum.drops()).getCount()==6 && maximum.miningExperience()==6,
                "Clay1..2 plus addedOres and strict luck/mining100 bonuses changed");
        var omni = new MineStoneMining.Context(819, 150, 10, 10, 0.1, true, true, true, true, true, true, 99, 99, true);
        var guaranteed = MineStoneMining.roll(omni, new FixedRandom());
        h.assertTrue(guaranteed.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(guaranteed.drops()).is(ModItems.OMNI_GEODE.get())
                && com.stardew.craft.port.PortJava.getFirst(guaranteed.drops()).getCount()==1 && guaranteed.miningExperience()==64,
                "Omni node must give exactly one749 and64XP, without ordinary geode multiplier");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void cinderAndVolcanicStoneRewards(GameTestHelper h) {
        for(int source:new int[]{843,844}) {
            var c=new MineStoneMining.Context(source,10,10,10,0.1,true,true,true,true,true,true,99,2,true);
            var edge=MineStoneMining.roll(c,new FixedRandom(0.1,0.05));
            var hits=MineStoneMining.roll(c,new FixedRandom(0.09999,0.04999));
            var max=MineStoneMining.roll(c,new LegacyRandomSource(0) {
                @Override public int nextInt(int bound) { return bound-1; }
                @Override public double nextDouble() { return 0; }
            });
            h.assertTrue(edge.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(edge.drops()).is(ModItems.CINDER_SHARD.get())
                    && com.stardew.craft.port.PortJava.getFirst(edge.drops()).getCount()==3 && com.stardew.craft.port.PortJava.getFirst(hits.drops()).getCount()==5
                    && com.stardew.craft.port.PortJava.getFirst(max.drops()).getCount()==6 && max.miningExperience()==12,
                    "Cinder count1..2/addedOres/luck100/mining200 or XP12 changed for"+source);
            var state=MineStoneMining.stateForSource(Integer.toString(source),12).orElseThrow();
            h.assertTrue(state.getBlock().defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH)==12,"Cinder HP must12");
        }
        for(int volcanicSource:new int[]{845,846,847}) {
        var volcanic=new MineStoneMining.Context(volcanicSource,10,10,10,0.1,true,true,true,true,true,true,99,2,true);
        var coal=MineStoneMining.roll(volcanic,new FixedRandom(0.09999,0.09999,0.07999));
        var dry=MineStoneMining.roll(volcanic,new FixedRandom(0.1,0.1,0.08));
        h.assertTrue(coal.drops().size()==2 && com.stardew.craft.port.PortJava.getFirst(coal.drops()).is(ModItems.STONE.get())
                && com.stardew.craft.port.PortJava.getFirst(coal.drops()).getCount()==5 && com.stardew.craft.port.PortJava.getLast(coal.drops()).is(ModItems.COAL.get())
                && com.stardew.craft.port.PortJava.getLast(coal.drops()).getCount()==3 && coal.miningExperience()==4
                && dry.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(dry.drops()).getCount()==3 && dry.miningExperience()==3,
                "Volcanic ordinary stone is source rich-stone branch, not MineShaft generic ore branch");
        }
        int[] cinderTicks={144,66,40,27,24};int[] cinderEnergy={24,12,8,6,6};
        int[] stoneTicks={72,33,20,18,16};int[] stoneEnergy={12,6,4,4,4};
        for(int tier=0;tier<5;tier++) h.assertTrue(MineStoneMining.breakTicks(12,tier+1,tier,false)==cinderTicks[tier]
                && MineStoneMining.energyCost(12,tier+1,0)==cinderEnergy[tier]
                && MineStoneMining.breakTicks(6,tier+1,tier,false)==stoneTicks[tier]
                && MineStoneMining.energyCost(6,tier+1,0)==stoneEnergy[tier],"12HP/6HP source swings mismatch");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void volcanoBatAndLocationBoundaries(GameTestHelper h) {
        for(String source:java.util.List.of("845","846","847")) h.assertTrue(
                com.stardew.craft.mining.IslandStoneRewards.rollVolcanoBat(source,new FixedRandom(0.004999))
                && !com.stardew.craft.mining.IslandStoneRewards.rollVolcanoBat(source,new FixedRandom(0.005)),
                "Source bat0.5% boundary changed");
        for(String source:java.util.List.of("843","844","819","32")) h.assertTrue(
                !com.stardew.craft.mining.IslandStoneRewards.rollVolcanoBat(source,new FixedRandom()),
                "Non-volcanic-basic appearance consumed bat RNG");
        var id=new net.minecraft.resources.ResourceLocation("stardewcraft","custom_volcano");
        var tag=new net.minecraft.resources.ResourceLocation("stardewcraft","volcano_dungeon");
        var location=new com.stardew.craft.api.v1.world.StardewLocation(id,h.getLevel().dimension().location(),
                BlockPos.ZERO,new BlockPos(10,10,10),"",java.util.List.of(),1,false,null,
                net.minecraft.network.chat.Component.empty(),net.minecraft.network.chat.Component.empty(),null,
                java.util.Set.of(tag),java.util.Map.of());
        h.assertTrue(com.stardew.craft.mining.IslandStoneRewards.isVolcano(location)
                && com.stardew.craft.mining.IslandStoneRewards.isIsland(location),
                "Declared volcano tag must retain IslandLocation inheritance");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void alternateCopperAndDangerStoneHealth(GameTestHelper h) {
        var c=new MineStoneMining.Context(849,1,10,10,0.1,true,true,true,true,true,true,99,2,true);
        var edge=MineStoneMining.roll(c,new FixedRandom(0.1,0.1));
        var hit=MineStoneMining.roll(c,new FixedRandom(0.09999,0.09999));
        var max=MineStoneMining.roll(c,new LegacyRandomSource(0) {
            @Override public int nextInt(int bound) { return bound-1; }
            @Override public double nextDouble() { return 0; }
        });
        h.assertTrue(edge.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(edge.drops()).is(ModItems.COPPER_ORE.get())
                && com.stardew.craft.port.PortJava.getFirst(edge.drops()).getCount()==3 && com.stardew.craft.port.PortJava.getFirst(hit.drops()).getCount()==5
                && com.stardew.craft.port.PortJava.getFirst(max.drops()).getCount()==7 && max.miningExperience()==5,
                "849 copper1..3/addedOres/skills or XP5 differs from original branch");
        var dangerous=MineStoneMining.stateForSource("846",5).orElseThrow();
        var volcano=MineStoneMining.stateForSource("846",6).orElseThrow();
        var weakCopper=MineStoneMining.stateForSource("849",1).orElseThrow();
        h.assertTrue(dangerous.getBlock()==volcano.getBlock() && dangerous.getValue(MineStoneBlock.STONE_HEALTH)==5
                && volcano.getValue(MineStoneBlock.STONE_HEALTH)==6 && weakCopper.getValue(MineStoneBlock.STONE_HEALTH)==1,
                "Source-context health was replaced by appearance default");
        int[] ticks5={60,33,20,18,8};int[] energy5={10,6,4,4,2};int[] ticks1={12,11,10,9,8};
        for(int tier=0;tier<5;tier++) h.assertTrue(MineStoneMining.breakTicks(5,tier+1,tier,false)==ticks5[tier]
                && MineStoneMining.energyCost(5,tier+1,0)==energy5[tier]
                && MineStoneMining.breakTicks(1,tier+1,tier,false)==ticks1[tier]
                && MineStoneMining.energyCost(1,tier+1,0)==2,"5HP/1HP source swing timing mismatch");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void volcanoMetalAndCoalSources(GameTestHelper h) {
        String[] sources={"850","VolcanoGoldNode","VolcanoCoalNode0"};
        net.minecraft.world.item.Item[] items={ModItems.IRON_ORE.get(),ModItems.GOLD_ORE.get(),ModItems.COAL.get()};
        int[] health={1,8,10},xp={12,18,10};
        int[][] ticks={{12,11,10,9,8},{96,44,30,18,16},{120,55,40,27,16}};
        int[][] energy={{2,2,2,2,2},{16,8,6,4,4},{20,10,8,6,4}};
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));
        var placer=FakePlayerFactory.getMinecraft(level);
        level.setBlock(pos.below(),ModBlocks.MINE_LAVA_SOIL.get().defaultBlockState(),3);
        for(int i=0;i<sources.length;i++) {
            var context=new MineStoneMining.Context(sources[i],5,10,10,0.1,true,true,true,true,true,true,99,2,true);
            var boundary=MineStoneMining.roll(context,new FixedRandom(0.1,0.1));
            var bonuses=MineStoneMining.roll(context,new FixedRandom(0.09999,0.09999));
            var maximum=MineStoneMining.roll(context,new LegacyRandomSource(0) {
                @Override public int nextInt(int bound) { return bound-1; }
                @Override public double nextDouble() { return 0; }
            });
            h.assertTrue(boundary.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(boundary.drops()).is(items[i])
                    && com.stardew.craft.port.PortJava.getFirst(boundary.drops()).getCount()==3 && boundary.miningExperience()==xp[i]
                    && com.stardew.craft.port.PortJava.getFirst(bonuses.drops()).getCount()==5 && com.stardew.craft.port.PortJava.getFirst(maximum.drops()).getCount()==7,
                    "Volcano ore1..3, skill thresholds, profession isolation or experience mismatch: "+sources[i]);
            var block=(MineStoneBlock)MineStoneMining.stateForSource(sources[i],health[i]).orElseThrow().getBlock();
            var placeContext=new net.minecraft.world.item.context.BlockPlaceContext(level,placer,InteractionHand.MAIN_HAND,
                    new ItemStack(block.asItem()),new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false));
            var placed=block.getStateForPlacement(placeContext);
            h.assertTrue(block.defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH)==health[i]
                    && placed!=null && placed.getValue(MineStoneBlock.STONE_HEALTH)==health[i],
                    "Volcano source health was overwritten by lava soil: "+sources[i]);
            for(int tier=0;tier<5;tier++) h.assertTrue(MineStoneMining.breakTicks(health[i],tier+1,tier,false)==ticks[i][tier]
                    && MineStoneMining.energyCost(health[i],tier+1,0)==energy[i][tier],
                    "Volcano health/tier timing or energy mismatch: "+sources[i]);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void coalVariantsAndQuarryHealth(GameTestHelper h) {
        String[] sources={"VolcanoCoalNode1","BasicCoalNode0","BasicCoalNode1"};
        int[] health={10,5,5};
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));
        var placer=FakePlayerFactory.getMinecraft(level);
        for(int i=0;i<sources.length;i++) {
            var context=new MineStoneMining.Context(sources[i],77377,10,10,0.1,true,true,true,true,true,true,99,2,true);
            var boundary=MineStoneMining.roll(context,new FixedRandom(0.1,0.1));
            var bonuses=MineStoneMining.roll(context,new FixedRandom(0.09999,0.09999));
            var maximum=MineStoneMining.roll(context,new LegacyRandomSource(0) {
                @Override public int nextInt(int bound) { return bound-1; }
                @Override public double nextDouble() { return 0; }
            });
            h.assertTrue(boundary.drops().size()==1 && com.stardew.craft.port.PortJava.getFirst(boundary.drops()).is(ModItems.COAL.get())
                    && com.stardew.craft.port.PortJava.getFirst(boundary.drops()).getCount()==3 && boundary.miningExperience()==10
                    && com.stardew.craft.port.PortJava.getFirst(bonuses.drops()).getCount()==5 && com.stardew.craft.port.PortJava.getFirst(maximum.drops()).getCount()==7,
                    "Coal source quantity/skill threshold/XP10 or Prospector isolation mismatch: "+sources[i]);
            var block=(MineStoneBlock)MineStoneMining.stateForSource(sources[i],health[i]).orElseThrow().getBlock();
            for(var soil:java.util.List.of(ModBlocks.MINE_EARTH_SOIL.get(),ModBlocks.MINE_LAVA_SOIL.get(),ModBlocks.MINE_DESERT_SOIL.get())) {
                level.setBlock(pos.below(),soil.defaultBlockState(),3);
                var placement=new net.minecraft.world.item.context.BlockPlaceContext(level,placer,InteractionHand.MAIN_HAND,
                        new ItemStack(block.asItem()),new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false));
                var placed=block.getStateForPlacement(placement);
                h.assertTrue(placed!=null && placed.getValue(MineStoneBlock.STONE_HEALTH)==health[i]
                        && block.defaultBlockState().getValue(MineStoneBlock.STONE_HEALTH)==health[i],
                        "Coal source HP changed with decorative soil: "+sources[i]);
            }
            int[] ticks=health[i]==10?new int[]{120,55,40,27,16}:new int[]{60,33,20,18,8};
            int[] energy=health[i]==10?new int[]{20,10,8,6,4}:new int[]{10,6,4,4,2};
            for(int tier=0;tier<5;tier++) h.assertTrue(MineStoneMining.breakTicks(health[i],tier+1,tier,false)==ticks[tier]
                    && MineStoneMining.energyCost(health[i],tier+1,0)==energy[tier]
                    && MineStoneMining.energyCost(health[i],tier+1,10)==energy[tier]/2f,
                    "Coal source tier timing or mining-level energy mismatch");
        }
        h.succeed();
    }

    private static final class FixedRandom extends LegacyRandomSource {
        private final double[] values;
        private int index;
        private final boolean booleanValue;
        FixedRandom(double... values) { this(true, values); }
        FixedRandom(boolean booleanValue, double... values) { super(0); this.values = values; this.booleanValue = booleanValue; }
        @Override public double nextDouble() { return values[index++]; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return booleanValue; }
    }
}
