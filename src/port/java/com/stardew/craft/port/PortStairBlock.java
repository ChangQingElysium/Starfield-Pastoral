package com.stardew.craft.port;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * PORT(1.20.1): 1.21.1 {@code StairBlock}. Since 1.20.5 vanilla stairs only take their explosion resistance from the
 * base block; 1.20.1 still forwards animateTick, attack, destroy, onPlace, onRemove, stepOn, isRandomlyTicking,
 * randomTick, tick, use and wasExploded to the base block. With a grass base that made authored grass stairs randomly
 * tick and run grass decay/spread on the stair state. Every overridden method here is the 1.21.1
 * {@code BlockBehaviour}/{@code Block} default.
 */
public class PortStairBlock extends StairBlock {
    public PortStairBlock(BlockState baseState, Properties properties) {
        super(baseState, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return this.isRandomlyTicking;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
    }
}
