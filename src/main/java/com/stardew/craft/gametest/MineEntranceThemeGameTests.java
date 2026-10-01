package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineExitBlock;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.block.mine.MineStepStoneBlock;
import com.stardew.craft.item.MineEntranceItem;
import com.stardew.craft.item.MineExitItem;
import com.stardew.craft.item.MineStepStoneItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_entrances")
@PrefixGameTestTemplate(false)
public final class MineEntranceThemeGameTests {
    @GameTest(templateNamespace = "stardewcraft_entrances", template = "ring_utilities")
    public static void themedItemsPlaceAndPickCorrectly(GameTestHelper helper) {
        var level = helper.getLevel();
        var exit = (MineExitBlock) ModBlocks.MINE_EXIT.get();
        var entrance = (MineLadderBlock) ModBlocks.MINE_LADDER.get();
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Entrance test"));
        player.getAbilities().instabuild = true;
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int tier = 0; tier < 4; tier++) level.setBlock(pos.above(tier).north(), Blocks.STONE.defaultBlockState(), 3);
        for (var theme : MineLadderBlock.Theme.values()) {
            var soil = com.stardew.craft.block.mine.MineBuildingTheme.valueOf(theme.name()).soil();
            level.setBlock(pos.below(), soil.defaultBlockState(), 3);
            helper.assertTrue(com.stardew.craft.mining.OrdinaryMineRuntime.entranceTheme(level, pos, 1) == theme, "Generated entrance ignored local soil theme");
            var stack = new ItemStack(exit);
            PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MineExitBlock.THEME, theme));
            place(helper, player, pos, stack);
            for (int tier = 0; tier < 4; tier++) {
                var at = pos.above(tier);
                var state = level.getBlockState(at);
                helper.assertTrue(state.is(exit) && state.getValue(MineExitBlock.THEME) == theme, "Exit extension lost theme");
                helper.assertTrue(MineExitItem.theme(exit.getCloneItemStack(level, at, state)) == theme, "Exit pick lost theme");
            }
            level.removeBlock(pos, false);
            for (boolean shaft : new boolean[]{false, true}) {
                stack = new ItemStack(entrance);
                PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MineLadderBlock.THEME, theme).with(MineLadderBlock.SHAFT, shaft));
                place(helper, player, pos, stack);
                var state = level.getBlockState(pos);
                var picked = entrance.getCloneItemStack(level, pos, state);
                helper.assertTrue(state.getValue(MineLadderBlock.THEME) == theme && state.getValue(MineLadderBlock.SHAFT) == shaft, "Entrance placement lost variant");
                helper.assertTrue(MineEntranceItem.theme(picked) == theme && MineEntranceItem.shaft(picked) == shaft, "Entrance pick lost variant");
                helper.assertTrue(state.getDestroySpeed(level, pos) < 0, "Entrance became breakable");
                level.removeBlock(pos, false);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_entrances", template = "ring_utilities")
    public static void stepStoneConnectionsFollowThemeChangesAndDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        var stone = (MineStepStoneBlock) ModBlocks.MINE_STEP_STONE.get();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        for (var theme : MineLadderBlock.Theme.values()) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.removeBlock(pos.offset(x, 0, z), false);
            var state = stone.defaultBlockState().setValue(MineStepStoneBlock.THEME, theme);
            level.setBlock(pos, state, 3);
            level.setBlock(pos.north(), state, 3);
            level.setBlock(pos.east(), state, 3);
            level.setBlock(pos.north().east(), state, 3);
            helper.assertTrue(level.getBlockState(pos).getValue(MineStepStoneBlock.CONNECTIONS) == 19, "Same-theme corner failed");
            var other = theme == MineLadderBlock.Theme.EARTH ? MineLadderBlock.Theme.FROST : MineLadderBlock.Theme.EARTH;
            level.setBlock(pos.north().east(), state.setValue(MineStepStoneBlock.THEME, other), 3);
            helper.assertTrue(level.getBlockState(pos).getValue(MineStepStoneBlock.CONNECTIONS) == 3, "Diagonal recoloring did not refresh corner");
            state = level.getBlockState(pos);
            helper.assertTrue(MineStepStoneItem.theme(stone.getCloneItemStack(level, pos, state)) == theme, "Step pick lost theme");
            var drops = Block.getDrops(state, level, pos, null);
            helper.assertTrue(drops.size() == 1 && MineStepStoneItem.theme(drops.getFirst()) == theme, "Step drop lost theme");
        }
        helper.succeed();
    }

    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Themed item placement failed");
    }
}
