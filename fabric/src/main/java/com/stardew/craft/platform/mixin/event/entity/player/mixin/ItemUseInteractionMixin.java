package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStack.class, priority = 900)
public abstract class ItemUseInteractionMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$use(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.RightClickItem(player, hand));
        if (event.isCanceled()) {
            cir.setReturnValue(new InteractionResultHolder<>(event.getCancellationResult(), (ItemStack) (Object) this));
        }
    }
}
