package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-to-client recovery for an aborted sleep fade. */
public record SleepFadeRestorePayload() implements CustomPacketPayload {
    private static final int FADE_TICKS = 12;

    public static final Type<SleepFadeRestorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "sleep_fade_restore"));

    public static final StreamCodec<FriendlyByteBuf, SleepFadeRestorePayload>
            STREAM_CODEC = StreamCodec.of(
                    (buf, payload) -> {
                    },
                    buf -> new SleepFadeRestorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            SleepFadeRestorePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(SleepFadeRestorePayload::handleClient);
    }

    @net.neoforged.api.distmarker.OnlyIn(
            net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        com.stardew.craft.cutscene.runtime.EventScreenFade
                .startFadeFromBlack(FADE_TICKS);
    }
}
