package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings("null")
public class ToggleableCeilingLightBlock extends MapDecorCeilingStaticBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ToggleableCeilingLightBlock(Properties properties, String modelId) {
        super(properties, modelId, true);
        registerDefaultState(defaultBlockState()
            .setValue(PART, Part.MAIN)
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos mainPos = state.getValue(PART) == Part.EXTENSION ? findMainPos(level, pos, state) : pos;
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this) || !mainState.hasProperty(LIT)) {
            return InteractionResult.PASS;
        }
        boolean nextLit = !mainState.getValue(LIT);
        level.setBlock(mainPos, mainState.setValue(LIT, nextLit), 3);
        level.playSound(null, mainPos, net.minecraft.sounds.SoundEvents.LEVER_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, nextLit ? 0.7F : 0.5F);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.hasProperty(LIT) && state.getValue(LIT) ? 15 : 0;
    }
}
