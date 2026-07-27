package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import com.stardew.craft.api.v1.network.StardewNetworkCapability;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilityRequirement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server capability advertisement sent during configuration. */
public record CapabilityHelloPayload(
        List<StardewNetworkCapability> capabilities
) implements CustomPacketPayload {
    public static final Type<CapabilityHelloPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "capability_hello_v1"));
    static final StreamCodec<FriendlyByteBuf, StardewNetworkCapability>
            CAPABILITY_CODEC = StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    StardewNetworkCapability::id,
                    ByteBufCodecs.VAR_INT,
                    StardewNetworkCapability::version,
                    ByteBufCodecs.BOOL,
                    capability -> capability.requirement()
                            == StardewNetworkCapabilityRequirement.REQUIRED_REMOTE,
                    (id, version, required) ->
                            new StardewNetworkCapability(
                                    id,
                                    version,
                                    required
                                            ? StardewNetworkCapabilityRequirement
                                                    .REQUIRED_REMOTE
                                            : StardewNetworkCapabilityRequirement
                                                    .OPTIONAL));
    public static final StreamCodec<FriendlyByteBuf, CapabilityHelloPayload>
            STREAM_CODEC = StreamCodec.composite(
                    CAPABILITY_CODEC.apply(ByteBufCodecs.list(
                            StardewNetworkCapabilityRegistry.MAX_CAPABILITIES)),
                    CapabilityHelloPayload::capabilities,
                    CapabilityHelloPayload::new);

    public CapabilityHelloPayload {
        capabilities = List.copyOf(capabilities);
        if (capabilities.size()
                > StardewNetworkCapabilityRegistry.MAX_CAPABILITIES) {
            throw new IllegalArgumentException(
                    "Too many Stardew network capabilities");
        }
    }

    public static void handle(
            CapabilityHelloPayload payload,
            IPayloadContext context
    ) {
        var result = StardewNetworkCapabilityRegistry.accept(
                context.connection(), payload.capabilities());
        context.reply(new CapabilityAckPayload(
                StardewNetworkCapabilityRegistry.local()));
        if (!result.accepted()) {
            context.disconnect(Component.literal(
                    "Incompatible StardewCraft capabilities: "
                            + String.join("; ", result.failures())));
            return;
        }
        StardewCraft.LOGGER.info(
                "Negotiated {} Stardew network capabilities on client",
                result.session().negotiated().size());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
