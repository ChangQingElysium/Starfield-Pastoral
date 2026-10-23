package com.stardew.craft.platform.fabric.client.extension;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

@Environment(EnvType.CLIENT)
public interface IClientBlockExtensions {
    IClientBlockExtensions DEFAULT = new IClientBlockExtensions() {};
    static IClientBlockExtensions of(Block block) { return FabricClientExtensions.registry().block(block); }
    static IClientBlockExtensions of(BlockState state) { return of(state.getBlock()); }
    default boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine engine) { return false; }
    default boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) { return !state.shouldSpawnTerrainParticles(); }
    default boolean areBreakingParticlesTinted(BlockState state, ClientLevel level, BlockPos pos) { return !state.is(Blocks.GRASS_BLOCK); }
}
