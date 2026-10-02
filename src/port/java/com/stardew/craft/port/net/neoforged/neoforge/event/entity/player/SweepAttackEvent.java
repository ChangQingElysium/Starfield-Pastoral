package com.stardew.craft.port.net.neoforged.neoforge.event.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code SweepAttackEvent}, fired by {@code com.stardew.craft.mixin.PortPlayerSweepEventMixin}
 * where {@code Player#attack} asks the held stack for {@code SWORD_SWEEP}; {@link #isSweeping()} replaces the vanilla
 * decision. Forge only reaches that call when the other vanilla sweep preconditions hold, so the event is not fired
 * (and cannot force a sweep) when they fail.
 */
@Cancelable
public class SweepAttackEvent extends PlayerEvent {
    private final Entity target;
    private final boolean isVanillaSweep;
    private boolean isSweeping;

    public SweepAttackEvent() {
        this(null, null, false);
    }

    public SweepAttackEvent(Player player, Entity target, boolean isVanillaSweep) {
        super(player);
        this.target = target;
        this.isSweeping = this.isVanillaSweep = isVanillaSweep;
    }

    public Entity getTarget() {
        return this.target;
    }

    public boolean isVanillaSweep() {
        return this.isVanillaSweep;
    }

    public boolean isSweeping() {
        return this.isSweeping;
    }

    public void setSweeping(boolean sweep) {
        this.isSweeping = sweep;
    }
}
