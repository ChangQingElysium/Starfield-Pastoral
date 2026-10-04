package com.stardew.craft.shop;

import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.GeodeCrackResultPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative processing of the shared builtin and addon geode catalog. */
@SuppressWarnings("null")
public class GeodeLootService {

    private static final int GEODE_COST = 25;

    /**
     * Pending treasure per player — SDV only gives treasure when geodeAnimationTimer <= 0.
     * Stored here until the client sends GeodeClaimPayload after animation finishes.
     */
    private static final Map<UUID, ItemStack> pendingTreasure = new ConcurrentHashMap<>();

    public static void handleGeodeCrack(ServerPlayer player, int slot) {
        if (pendingTreasure.containsKey(player.getUUID())) return;
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

        ItemStack geodeStack = player.getInventory().getItem(slot);
        if (geodeStack.isEmpty()) return;

        String geodeType = getGeodeType(geodeStack);
        if (geodeType == null) return;
        ResourceLocation customId = geodeType.indexOf(':') >= 0 ? ResourceLocation.tryParse(geodeType) : null;
        if (customId != null && !GeodeDropData.isAvailable(customId, player)) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        if (money < GEODE_COST) return;

        int freeSlots = countFreeSlots(player);
        if (freeSlots < 1) return;

        // Query failures must not consume an addon item or charge the player.
        var effects = new ArrayList<com.stardew.craft.api.v1.action.StardewAction>();
        // GeodeMenu increments GeodesCracked before the treasure is rolled, so the roll sees the post-increment value.
        int crackedAfter = crackedCount(player) + 1;
        ItemStack treasure = getTreasureFromGeode(geodeType, player, seededRandom(geodeType, player, crackedAfter),
                effects::add, Map.of(STAT_GEODES_CRACKED_KEY, (double) crackedAfter));
        if (treasure.isEmpty()) return;
        PlayerDataManager.getPlayerData(player).incrementStat(STAT_GEODES_CRACKED, 1);

        // Deduct cost (SDV: Game1.player.Money -= 25)
        PlayerStardewDataAPI.removeMoney(player, GEODE_COST);

        // Consume one geode
        geodeStack.shrink(1);
        com.stardew.craft.loot.LootEffects.commit(player, effects);

        // SDV: treasure is given to player ONLY when geodeAnimationTimer <= 0 in update().
        // Store pending treasure — the client will send GeodeClaimPayload after animation finishes.
        if (!treasure.isEmpty()) {
            pendingTreasure.put(player.getUUID(), treasure.copy());
        }

        // Sync the geode consumption to client (geode stack shrunk above).
        player.inventoryMenu.broadcastChanges();

        int newMoney = PlayerStardewDataAPI.getMoney(player);
        String treasureId = BuiltInRegistries.ITEM.getKey(treasure.getItem()).toString();
        PacketDistributor.sendToPlayer(player,
            new GeodeCrackResultPayload(treasureId, customId == null ? geodeType
                    : GeodeDropData.snapshot().definitions().get(customId).animation(), newMoney));
    }

    /**
     * Called when client sends GeodeClaimPayload (animation finished).
     * Matches SDV: Game1.player.addItemToInventoryBool(geodeTreasure) at timer <= 0.
     */
    public static void handleGeodeClaim(ServerPlayer player) {
        ItemStack treasure = pendingTreasure.remove(player.getUUID());
        if (treasure != null && !treasure.isEmpty()) {
            ItemStack hudStack = treasure.copy();
            if (!player.getInventory().add(treasure)) {
                player.drop(treasure, false);
            }
            ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Clean up pending treasure on player disconnect.
     * If the player had a pending geode treasure, give it back so it's not lost.
     */
    public static void onPlayerLogout(ServerPlayer player) {
        ItemStack treasure = pendingTreasure.remove(player.getUUID());
        if (treasure != null && !treasure.isEmpty()) {
            if (!player.getInventory().add(treasure)) {
                player.drop(treasure, false);
            }
        }
    }

    /**
     * SDV Utility.getTreasureFromGeode() parity.
     */
    private static ItemStack getTreasureFromGeode(String geodeType, ServerPlayer player) {
        // Geode Crusher: Utility.getTreasureFromGeode without a GeodesCracked increment.
        return getTreasureFromGeode(geodeType, player, seededRandom(geodeType, player, crackedCount(player)),
                action -> {}, Map.of());
    }

    private static ItemStack getTreasureFromGeode(String geodeType, ServerPlayer player, Random r,
            java.util.function.Consumer<com.stardew.craft.api.v1.action.StardewAction> effects,
            Map<String, Double> parameters) {
        // SDV: two prewarm rounds of r.Next(1, 10) draws on the seeded generator.
        for (int round = 0; round < 2; round++) {
            int prewarm = r.nextInt(9) + 1;
            for (int i = 0; i < prewarm; i++) r.nextDouble();
        }

        ResourceLocation id = ResourceLocation.tryParse(geodeType);
        return id == null ? ItemStack.EMPTY : GeodeDropData.roll(id, player, r, effects, parameters).orElse(ItemStack.EMPTY);
    }

    private static final String STAT_GEODES_CRACKED = "GeodesCracked";
    private static final String STAT_GEODES_CRACKED_KEY = "stat:" + STAT_GEODES_CRACKED;
    private static final String STAT_MYSTERY_BOXES_OPENED = "MysteryBoxesOpened";

    private static int crackedCount(ServerPlayer player) {
        return player == null ? 0 : PlayerDataManager.getPlayerData(player).getStat(STAT_GEODES_CRACKED);
    }

    /**
     * Utility.getTreasureFromGeode seeds its generator from the cracked-geode counter (the mystery box counter for
     * mystery boxes), the world and the player, so the result is reproducible across save and reload.
     */
    private static Random seededRandom(String geodeType, ServerPlayer player, int geodesCracked) {
        long counter = geodeType.contains("mystery_box") && player != null
                ? PlayerDataManager.getPlayerData(player).getStat(STAT_MYSTERY_BOXES_OPENED)
                : geodesCracked;
        long worldHalf = player == null ? 0L : player.serverLevel().getSeed() / 2L;
        long playerHalf = player == null ? 0L : player.getUUID().getLeastSignificantBits() / 2L;
        long seed = counter * 0x9E3779B97F4A7C15L;
        seed = (seed ^ (seed >>> 31)) + worldHalf * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 29)) + playerHalf * 0x94D049BB133111EBL;
        return new Random(seed ^ (seed >>> 32));
    }

    public static boolean isGeodeCrusherInput(ItemStack stack) {
        ResourceLocation id = GeodeDropData.definitionFor(stack);
        return id != null && GeodeDropData.snapshot().definitions().get(id).crusherAllowed();
    }

    public static ItemStack getTreasureForGeodeCrusher(ItemStack stack, ServerPlayer player) {
        String geodeType = getGeodeType(stack);
        if (!isGeodeCrusherInput(stack) || geodeType == null) {
            return ItemStack.EMPTY;
        }
        return getTreasureFromGeode(geodeType, player);
    }

    /** The blacksmith menu and its inventory picker use the same inputs as server processing. */
    public static boolean isClintInput(ItemStack stack) {
        return getGeodeType(stack) != null;
    }

    public static List<ResourceLocation> clintInputs(ServerPlayer player) {
        var inputs = new LinkedHashSet<ResourceLocation>();
        GeodeDropData.snapshot().definitions().forEach((id, definition) -> {
            if (GeodeDropData.isAvailable(id, player)) inputs.addAll(GeodeDropData.inputsFor(id));
        });
        return List.copyOf(inputs);
    }

    private static String getGeodeType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation custom = GeodeDropData.definitionFor(stack);
        if (custom != null) return custom.toString();
        return null;
    }

    private static int countFreeSlots(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) count++;
        }
        return count;
    }
}
