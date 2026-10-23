package com.stardew.craft.platform.mixin.client.event.tooltip.mixin;


import com.stardew.craft.platform.fabric.client.event.tooltip.FabricPresentationEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class RenderFrameMixin {
    @Shadow @Final private DeltaTracker.Timer timer;
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=gameRenderer"))
    private void stardewcraft$beforeFrame(boolean renderLevel, CallbackInfo callback) {
        FabricPresentationEvents.framePre(timer);
    }
    @Inject(method = "runTick", at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V")))
    private void stardewcraft$afterFrame(boolean renderLevel, CallbackInfo callback) {
        FabricPresentationEvents.framePost(timer);
    }
}
