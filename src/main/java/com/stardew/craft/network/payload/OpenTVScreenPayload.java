package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: raw numeric/boolean data for the TV GUI.
 * All display text is resolved client-side via I18n translation keys,
 * exactly matching Stardew Valley StringsFromCSFiles + Tv_CookingChannel + Tv_TipChannel.
 */
@SuppressWarnings("null")
public record OpenTVScreenPayload(
        // Channel availability (matches TV.cs checkForAction logic)
        boolean tipsAvailable,
        boolean cookingAvailable,
        boolean fishingAvailable,
        boolean cursedAvailable,
        // Weather — raw weather ID from WeatherManager
        String tomorrowWeather,       // "Sun","Rain","Storm","Snow","WindSpring","WindFall","Festival"
        // Fortune
        double dailyLuck,
        int fortuneOpeningVariant,    // 0-4 → TV.cs.13128/13130/13132/13133/13134/13135
        // Tips
        int tipIndex,                 // daysPlayed % 224 → key into TipChannel data
        // Cooking
        int cookingWeek,              // 1-32 index into CookingChannel data
        String cookingRecipeId,       // mod recipe id e.g. "stir_fry"
        boolean cookingAlreadyKnown,
        boolean cookingIsRerun,
        // Calendar
        int currentDay,               // 1-28
        int currentSeason,            // 0-3
        int daysPlayed,
        // TV block position (main block)
        int blockX,
        int blockY,
        int blockZ
) implements CustomPacketPayload {

    public static final Type<OpenTVScreenPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_tv_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenTVScreenPayload> STREAM_CODEC = StreamCodec.of(
            OpenTVScreenPayload::write,
            OpenTVScreenPayload::read
    );

    private static void write(FriendlyByteBuf buf, OpenTVScreenPayload p) {
        buf.writeBoolean(p.tipsAvailable);
        buf.writeBoolean(p.cookingAvailable);
        buf.writeBoolean(p.fishingAvailable);
        buf.writeBoolean(p.cursedAvailable);

        buf.writeUtf(p.tomorrowWeather, 64);

        buf.writeDouble(p.dailyLuck);
        buf.writeVarInt(p.fortuneOpeningVariant);

        buf.writeVarInt(p.tipIndex);

        buf.writeVarInt(p.cookingWeek);
        buf.writeUtf(p.cookingRecipeId, 128);
        buf.writeBoolean(p.cookingAlreadyKnown);
        buf.writeBoolean(p.cookingIsRerun);

        buf.writeVarInt(p.currentDay);
        buf.writeVarInt(p.currentSeason);
        buf.writeVarInt(p.daysPlayed);

        buf.writeVarInt(p.blockX);
        buf.writeVarInt(p.blockY);
        buf.writeVarInt(p.blockZ);
    }

    private static OpenTVScreenPayload read(FriendlyByteBuf buf) {
        return new OpenTVScreenPayload(
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(64),
                buf.readDouble(), buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(), buf.readUtf(128), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt()  // blockX, blockY, blockZ
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTVScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenTVScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.TVScreen(payload));
    }
}
