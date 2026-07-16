package com.stardew.craft.mixin;

import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes farm and mine scheduled ticks use the pause-aware Stardew simulation clock. */
@Mixin(Level.class)
public abstract class LevelStardewGameTimeMixin {

    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$useStardewSimulationGameTime(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof ServerLevel level
                && StardewTimePauseService.isStardewTimeDimension(level)) {
            cir.setReturnValue(StardewTimePauseService.getStardewSimulationGameTime(level));
        }
    }
}
