package com.stardew.craft.gingerisland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Authored ground variants chosen once; save/reload never reshuffles the painted surface. */
public final class GingerIslandSurfaceBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 5);
    private final int count;
    private final int baseWeight;

    public GingerIslandSurfaceBlock(Properties properties, int count, int baseWeight) {
        super(properties);
        this.count = count;
        this.baseWeight = baseWeight;
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(VARIANT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(VARIANT);
        if (fixed != null) return defaultBlockState().setValue(VARIANT, Math.min(fixed, count - 1));
        if (context.getLevel().isClientSide) return defaultBlockState();
        int roll = context.getLevel().getRandom().nextInt(100);
        int selected = roll < baseWeight ? 0 : 1 + (roll - baseWeight) * (count - 1) / (100 - baseWeight);
        return defaultBlockState().setValue(VARIANT, selected);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }
}
