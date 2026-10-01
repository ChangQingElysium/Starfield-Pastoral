package com.stardew.craft.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): 1.21 {@code IntegratedServer#isPaused()} (1.20.1 keeps the same field without a getter). */
@Mixin(IntegratedServer.class)
public interface PortIntegratedServerAccessor {
    @Accessor("paused")
    boolean stardewcraft$isPaused();
}
