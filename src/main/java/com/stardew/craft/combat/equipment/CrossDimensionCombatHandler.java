package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Maps reactive Stardew equipment effects onto Minecraft's native health and
 * armor pipeline outside the Stardew dimensions.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CrossDimensionCombatHandler {
    private CrossDimensionCombatHandler() {
    }

    public static boolean tryBlockIncoming(
            ServerPlayer player,
            LivingIncomingDamageEvent event
    ) {
        if (DimensionDamageMapper.isInStardewDimension(player)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        Entity attacker = event.getSource().getEntity();
        if (equipment.hasSlimeCharmer() && isSlime(attacker)) {
            event.setAmount(0.0f);
            return true;
        }

        long nowTick = player.level().getGameTime();
        if (YobaProtectionState.isActive(player, nowTick)) {
            event.setAmount(0.0f);
            return true;
        }

        if (equipment.hasYobaProtection()) {
            int scaledHealth = CombatRingRules.healthOnStardewScale(
                    player.getHealth(),
                    player.getMaxHealth()
            );
            float chance = CombatRingRules.yobaProtectionChance(
                    scaledHealth,
                    PlayerStardewDataAPI.getLuckBuffLevel(player)
            );
            if (player.getRandom().nextFloat() < chance) {
                YobaProtectionState.start(player, nowTick);
                player.playNotifySound(
                        ModSounds.YOBA.get(),
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f
                );
                event.setAmount(0.0f);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || DimensionDamageMapper.isInStardewDimension(player)
                || event.getNewDamage() < player.getHealth()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        if (!equipment.hasPhoenix()) {
            return;
        }

        long currentDay = StardewTimeManager.get().getAbsoluteDay();
        if (data.getLastPhoenixReviveDay() == currentDay) {
            return;
        }

        data.setLastPhoenixReviveDay(currentDay);
        event.setNewDamage(0.0f);
        player.setHealth(CombatRingRules.phoenixMinecraftHealth(
                player.getMaxHealth(),
                equipment.getPhoenixCount(),
                DimensionDamageMapper.getHealthRatio()
        ));
        player.invulnerableTime = Math.max(
                player.invulnerableTime,
                CombatRingRules.minecraftInvulnerabilityTicks(
                        20,
                        equipment.getProtectionCount()
                )
        );
        PlayerDataEventHandler.syncPlayerData(player, data);
        player.playNotifySound(
                ModSounds.YOBA.get(),
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || DimensionDamageMapper.isInStardewDimension(player)
                || event.getNewDamage() <= 0.0f) {
            return;
        }

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        player.invulnerableTime = Math.max(
                player.invulnerableTime,
                CombatRingRules.minecraftInvulnerabilityTicks(
                        event.getPostAttackInvulnerabilityTicks(),
                        equipment.getProtectionCount()
                )
        );

        if (equipment.hasThorns()
                && event.getSource().getEntity() instanceof Mob attacker) {
            int reflectedDamage = CombatRingRules.thornsDamage(
                    (int) Math.ceil(event.getOriginalDamage()),
                    (int) Math.ceil(event.getNewDamage()),
                    equipment.getThornsCount()
            );
            attacker.hurt(player.damageSources().thorns(player), reflectedDamage);
        }
    }

    private static boolean isSlime(Entity entity) {
        if (entity == null) {
            return false;
        }
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return entityType.contains("slime")
                || entityType.contains("green_slime")
                || entityType.contains("frost_jelly")
                || entityType.contains("sludge");
    }
}
