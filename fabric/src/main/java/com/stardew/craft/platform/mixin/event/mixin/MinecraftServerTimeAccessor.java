package com.stardew.craft.platform.mixin.event.mixin;


import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the server's real tick budget instead of guessing from wall-clock time. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerTimeAccessor {
    @Invoker("haveTime")
    boolean stardewcraft$haveTime();
}
