package com.stardew.craft.platform.mixin.event.enchant.mixin;


import com.stardew.craft.platform.mixin.event.enchant.mixin.GrindstoneInputsAccess;

import com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents;
import com.stardew.craft.platform.fabric.event.enchant.GrindstoneExperience;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneTakeMixin {
    @Unique private GrindstoneMenu stardewcraft$menu;
    @Unique private ContainerLevelAccess stardewcraft$access;
    @Shadow private int getExperienceAmount(Level level) { throw new AssertionError(); }

    // Capture constructor arguments instead of naming the anonymous class's unmapped outer-instance field.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void stardewcraft$context(GrindstoneMenu menu, Container result, int index, int x, int y,
            ContainerLevelAccess access, CallbackInfo callback) {
        stardewcraft$menu = menu;
        stardewcraft$access = access;
    }
    @Inject(method = "getExperienceAmount", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$experience(Level level, CallbackInfoReturnable<Integer> callback) {
        int xp = ((GrindstoneExperience) stardewcraft$menu).stardewcraft$grindstoneExperience();
        if (xp >= 0) callback.setReturnValue(xp);
    }
    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$take(Player player, ItemStack output, CallbackInfo callback) {
        FabricEnchantingEvents.take(((GrindstoneInputsAccess) stardewcraft$menu).stardewcraft$inputs(),
                stardewcraft$access, player, this::getExperienceAmount);
        callback.cancel();
    }
}
