package com.stardew.craft.platform.fabric.blockextension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Only the contextual block properties implemented by StardewCraft. */
public interface BlockBehaviorProvider {
    default SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) { return state.getSoundType(); }
    default float getFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity) { return state.getBlock().getFriction(); }
    default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) { return state.getBlock().getExplosionResistance(); }
    default MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) { return defaultColor; }
    default boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) { return BlockBehaviors.vanillaLadder(state, level, pos); }
}
