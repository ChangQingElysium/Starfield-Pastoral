package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.entity.player.ItemPickupHooks;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
abstract class ItemPickupEventsMixin {
    @ModifyExpressionValue(method = "playerTouch", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;pickupDelay:I"))
    private int stardewcraft$before(int delay, Player player,
            @Share("stardewcraft$pickup") LocalRef<ItemPickupHooks.Frame> frame) {
        frame.set(ItemPickupHooks.before((ItemEntity) (Object) this, player));
        return ItemPickupHooks.delay(frame.get(), delay);
    }

    @ModifyExpressionValue(method = "playerTouch", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;target:Ljava/util/UUID;"))
    private UUID stardewcraft$owner(UUID owner, @Share("stardewcraft$pickup") LocalRef<ItemPickupHooks.Frame> frame) {
        return ItemPickupHooks.owner(frame.get(), owner);
    }

    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void stardewcraft$after(Player player, Entity target, int nativeCount, Operation<Void> original,
            @Share("stardewcraft$pickup") LocalRef<ItemPickupHooks.Frame> frame,
            @Local(ordinal = 0) ItemStack stack, @Local(ordinal = 0) LocalIntRef count) {
        int pickedUp = ItemPickupHooks.after((ItemEntity) target, player, frame.get(), stack);
        count.set(pickedUp);
        original.call(player, target, pickedUp);
    }
}
