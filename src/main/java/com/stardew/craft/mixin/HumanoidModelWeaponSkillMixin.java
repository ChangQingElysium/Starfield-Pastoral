package com.stardew.craft.mixin;

import com.stardew.craft.client.weapon.animation.WeaponSkillThirdPersonAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelWeaponSkillMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void stardewcraft$applyWeaponSkillPose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        float partialTick = Math.clamp(ageInTicks - entity.tickCount, 0.0f, 1.0f);
        WeaponSkillThirdPersonAnimator.apply(
                (HumanoidModel<?>) (Object) this,
                entity,
                partialTick
        );
    }
}
