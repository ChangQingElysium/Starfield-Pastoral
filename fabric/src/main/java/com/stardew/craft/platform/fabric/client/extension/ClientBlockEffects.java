package com.stardew.craft.platform.fabric.client.extension;

import com.stardew.craft.platform.mixin.client.extension.mixin.ParticleEngineLevelAccess;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

@Environment(EnvType.CLIENT)
public final class ClientBlockEffects {
    private ClientBlockEffects() {}
    /** Retains the real hit coordinates and face; no center-of-block synthetic hit is substituted. */
    public static void crack(ParticleEngine engine, BlockPos pos, BlockHitResult hit) {
        Level level = Objects.requireNonNull(((ParticleEngineLevelAccess) engine).stardewcraft$level(), "particle level");
        BlockState state = level.getBlockState(pos);
        hit(IClientBlockExtensions.of(state), state, level, hit, engine, () -> engine.crack(pos, hit.getDirection()));
    }
    static void hit(IClientBlockExtensions extension, BlockState state, Level level, BlockHitResult hit,
                    ParticleEngine engine, Runnable vanilla) {
        if (!extension.addHitEffects(state, level, hit, engine)) vanilla.run();
    }
}
