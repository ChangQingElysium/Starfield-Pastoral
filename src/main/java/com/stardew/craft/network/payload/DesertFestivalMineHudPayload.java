package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record DesertFestivalMineHudPayload(int displayRating, boolean shake) implements CustomPacketPayload {
    public static final Type<DesertFestivalMineHudPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_festival_mine_hud"));

    public static final StreamCodec<ByteBuf, DesertFestivalMineHudPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        DesertFestivalMineHudPayload::displayRating,
        ByteBufCodecs.BOOL,
        DesertFestivalMineHudPayload::shake,
        DesertFestivalMineHudPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertFestivalMineHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.stardew.craft.client.hud.StardewTimeHud.updateDesertFestivalMineRating(payload.displayRating(), payload.shake())
        );
    }
}
