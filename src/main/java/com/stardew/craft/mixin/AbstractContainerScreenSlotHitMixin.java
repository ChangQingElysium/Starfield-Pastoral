package com.stardew.craft.mixin;

import com.stardew.craft.client.gui.menu.StardewGameMenuScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Expands hit-testing from the 16px Minecraft Slot to the full SDV-styled tile. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotHitMixin implements StardewGameMenuScreen.QuickCraftStateAccess {
    @Shadow
    private int quickCraftingType;

    @Shadow
    private int quickCraftingRemainder;

    @Override
    public int stardewcraft$quickCraftingType() {
        return quickCraftingType;
    }

    @Override
    public int stardewcraft$quickCraftingRemainder() {
        return quickCraftingRemainder;
    }

    @Inject(method = "findSlot", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$findVisualInventorySlot(double mouseX, double mouseY,
                                                       CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof StardewGameMenuScreen screen) {
            Slot slot = screen.findVisualInventorySlot(mouseX, mouseY);
            if (slot != null) {
                cir.setReturnValue(slot);
            }
        }
    }
}
