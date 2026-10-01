package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record CosmeticAppearanceSyncPayload(UUID playerId, String hat, String shirt, String pants)
        implements CustomPacketPayload {

    public static final Type<CosmeticAppearanceSyncPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "cosmetic_appearance_sync"));

    public static final StreamCodec<FriendlyByteBuf, CosmeticAppearanceSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.playerId);
                buf.writeUtf(payload.hat);
                buf.writeUtf(payload.shirt);
                buf.writeUtf(payload.pants);
            },
            buf -> new CosmeticAppearanceSyncPayload(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CosmeticAppearanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.ClientPlayerDataCache.setCosmeticAppearance(
                payload.playerId, payload.hat, payload.shirt, payload.pants));
    }
}
