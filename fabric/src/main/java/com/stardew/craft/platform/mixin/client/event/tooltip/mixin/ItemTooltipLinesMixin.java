package com.stardew.craft.platform.mixin.client.event.tooltip.mixin;


import com.stardew.craft.platform.fabric.client.event.tooltip.FabricTooltipEvents;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public abstract class ItemTooltipLinesMixin {
    // The HIDE_TOOLTIP early return is intentionally excluded, as in NeoForge's source hook.
    // The FAPI ItemTooltipCallback lacks the nullable original player, so use the exact native arguments.
    @Inject(method = "getTooltipLines", at = @At("TAIL"))
    private void stardewcraft$itemTooltip(Item.TooltipContext context, Player player, TooltipFlag flags,
                                         CallbackInfoReturnable<List<Component>> callback) {
        FabricTooltipEvents.itemLines((ItemStack) (Object) this, player, callback.getReturnValue(), flags, context);
    }
}
