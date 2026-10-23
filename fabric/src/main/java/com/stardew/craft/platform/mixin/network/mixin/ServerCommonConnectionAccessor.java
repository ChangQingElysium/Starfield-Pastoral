package com.stardew.craft.platform.mixin.network.mixin;


import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla has no public server-side connection getter. */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonConnectionAccessor {
    @Accessor("connection")
    Connection stardewcraft$getConnection();
}
