package com.stardew.craft.block.terrain;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The warm ochre wall brick used by the Tavern and Pierre's brick base.
 *
 * The three authored faces share the same mortar layout.  The visual choice
 * is stored in the block state so a placed wall keeps its appearance after a
 * save/reload instead of being re-rolled by the renderer.
 */
public final class OchreBricksBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

    public OchreBricksBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel().isClientSide) {
            return defaultBlockState();
        }
        int roll = context.getLevel().getRandom().nextInt(100);
        int variant = roll < 40 ? 0 : roll < 65 ? 1 : 2;
        return defaultBlockState().setValue(VARIANT, variant);
    }
}
