package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class CriticalHitEvent extends PlayerEvent {
    private final Entity target;
    private final float vanillaMultiplier;
    private final boolean vanillaCritical;
    private float multiplier;
    private boolean critical;
    private boolean disableSweep = true;
    public CriticalHitEvent(Player player, Entity target, float multiplier, boolean critical) {
        super(player);
        this.target = java.util.Objects.requireNonNull(target);
        this.multiplier = this.vanillaMultiplier = multiplier;
        this.critical = this.vanillaCritical = critical;
    }
    public Entity getTarget() { return target; }
    public float getDamageMultiplier() { return multiplier; }
    public void setDamageMultiplier(float multiplier) {
        if (multiplier < 0) throw new UnsupportedOperationException("Attempted to set a negative damage multiplier: " + multiplier);
        this.multiplier = multiplier;
    }
    public boolean isCriticalHit() { return critical; }
    public void setCriticalHit(boolean critical) { this.critical = critical; }
    public float getVanillaMultiplier() { return vanillaMultiplier; }
    public boolean isVanillaCritical() { return vanillaCritical; }
    public void setDisableSweep(boolean disableSweep) { this.disableSweep = disableSweep; }
    public boolean disableSweep() { return disableSweep; }
}
