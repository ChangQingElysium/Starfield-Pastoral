package com.stardew.craft.platform.mixin.network.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Register in the client-only Mixin list, never the dedicated-server list. */
@Environment(EnvType.CLIENT)
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonConnectionAccessor {
    @Accessor("connection")
    Connection stardewcraft$getConnection();
}
