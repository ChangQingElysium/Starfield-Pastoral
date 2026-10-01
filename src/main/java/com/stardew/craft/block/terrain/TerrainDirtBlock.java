package com.stardew.craft.block.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbilities;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbility;

import javax.annotation.Nullable;

/** Plain dirt with an authored terrain texture; independent of the special farm soil. */
public class TerrainDirtBlock extends Block {
    public static final MapCodec<TerrainDirtBlock> CODEC = simpleCodec(TerrainDirtBlock::new);

    public TerrainDirtBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TerrainVariants.DIRT, 0));
    }

    @Override
    public MapCodec<TerrainDirtBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TerrainVariants.DIRT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return TerrainVariants.placement(defaultBlockState(), context);
    }

    @Nullable
    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability == ItemAbilities.HOE_TILL) {
            return context.getClickedFace() != net.minecraft.core.Direction.DOWN
                    && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()
                    ? com.stardew.craft.block.ModBlocks.FARMLAND.get().defaultBlockState() : null;
        }
        if (ability == ItemAbilities.SHOVEL_FLATTEN) {
            // Vanilla tool maps use exact block identity. The caller still posts the event with our actual state.
            return Blocks.DIRT.getToolModifiedState(Blocks.DIRT.defaultBlockState(), context, ability, simulate);
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }
}
