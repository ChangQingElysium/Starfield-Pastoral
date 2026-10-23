package com.stardew.craft.platform.mixin.event.sleep.mixin;


import com.stardew.craft.platform.fabric.event.sleep.FabricSleepEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerWakeUpMixin {
    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void stardewcraft$wake(boolean immediately, boolean updateLevel, CallbackInfo callback) {
        FabricSleepEvents.wake((Player) (Object) this, immediately, updateLevel);
    }
}
