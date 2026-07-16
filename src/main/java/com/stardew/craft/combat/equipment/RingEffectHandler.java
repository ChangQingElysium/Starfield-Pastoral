package com.stardew.craft.combat.equipment;

import com.stardew.craft.item.equipment.RingType;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerDataEventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles ring-specific on-kill and on-hit effects.
 * Registered as a Forge event subscriber.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.stardew.craft.StardewCraft.MODID)
@SuppressWarnings("null")
public class RingEffectHandler {

    @SubscribeEvent
    public static void onMobKilled(LivingDeathEvent event) {
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity killed = event.getEntity();
        if (killed instanceof ServerPlayer) return; // don't trigger on player kills

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        List<RingType> equippedRings = getEquippedRingTypes(data);

        for (RingType ring : equippedRings) {
            applyOnKillEffect(player, data, ring, killed);
        }
    }

    private static void applyOnKillEffect(ServerPlayer player, PlayerStardewData data, RingType ring, LivingEntity killed) {
        switch (ring) {
            case VAMPIRE_RING -> {
                // +2 HP on monster kill
                int newHealth = Math.min(data.getHealth() + 2, data.getMaxHealth());
                data.setHealth(newHealth);
                PlayerDataEventHandler.syncPlayerData(player, data);
            }
            case SOUL_SAPPER_RING -> {
                // +4 stamina (energy) on monster kill
                float newEnergy = Math.min(data.getEnergy() + 4.0f, data.getMaxEnergy());
                data.setEnergy(newEnergy);
                PlayerDataEventHandler.syncPlayerData(player, data);
            }
            case SAVAGE_RING -> {
                // +2 speed buff for 3 seconds on monster kill (use MC speed effect)
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 1, false, true));
            }
            case WARRIOR_RING -> {
                // 10% chance: warrior energy (attack buff) for 5 seconds
                if (player.getRandom().nextFloat() < 0.10f) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 100, 0, false, true));
                }
            }
            case NAPALM_RING -> {
                // Explosion at killed mob position (small, no block damage)
                player.level().explode(null, killed.getX(), killed.getY(), killed.getZ(),
                        2.0f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
            case HOT_JAVA_RING -> {
                // Drop coffee on monster kill (25% chance)
                if (player.getRandom().nextFloat() < 0.25f) {
                    // TODO: drop coffee item once beverages are implemented
                }
            }
            default -> {}
        }
    }

    /**
     * Get the RingTypes currently equipped by a player (0-2 entries).
     */
    public static List<RingType> getEquippedRingTypes(PlayerStardewData data) {
        List<RingType> result = new ArrayList<>(2);
        addRingType(data.getEquippedLeftRing(), result);
        addRingType(data.getEquippedRightRing(), result);
        return result;
    }

    private static void addRingType(String itemId, List<RingType> list) {
        if (itemId == null || itemId.isEmpty()) return;
        if (CombinedRingData.isEncodedEquipmentSlot(itemId)) {
            for (net.minecraft.world.item.ItemStack ringStack : CombinedRingData.splitEquipmentSlot(itemId)) {
                if (ringStack.getItem() instanceof StardewRingItem ring) {
                    list.add(ring.getRingType());
                }
            }
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item instanceof StardewRingItem ring) {
            list.add(ring.getRingType());
        }
    }

    /**
     * Calculate total attack multiplier from equipped rings (Ruby Ring = +10%, Iridium Band = +10%).
     * Returns 1.0 if no multiplier rings are equipped.
     */
    public static float getAttackMultiplier(PlayerStardewData data) {
        float mult = 1.0f;
        mult += getAttackMultFromRing(data.getEquippedLeftRing());
        mult += getAttackMultFromRing(data.getEquippedRightRing());
        return mult;
    }

    private static float getAttackMultFromRing(String itemId) {
        if (itemId == null || itemId.isEmpty()) return 0f;
        if (CombinedRingData.isEncodedEquipmentSlot(itemId)) {
            float total = 0f;
            for (net.minecraft.world.item.ItemStack ringStack : CombinedRingData.splitEquipmentSlot(itemId)) {
                if (ringStack.getItem() instanceof StardewRingItem ring) {
                    total += ring.getRingType().getAttackMultiplier();
                }
            }
            return total;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return 0f;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item instanceof StardewRingItem ring) {
            return ring.getRingType().getAttackMultiplier();
        }
        return 0f;
    }

    /**
     * 坚韧戒指：是否应减半负面效果持续时间。
     */
    public static boolean hasSturdy(ServerPlayer player) {
        EquipmentStats stats = EquipmentResolver.getMergedStats(player);
        return stats.hasSturdy();
    }

    /**
     * 盗贼戒指：是否应加倍怪物掉落。
     */
    public static boolean hasBurglar(ServerPlayer player) {
        EquipmentStats stats = EquipmentResolver.getMergedStats(player);
        return stats.hasBurglar();
    }
}
