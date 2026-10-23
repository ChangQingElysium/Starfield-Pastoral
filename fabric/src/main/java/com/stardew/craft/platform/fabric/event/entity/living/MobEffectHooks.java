package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.function.BooleanSupplier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class MobEffectHooks {
    private MobEffectHooks() {}
    public static boolean applicable(LivingEntity target, MobEffectInstance effect, Entity source, BooleanSupplier nativeCheck) {
        var event = new MobEffectEvent.Applicable(target, effect, source);
        FabricEvents.GAME.post(event);
        return switch (event.getResult()) {
            case APPLY -> true;
            case DO_NOT_APPLY -> false;
            case DEFAULT -> nativeCheck.getAsBoolean();
        };
    }
}
