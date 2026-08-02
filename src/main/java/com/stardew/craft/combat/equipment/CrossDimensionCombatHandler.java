package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.skill.handler.DwarfFortressSkillHandler;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.item.trinket.TrinketEffectHandler;
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
                || DimensionDamageMapper.isInStardewDimension(player)) {
            return;
        }

        long nowTick = player.level().getGameTime();
        NativeIncomingDamageStore.bind(
                player,
                event.getSource(),
                damageEnteringNativeProtection(
                        event.getNewDamage(),
                        event.getContainer().getReduction(
                                net.neoforged.neoforge.common.damagesource
                                        .DamageContainer.Reduction.ARMOR
                        ),
                        event.getContainer().getReduction(
                                net.neoforged.neoforge.common.damagesource
                                        .DamageContainer.Reduction.ENCHANTMENTS
                        ),
                        event.getContainer().getReduction(
                                net.neoforged.neoforge.common.damagesource
                                        .DamageContainer.Reduction.MOB_EFFECTS
                        )
                ),
                nowTick + 2L
        );

        if (!isLethalAfterAbsorption(
                        event.getNewDamage(),
                        player.getHealth(),
                        player.getAbsorptionAmount()
                )
                || event.getSource().is(
                        DamageTypeTags.BYPASSES_INVULNERABILITY
                )) {
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
                || DimensionDamageMapper.isInStardewDimension(player)) {
            return;
        }

        Float damageEnteringProtection = NativeIncomingDamageStore.consume(
                player,
                event.getSource(),
                player.level().getGameTime()
        );
        if (event.getNewDamage() <= 0.0f) return;

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        TrinketEffectHandler.onReceiveDamage(
                player,
                fairyDamageOnStardewScale(event.getNewDamage())
        );
        int reactiveDamage = reactiveSkillDamage(event.getNewDamage());
        long nowTick = player.level().getGameTime();
        SteelSpineFurySkillHandler.onDamageTaken(
                player,
                nowTick,
                reactiveDamage
        );
        DwarfFortressSkillHandler.onDamageTaken(player, nowTick);
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
                    (int) Math.ceil(damageEnteringProtection != null
                            ? damageEnteringProtection
                            : event.getNewDamage()),
                    (int) Math.ceil(event.getNewDamage()),
                    equipment.getThornsCount()
            );
            attacker.hurt(player.damageSources().thorns(player), reflectedDamage);
        }
    }

    static int fairyDamageOnStardewScale(float finalMinecraftDamage) {
        if (finalMinecraftDamage <= 0.0F) {
            return 0;
        }
        return Math.max(
                1,
                (int) Math.ceil(
                        finalMinecraftDamage
                                * DimensionDamageMapper.getHealthRatio()
                )
        );
    }

    /**
     * Reactive weapon skills use the actual native health loss at a 1:1
     * boundary; the Stardew 100:20 health-display ratio is not weapon damage.
     */
    static int reactiveSkillDamage(float finalMinecraftDamage) {
        if (finalMinecraftDamage <= 0.0F) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(finalMinecraftDamage));
    }

    static boolean isLethalAfterAbsorption(
            float damageBeforeAbsorption,
            float currentHealth,
            float absorptionHealth
    ) {
        if (!Float.isFinite(damageBeforeAbsorption)
                || !Float.isFinite(currentHealth)
                || !Float.isFinite(absorptionHealth)
                || damageBeforeAbsorption <= 0.0F
                || currentHealth <= 0.0F) {
            return false;
        }
        float healthDamage = Math.max(
                0.0F,
                damageBeforeAbsorption - Math.max(0.0F, absorptionHealth)
        );
        return healthDamage >= currentHealth;
    }

    static float damageEnteringNativeProtection(
            float damageAfterProtection,
            float armorReduction,
            float enchantmentReduction,
            float mobEffectReduction
    ) {
        if (!Float.isFinite(damageAfterProtection)
                || !Float.isFinite(armorReduction)
                || !Float.isFinite(enchantmentReduction)
                || !Float.isFinite(mobEffectReduction)) {
            throw new IllegalArgumentException(
                    "Native damage breakdown values must be finite"
            );
        }
        return Math.max(0.0F, damageAfterProtection)
                + Math.max(0.0F, armorReduction)
                + Math.max(0.0F, enchantmentReduction)
                + Math.max(0.0F, mobEffectReduction);
    }

    public static void clear(ServerPlayer player) {
        NativeIncomingDamageStore.clear(player);
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
