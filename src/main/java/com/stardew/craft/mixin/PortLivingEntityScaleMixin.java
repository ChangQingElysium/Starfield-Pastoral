package com.stardew.craft.mixin;

import com.stardew.craft.port.PortInheritance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 {@code LivingEntity#getScale()} is only the SCALE attribute (1 without it); the baby factor is
 * the separate {@code getAgeScale()}, applied in {@code getDefaultDimensions}. 1.20.1 {@code getScale()} is the baby
 * factor (0.5) and is what {@code getDimensions} multiplies by. StardewCraft entities read {@code getScale()} directly
 * (Blockbench entity renderers scale baby coop animals by it; the 1.21 inventory-preview helper divides by it), so a
 * baby rendered at full model size in 1.21.1 and at half size here. For StardewCraft entities {@code getScale()} and
 * the inherited {@code getDimensions} now return the 1.21.1 values (dimensions unchanged: type size x age scale x
 * SCALE attribute, which also covers what {@code PortLivingEntityAttributesMixin} adds on 1.20.1's result).
 * Vanilla and other mods' entities are unchanged.
 */
@Mixin(LivingEntity.class)
public abstract class PortLivingEntityScaleMixin {
    @Shadow
    @Final
    protected static EntityDimensions SLEEPING_DIMENSIONS;

    @Inject(method = "getScale", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$scale121(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (PortInheritance.isModEntity(self)) cir.setReturnValue(PortInheritance.scale(self));
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$dimensions121(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!PortInheritance.isModEntity(self)) return;
        cir.setReturnValue(pose == Pose.SLEEPING ? SLEEPING_DIMENSIONS : PortInheritance.dimensions(self));
    }
}
