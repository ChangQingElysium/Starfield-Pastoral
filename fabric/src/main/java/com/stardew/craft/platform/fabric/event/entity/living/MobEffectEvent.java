package com.stardew.craft.platform.fabric.event.entity.living;

import java.util.Objects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public abstract class MobEffectEvent extends LivingEvent {
    private final MobEffectInstance effect;
    protected MobEffectEvent(LivingEntity entity, MobEffectInstance effect) {
        super(entity, false);
        this.effect = Objects.requireNonNull(effect);
    }
    public MobEffectInstance getEffectInstance() { return effect; }

    public static final class Applicable extends MobEffectEvent {
        private final Entity source;
        private Result result = Result.DEFAULT;
        public Applicable(LivingEntity entity, MobEffectInstance effect, Entity source) {
            super(entity, effect);
            this.source = source;
        }
        public Entity getEffectSource() { return source; }
        public Result getResult() { return result; }
        public void setResult(Result result) { this.result = Objects.requireNonNull(result); }
        public boolean getApplicationResult() {
            return result == Result.APPLY || result == Result.DEFAULT && getEntity().canBeAffected(getEffectInstance());
        }
        public enum Result { APPLY, DEFAULT, DO_NOT_APPLY }
    }
}
