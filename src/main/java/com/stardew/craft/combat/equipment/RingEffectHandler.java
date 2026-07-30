package com.stardew.craft.combat.equipment;

import com.stardew.craft.item.equipment.RingType;
import com.stardew.craft.item.equipment.CombinedRingData;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        boolean isStardewMonster = killed.getTags().stream()
                .anyMatch(tag -> tag.startsWith("sd_mob_"));
        if (!(killed instanceof Enemy) && !isStardewMonster) {
            return;
        }

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
                float luckLevel = PlayerStardewDataAPI.getLuckBuffLevel(player);
                if (player.getRandom().nextFloat() < CombatRingRules.warriorTriggerChance(luckLevel)) {
                    // SDV buff 20: +10 Attack for 5 seconds.
                    PlayerStardewDataAPI.applyAttackBuff(player, 10, 100);
                    player.playNotifySound(
                            com.stardew.craft.sound.ModSounds.WARRIOR.get(),
                            SoundSource.PLAYERS,
                            1.0f,
                            1.0f
                    );
                }
            }
            case NAPALM_RING -> {
                // Explosion at killed mob position (small, no block damage)
                player.level().explode(null, killed.getX(), killed.getY(), killed.getZ(),
                        2.0f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
            case HOT_JAVA_RING -> {
                if (player.getRandom().nextFloat() < 0.25f) {
                    killed.spawnAtLocation(new ItemStack(com.stardew.craft.item.ModItems.COFFEE.get()));
                } else if (player.getRandom().nextFloat() < 0.10f) {
                    Item espresso = BuiltInRegistries.ITEM.get(
                            ResourceLocation.fromNamespaceAndPath(
                                    com.stardew.craft.StardewCraft.MODID,
                                    "triple_shot_espresso"
                            )
                    );
                    if (espresso != Items.AIR) {
                        killed.spawnAtLocation(new ItemStack(espresso));
                    }
                }
            }
            default -> {}
        }
    }

    /**
     * Get the RingTypes currently equipped by a player. Combined rings contribute
     * both component types.
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
     * 坚韧戒指：是否应减半负面效果持续时间。
     */
    public static boolean hasSturdy(ServerPlayer player) {
        EquipmentStats stats = EquipmentResolver.getMergedStats(player);
        return stats.hasSturdy();
    }

    /**
     * 盗贼戒指：是否应额外重掷怪物基础掉落表。
     */
    public static boolean hasBurglar(ServerPlayer player) {
        EquipmentStats stats = EquipmentResolver.getMergedStats(player);
        return stats.hasBurglar();
    }
}
