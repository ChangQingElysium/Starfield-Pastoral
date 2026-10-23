package com.stardew.craft.platform.mixin.event.sleep.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.sleep.FabricSleepEvents;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class SleepFinishedTimeMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void stardewcraft$finished(ServerLevel level, long proposed, Operation<Void> original) {
        original.call(level, FabricSleepEvents.finished(level, proposed));
    }
}
