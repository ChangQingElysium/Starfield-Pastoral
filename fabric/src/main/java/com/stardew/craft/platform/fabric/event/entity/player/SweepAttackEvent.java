package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class SweepAttackEvent extends PlayerEvent {
    private final Entity target;
    private final boolean vanillaSweep;
    private boolean sweeping;
    public SweepAttackEvent(Player player, Entity target, boolean sweeping) {
        super(player, true);
        this.target = java.util.Objects.requireNonNull(target);
        this.vanillaSweep = this.sweeping = sweeping;
    }
    public Entity getTarget() { return target; }
    public boolean isVanillaSweep() { return vanillaSweep; }
    public boolean isSweeping() { return sweeping; }
    public void setSweeping(boolean sweeping) { this.sweeping = sweeping; }
}
