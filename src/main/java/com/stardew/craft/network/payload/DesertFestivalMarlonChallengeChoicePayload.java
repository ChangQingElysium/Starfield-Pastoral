package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.desert.DesertFestivalMarlonChallengeService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record DesertFestivalMarlonChallengeChoicePayload(String challengeId, boolean claimReward) implements CustomPacketPayload {
    public static final Type<DesertFestivalMarlonChallengeChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_festival_marlon_challenge_choice"));

    public static final StreamCodec<FriendlyByteBuf, DesertFestivalMarlonChallengeChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.challengeId());
            buf.writeBoolean(payload.claimReward());
        },
        buf -> new DesertFestivalMarlonChallengeChoicePayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertFestivalMarlonChallengeChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DesertFestivalMarlonChallengeService.handleChallengeChoice(player, payload.challengeId(), payload.claimReward());
            }
        });
    }
}