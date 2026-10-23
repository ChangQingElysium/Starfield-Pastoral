package com.stardew.craft.platform.mixin.client.widget.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.widget.SlotHighlights;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotHighlightMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isHighlightable()Z"))
    private boolean stardewcraft$highlight(Slot slot, Operation<Boolean> original, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Neo moves this predicate into the default virtual method, so overrides and the seasonal callback
        // must run even for a non-highlightable slot. The facade performs the actual predicate exactly once.
        SlotHighlights.render((AbstractContainerScreen<?>) (Object) this, graphics, slot, mouseX, mouseY, partialTick);
        return false; // The complete draw above replaces the following native static highlight call.
    }
}
