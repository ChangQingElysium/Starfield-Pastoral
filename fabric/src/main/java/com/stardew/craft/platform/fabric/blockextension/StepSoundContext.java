package com.stardew.craft.platform.fabric.blockextension;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Carries positions discarded by vanilla's two-argument combination/muffled sound methods. */
public final class StepSoundContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private record Context(Entity entity, BlockPos primary, BlockPos secondary) {}
    private StepSoundContext() {}

    public static void run(Entity entity, BlockPos primary, BlockPos secondary, Runnable action) {
        var previous = CURRENT.get();
        CURRENT.set(new Context(Objects.requireNonNull(entity), primary.immutable(), secondary.immutable()));
        try { action.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    public static BlockPos primary(Entity entity) { var current = CURRENT.get(); return current != null && current.entity == entity ? current.primary : null; }
    public static BlockPos secondary(Entity entity) { var current = CURRENT.get(); return current != null && current.entity == entity ? current.secondary : null; }
}
