package com.stardew.craft.platform.mixin.event.client.world;

import com.stardew.craft.platform.fabric.event.client.FabricClientWorldEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelLoadEventMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void stardewcraft$clientLevelLoaded(CallbackInfo callback) {
        // Neo posts after prepareWeather, before the new level is assigned to Minecraft.level.
        FabricClientWorldEvents.levelLoaded((ClientLevel) (Object) this);
    }
}
