package com.stardew.craft.platform.mixin.event.client.world;

import com.stardew.craft.platform.fabric.event.client.FabricClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftLevelUnloadEventMixin {
    @Shadow public ClientLevel level;

    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;)V",
            at = @At("HEAD"), require = 1)
    private void stardewcraft$clientLevelReplacing(CallbackInfo callback) {
        FabricClientWorldEvents.levelUnloading(level);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V",
            shift = At.Shift.AFTER), require = 1)
    private void stardewcraft$clientLevelDisconnecting(CallbackInfo callback) {
        // Neo posts after the forced screen tick, before waiting for the local server or clearing level.
        // clearClientLevel is a separate transfer helper and did not post a Neo unload event.
        FabricClientWorldEvents.levelUnloading(level);
    }
}
