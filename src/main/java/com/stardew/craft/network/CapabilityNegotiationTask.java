package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

/** Configuration task that advertises server capabilities and waits for the client reply. */
public record CapabilityNegotiationTask() implements ICustomConfigurationTask {
    public static final Type TYPE = new Type(
            ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "capability_negotiation_v1"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(new CapabilityHelloPayload(
                StardewNetworkCapabilityRegistry.local()));
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
