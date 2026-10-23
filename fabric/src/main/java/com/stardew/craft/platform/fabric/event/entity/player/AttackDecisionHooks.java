package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class AttackDecisionHooks {
    private AttackDecisionHooks() {}
    public static CriticalHitEvent critical(Player player, Entity target, boolean nativeCritical) {
        return FabricEvents.GAME.post(new CriticalHitEvent(player, target, nativeCritical ? 1.5F : 1, nativeCritical));
    }
    public static boolean blocksSweep(CriticalHitEvent critical) { return critical.isCriticalHit() && critical.disableSweep(); }
    public static boolean sweep(Player player, Entity target, boolean nativeSweep) {
        // Cancel stops later listeners; it does not override the event's chosen sweep value.
        return FabricEvents.GAME.post(new SweepAttackEvent(player, target, nativeSweep)).isSweeping();
    }
}
