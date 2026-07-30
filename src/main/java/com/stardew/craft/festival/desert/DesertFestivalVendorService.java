package com.stardew.craft.festival.desert;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DesertFestivalVendorService {
    public static final String STALL_A_POINT = "desert_festival_stall_a";
    public static final String STALL_B_POINT = "desert_festival_stall_b";
    private static final int STALL_A_ARRIVAL = 1130;
    private static final int STALL_B_ARRIVAL = 1140;
    private static final int STALL_CLOSE = 2400;
    private static final BlockPos STALL_A_STAND = new BlockPos(-215, 64, -164);
    private static final BlockPos STALL_B_STAND = new BlockPos(-238, 64, -163);

    private static final List<VendorCandidate> VENDORS = List.of(
        vendor("abigail", "Abigail"),
        vendor("alex", "Alex"),
        vendor("caroline", "Caroline"),
        vendor("clint", "Clint"),
        vendor("demetrius", "Demetrius"),
        vendor("elliott", "Elliott"),
        vendor("emily", "Emily"),
        vendor("evelyn", "Evelyn"),
        vendor("george", "George"),
        vendor("gus", "Gus"),
        vendor("haley", "Haley"),
        vendor("harvey", "Harvey"),
        vendor("jas", "Jas"),
        vendor("jodi", "Jodi"),
        vendor("kent", "Kent"),
        vendor("leah", "Leah"),
        vendor("leo", "Leo"),
        vendor("marnie", "Marnie"),
        vendor("maru", "Maru"),
        vendor("pam", "Pam"),
        vendor("penny", "Penny"),
        vendor("pierre", "Pierre"),
        vendor("robin", "Robin"),
        vendor("sam", "Sam"),
        vendor("sebastian", "Sebastian"),
        vendor("shane", "Shane"),
        vendor("vincent", "Vincent")
    );

    private DesertFestivalVendorService() {
    }

    public static boolean isSelectedVendor(String npcId) {
        return vendorSlotFor(npcId) >= 0;
    }

    public static int vendorSlotFor(String npcId) {
        if (!DesertFestivalService.isFestivalDay() || npcId == null || npcId.isBlank()) {
            return -1;
        }
        DailySelection selection = currentSelection();
        String normalized = normalize(npcId);
        if (selection.stallA().npcId().equals(normalized)) {
            return 0;
        }
        if (selection.stallB().npcId().equals(normalized)) {
            return 1;
        }
        return -1;
    }

    public static List<String> selectedVendorNpcIds() {
        if (!DesertFestivalService.isFestivalDay()) {
            return List.of();
        }
        DailySelection selection = currentSelection();
        return List.of(selection.stallA().npcId(), selection.stallB().npcId());
    }

    public static String stallPointForSlot(int slot) {
        return slot == 0 ? STALL_A_POINT : slot == 1 ? STALL_B_POINT : "";
    }

    public static int arrivalTimeForSlot(int slot) {
        return slot == 0 ? STALL_A_ARRIVAL : slot == 1 ? STALL_B_ARRIVAL : -1;
    }

    public static int closeTime() {
        return STALL_CLOSE;
    }

    public static boolean openVendorShop(ServerPlayer player, int slot) {
        if (player == null || !DesertFestivalService.isFestivalOpen()) {
            return false;
        }
        DailySelection selection = currentSelection();
        VendorCandidate vendor = slot == 0 ? selection.stallA() : slot == 1 ? selection.stallB() : null;
        if (vendor == null) {
            return false;
        }
        if (!isVendorAtStall(player, vendor.npcId(), slot)) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(vendor.shopId());
        if (shop == null) {
            return false;
        }
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(vendor.shopId(), shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            vendor.shopId(),
            PlayerStardewDataAPI.getMoney(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            List.copyOf(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static boolean tryOpenVendorShop(ServerPlayer player, String npcId) {
        int vendorSlot = vendorSlotFor(npcId);
        if (vendorSlot < 0 || stallAt(player == null ? null : player.blockPosition()) != vendorSlot) {
            return false;
        }
        return openVendorShop(player, vendorSlot);
    }

    public static boolean shouldUseVendorDialogue(ServerPlayer player, String npcId) {
        return player != null
            && DesertFestivalService.isFestivalOpen()
            && isSelectedVendor(npcId)
            && DesertConstants.isInDesertRegion(player.blockPosition());
    }

    private static DailySelection currentSelection() {
        StardewTimeManager time = StardewTimeManager.get();
        int dayOfFestival = Math.max(1, DesertFestivalService.isFestivalDay()
            ? com.stardew.craft.festival.FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID)
            : 1);
        List<VendorCandidate> candidates = VENDORS.stream()
            .filter(candidate -> ShopRegistry.get(candidate.shopId()) != null)
            .filter(candidate -> isNpcRuntimeReady(candidate.npcId()))
            .filter(candidate -> !DesertFestivalNpcVisitService.hasDaySpecificVisitToday(candidate.npcId()))
            .sorted(Comparator.comparing(VendorCandidate::npcId))
            .toList();
        if (candidates.size() < 2) {
            return new DailySelection(VENDORS.get(0), VENDORS.get(1));
        }

        Set<String> previouslyPicked = new HashSet<>();
        DailySelection selected = null;
        for (int festivalDay = 1; festivalDay <= dayOfFestival; festivalDay++) {
            List<VendorCandidate> pool = new ArrayList<>(candidates.stream()
                .filter(candidate -> !previouslyPicked.contains(candidate.npcId()))
                .toList());
            if (pool.size() < 2) {
                pool = new ArrayList<>(candidates);
            }
            Random random = new Random(selectionSeed(time.getCurrentYear(), time.getCurrentSeason(), festivalDay));
            VendorCandidate first = pool.remove(random.nextInt(pool.size()));
            VendorCandidate second = pool.remove(random.nextInt(pool.size()));
            selected = new DailySelection(first, second);
            previouslyPicked.add(first.npcId());
            previouslyPicked.add(second.npcId());
        }
        return selected == null ? new DailySelection(candidates.get(0), candidates.get(1)) : selected;
    }

    private static boolean isNpcRuntimeReady(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        com.stardew.craft.npc.data.NpcCapabilityProfile profile =
            com.stardew.craft.npc.data.NpcDataRegistry.capabilities().get(normalize(npcId));
        return profile != null
            && profile.implemented()
            && com.stardew.craft.npc.data.NpcDataRegistry.schedules().containsKey(normalize(npcId));
    }

    private static long selectionSeed(int year, int season, int festivalDay) {
        long seed = 0x5DEECE66DL;
        seed = seed * 31L + year;
        seed = seed * 31L + season;
        seed = seed * 31L + festivalDay;
        return seed;
    }

    private static VendorCandidate vendor(String npcId, String sourceName) {
        return new VendorCandidate(normalize(npcId), "DesertFestival_" + sourceName);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int stallAt(BlockPos pos) {
        if (pos == null) {
            return -1;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x >= -220 && x <= -212 && y >= 63 && y <= 67 && z >= -161 && z <= -157) {
            return 0;
        }
        if (x >= -243 && x <= -235 && y >= 63 && y <= 68 && z >= -161 && z <= -155) {
            return 1;
        }
        return -1;
    }

    private static boolean isVendorAtStall(ServerPlayer player, String npcId, int slot) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        BlockPos stand = slot == 0 ? STALL_A_STAND : slot == 1 ? STALL_B_STAND : null;
        if (stand == null) {
            return false;
        }
        AABB box = new AABB(stand).inflate(1.75D, 2.0D, 1.75D);
        return !player.serverLevel().getEntitiesOfClass(StardewNpcEntity.class, box,
            npc -> normalize(npc.getNpcId()).equals(npcId)).isEmpty();
    }

    private static boolean tryOpenAtPlayer(ServerPlayer player) {
        if (!canOpenAtPlayer(player)) {
            return false;
        }
        int slot = stallAt(player.blockPosition());
        return openVendorShop(player, slot);
    }

    public static boolean canOpenAtPlayer(ServerPlayer player) {
        if (player == null
            || player.level().dimension() != ModDimensions.STARDEW_VALLEY
            || !DesertFestivalService.isFestivalOpen()) {
            return false;
        }
        int slot = stallAt(player.blockPosition());
        if (slot < 0) {
            return false;
        }
        DailySelection selection = currentSelection();
        VendorCandidate vendor = slot == 0
            ? selection.stallA()
            : selection.stallB();
        return isVendorAtStall(player, vendor.npcId(), slot)
            && ShopRegistry.get(vendor.shopId()) != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (tryOpenAtPlayer(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (tryOpenAtPlayer(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private record VendorCandidate(String npcId, String shopId) {
    }

    private record DailySelection(VendorCandidate stallA, VendorCandidate stallB) {
    }
}
