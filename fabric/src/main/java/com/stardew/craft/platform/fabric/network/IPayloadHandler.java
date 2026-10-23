package com.stardew.craft.platform.fabric.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A payload handler shared by the Fabric play and configuration adapters. */
@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}
