package com.stardew.craft.mixin;

import com.stardew.craft.item.ExternalStardewFoodService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerStardewFoodMixin {
    @Inject(method = "canEat", at = @At("RETURN"), cancellable = true)
    private void stardewcraft$allowConfiguredFoodAtFullHunger(
            boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && ExternalStardewFoodService.shouldAllowCurrentFoodAtFullHunger((Player) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
