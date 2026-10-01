package com.stardew.craft.mixin;

import com.stardew.craft.client.combat.CombatCollapseModelPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelCombatCollapseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void stardewcraft$collapsePose(LivingEntity entity,float a,float b,float age,float d,float e,CallbackInfo ci) {
        float partialTick=com.stardew.craft.port.PortJava.clamp(age-entity.tickCount,0,1);
        if(entity instanceof com.stardew.craft.cutscene.runtime.EventPlayerActorEntity actor && actor.isInHospitalBedScene()) {
            com.stardew.craft.client.combat.HospitalBedPose.apply((PlayerModel<?>)(Object)this,
                    com.stardew.craft.client.combat.HospitalBedPose.sample(actor.hospitalBedTime(partialTick)));
            return;
        }
        var frame=CombatCollapseModelPose.frame(entity,partialTick);
        if(frame!=null) CombatCollapseModelPose.apply((PlayerModel<?>)(Object)this,frame);
    }
}
