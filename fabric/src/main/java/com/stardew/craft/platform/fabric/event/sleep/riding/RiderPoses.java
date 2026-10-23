package com.stardew.craft.platform.fabric.event.sleep.riding;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class RiderPoses {
    private RiderPoses() {}
    public static boolean shouldSit(boolean passenger, @Nullable Entity vehicle) {
        return passenger && vehicle != null && (!(vehicle instanceof RiderPoseProvider provider) || provider.shouldRiderSit());
    }
}
