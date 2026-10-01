package com.stardew.craft.shop;

import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.GeodeCrackResultPayload;
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
        ItemStack treasure = getTreasureFromGeode(geodeType, player, new Random(), effects::add);
        if (treasure.isEmpty()) return;

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
        return getTreasureFromGeode(geodeType, player, new Random());
    }

    private static ItemStack getTreasureFromGeode(String geodeType, ServerPlayer player, Random r) {
        return getTreasureFromGeode(geodeType, player, r, action -> {});
    }

    private static ItemStack getTreasureFromGeode(String geodeType, ServerPlayer player, Random r,
            java.util.function.Consumer<com.stardew.craft.api.v1.action.StardewAction> effects) {
        // SDV: prewarm random (mimics seed-based RNG warm-up)
        int prewarm = r.nextInt(9) + 1;
        for (int i = 0; i < prewarm; i++) r.nextDouble();

        ResourceLocation id = ResourceLocation.tryParse(geodeType);
        return id == null ? ItemStack.EMPTY : GeodeDropData.roll(id, player, r, effects).orElse(ItemStack.EMPTY);
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
