package com.stardew.craft.lostandfound;

import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.GlobalHudMessagePayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative lost-and-found flow, modelled after FarmerTeam.returnedDonations. */
public final class LostAndFoundService {
    public static final String SHOP_ID_PREFIX = "LewisLostAndFound:";

    private LostAndFoundService() {
    }

    public static void queueForPlayer(ServerLevel level, UUID playerId, List<ItemStack> stacks, int availableDay) {
        if (level == null || playerId == null || stacks == null || stacks.isEmpty()) {
            return;
        }
        queueForFarm(level, farmId(playerId), stacks, availableDay);
    }

    public static void queueDisplays(ServerLevel level, Map<UUID, List<ItemStack>> displays) {
        if (level == null || displays == null || displays.isEmpty()) {
            return;
        }
        int availableDay = StardewTimeManager.get().getAbsoluteDay() + 1;
        for (Map.Entry<UUID, List<ItemStack>> display : displays.entrySet()) {
            queueForPlayer(level, display.getKey(), display.getValue(), availableDay);
        }
    }

    public static void open(ServerPlayer player) {
        UUID farmId = farmId(player.getUUID());
        LostAndFoundData data = LostAndFoundData.get(player.serverLevel());
        LostAndFoundData.TeamReturns returns = data.returnsIfPresent(farmId);
        int today = StardewTimeManager.get().getAbsoluteDay();
        List<ShopItemEntry> items = new ArrayList<>();
        long revision = returns == null ? 0L : returns.revision();
        if (returns != null) {
            for (LostAndFoundData.StoredReturn stored : returns.items()) {
                if (stored.availableDay() <= today && !stored.stack().isEmpty()) {
                    ItemStack stack = stored.stack();
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    items.add(new ShopItemEntry(
                        itemId,
                        stack.getHoverName().getString(),
                        "",
                        0,
                        1,
                        "",
                        0,
                        Set.of(),
                        1,
                        0,
                        null,
                        -1,
                        0,
                        stack.getCount()));
                }
            }
        }

        String shopId = SHOP_ID_PREFIX + farmId + ":" + revision;
        String dialogue = items.isEmpty()
            ? "stardewcraft.lewis.lost_and_found.empty_dialogue"
            : "stardewcraft.lewis.lost_and_found.dialogue";
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            shopId,
            0,
            items,
            "lewis",
            dialogue,
            List.of()));
    }

    public static void claim(ServerPlayer player, String shopId, int itemIndex, String expectedItemId) {
        ParsedShopId parsed = parseShopId(shopId);
        UUID currentFarmId = farmId(player.getUUID());
        if (parsed == null || !parsed.farmId.equals(currentFarmId)) {
            open(player);
            return;
        }

        LostAndFoundData data = LostAndFoundData.get(player.serverLevel());
        LostAndFoundData.TeamReturns returns = data.returnsIfPresent(currentFarmId);
        int today = StardewTimeManager.get().getAbsoluteDay();
        if (returns == null || returns.revision() != parsed.revision) {
            open(player);
            return;
        }

        List<LostAndFoundData.StoredReturn> available = returns.items().stream()
            .filter(stored -> stored.availableDay() <= today && !stored.stack().isEmpty())
            .toList();
        if (itemIndex < 0 || itemIndex >= available.size()) {
            open(player);
            return;
        }

        LostAndFoundData.StoredReturn selected = available.get(itemIndex);
        String actualItemId = BuiltInRegistries.ITEM.getKey(selected.stack().getItem()).toString();
        if (!actualItemId.equals(expectedItemId)) {
            open(player);
            return;
        }

        ItemStack toInsert = selected.stack().copy();
        int originalCount = toInsert.getCount();
        player.getInventory().add(toInsert);
        int inserted = originalCount - toInsert.getCount();
        if (inserted <= 0) {
            open(player);
            return;
        }

        if (toInsert.isEmpty()) {
            returns.items().remove(selected);
        } else {
            selected.setStack(toInsert.copy());
        }
        returns.changed();
        data.removeIfEmpty(currentFarmId);
        data.setDirty();
        player.inventoryMenu.broadcastChanges();
        open(player);
    }

    public static void onNewDay(ServerLevel level) {
        if (level == null) {
            return;
        }
        int today = StardewTimeManager.get().getAbsoluteDay();
        LostAndFoundData data = LostAndFoundData.get(level);
        boolean changed = false;
        for (Map.Entry<UUID, LostAndFoundData.TeamReturns> entry : data.allReturns().entrySet()) {
            boolean hasNewItems = false;
            for (LostAndFoundData.StoredReturn stored : entry.getValue().items()) {
                if (stored.availableDay() <= today && !stored.announced()) {
                    stored.markAnnounced();
                    hasNewItems = true;
                    changed = true;
                }
            }
            if (hasNewItems) {
                notifyFarm(level, entry.getKey());
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    private static void queueForFarm(ServerLevel level, UUID farmId, List<ItemStack> stacks, int availableDay) {
        LostAndFoundData data = LostAndFoundData.get(level);
        LostAndFoundData.TeamReturns returns = data.returns(farmId);
        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                returns.items().add(new LostAndFoundData.StoredReturn(stack.copy(), availableDay, false));
                changed = true;
            }
        }
        if (changed) {
            returns.changed();
            data.setDirty();
        } else {
            data.removeIfEmpty(farmId);
        }
    }

    private static UUID farmId(UUID playerId) {
        UUID owner = FarmInstanceRegistry.get().getOwnerForPlayer(playerId);
        return owner == null ? playerId : owner;
    }

    private static void notifyFarm(ServerLevel level, UUID farmId) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(farmId);
        List<UUID> farmers = farm == null ? List.of(farmId) : farm.getAllFarmers();
        for (UUID farmerId : farmers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(farmerId);
            if (player != null) {
                GlobalHudMessagePayload.sendTo(player,
                    Component.translatable("stardewcraft.lewis.lost_and_found.new_items"));
            }
        }
    }

    private static ParsedShopId parseShopId(String shopId) {
        if (shopId == null || !shopId.startsWith(SHOP_ID_PREFIX)) {
            return null;
        }
        String[] parts = shopId.substring(SHOP_ID_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ParsedShopId(UUID.fromString(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ParsedShopId(UUID farmId, long revision) {
    }
}
