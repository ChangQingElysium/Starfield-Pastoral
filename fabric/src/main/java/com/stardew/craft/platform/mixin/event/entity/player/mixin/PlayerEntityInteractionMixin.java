package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Player.class, priority = 900)
public abstract class PlayerEntityInteractionMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$interact(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = (Player) (Object) this;
        if (player.isSpectator()) return;
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.EntityInteract(player, hand, target));
        if (event.isCanceled()) cir.setReturnValue(event.getCancellationResult());
    }
}
