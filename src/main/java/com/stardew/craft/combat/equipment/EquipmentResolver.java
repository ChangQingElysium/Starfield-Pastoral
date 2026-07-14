package com.stardew.craft.combat.equipment;

import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.CombinedRingItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the merged EquipmentStats from a player's currently equipped rings and boots.
 */
public final class EquipmentResolver {

    private EquipmentResolver() {}

    /**
     * Get merged EquipmentStats from a server player's equipped rings and boots.
     * Returns empty stats if nothing is equipped.
     */
    public static EquipmentStats getMergedStats(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        EquipmentStats.Builder builder = EquipmentStats.builder();

        resolveRing(data.getEquippedLeftRingStack(), builder);
        resolveRing(data.getEquippedRightRingStack(), builder);
        resolveBoots(data.getEquippedBootsStack(), builder);

        // Curios 可选兼容：合并 Curios 槽位中的装备属性
        if (com.stardew.craft.compat.CuriosCompatBridge.isCuriosLoaded()) {
            com.stardew.craft.compat.CuriosEquipmentReader.mergeFromCurios(player, builder);
        }

        return builder.build();
    }

    private static void resolveRing(ItemStack stack, EquipmentStats.Builder builder) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getItem() instanceof CombinedRingItem ring) {
            builder.merge(ring.getEquipmentStats(stack));
            return;
        }
        if (stack.getItem() instanceof StardewRingItem ring) {
            builder.merge(ring.getEquipmentStats());
        } else {
            mergeApiData(stack, builder);
        }
    }

    private static void resolveBoots(ItemStack stack, EquipmentStats.Builder builder) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getItem() instanceof StardewBootsItem boots) {
            builder.merge(boots.getEquipmentStats());
        } else {
            mergeApiData(stack, builder);
        }
    }

    private static void mergeApiData(ItemStack stack, EquipmentStats.Builder builder) {
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data == null) return;
        EquipmentStats.Builder resolved = EquipmentStats.builder()
                .defense(data.defense())
                .immunity(data.immunity())
                .attack(data.attack())
                .critChance(data.critChance())
                .critPower(data.critPower())
                .magneticRadius(data.magneticRadius())
                .knockbackBonus(data.knockbackBonus())
                .luck(data.luck())
                .lightLevel(data.lightLevel());
        for (ResourceLocation effect : data.effects()) {
            if (!effect.getNamespace().equals("stardewcraft")) continue;
            switch (effect.getPath()) {
                case "yoba_protection" -> resolved.yobaProtection(true);
                case "thorns" -> resolved.thorns(true);
                case "slime_charmer" -> resolved.slimeCharmer(true);
                case "sturdy" -> resolved.sturdy(true);
                case "burglar" -> resolved.burglar(true);
                case "protection" -> resolved.protection(true);
                case "phoenix" -> resolved.phoenix(true);
                default -> {
                }
            }
        }
        builder.merge(resolved.build());
    }
}
