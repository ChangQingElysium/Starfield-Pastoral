package com.stardew.craft.port.net.minecraft.network.protocol.configuration;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts of NeoForge's configuration listener that {@code RegisterConfigurationTasksEvent}
 * exposes. On Forge 1.20.1 it is backed by the login handshake that is being prepared.
 */
public interface ServerConfigurationPacketListener {
    boolean hasChannel(ResourceLocation payloadId);

    default boolean hasChannel(CustomPacketPayload.Type<?> type) {
        return this.hasChannel(type.id());
    }

    default boolean hasChannel(CustomPacketPayload payload) {
        return this.hasChannel(payload.type());
    }

    void disconnect(Component reason);
}
