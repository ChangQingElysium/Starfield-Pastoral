package com.stardew.craft.platform.fabric.event.entity.living;

import net.minecraft.world.entity.LivingEntity;

public final class LivingChangeTargetEvent extends LivingEvent {
    private final ILivingTargetType type;
    private final LivingEntity original;
    private LivingEntity target;
    public LivingChangeTargetEvent(LivingEntity owner, LivingEntity target, ILivingTargetType type) {
        super(owner, true);
        this.original = this.target = target;
        this.type = java.util.Objects.requireNonNull(type);
    }
    public LivingEntity getNewAboutToBeSetTarget() { return target; }
    public void setNewAboutToBeSetTarget(LivingEntity target) { this.target = target; }
    public LivingEntity getOriginalAboutToBeSetTarget() { return original; }
    public ILivingTargetType getTargetType() { return type; }
    public interface ILivingTargetType {}
    public enum LivingTargetType implements ILivingTargetType { MOB_TARGET, BEHAVIOR_TARGET }
}
