package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairFishingHudStatePayload(boolean active, int remainingMs, int score)
    implements CustomPacketPayload {
    public static final Type<FairFishingHudStatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_fishing_hud_state"));

    public static final StreamCodec<ByteBuf, FairFishingHudStatePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FairFishingHudStatePayload::active,
            ByteBufCodecs.VAR_INT,
            FairFishingHudStatePayload::remainingMs,
            ByteBufCodecs.VAR_INT,
            FairFishingHudStatePayload::score,
            FairFishingHudStatePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairFishingHudStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(FairFishingHudStatePayload payload) {
        com.stardew.craft.client.hud.StardewTimeHud.setFairFishingHudState(
            payload.active(),
            payload.remainingMs(),
            payload.score()
        );
    }
}
