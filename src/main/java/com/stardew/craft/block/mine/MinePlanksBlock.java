package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
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

/** Two persistent painted surfaces sharing one full-cube block and item identity. */
public final class MinePlanksBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);

    public MinePlanksBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0).setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, MineBuildingTheme.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        BlockState themed = defaultBlockState().setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.forPlacement(context));
        if (fixed != null) return themed.setValue(VARIANT, fixed);
        if (context.getLevel().isClientSide) return themed;
        // Sparse visual variation, chosen once and saved; unrelated to mine gameplay RNG.
        return themed.setValue(VARIANT, context.getLevel().getRandom().nextInt(4) == 0 ? 1 : 0);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        PortItemData.set(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state).with(MineBuildingTheme.PROPERTY, state));
        return stack;
    }
}
