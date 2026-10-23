package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.living.LivingChangeTargetEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StartAttacking.class)
public abstract class BehaviorTargetChangeMixin {
    @Inject(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"), cancellable = true)
    private static void stardewcraft$target(CallbackInfoReturnable<Boolean> callback,
            @Local(argsOnly = true) Mob owner, @Local LivingEntity target,
            @Share("stardewcraft$behaviorTarget") LocalRef<LivingChangeTargetEvent> decision) {
        var event = FabricEvents.GAME.post(new LivingChangeTargetEvent(owner, target,
                LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET));
        decision.set(event);
        if (event.isCanceled() || event.getNewAboutToBeSetTarget() == null) callback.setReturnValue(false);
    }
    @ModifyArg(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"), index = 0)
    private static Object stardewcraft$newTarget(Object target,
            @Share("stardewcraft$behaviorTarget") LocalRef<LivingChangeTargetEvent> decision) {
        return decision.get().getNewAboutToBeSetTarget();
    }
}
