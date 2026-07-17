package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.client.IceFishingCutsceneClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record IceFishingCutsceneStatePayload(boolean playerWon, String winnerText) implements CustomPacketPayload {
    private static final int MAX_WINNER_TEXT_LENGTH = 1024;
    public static final Type<IceFishingCutsceneStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "ice_fishing_cutscene_state"));

    public static final StreamCodec<FriendlyByteBuf, IceFishingCutsceneStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.playerWon());
            buf.writeUtf(payload.winnerText() == null ? "" : payload.winnerText(), MAX_WINNER_TEXT_LENGTH);
        },
        buf -> new IceFishingCutsceneStatePayload(buf.readBoolean(), buf.readUtf(MAX_WINNER_TEXT_LENGTH))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IceFishingCutsceneStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(IceFishingCutsceneStatePayload payload) {
        IceFishingCutsceneClientState.set(payload.playerWon(), payload.winnerText());
    }
}
