package com.stardew.craft.port.net.neoforged.neoforge.network.handling;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}
