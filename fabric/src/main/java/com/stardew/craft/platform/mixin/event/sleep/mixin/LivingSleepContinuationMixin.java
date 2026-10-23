package com.stardew.craft.platform.mixin.event.sleep.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.event.sleep.FabricSleepEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingSleepContinuationMixin {
    @ModifyReturnValue(method = "checkBedExists", at = @At("RETURN"))
    private boolean stardewcraft$continueWithBed(boolean hasBed) {
        var entity = (LivingEntity) (Object) this;
        return entity.level().isClientSide ? hasBed : FabricSleepEvents.canContinue(FabricSleepEvents.bus(), entity,
                hasBed ? null : BedSleepingProblem.NOT_POSSIBLE_HERE);
    }
}
