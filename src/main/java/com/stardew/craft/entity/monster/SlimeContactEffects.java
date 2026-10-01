package com.stardew.craft.entity.monster;

import com.stardew.craft.combat.equipment.EquipmentResolver;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.trinket.TrinketEffectHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/** Called after protection/parry gates but before this contact rolls Yoba or removes HP. */
public final class SlimeContactEffects {
    private SlimeContactEffects() {}
    public static void onAcceptedContact(GreenSlimeEntity slime, ServerPlayer player) {
        if (EquipmentResolver.getMergedStats(player).hasSlimeCharmer()
                || player.hasEffect(ModMobEffects.SQUID_INK_RAVIOLI.get())
                || TrinketEffectHandler.blocksNegativeEffects(player)
                || slime.getRandom().nextDouble() >= .3) return;
        var protection = com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection
                .decideMilliseconds(player, 2500 + slime.getRandom().nextInt(501));
        if (!protection.resisted()) com.stardew.craft.combat.equipment.EquipmentMobEffectHandler
                .addPreAdjustedEffect(player, new MobEffectInstance(ModMobEffects.SLIMED.get(), protection.durationTicks(), 0));
    }
}
