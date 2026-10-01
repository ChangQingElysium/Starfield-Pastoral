package com.stardew.craft.templates;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SnowLayerTemplateBlock extends MaterialTemplateBlock {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    public SnowLayerTemplateBlock(Properties properties) {
        super(TemplateShape.SNOW_LAYER, properties);
        registerDefaultState(defaultBlockState().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LAYERS);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && state.getValue(LAYERS) < 8
                && (!context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        return existing.is(this) ? existing.setValue(LAYERS, Math.min(8, existing.getValue(LAYERS) + 1))
                : super.getStateForPlacement(context);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Let BlockItem placement handle stacking, item consumption and collision checks.
        return stack.is(asItem()) ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
