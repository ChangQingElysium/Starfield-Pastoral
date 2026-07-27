package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.combat.StardewCombatDamageContext;
import com.stardew.craft.api.v1.combat.StardewCombatKillContext;
import com.stardew.craft.api.v1.internal.combat.StardewCombatRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Projects NeoForge combat events into the ordered addon extension API.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CombatExtensionEvents {
    private CombatExtensionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event
    ) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel)) {
            return;
        }
        float amount = StardewCombatRegistry.applyDamageModifiers(
                new StardewCombatDamageContext(
                        target,
                        event.getSource().getEntity(),
                        event.getSource(),
                        target.level().dimension(),
                        Math.max(0.0F, event.getAmount())));
        event.setAmount(amount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()
                || event.getEntity() instanceof ServerPlayer
                || !(event.getEntity().level()
                instanceof ServerLevel)) {
            return;
        }
        if (!(event.getSource().getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        StardewCombatRegistry.announceKill(
                new StardewCombatKillContext(
                        player,
                        target,
                        event.getSource(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(
                                target.getType()),
                        target.getTags(),
                        target.level().dimension(),
                        target.blockPosition()));
    }
}
