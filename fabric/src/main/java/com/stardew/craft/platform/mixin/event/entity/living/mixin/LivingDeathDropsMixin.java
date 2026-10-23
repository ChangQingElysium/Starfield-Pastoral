package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDropHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntity.class)
public abstract class LivingDeathDropsMixin {
    @Shadow protected int lastHurtByPlayerTime;
    @WrapMethod(method = "dropAllDeathLoot")
    private void stardewcraft$captureDeathDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        LivingEntity owner = (LivingEntity) (Object) this;
        var capture = LivingDropHooks.begin(owner);
        try (capture) { original.call(level, source); }
        LivingDropHooks.publish(owner, source, capture.items(), lastHurtByPlayerTime > 0, level::addFreshEntity);
    }
}
