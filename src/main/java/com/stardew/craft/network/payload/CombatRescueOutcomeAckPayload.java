package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PassOutService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S acknowledgement for the durable combat-rescue outcome. */
public record CombatRescueOutcomeAckPayload(long transactionId) implements CustomPacketPayload {
    public static final Type<CombatRescueOutcomeAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "combat_rescue_outcome_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatRescueOutcomeAckPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    CombatRescueOutcomeAckPayload::transactionId,
                    CombatRescueOutcomeAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatRescueOutcomeAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PassOutService.acknowledgeCombatOutcome(player, payload.transactionId());
            }
        });
    }
}
