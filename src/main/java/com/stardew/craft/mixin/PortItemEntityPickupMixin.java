package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.event.PortPickupHooks;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Restore the 1.21 pickup Pre/complete-original Post while keeping native Forge pickup events. */
@Mixin(ItemEntity.class)
public abstract class PortItemEntityPickupMixin {
    @WrapMethod(method = "playerTouch")
    private void stardewcraft$pickupPre(Player player, Operation<Void> original) {
        PortPickupHooks.playerTouch((ItemEntity) (Object) this, player, () -> original.call(player));
    }

    @ModifyExpressionValue(method = "playerTouch", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/entity/item/ItemEntity;pickupDelay:I"), require = 2)
    private int stardewcraft$forcedPickupDelay(int delay) {
        return PortPickupHooks.pickupDelay((ItemEntity) (Object) this, delay);
    }

    @ModifyExpressionValue(method = "playerTouch", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/entity/item/ItemEntity;target:Ljava/util/UUID;"), require = 2)
    private UUID stardewcraft$forcedPickupTarget(UUID target) {
        return PortPickupHooks.pickupTarget((ItemEntity) (Object) this, target);
    }

    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack stardewcraft$completeOriginalStack(ItemStack stack, Operation<ItemStack> original) {
        return PortPickupHooks.captureOriginal((ItemEntity) (Object) this, original.call(stack));
    }

    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/event/ForgeEventFactory;firePlayerItemPickupEvent(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void stardewcraft$pickupPost(Player player, ItemEntity item, ItemStack picked, Operation<Void> original) {
        original.call(player, item, picked);
        PortPickupHooks.afterNativePickup(item, player);
    }
}
