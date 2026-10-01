package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineChestBlock;
import com.stardew.craft.block.mine.MineDesertWallReliefBlock;
import com.stardew.craft.blockentity.MineChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_skull_finish")
@PrefixGameTestTemplate(false)
public final class SkullMineFinishingGameTests {
    @GameTest(templateNamespace = "stardewcraft_skull_finish", template = "ring_utilities", timeoutTicks = 40)
    public static void wholeWallOrnamentSurvivesImportAndRequiresItsWall(GameTestHelper h) {
        var level = h.getLevel();
        var block = ModBlocks.MINE_DESERT_WALL_RELIEF.get();
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = h.absolutePos(new BlockPos(4 + index % 2 * 8, 3, 4 + index / 2 * 8));
            index++;
            var base = block.defaultBlockState().setValue(MineDesertWallReliefBlock.FACING, facing);
            for (int tier = 0; tier < 3; tier++) level.setBlock(root.above(tier).relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(root, base, 3);
            block.setPlacedBy(level, root, base, null, new ItemStack(block));
            var whole = base.getShape(level, root).bounds();
            for (int tier = 0; tier < 3; tier++) {
                var pos = root.above(tier);
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(block) && state.getValue(MineDesertWallReliefBlock.TIER) == tier, "Missing automatic wall ornament part");
                h.assertTrue(state.getShape(level, pos).bounds().move(0, tier, 0).equals(whole), "Outline differs between parts");
                h.assertTrue(state.getLightEmission() == 0 && state.getLightBlock(level, pos) == 0, "Wall ornament blocks light");
            }
            level.removeBlock(root.above(), false);
            for (int tier = 0; tier < 3; tier++) h.assertTrue(level.getBlockState(root.above(tier)).isAir(), "Orphaned wall ornament");
            for (int tier = 2; tier >= 0; tier--) level.setBlock(root.above(tier), base.setValue(MineDesertWallReliefBlock.TIER, tier), 3);
            h.runAtTickTime(3, () -> {
                for (int tier = 0; tier < 3; tier++) h.assertTrue(level.getBlockState(root.above(tier)).is(block), "Import removed ornament too early");
                level.removeBlock(root.above().relative(facing.getOpposite()), false);
            });
            h.runAtTickTime(6, () -> {
                for (int tier = 0; tier < 3; tier++) h.assertTrue(level.getBlockState(root.above(tier)).isAir(), "Unsupported ornament survived");
            });
        }
        h.runAtTickTime(7, h::succeed);
    }

    @GameTest(templateNamespace = "stardewcraft_skull_finish", template = "ring_utilities", timeoutTicks = 40)
    public static void specialChestKeepsAppearanceWhileOpeningAndSaving(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.getMinecraft(level);
        for (boolean special : new boolean[]{false, true}) {
            var state = ModBlocks.MINE_CHEST.get().defaultBlockState().setValue(MineChestBlock.SPECIAL, special);
            level.setBlock(pos, state, 3);
            var chest = (MineChestBlockEntity) level.getBlockEntity(pos);
            h.assertTrue(chest != null, "Special chest did not create shared block entity");
            chest.startOpen(player);
            var opened = level.getBlockState(pos);
            h.assertTrue(opened.getValue(MineChestBlock.OPEN) && opened.getValue(MineChestBlock.SPECIAL) == special, "Opening lost appearance");
            var loaded = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(opened));
            h.assertTrue(loaded.equals(opened), "Saving lost chest appearance or open state");
            chest.stopOpen(player);
            h.assertTrue(!level.getBlockState(pos).getValue(MineChestBlock.OPEN), "Chest did not close");
            h.assertTrue(state.getDestroySpeed(level, pos) < 0, "Reward chest is breakable");
            level.removeBlock(pos, false);
        }
        h.succeed();
    }
}
