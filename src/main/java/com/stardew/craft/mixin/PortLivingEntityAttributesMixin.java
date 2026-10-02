package com.stardew.craft.mixin;

import com.stardew.craft.port.PortAttributes;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.20.5+ {@code step_height} / {@code scale} attribute behaviour for living entities that carry the
 * port attributes ({@link PortAttributes}; only mod entities). Everything else keeps 1.20.1 behaviour.
 * <ul>
 * <li>1.21 {@code maxUpStep()} returns the step-height attribute (at least 1 with a player controlling passenger);
 * subclass overrides calling {@code super.maxUpStep()} see that value.</li>
 * <li>1.21 {@code getDimensions(pose)} multiplies the (age-scaled) default dimensions by {@code getScale()} except
 * when sleeping; {@code PortLivingEntityScaleMixin} owns that result for mod entities, so this fallback must not
 * multiply it a second time. Like 1.21 ({@code appliedScale} at the end of {@code LivingEntity#tick}), a changed scale refreshes the
 * dimensions at the end of the entity's living tick on both sides.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class PortLivingEntityAttributesMixin {
    @Unique
    private float stardewcraft$appliedScale = 1.0F;

    @Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$stepHeightAttribute(CallbackInfoReturnable<Float> cir) {
        float value = PortAttributes.maxUpStep((LivingEntity) (Object) this);
        if (!Float.isNaN(value)) cir.setReturnValue(value);
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$scaleAttribute(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (pose == Pose.SLEEPING || PortInheritance.isModEntity(self)) return;
        float scale = PortAttributes.scale(self);
        if (scale != 1.0F) cir.setReturnValue(cir.getReturnValue().scale(scale));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void stardewcraft$refreshScaledDimensions(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        float scale = PortAttributes.scale(self);
        if (scale != stardewcraft$appliedScale) {
            stardewcraft$appliedScale = scale;
            self.refreshDimensions();
        }
    }
}
