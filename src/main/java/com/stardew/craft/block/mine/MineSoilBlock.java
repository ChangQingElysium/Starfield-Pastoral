package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Three persistent painted surfaces sharing one full-cube block and item identity. */
public final class MineSoilBlock extends Block {
    public static final MapCodec<MineSoilBlock> CODEC = simpleCodec(MineSoilBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

    public MineSoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    public MapCodec<MineSoilBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        if (fixed != null) return defaultBlockState().setValue(VARIANT, fixed);
        if (context.getLevel().isClientSide) return defaultBlockState();
        // Sparse visual variation, chosen once and saved; unrelated to mine gameplay RNG.
        int roll = context.getLevel().getRandom().nextInt(200);
        return defaultBlockState().setValue(VARIANT, roll < 190 ? 0 : roll < 195 ? 1 : 2);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }
}
