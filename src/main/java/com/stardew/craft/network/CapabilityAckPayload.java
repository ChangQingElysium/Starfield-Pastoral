package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import com.stardew.craft.api.v1.network.StardewNetworkCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Client capability response that completes the configuration task. */
public record CapabilityAckPayload(
        List<StardewNetworkCapability> capabilities
) implements CustomPacketPayload {
    public static final Type<CapabilityAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "capability_ack_v1"));
    public static final StreamCodec<FriendlyByteBuf, CapabilityAckPayload>
            STREAM_CODEC = StreamCodec.composite(
                    CapabilityHelloPayload.CAPABILITY_CODEC.apply(
                            ByteBufCodecs.list(
                                    StardewNetworkCapabilityRegistry
                                            .MAX_CAPABILITIES)),
                    CapabilityAckPayload::capabilities,
                    CapabilityAckPayload::new);

    public CapabilityAckPayload {
        capabilities = List.copyOf(capabilities);
        if (capabilities.size()
                > StardewNetworkCapabilityRegistry.MAX_CAPABILITIES) {
            throw new IllegalArgumentException(
                    "Too many Stardew network capabilities");
        }
    }

    public static void handle(
            CapabilityAckPayload payload,
            IPayloadContext context
    ) {
        var result = StardewNetworkCapabilityRegistry.accept(
                context.connection(), payload.capabilities());
        if (!result.accepted()) {
            context.disconnect(Component.literal(
                    "Incompatible StardewCraft capabilities: "
                            + String.join("; ", result.failures())));
            return;
        }
        StardewCraft.LOGGER.info(
                "Negotiated {} Stardew network capabilities on server",
                result.session().negotiated().size());
        context.finishCurrentTask(CapabilityNegotiationTask.TYPE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
