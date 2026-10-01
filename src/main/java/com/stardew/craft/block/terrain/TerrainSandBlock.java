package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.ToolAction;

/** Stable, buildable beach sand; only this sand tills into sandy farmland. */
public final class TerrainSandBlock extends Block {

    public TerrainSandBlock(Properties properties) { super(properties); }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction ability, boolean simulate) {
        if (ability == ToolActions.HOE_TILL) {
            return context.getClickedFace() != Direction.DOWN
                    && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()
                    ? ModBlocks.SANDY_FARMLAND.get().defaultBlockState() : null;
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }
}
