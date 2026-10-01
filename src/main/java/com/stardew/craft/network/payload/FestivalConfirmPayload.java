package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FestivalConfirmPayload(OpenFestivalConfirmPayload.Action action, boolean confirmed) implements CustomPacketPayload {
    public static final Type<FestivalConfirmPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "festival_confirm"));

    public static final StreamCodec<FriendlyByteBuf, FestivalConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.action().name());
            buf.writeBoolean(payload.confirmed());
        },
        buf -> new FestivalConfirmPayload(OpenFestivalConfirmPayload.Action.parse(buf.readUtf(32)), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FestivalConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (com.stardew.craft.festival.WinterStarFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.WinterStarFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.FestivalOfIceService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.FestivalOfIceService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.SpiritEveFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.SpiritEveFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.MoonlightJelliesFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.MoonlightJelliesFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.FairFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.FairFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.LuauFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.LuauFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.FlowerDanceService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.FlowerDanceService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                } else if (com.stardew.craft.festival.EggFestivalService.handlesConfirmation(player, payload.action())) {
                    com.stardew.craft.festival.EggFestivalService.onPlayerConfirmed(player, payload.action(), payload.confirmed());
                }
            }
        });
    }
}
