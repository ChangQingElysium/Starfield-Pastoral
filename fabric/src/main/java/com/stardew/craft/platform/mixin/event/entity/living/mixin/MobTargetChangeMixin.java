package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.living.LivingChangeTargetEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public abstract class MobTargetChangeMixin {
    @WrapMethod(method = "setTarget")
    private void stardewcraft$target(LivingEntity target, Operation<Void> original) {
        var event = FabricEvents.GAME.post(new LivingChangeTargetEvent((Mob) (Object) this, target,
                LivingChangeTargetEvent.LivingTargetType.MOB_TARGET));
        if (!event.isCanceled()) original.call(event.getNewAboutToBeSetTarget());
    }
}
