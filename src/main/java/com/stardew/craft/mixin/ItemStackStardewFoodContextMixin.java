package com.stardew.craft.mixin;

import com.stardew.craft.item.ExternalStardewFoodService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStack.class)
public abstract class ItemStackStardewFoodContextMixin {
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"
            )
    )
    private InteractionResultHolder<ItemStack> stardewcraft$trackFoodUseContext(
            Item item, Level level, Player player, InteractionHand hand) {
        ExternalStardewFoodService.pushUseContext((ItemStack) (Object) this);
        try {
            return item.use(level, player, hand);
        } finally {
            ExternalStardewFoodService.popUseContext();
        }
    }
}
