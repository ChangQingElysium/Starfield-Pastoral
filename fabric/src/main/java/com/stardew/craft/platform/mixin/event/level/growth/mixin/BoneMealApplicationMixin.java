package com.stardew.craft.platform.mixin.event.level.growth.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.stardew.craft.platform.fabric.event.level.growth.BoneMealApplications;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.world.item.BoneMealItem.class)
abstract class BoneMealApplicationMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BoneMealItem;growCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"), require = 1)
    private boolean stardew$player(ItemStack stack, Level level, BlockPos pos, Operation<Boolean> original, UseOnContext context) {
        return BoneMealApplications.withPlayer(context.getPlayer(), () -> original.call(stack, level, pos));
    }
    @WrapMethod(method = "growCrop")
    private static boolean stardew$application(ItemStack stack, Level level, BlockPos pos, Operation<Boolean> original) {
        return BoneMealApplications.growCrop(stack, level, pos);
    }
}
