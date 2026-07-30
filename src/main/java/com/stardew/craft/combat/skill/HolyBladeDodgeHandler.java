package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class HolyBladeDodgeHandler {

    private HolyBladeDodgeHandler() {}

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }

        if (event.getAmount() <= 0.0f) {
            return;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return;
        }

        Entity src = event.getSource().getEntity();
        if (!(src instanceof LivingEntity)) {
            return;
        }

        long nowTick = level.getGameTime();
        float chance = HolyBladeDodgeTracker.getDodgeChance(player, nowTick);
        if (chance <= 0.0f) {
            return;
        }

        if (player.getRandom().nextFloat() >= chance) {
            return;
        }

        event.setAmount(0.0f);
        HolyBladeEffects.playDodgeSuccess(player);
        player.sendSystemMessage(Component.translatable("stardewcraft.message.holy_blade_dodge"));
    }
}
