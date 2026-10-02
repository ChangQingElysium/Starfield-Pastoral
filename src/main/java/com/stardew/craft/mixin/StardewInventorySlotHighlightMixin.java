package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.gui.common.StardewInventoryGui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class StardewInventorySlotHighlightMixin {
    @Shadow
    protected Slot hoveredSlot;

    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void stardewcraft$readableEquipmentHint(GuiGraphics graphics, int x, int y, int z, int width, int height,
                                                   TextureAtlasSprite sprite, Operation<Void> original) {
        if (!StardewInventoryGui.renderEmptySlot((AbstractContainerScreen<?>) (Object) this,
                graphics, sprite, x, y, z, width, height)) {
            original.call(graphics, x, y, z, width, height, sprite);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", remap = false))
    private void stardewcraft$seasonalOutline(GuiGraphics graphics, int x, int y, int z, int color,
                                            Operation<Void> original) {
        if (hoveredSlot == null || !StardewInventoryGui.renderSlotHighlight(
                (AbstractContainerScreen<?>) (Object) this, graphics, hoveredSlot)) {
            original.call(graphics, x, y, z, color);
        }
    }
}
