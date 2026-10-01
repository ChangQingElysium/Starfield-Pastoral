package com.stardew.craft.mixin;

import com.stardew.craft.config.StackSizeHolder;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces {@code Inventory.getMaxStackSize()} to return 999.
 * <p>
 * On hybrid servers (Mohist) the CraftBukkit layer promotes the
 * {@code Container} default method into the concrete class, bypassing
 * our {@code ContainerMixin}. This mixin injects at HEAD to guarantee
 * the correct value regardless of whether the method is a default or
 * a concrete override.
 */
@Mixin(value = Inventory.class, priority = 2000)
public abstract class InventoryMaxStackMixin {

    // Vanilla/Forge Inventory inherits Container#getMaxStackSize (no own method in 1.20.1 or 1.21.1), so this is a
    // no-op there, exactly as on 1.21.1; it only applies where a hybrid server adds the override. The refmap entry
    // (m_6893_) is still generated, hence the suppressed "Cannot find target method" AP warning.
    @SuppressWarnings("target")
    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true, require = 0)
    private void stardewcraft$forceMaxStack(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(StackSizeHolder.get());
    }
}
