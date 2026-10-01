package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainVariantWeights;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.block.terrain.TownPavingConnections;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_paving")
@PrefixGameTestTemplate(false)
public final class RedPavingGameTests {
    private RedPavingGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_paving", template = "ring_utilities")
    public static void pavingConnectsAcrossVariantsAndKeepsConcaveCorners(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var paving = ModBlocks.PLAZA_RED_BRICKS.get().defaultBlockState();
        level.setBlock(pos, paving, 3);
        int[][] offsets = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        var rows = new HashSet<Integer>();
        for (int mask = 0; mask < 256; mask++) {
            int expected = mask;
            for (int i = 0; i < 8; i++) level.setBlock(pos.offset(offsets[i][0], 0, offsets[i][1]),
                    (mask & 1 << i) == 0 ? Blocks.AIR.defaultBlockState() : paving.setValue(TerrainVariants.PAVING, i % 6), 3);
            if ((mask & 3) != 3) expected &= ~16;
            if ((mask & 6) != 6) expected &= ~32;
            if ((mask & 12) != 12) expected &= ~64;
            if ((mask & 9) != 9) expected &= ~128;
            helper.assertTrue(TownPavingConnections.mask(level, pos) == expected, "Wrong corner/rail selection for neighbors " + mask);
            rows.add(TownPavingConnections.row(expected));
        }
        helper.assertTrue(rows.size() == 47 && rows.contains(0) && rows.contains(46), "Missing connection atlas cells");
        level.setBlock(pos.east(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(pos.east().above(), paving, 3);
        helper.assertTrue((TownPavingConnections.mask(level, pos) & 2) == 0, "Connected to another material or elevation");
        helper.assertTrue(TownPavingConnections.phase(new BlockPos(-1, 0, -1)) == 3
                && TownPavingConnections.phase(new BlockPos(-16, 0, 16)) == 0, "Layout discontinuity at negative coordinates/chunk boundary");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_paving", template = "ring_utilities")
    public static void pavingFixedCopiesPlaceAndSaveEveryStructure(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var block = ModBlocks.PLAZA_RED_BRICKS.get();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Paving test"));
        player.getAbilities().instabuild = true;
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        for (int variant = 0; variant < 6; variant++) {
            var source = block.defaultBlockState().setValue(TerrainVariants.PAVING, variant);
            var ordinary = new ItemStack(block);
            var fixed = TerrainVariants.fixedCopy(ordinary, source);
            helper.assertTrue(!PortItemData.has(ordinary, DataComponents.BLOCK_STATE), "Ctrl-copy fixed the original stack");
            player.setItemInHand(InteractionHand.MAIN_HAND, fixed);
            for (int repeat = 0; repeat < 5; repeat++) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
                helper.assertTrue(((BlockItem) fixed.getItem()).place(context).consumesAction(), "Paving placement failed");
                var placed = level.getBlockState(pos);
                helper.assertTrue(placed.equals(source), "Copied structure rerolled on placement");
                var nbt = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, placed).getOrThrow();
                helper.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(source), "Lost structure in save");
                helper.assertTrue(TerrainWorldUpgrade.varied(source, 123, pos).equals(source), "Terrain migration changed paving");
            }
        }
        var state = block.defaultBlockState();
        helper.assertTrue(state.isCollisionShapeFullBlock(level, pos) && state.is(BlockTags.MINEABLE_WITH_PICKAXE), "Wrong collision/mining family");
        helper.assertTrue(state.getDestroySpeed(level, pos) == Blocks.BRICKS.defaultBlockState().getDestroySpeed(level, pos), "Wrong stone hardness");
        var drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && com.stardew.craft.port.PortJava.getFirst(drops).is(block.asItem()), "Paving did not drop itself");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.BUILDING, "Missing building catalog entry");
        int[] counts = new int[6];
        for (int roll = 0; roll < 100; roll++) counts[TerrainVariantWeights.redPaving(roll)]++;
        helper.assertTrue(java.util.Arrays.equals(counts, new int[]{50,14,12,10,9,5}), "Placement weights differ from documented authored proportions");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_paving", template = "ring_utilities")
    public static void mixedPavingSharesPerimeterAndKeepsRedDiagonal(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var stone = ModBlocks.TOWN_PAVING.get().defaultBlockState();
        var red = ModBlocks.PLAZA_RED_BRICKS.get().defaultBlockState();
        level.setBlock(pos, stone, 3);
        int[][] offsets = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for (int redMask = 0; redMask < 256; redMask++) for (boolean filled : new boolean[]{false, true}) {
            for (int i = 0; i < 8; i++) {
                var neighbor = (redMask & (1 << i)) != 0 ? red.setValue(TerrainVariants.PAVING, i % 6)
                        : filled ? stone.setValue(TerrainVariants.PAVING, (i + 1) % 6) : Blocks.AIR.defaultBlockState();
                level.setBlock(pos.offset(offsets[i][0], 0, offsets[i][1]), neighbor, 3);
            }
            helper.assertTrue(TownPavingConnections.mask(level, pos) == TownPavingConnections.canonical(filled ? 255 : redMask),
                    "Mixed paving retained an internal outer border");
            int expected = 255 ^ redMask;
            if ((expected & 3) != 3) expected &= ~16;
            if ((expected & 6) != 6) expected &= ~32;
            if ((expected & 12) != 12) expected &= ~64;
            if ((expected & 9) != 9) expected &= ~128;
            helper.assertTrue(TownPavingConnections.redTransitionRow(level, pos) == TownPavingConnections.row(expected),
                    "Red receiver corner incorrect for " + redMask);
        }
        // Existing town stone placement, copy, persistence, and distribution remain unchanged.
        TownPavingGameTests.pavingFixedCopiesPlaceAndSaveEveryStructure(helper);
    }
}
