package com.stardew.craft.port;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * PORT(1.20.1): private channel for state NeoForge syncs itself (synced data maps, auxiliary light data).
 * Independent of the mod's payload layer.
 */
public final class PortNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(PortBootstrap.NAMESPACE, "port_internal"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();
    private static boolean initialized;

    private PortNetwork() {}

    static synchronized void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.messageBuilder(PortDataMaps.SyncMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PortDataMaps.SyncMessage::encode)
                .decoder(PortDataMaps.SyncMessage::decode)
                .consumerMainThread((message, context) -> PortClientHandlers.handle(message))
                .add();
        CHANNEL.messageBuilder(PortAuxLight.SyncMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PortAuxLight.SyncMessage::encode)
                .decoder(PortAuxLight.SyncMessage::decode)
                .consumerMainThread((message, context) -> PortClientHandlers.handle(message))
                .add();
    }
}
