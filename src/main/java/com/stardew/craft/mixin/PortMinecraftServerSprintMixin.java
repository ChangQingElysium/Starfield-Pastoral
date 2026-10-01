package com.stardew.craft.mixin;

import com.stardew.craft.port.PortTickRateManager;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21 tick sprint (see {@link PortTickRateManager}). 1.21 resets the next tick time to "now" for a
 * sprint tick instead of advancing it by one tick interval; here the same happens at the start of
 * {@code tickServer}, after the 1.20.1 run loop added its 50 ms, so the following wait returns immediately.
 */
@Mixin(MinecraftServer.class)
public abstract class PortMinecraftServerSprintMixin {
    @Shadow
    private long nextTickTime;
    @Shadow
    private long lastOverloadWarning;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void stardewcraft$beginSprintTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (PortTickRateManager.of((MinecraftServer) (Object) this).beginTick()) {
            nextTickTime = Util.getMillis();
            lastOverloadWarning = nextTickTime;
        }
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void stardewcraft$endSprintTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        PortTickRateManager.of((MinecraftServer) (Object) this).endTick();
    }
}
