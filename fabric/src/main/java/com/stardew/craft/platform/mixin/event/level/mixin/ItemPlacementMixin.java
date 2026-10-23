package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.level.PlacementTransactions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemPlacementMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult stardewcraft$place(Item item, UseOnContext context, Operation<InteractionResult> original) {
        if (context.getLevel().isClientSide || item instanceof BucketItem) return original.call(item, context);
        return PlacementTransactions.run(context.getLevel(), context.getPlayer(), context.getClickedFace(),
                (ItemStack) (Object) this, () -> original.call(item, context));
    }
}
