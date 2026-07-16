package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.client.StardewClientTimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the collective server pause to the client simulation while screens/network keep ticking. */
@Mixin(Minecraft.class)
public abstract class MinecraftStardewPauseMixin {

    @Shadow
    private volatile boolean pause;

    @Shadow
    public abstract MusicManager getMusicManager();

    @ModifyExpressionValue(
        method = "tick",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;pause:Z"
        )
    )
    private boolean stardewcraft$applyCollectivePause(boolean vanillaPause) {
        // Do not mutate Minecraft.pause: IntegratedServer reads that field to decide whether to
        // pause every dimension. Only the pause checks inside the client tick should see this state.
        return vanillaPause || StardewClientTimeState.shouldPauseCurrentLevel();
    }

    @ModifyArg(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/DeltaTracker$Timer;updatePauseState(Z)V"
        ),
        index = 0
    )
    private boolean stardewcraft$freezeRenderInterpolation(boolean vanillaPause) {
        // Vanilla pause also holds deltaTickResidual. Without this, a moving player/camera keeps
        // rendering with a changing partial tick while its simulation positions are frozen.
        return vanillaPause || StardewClientTimeState.shouldPauseCurrentLevel();
    }

    @ModifyArg(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"
        ),
        index = 0
    )
    private boolean stardewcraft$keepAudioRunningDuringStardewPause(boolean effectivePause) {
        if (StardewClientTimeState.shouldPauseCurrentLevel()) {
            return this.pause;
        }
        return effectivePause;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void stardewcraft$tickMusicDuringStardewPause(CallbackInfo ci) {
        if (!this.pause && StardewClientTimeState.shouldPauseCurrentLevel()) {
            this.getMusicManager().tick();
        }
    }
}
