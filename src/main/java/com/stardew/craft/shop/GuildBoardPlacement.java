package com.stardew.craft.shop;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.MapDecorWallThinBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Replace the shipped map's placeholders when entering the guild, including existing saves. */
public final class GuildBoardPlacement {
    public static final BlockPos BOARD = new BlockPos(109, 61, -150);
    private static final BlockPos LOWER_NOTICE = new BlockPos(108, 61, -150);

    private GuildBoardPlacement() {}

    public static boolean replaceLegacyNotices(ServerLevel level) {
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                || !level.hasChunkAt(BOARD) || !level.hasChunkAt(BOARD.north())) return false;
        var oldNotice = ModBlocks.PAPER_CHECKLIST.get().defaultBlockState()
                .setValue(MapDecorWallThinBlock.FACING, Direction.SOUTH);
        // Never overwrite player furniture, a removed board, or other paper decorations.
        if (!level.getBlockState(BOARD).isAir() || !level.getBlockState(BOARD.above()).equals(oldNotice)) return false;
        var board = ModBlocks.GUILD_MONSTER_BOARD.get().defaultBlockState()
                .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        if (!board.canSurvive(level, BOARD)) return false;
        level.setBlock(BOARD, board, 2);
        level.setBlock(BOARD.above(), board.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION), 2);
        if (level.getBlockState(LOWER_NOTICE).equals(oldNotice)) level.setBlock(LOWER_NOTICE, Blocks.AIR.defaultBlockState(), 2);
        return true;
    }
}
