package com.stardew.craft.platform.fabric.blockextension;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class BlockBehaviors {
    private BlockBehaviors() {}

    public static SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return getSoundType(state.getBlock(), state, level, pos, entity);
    }

    public static SoundType getSoundType(Block block, BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return block instanceof BlockBehaviorProvider provider
                ? Objects.requireNonNull(provider.getSoundType(state, level, pos, entity), "block sound type") : state.getSoundType();
    }

    public static SoundType resolveSound(BlockState state, LevelReader level, BlockPos pos, Entity entity, SoundType nativeValue) {
        return state.getBlock() instanceof BlockBehaviorProvider provider
                ? Objects.requireNonNull(provider.getSoundType(state, level, pos, entity), "block sound type") : nativeValue;
    }

    public static float getFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return state.getBlock() instanceof BlockBehaviorProvider provider
                ? provider.getFriction(state, level, pos, entity) : state.getBlock().getFriction();
    }

    public static float resolveFriction(Block block, Entity entity, BlockPos pos, float nativeValue) {
        return block instanceof BlockBehaviorProvider provider
                ? provider.getFriction(entity.level().getBlockState(pos), entity.level(), pos, entity) : nativeValue;
    }

    public static float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return resolveExplosionResistance(state, level, pos, explosion, state.getBlock().getExplosionResistance());
    }

    public static float resolveExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion, float nativeValue) {
        return state.getBlock() instanceof BlockBehaviorProvider provider ? provider.getExplosionResistance(state, level, pos, explosion) : nativeValue;
    }

    public static MapColor resolveMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor nativeValue) {
        return state.getBlock() instanceof BlockBehaviorProvider provider
                ? Objects.requireNonNull(provider.getMapColor(state, level, pos, nativeValue), "block map color") : nativeValue;
    }

    public static boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getBlock() instanceof BlockBehaviorProvider provider ? provider.isLadder(state, level, pos, entity) : vanillaLadder(state, level, pos);
    }

    public static boolean isLadder(Block block, BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return block instanceof BlockBehaviorProvider provider ? provider.isLadder(state, level, pos, entity) : vanillaLadder(state, level, pos);
    }

    public static boolean vanillaLadder(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            if (!state.getValue(TrapDoorBlock.OPEN)) return false;
            var below = level.getBlockState(pos.below());
            return below.getBlock() instanceof LadderBlock && below.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
        }
        return state.is(BlockTags.CLIMBABLE);
    }
}
