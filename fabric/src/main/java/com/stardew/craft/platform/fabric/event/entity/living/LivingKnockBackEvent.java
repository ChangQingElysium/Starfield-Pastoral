package com.stardew.craft.platform.fabric.event.entity.living;

import net.minecraft.world.entity.LivingEntity;

/** Before native knockback resistance and motion are applied. */
public final class LivingKnockBackEvent extends LivingEvent {
    private final float originalStrength;
    private final double originalRatioX;
    private final double originalRatioZ;
    private float strength;
    private double ratioX;
    private double ratioZ;
    public LivingKnockBackEvent(LivingEntity entity, float strength, double ratioX, double ratioZ) {
        super(entity, true);
        this.strength = originalStrength = strength;
        this.ratioX = originalRatioX = ratioX;
        this.ratioZ = originalRatioZ = ratioZ;
    }
    public float getStrength() { return strength; }
    public float getOriginalStrength() { return originalStrength; }
    public double getRatioX() { return ratioX; }
    public double getRatioZ() { return ratioZ; }
    public double getOriginalRatioX() { return originalRatioX; }
    public double getOriginalRatioZ() { return originalRatioZ; }
    public void setStrength(float value) { strength = value; }
    public void setRatioX(double value) { ratioX = value; }
    public void setRatioZ(double value) { ratioZ = value; }
}
