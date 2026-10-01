package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.client.StardewCalendarDate;
import com.stardew.craft.api.v1.client.StardewDailyInfoSnapshot;
import com.stardew.craft.api.v1.client.StardewToolUpgradeSnapshot;
import com.stardew.craft.api.v1.client.StardewQueenOfSauceSnapshot;
import com.stardew.craft.api.v1.internal.client.StardewDailyInfoCache;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Optional;

/** Small replacement snapshot; sent only when the authoritative daily information changes. */
public record DailyInfoSyncPayload(StardewDailyInfoSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<DailyInfoSyncPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "daily_info"));
    public static final StreamCodec<FriendlyByteBuf, DailyInfoSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                var info = payload.snapshot();
                buf.writeUUID(info.playerId());
                writeDate(buf, info.date());
                buf.writeDouble(info.dailyLuck());
                buf.writeUtf(info.tomorrowWeather(), 64);
                buf.writeEnum(info.berrySeason());
                buf.writeBoolean(info.booksellerToday());
                buf.writeBoolean(info.travelingCartToday());
                buf.writeCollection(info.birthdayNpcIds(), FriendlyByteBuf::writeResourceLocation);
                buf.writeBoolean(info.toolUpgrade().isPresent());
                info.toolUpgrade().ifPresent(tool -> {
                    buf.writeResourceLocation(tool.resultItemId());
                    buf.writeVarInt(tool.daysRemaining());
                    writeDate(buf, tool.expectedReadyDate());
                });
                buf.writeBoolean(info.queenOfSauce().isPresent());
                info.queenOfSauce().ifPresent(cooking -> {
                    buf.writeUtf(cooking.recipeId(), 128);
                    buf.writeBoolean(cooking.rerun());
                    buf.writeBoolean(cooking.recipeKnown());
                    buf.writeBoolean(cooking.watchedToday());
                });
            }, buf -> {
                var player = buf.readUUID();
                var date = readDate(buf);
                double luck = buf.readDouble();
                String weather = buf.readUtf(64);
                var berry = buf.readEnum(StardewDailyInfoSnapshot.BerrySeason.class);
                boolean bookseller = buf.readBoolean(), cart = buf.readBoolean();
                var birthdays = buf.readList(FriendlyByteBuf::readResourceLocation);
                Optional<StardewToolUpgradeSnapshot> tool = buf.readBoolean()
                        ? Optional.of(new StardewToolUpgradeSnapshot(buf.readResourceLocation(),
                                buf.readVarInt(), readDate(buf))) : Optional.empty();
                Optional<StardewQueenOfSauceSnapshot> cooking = buf.readBoolean()
                        ? Optional.of(new StardewQueenOfSauceSnapshot(buf.readUtf(128),
                                buf.readBoolean(), buf.readBoolean(), buf.readBoolean())) : Optional.empty();
                return new DailyInfoSyncPayload(new StardewDailyInfoSnapshot(player, date, luck,
                        weather, berry, bookseller, cart, birthdays, tool, cooking));
            });

    private static void writeDate(FriendlyByteBuf buf, StardewCalendarDate date) {
        buf.writeVarInt(date.year());
        buf.writeVarInt(date.season());
        buf.writeVarInt(date.day());
    }

    private static StardewCalendarDate readDate(FriendlyByteBuf buf) {
        return new StardewCalendarDate(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override public Type<DailyInfoSyncPayload> type() { return TYPE; }

    public static void handle(DailyInfoSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getUUID().equals(payload.snapshot().playerId())) {
                StardewDailyInfoCache.replace(payload.snapshot());
            }
        });
    }
}
