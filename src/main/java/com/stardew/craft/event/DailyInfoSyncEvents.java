package com.stardew.craft.event;

import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.client.StardewCalendarDate;
import com.stardew.craft.api.v1.client.StardewDailyInfoSnapshot;
import com.stardew.craft.api.v1.client.StardewToolUpgradeSnapshot;
import com.stardew.craft.api.v1.client.StardewQueenOfSauceSnapshot;
import com.stardew.craft.block.tv.TVChannelData;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.book.BooksellerSchedule;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.DailyInfoSyncPayload;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.TravelingCartEvents;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DailyInfoSyncEvents {
    private static final Map<UUID, StardewDailyInfoSnapshot> SENT = new HashMap<>();
    private DailyInfoSyncEvents() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0 || server.getPlayerList().getPlayers().isEmpty()) return;
        var valley = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (valley == null) return;
        var time = StardewTimeManager.get();
        var date = new StardewCalendarDate(time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
        String weather = WeatherManager.getTomorrowWeather(valley);
        boolean bookseller = BooksellerSchedule.isToday(valley);
        boolean cart = TravelingCartEvents.shouldTravelingMerchantVisitToday(date.day());
        var berry = switch (BerryBushBlock.getBloomBerry(date.season(), date.day())) {
            case SALMONBERRY -> StardewDailyInfoSnapshot.BerrySeason.SALMONBERRY;
            case BLACKBERRY -> StardewDailyInfoSnapshot.BerrySeason.BLACKBERRY;
            case NONE -> StardewDailyInfoSnapshot.BerrySeason.NONE;
        };
        var birthdays = birthdaysToday(NpcDataRegistry.events().get("npc_birthdays"), date);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // This is the same lazy per-player roll used by the TV and gameplay, never a second roll.
            double luck = PlayerStardewDataAPI.getDailyLuck(player);
            var data = PlayerStardewDataAPI.getData(player);
            var tool = toolUpgrade(data, date);
            var snapshot = new StardewDailyInfoSnapshot(player.getUUID(), date, luck, weather, berry,
                    bookseller, cart, birthdays, tool, queenOfSauce(data, date));
            if (!snapshot.equals(SENT.get(player.getUUID()))) {
                PacketDistributor.sendToPlayer(player, new DailyInfoSyncPayload(snapshot));
                SENT.put(player.getUUID(), snapshot);
            }
        }
    }

    /** Uses the TV's per-player rerun selection without watching or unlocking anything. */
    public static Optional<StardewQueenOfSauceSnapshot> queenOfSauce(
            com.stardew.craft.player.PlayerStardewData data, StardewCalendarDate date) {
        int day = Math.toIntExact(((long) date.year() - 1) * 112 + date.season() * 28 + date.day());
        int weekday = (date.day() - 1) % 7;
        if (!TVChannelData.isCookingAvailable(day, weekday)) return Optional.empty();
        String recipe = TVChannelData.getCookingRecipeIdForDay(data, day, weekday);
        return Optional.of(new StardewQueenOfSauceSnapshot(recipe, weekday == 2,
                data.isRecipeUnlocked(recipe), data.hasWatchedQueenOfSauceOnDay(day)));
    }

    public static Optional<StardewToolUpgradeSnapshot> toolUpgrade(
            com.stardew.craft.player.PlayerStardewData data, StardewCalendarDate date) {
        String rawId = data.getToolBeingUpgraded();
        if (rawId == null || rawId.isBlank()) return Optional.empty();
        var toolId = ResourceLocation.tryParse(rawId);
        if (toolId == null) return Optional.empty();
        int days = Math.max(0, data.getDaysLeftForToolUpgrade());
        return Optional.of(new StardewToolUpgradeSnapshot(toolId, days, date.plusDays(days)));
    }

    /** Reads the same data-pack registry as birthday gifts, retaining multiple birthdays on one day. */
    public static List<ResourceLocation> birthdaysToday(JsonObject root, StardewCalendarDate date) {
        if (root == null || !root.has("birthdays") || !root.get("birthdays").isJsonObject()) return List.of();
        String season = switch (date.season()) {
            case 0 -> "spring"; case 1 -> "summer"; case 2 -> "fall"; default -> "winter";
        };
        var result = new TreeSet<ResourceLocation>();
        for (var entry : root.getAsJsonObject("birthdays").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            var birthday = entry.getValue().getAsJsonObject();
            if (!birthday.has("season") || !birthday.has("day")) continue;
            try {
                if (!season.equalsIgnoreCase(birthday.get("season").getAsString())
                        || birthday.get("day").getAsInt() != date.day()) continue;
                var id = StardewNpcInteractions.normalizeNpcId(entry.getKey());
                if (id != null) result.add(id);
            } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException ignored) {
                // A malformed addon birthday must not break synchronization for every player.
            }
        }
        return List.copyOf(result);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SENT.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SENT.clear(); }
}
