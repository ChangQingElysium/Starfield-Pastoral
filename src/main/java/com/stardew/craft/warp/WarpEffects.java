package com.stardew.craft.warp;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Shared visual effect used by fixed-destination warp devices. */
public final class WarpEffects {
    private WarpEffects() {
    }

    public static void spawnWarpParticles(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    x + level.random.nextDouble() * 8.0D - 4.0D,
                    y + 1.0D + level.random.nextDouble() * 2.0D,
                    z + level.random.nextDouble() * 8.0D - 4.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0D * i / 24.0D;
            double radius = 3.0D + level.random.nextDouble() * 3.0D;
            level.sendParticles(ParticleTypes.FIREWORK,
                    x + Math.cos(angle) * radius, y + 0.5D, z + Math.sin(angle) * radius,
                    1, 0.0D, 0.1D, 0.0D, 0.02D);
        }
        for (int offset = 8; offset >= -8; offset--) {
            level.sendParticles(ParticleTypes.END_ROD, x + offset, y + 0.5D, z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
