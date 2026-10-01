package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mine.MineRockClumpBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.MineRockClumpMining;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("stardewcraft_ground_stone")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class MineRockClumpGameTests {
    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void clumpSourceTimingAndEnchantments(GameTestHelper h) throws Exception {
        Item[] picks = {ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(), ModItems.STEEL_PICKAXE.get(),
                ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get()};
        int[] swings = {8, 6, 4, 3, 3}, ticks = {96, 66, 40, 27, 24};
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < picks.length; i++) {
            var tool = new ItemStack(picks[i]);
            h.assertTrue(MineRockClumpMining.requiredSwings(752, tool) == swings[i]
                    && MineRockClumpMining.breakTicks(752, tool) == ticks[i], "Wrong 8 HP fractional clump damage");
            h.assertTrue(MineRockClumpMining.energyCost(752, tool, 0) == swings[i] * 2
                    && MineRockClumpMining.energyCost(752, tool, 10) == swings[i], "Clump energy used fractional swings");
            tool.enchant(registry.getHolderOrThrow(StardewEnchantments.POWERFUL), 1);
            h.assertTrue(MineRockClumpMining.requiredSwings(752, tool) == swings[i], "Powerful incorrectly affects ResourceClump");
        }
        var foreign = new ItemStack(Items.NETHERITE_PICKAXE);
        foreign.enchant(registry.getHolderOrThrow(Enchantments.EFFICIENCY), 5);
        foreign.enchant(registry.getHolderOrThrow(StardewEnchantments.SWIFT), 1);
        foreign.enchant(registry.getHolderOrThrow(StardewEnchantments.EFFICIENT), 1);
        h.assertTrue(MineRockClumpMining.breakTicks(752, foreign) == 96
                && MineRockClumpMining.energyCost(752, foreign, 0) == 16, "Foreign pickaxe was not normalized");
        var swift = new ItemStack(ModItems.IRIDIUM_PICKAXE.get());
        swift.enchant(registry.getHolderOrThrow(StardewEnchantments.SWIFT), 1);
        h.assertTrue(MineRockClumpMining.breakTicks(752, swift) == 16, "Iridium Swift clump must take 16 ticks");
        h.assertTrue(MineRockClumpMining.stateForSource("C999").isEmpty(), "Missing clump silently substituted");
        try (var stream = MineRockClumpGameTests.class.getClassLoader().getResourceAsStream(
                "data/stardewcraft/mine_node_sources/rock_clumps.json")) {
            var entries = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries");
            h.assertTrue(entries.size() == 7, "Missing clump mapping");
            for (var raw : entries) {
                var entry = raw.getAsJsonObject();
                var state = MineRockClumpMining.stateForSource(entry.get("source_id").getAsString()).orElseThrow();
                h.assertTrue(((MineRockClumpBlock) state.getBlock()).sourceId() == entry.get("source_index").getAsInt()
                        && entry.get("source_health").getAsInt() == MineRockClumpMining.health(entry.get("source_index").getAsInt())
                        && entry.get("minimum_pickaxe_tier").getAsInt() == MineRockClumpMining.minimumTier(entry.get("source_index").getAsInt()), "Clump source mapping drift");
                String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                try (var itemStream = MineRockClumpGameTests.class.getClassLoader().getResourceAsStream(
                        "assets/stardewcraft/models/item/" + key + ".json")) {
                    var item = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(itemStream,
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    h.assertTrue(item.get("parent").getAsString().equals(entry.get("model").getAsString())
                            && !item.has("display"), "Clump item does not inherit block display");
                }
                h.assertTrue(com.stardew.craft.item.catalog.StardewItemCatalog.tabForItem(state.getBlock().asItem())
                        == com.stardew.craft.item.catalog.StardewCatalogTab.NATURE, "Clump missing from Nature");
                h.assertTrue(state.getBlock().getExplosionResistance() >= 3600000,
                        "ResourceClump is destructible by the mod bomb path");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void hardBouldersTierHealthTiming(GameTestHelper h) {
        Item[] picks = {ModItems.PICKAXE.get(), ModItems.COPPER_PICKAXE.get(), ModItems.STEEL_PICKAXE.get(),
                ModItems.GOLD_PICKAXE.get(), ModItems.IRIDIUM_PICKAXE.get()};
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int source : new int[]{672, 622, 148}) {
            for (int tier = 0; tier < picks.length; tier++) {
                var tool = new ItemStack(picks[tier]);
                h.assertTrue(MineRockClumpMining.canMine(source, tool) == (tier >= (source == 672 ? 2 : 3)),
                        "Wrong minimum upgrade tier");
                if (tier < (source == 672 ? 2 : 3)) continue;
                int expectedHits = source == 672 ? new int[]{0, 0, 5, 4, 3}[tier] : tier == 3 ? 7 : 6;
                int expectedTicks = source == 672 ? new int[]{0, 0, 50, 36, 24}[tier] : tier == 3 ? 63 : 48;
                h.assertTrue(MineRockClumpMining.requiredSwings(source, tool) == expectedHits
                        && MineRockClumpMining.breakTicks(source, tool) == expectedTicks
                        && MineRockClumpMining.energyCost(source, tool, 0) == expectedHits * 2
                        && MineRockClumpMining.energyCost(source, tool, 10) == expectedHits,
                        "Hard boulder source damage/time/energy mismatch");
                tool.enchant(registry.getHolderOrThrow(StardewEnchantments.POWERFUL), 1);
                h.assertTrue(MineRockClumpMining.requiredSwings(source, tool) == expectedHits, "Powerful changed clump damage");
                tool.enchant(registry.getHolderOrThrow(StardewEnchantments.SWIFT), 1);
                h.assertTrue(MineRockClumpMining.breakTicks(source, tool) == (int) Math.ceil(expectedTicks * 0.66),
                        "Swift clump timing mismatch");
                tool.enchant(registry.getHolderOrThrow(StardewEnchantments.EFFICIENT), 1);
                h.assertTrue(MineRockClumpMining.energyCost(source, tool, 0) == 0, "Efficient clump consumed energy");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities", timeoutTicks = 200)
    public static void clumpPlacementWholeAabbAndOneSettlement(GameTestHelper h) {
        var level = h.getLevel();
        // Exercise a positive shard result: a missing pre-cleanup anchor must fail, not silently pass a no-drop case.
        var main = java.util.stream.IntStream.range(0, 64)
                .mapToObj(i -> h.absolutePos(new BlockPos(4 + i % 8, 2, 4 + i / 8)))
                .filter(p -> MineRockClumpMining.meteoriteHasShard(level.getSeed(), p)).findFirst().orElseThrow();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Clump break"));
        var data = PlayerDataManager.getPlayerData(player);
        var area = new AABB(main).inflate(4);
        for (var clump : new MineRockClumpBlock[]{ModBlocks.MINE_ROCK_CLUMP_752.get(), ModBlocks.MINE_ROCK_CLUMP_754.get(), ModBlocks.MINE_ROCK_CLUMP_756.get(), ModBlocks.MINE_ROCK_CLUMP_758.get(), ModBlocks.MINE_ROCK_CLUMP_672.get(), ModBlocks.MINE_ROCK_CLUMP_622.get(), ModBlocks.MINE_ROCK_CLUMP_148.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean hitExtension : new boolean[]{false, true}) {
                    player.setGameMode(GameType.SURVIVAL);
                    for (BlockPos pos : BlockPos.betweenClosed(main.offset(-2, 0, -2), main.offset(2, 2, 2)))
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    for (BlockPos pos : BlockPos.betweenClosed(main.offset(-2, -1, -2), main.offset(2, -1, 2)))
                        level.setBlock(pos, ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
                    var state = clump.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                    level.setBlock(main, state, 3);
                    clump.setPlacedBy(level, main, state, player, new ItemStack(clump));
                    var cells = new ArrayList<BlockPos>();
                    var whole = state.getShape(level, main);
                    h.assertTrue(whole.toAabbs().size() == 1 && whole.bounds().minY == 0, "Clump is not an above-soil AABB");
                    for (BlockPos pos : BlockPos.betweenClosed(main.offset(-2, 0, -2), main.offset(2, 2, 2))) {
                        var part = level.getBlockState(pos);
                        if (!part.is(clump)) continue;
                        cells.add(pos.immutable());
                        var shape = part.getShape(level, pos).move(pos.getX() - main.getX(), pos.getY() - main.getY(), pos.getZ() - main.getZ());
                        h.assertTrue(!Shapes.joinIsNotEmpty(whole, shape, BooleanOp.NOT_SAME), "Extension outline differs in world space");
                        h.assertTrue(!Shapes.joinIsNotEmpty(part.getShape(level, pos), part.getCollisionShape(level, pos), BooleanOp.NOT_SAME),
                                "Collision and selection differ");
                    }
                    h.assertTrue(cells.size() == (clump.sourceId() == 752 || clump.sourceId() == 756 ? 4 : 8), "Missing or excessive extension footprint");
                    BlockPos hit = hitExtension ? cells.stream().filter(p -> !p.equals(main)).findFirst().orElseThrow() : main;
                    player.setPos(hit.getX() + 0.5, hit.getY(), hit.getZ() + 1.5);
                    data.setEnergy(100);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                    h.assertTrue(!player.gameMode.destroyBlock(hit) && level.getBlockState(main).is(clump), "Wrong tool removed clump");
                    if (MineRockClumpMining.minimumTier(clump.sourceId()) > 0) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
                        h.assertTrue(level.getBlockState(hit).getDestroyProgress(player, level, hit) == 0
                                && !player.gameMode.destroyBlock(hit) && level.getBlockState(main).is(clump)
                                && data.getEnergy() == 100, "Foreign pickaxe bypassed clump gate");
                    }
                    var tool = new ItemStack(MineRockClumpMining.minimumTier(clump.sourceId()) > 0
                            ? ModItems.IRIDIUM_PICKAXE.get() : Items.WOODEN_PICKAXE);
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                    for (int i = 0; i < 5; i++) level.getBlockState(hit).getDestroyProgress(player, level, hit);
                    h.assertTrue(data.getEnergy() == 100, "Partial mining charged energy");
                    data.setEnergy(0);
                    h.assertTrue(!player.gameMode.destroyBlock(hit) && level.getBlockState(main).is(clump), "Exhausted player removed clump");
                    data.setEnergy(100);
                    float cost = MineRockClumpMining.energyCost(clump.sourceId(), tool, data.getSkillLevel(com.stardew.craft.player.SkillType.MINING));
                    for (var entity : level.getEntitiesOfClass(ItemEntity.class, area)) entity.discard();
                    int broken = data.getMineStonesBroken();
                    h.assertTrue(player.gameMode.destroyBlock(hit), "Clump survival break failed");
                    h.assertTrue(cells.stream().allMatch(level::isEmptyBlock), "Clump left orphan parts");
                    h.assertTrue(Math.abs(data.getEnergy() - (100 - cost)) < 0.0001, "Clump paid energy more than once");
                    h.assertTrue(data.getMineStonesBroken() == broken, "Clump entered ordinary stone/ladder bookkeeping");
                    int stones = 0, iridium = 0, geodes = 0, shards = 0;
                    for (var entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                        var stack = entity.getItem();
                        if (stack.is(ModItems.STONE.get())) stones += stack.getCount();
                        if (stack.is(ModItems.IRIDIUM_ORE.get())) iridium += stack.getCount();
                        if (stack.is(ModItems.OMNI_GEODE.get())) geodes += stack.getCount();
                        if (stack.is(ModItems.PRISMATIC_SHARD.get())) shards += stack.getCount();
                        h.assertTrue(!(stack.getItem() instanceof net.minecraft.world.item.BlockItem), "Clump block item leaked into drops");
                        entity.discard();
                    }
                    h.assertTrue(stones == (clump.sourceId() == 672 ? 15 : clump.sourceId() == 622 ? 8 : 10),
                            "Clump stone count drift");
                    boolean meteorite = clump.sourceId() == 622;
                    h.assertTrue(iridium == (meteorite ? 10 : 0) && geodes == (meteorite ? 2 : 0)
                            && shards == (meteorite && MineRockClumpMining.meteoriteHasShard(level.getSeed(), main) ? 1 : 0),
                            "Meteorite drops or main-anchor shard roll changed by facing/extension");
                    h.assertTrue(level.getBlockState(main.below()).is(ModBlocks.MINE_EARTH_SOIL.get()), "Clump destroyed soil");
                    player.gameMode.destroyBlock(hit);
                    h.assertTrue(Math.abs(data.getEnergy() - (100 - cost)) < 0.0001, "Repeated break charged again");
                }
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_ground_stone", template = "ring_utilities")
    public static void clumpCanceledBreakAndToolLifecycle(GameTestHelper h) {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Clump lifecycle"));
        var data = PlayerDataManager.getPlayerData(player);
        var clump = ModBlocks.MINE_ROCK_CLUMP_752.get();
        var state = clump.defaultBlockState();
        var area = new AABB(main).inflate(3);
        for (BlockPos pos : BlockPos.betweenClosed(main.offset(-1, -1, -1), main.offset(2, -1, 2)))
            level.setBlock(pos, ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
        for (int mode = 0; mode < 4; mode++) {
            player.setGameMode(mode == 3 ? GameType.CREATIVE : GameType.SURVIVAL);
            for (var entity : level.getEntitiesOfClass(ItemEntity.class, area)) entity.discard();
            level.setBlock(main, state, 3);
            clump.setPlacedBy(level, main, state, player, new ItemStack(clump));
            var hit = main.east();
            ItemStack tool = new ItemStack(mode == 2 ? ModItems.PICKAXE.get() : Items.WOODEN_PICKAXE);
            if (mode == 1) tool.setDamageValue(tool.getMaxDamage() - 1);
            if (mode == 2) tool.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(StardewEnchantments.EFFICIENT), 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            data.setEnergy(mode == 2 ? 0 : 100);
            if (mode == 0) {
                java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> cancel = event -> {
                    if (event.getPlayer() == player && event.getPos().equals(hit)) event.setCanceled(true);
                };
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(cancel);
                try {
                    h.assertTrue(!player.gameMode.destroyBlock(hit), "Canceled clump break succeeded");
                } finally {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);
                }
                h.assertTrue(level.getBlockState(main).is(clump) && level.getBlockState(hit).is(clump)
                        && data.getEnergy() == 100 && level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Canceled clump break changed state, energy or drops");
                level.setBlock(main, Blocks.AIR.defaultBlockState(), 3);
                continue;
            }
            float cost = mode == 3 ? 0 : MineRockClumpMining.energyCost(752, tool,
                    data.getSkillLevel(com.stardew.craft.player.SkillType.MINING));
            h.assertTrue(player.gameMode.destroyBlock(hit) && level.isEmptyBlock(main) && level.isEmptyBlock(hit),
                    "Lifecycle break left clump parts");
            h.assertTrue(Math.abs(data.getEnergy() - ((mode == 2 ? 0 : 100) - cost)) < 0.0001,
                    "Lifecycle break lost its energy settlement");
            if (mode == 1) h.assertTrue(player.getMainHandItem().isEmpty(), "Last durability did not break tool");
            int stones = 0;
            for (var entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (entity.getItem().is(ModItems.STONE.get())) stones += entity.getItem().getCount();
                h.assertTrue(!(entity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem), "Lifecycle leaked clump item");
                entity.discard();
            }
            h.assertTrue(stones == (mode == 3 ? 0 : 10), "Wrong creative/efficient/last-durability drops");
        }
        h.succeed();
    }

}
