package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.MarlonService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: player wants to claim a monster slayer reward from Marlon.
 */
@SuppressWarnings("null")
public record GilClaimRewardPayload(String goalKey) implements CustomPacketPayload {

    public static final Type<GilClaimRewardPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "gil_claim_reward"));

    public static final StreamCodec<FriendlyByteBuf, GilClaimRewardPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.goalKey),
        buf -> new GilClaimRewardPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GilClaimRewardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarlonService.handleGilClaim(player, payload.goalKey());
            }
        });
    }
}
