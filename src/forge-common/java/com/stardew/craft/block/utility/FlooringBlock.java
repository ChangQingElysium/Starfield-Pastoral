package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.DecorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class FlooringBlock extends Block implements EntityBlock {
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 64);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    public FlooringBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STYLE, 0).setValue(PART, 0));
    }

    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        return new DecorBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STYLE, PART);
    }
}
