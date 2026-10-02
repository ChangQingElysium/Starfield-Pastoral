package com.stardew.craft.port.net.neoforged.neoforge.network.configuration;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.minecraft.server.network.ConfigurationTask;
import java.util.function.Consumer;

/**
 * NeoForge configuration task that talks in custom payloads. On Forge 1.20.1 {@link #run} is invoked
 * while the server gathers its login handshake messages; every payload passed to the sender is
 * delivered to the client's {@code configurationToClient} handler before the player joins.
 */
public interface ICustomConfigurationTask extends ConfigurationTask {
    void run(Consumer<CustomPacketPayload> sender);
}
