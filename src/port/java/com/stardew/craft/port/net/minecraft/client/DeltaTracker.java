package com.stardew.craft.port.net.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 1.21 {@code net.minecraft.client.DeltaTracker}. 1.20.1 passes a bare {@code float partialTick}; code that receives a
 * frame's partial tick can wrap it with {@link #of(float)}, and {@link #client()} is a live view of the 1.20.1 client
 * timer ({@code Minecraft#getFrameTime} already freezes the partial tick while paused, like 1.21's
 * {@code Timer#getGameTimeDeltaPartialTick}; 1.20.1 has no tick freezing, so {@code runsNormally} is irrelevant).
 */
@OnlyIn(Dist.CLIENT)
public interface DeltaTracker {
    DeltaTracker ZERO = new DefaultValue(0.0F);
    DeltaTracker ONE = new DefaultValue(1.0F);

    float getGameTimeDeltaTicks();

    float getGameTimeDeltaPartialTick(boolean runsNormally);

    float getRealtimeDeltaTicks();

    /** A frame whose partial tick is {@code partialTick}; tick deltas come from the client timer. */
    static DeltaTracker of(float partialTick) {
        return new DeltaTracker() {
            @Override
            public float getGameTimeDeltaTicks() {
                return client().getGameTimeDeltaTicks();
            }

            @Override
            public float getGameTimeDeltaPartialTick(boolean runsNormally) {
                return partialTick;
            }

            @Override
            public float getRealtimeDeltaTicks() {
                return client().getRealtimeDeltaTicks();
            }
        };
    }

    static DeltaTracker client() {
        return ClientTimer.INSTANCE;
    }

    @OnlyIn(Dist.CLIENT)
    class DefaultValue implements DeltaTracker {
        private final float value;

        DefaultValue(float value) {
            this.value = value;
        }

        @Override
        public float getGameTimeDeltaTicks() {
            return this.value;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean runsNormally) {
            return this.value;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return this.value;
        }
    }

    @OnlyIn(Dist.CLIENT)
    final class ClientTimer implements DeltaTracker {
        private static final ClientTimer INSTANCE = new ClientTimer();

        private ClientTimer() {}

        @Override
        public float getGameTimeDeltaTicks() {
            return Minecraft.getInstance().getDeltaFrameTime();
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean runsNormally) {
            return Minecraft.getInstance().getFrameTime();
        }

        @Override
        public float getRealtimeDeltaTicks() {
            // The 1.20.1 timer keeps running while paused, i.e. it is the real-time timer (fixed 20 TPS).
            float delta = Minecraft.getInstance().getDeltaFrameTime();
            return delta > 7.0F ? 0.5F : delta;
        }
    }
}
