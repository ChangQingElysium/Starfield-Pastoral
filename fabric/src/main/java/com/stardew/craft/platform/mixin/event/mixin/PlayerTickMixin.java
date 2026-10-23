package com.stardew.craft.platform.mixin.event.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.PlayerTickEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric API has no player-tick callback; inject at the actual Player.tick boundaries. */
@Mixin(Player.class)
public abstract class PlayerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void stardewcraft$beforePlayerTick(CallbackInfo callback) {
        FabricEvents.GAME.post(new PlayerTickEvent.Pre((Player) (Object) this));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void stardewcraft$afterPlayerTick(CallbackInfo callback) {
        FabricEvents.GAME.post(new PlayerTickEvent.Post((Player) (Object) this));
    }
}
