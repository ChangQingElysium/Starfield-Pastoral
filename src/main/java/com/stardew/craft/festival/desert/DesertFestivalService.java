package com.stardew.craft.festival.desert;

import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.FestivalCurrencyHudStatePayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DesertFestivalService {
    public static final String FESTIVAL_ID = "DesertFestival";
    public static final String EGG_SHOP_ID = "DesertFestival_EggShop";
    public static final String EGG_SHOP_TARGET_ID = "desert_festival_egg_shop";
    public static final String EGG_SHOP_MARKER_TAG = "sdv_festival_marker:desert_egg_shop";
    public static final BlockPos EGG_SHOP_INTERACTION_POS = new BlockPos(-198, 65, -200);
    private static final Set<UUID> CURRENCY_HUD_PLAYERS = new HashSet<>();

    private DesertFestivalService() {
    }

    public static boolean isFestivalDay() {
        return FestivalService.isPassiveFestivalDay(FESTIVAL_ID);
    }

    public static boolean isFestivalOpen() {
        return FestivalService.isPassiveFestivalOpen(FESTIVAL_ID);
    }

    public static void syncCurrencyHud(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        boolean festivalOpen = isFestivalOpen();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            boolean shouldShow = festivalOpen
                && player.level().dimension() == ModDimensions.STARDEW_VALLEY
                && DesertConstants.isInDesertRegion(player.blockPosition());
            boolean showing = CURRENCY_HUD_PLAYERS.contains(player.getUUID());
            if (shouldShow == showing) {
                continue;
            }
            if (shouldShow) {
                CURRENCY_HUD_PLAYERS.add(player.getUUID());
            } else {
                CURRENCY_HUD_PLAYERS.remove(player.getUUID());
            }
            PacketDistributor.sendToPlayer(player, new FestivalCurrencyHudStatePayload(
                shouldShow ? FestivalCurrencyHudStatePayload.CALICO_EGG : FestivalCurrencyHudStatePayload.NONE));
        }
        CURRENCY_HUD_PLAYERS.retainAll(online);
    }

    public static void clearCurrencyHud(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player,
                    new FestivalCurrencyHudStatePayload(FestivalCurrencyHudStatePayload.NONE));
            }
        }
        CURRENCY_HUD_PLAYERS.clear();
    }

    public static int countEggs(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return player.getInventory().countItem(ModItems.CALICO_EGG.get());
    }

    public static int giveEggs(ServerPlayer player, int count) {
        if (player == null || count <= 0) {
            return 0;
        }
        ItemStack eggs = new ItemStack(ModItems.CALICO_EGG.get(), count);
        ItemStack hudStack = eggs.copy();
        if (!player.getInventory().add(eggs)) {
            player.drop(eggs, false);
        }
        player.inventoryMenu.broadcastChanges();
        ItemPickupHudPacket.sendTo(player, hudStack, count, false);
        return count;
    }

    public static boolean consumeEggs(ServerPlayer player, int count) {
        if (player == null || count <= 0) {
            return true;
        }
        if (countEggs(player) < count) {
            return false;
        }
        removeEggs(player, count);
        return true;
    }

    public static int clearEggs(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        int removed = removeEggs(player.getInventory(), Integer.MAX_VALUE);
        removed += removeEggs(player.getEnderChestInventory(), Integer.MAX_VALUE);
        removed += removeEggsFromOpenMenu(player);
        if (removed > 0) {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    public static void cleanupExpiredEggs(MinecraftServer server) {
        if (server == null || isFestivalDay()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearEggs(player);
        }
    }

    public static void cleanupExpiredEggsOnLogin(ServerPlayer player) {
        if (!isFestivalDay()) {
            clearEggs(player);
        }
    }

    public static boolean openEggShop(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!isFestivalOpen()) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(EGG_SHOP_ID);
        if (shop == null) {
            return false;
        }
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(EGG_SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            EGG_SHOP_ID,
            PlayerStardewDataAPI.getMoney(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            List.copyOf(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static void placeEggShopInteraction(ServerLevel level) {
        setupFestivalInteractions(level);
    }

    public static void setupFestivalInteractions(ServerLevel level) {
        if (level == null) {
            return;
        }
        configureInteraction(level, EGG_SHOP_INTERACTION_POS, EGG_SHOP_TARGET_ID, EGG_SHOP_MARKER_TAG);
        removeInteraction(level, DesertFestivalRaceService.RACE_MAN_TARGET_ID, DesertFestivalRaceService.RACE_MAN_INTERACTION_POS);
        removeInteraction(level, DesertFestivalRaceService.SHADY_GUY_TARGET_ID, DesertFestivalRaceService.SHADY_GUY_INTERACTION_POS);
        configureInteractionArea(level, DesertFestivalRaceService.RACE_MAN_INTERACTION_MIN,
            DesertFestivalRaceService.RACE_MAN_INTERACTION_MAX,
            DesertFestivalRaceService.RACE_MAN_TARGET_ID, DesertFestivalRaceService.RACE_MAN_MARKER_TAG);
        configureInteractionArea(level, DesertFestivalRaceService.SHADY_GUY_INTERACTION_MIN,
            DesertFestivalRaceService.SHADY_GUY_INTERACTION_MAX,
            DesertFestivalRaceService.SHADY_GUY_TARGET_ID, DesertFestivalRaceService.SHADY_GUY_MARKER_TAG);
        configureInteractionArea(level, DesertFestivalSpecialInteractionService.SCHOLAR_INTERACTION_MIN,
            DesertFestivalSpecialInteractionService.SCHOLAR_INTERACTION_MAX,
            DesertFestivalSpecialInteractionService.SCHOLAR_TARGET_ID, DesertFestivalSpecialInteractionService.SCHOLAR_MARKER_TAG);
        configureInteraction(level, DesertFestivalWillyFishingService.INTERACTION_POS,
            DesertFestivalWillyFishingService.TARGET_ID, DesertFestivalWillyFishingService.MARKER_TAG);
        removeInteraction(level, DesertFestivalCookService.TARGET_ID, DesertFestivalCookService.INTERACTION_MIN);
        configureInteractionArea(level, DesertFestivalCookService.INTERACTION_MIN,
            DesertFestivalCookService.INTERACTION_MAX,
            DesertFestivalCookService.TARGET_ID, DesertFestivalCookService.MARKER_TAG);
        removeInteraction(level, DesertFestivalSpecialInteractionService.WARPER_TARGET_ID,
            DesertFestivalSpecialInteractionService.WARPER_INTERACTION_POS);
        DesertFestivalSpecialInteractionService.spawnWarperInteraction(level);
        DesertFestivalSpecialInteractionService.spawnWarperDisplay(level);
        DesertFestivalSpecialInteractionService.syncFestivalTravelingCart(level);
    }

    public static void removeEggShopInteraction(ServerLevel level) {
        cleanupFestivalInteractions(level);
    }

    public static void cleanupFestivalInteractions(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (level.getBlockState(EGG_SHOP_INTERACTION_POS).is(ModBlocks.PORTAL_TRIGGER.get())) {
            level.removeBlock(EGG_SHOP_INTERACTION_POS, false);
        }
        removeInteractionArea(level, DesertFestivalRaceService.RACE_MAN_INTERACTION_MIN, DesertFestivalRaceService.RACE_MAN_INTERACTION_MAX);
        removeInteractionArea(level, DesertFestivalRaceService.SHADY_GUY_INTERACTION_MIN, DesertFestivalRaceService.SHADY_GUY_INTERACTION_MAX);
        removeInteractionArea(level, DesertFestivalSpecialInteractionService.SCHOLAR_INTERACTION_MIN,
            DesertFestivalSpecialInteractionService.SCHOLAR_INTERACTION_MAX);
        removeInteraction(level, DesertFestivalWillyFishingService.TARGET_ID,
            DesertFestivalWillyFishingService.INTERACTION_POS);
        removeInteractionArea(level, DesertFestivalCookService.INTERACTION_MIN,
            DesertFestivalCookService.INTERACTION_MAX);
        removeInteraction(level, DesertFestivalCookService.TARGET_ID,
            DesertFestivalCookService.INTERACTION_MIN);
        removeInteraction(level, DesertFestivalSpecialInteractionService.WARPER_TARGET_ID,
            DesertFestivalSpecialInteractionService.WARPER_INTERACTION_POS);
        DesertFestivalSpecialInteractionService.removeWarperInteraction(level);
        DesertFestivalSpecialInteractionService.removeWarperDisplay(level);
        DesertFestivalSpecialInteractionService.removeFestivalTravelingCart(level);
        removeInteraction(level, DesertFestivalRaceService.RACE_MAN_TARGET_ID, DesertFestivalRaceService.RACE_MAN_INTERACTION_POS);
        removeInteraction(level, DesertFestivalRaceService.SHADY_GUY_TARGET_ID, DesertFestivalRaceService.SHADY_GUY_INTERACTION_POS);
    }

    public static int forceRefreshNpcSchedules(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
        int moved = 0;
        for (String npcId : NpcDataRegistry.schedules().keySet()) {
            if (NpcSpawnManager.forceNpcToCurrentSchedule(level, npcId)) {
                moved++;
            }
        }
        return moved;
    }

    private static void configureInteractionArea(ServerLevel level, BlockPos min, BlockPos max, String targetId, String markerTag) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            configureInteraction(level, pos.immutable(), targetId, markerTag);
        }
    }

    private static void removeInteractionArea(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void removeInteraction(ServerLevel level, String targetId, BlockPos fallbackPos) {
        if (level.getBlockState(fallbackPos).is(ModBlocks.PORTAL_TRIGGER.get())) {
            level.removeBlock(fallbackPos, false);
        }
        int range = 96;
        BlockPos center = fallbackPos;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -4, -range), center.offset(range, 4, range))) {
            if (!level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) continue;
            if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void configureInteraction(ServerLevel level, BlockPos pos, String targetId, String markerTag) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(targetId, markerTag);
        }
    }

    private static int removeEggs(ServerPlayer player, int count) {
        return removeEggs(player.getInventory(), count);
    }

    private static int removeEggs(Container container, int count) {
        Item eggItem = ModItems.CALICO_EGG.get();
        int remaining = count;
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !stack.is(eggItem)) {
                continue;
            }
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            removed += take;
            remaining -= take;
        }
        if (removed > 0) {
            container.setChanged();
        }
        return removed;
    }

    private static int removeEggsFromOpenMenu(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            return 0;
        }
        Item eggItem = ModItems.CALICO_EGG.get();
        int removed = 0;
        for (Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.is(eggItem)) {
                continue;
            }
            removed += stack.getCount();
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
        }
        return removed;
    }
}
