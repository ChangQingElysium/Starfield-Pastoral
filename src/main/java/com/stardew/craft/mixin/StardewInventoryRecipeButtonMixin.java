package com.stardew.craft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.client.gui.common.StardewInventoryGui;
import com.stardew.craft.port.PortGuiSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Maps the old recipe-button texture to the two sprite states used by 1.21.1. */
@Mixin(ImageButton.class)
public abstract class StardewInventoryRecipeButtonMixin {
    @Shadow @Final
    protected ResourceLocation resourceLocation;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$seasonalRecipeButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo callback) {
        if (!resourceLocation.equals(new ResourceLocation("textures/gui/recipe_button.png"))
                || !StardewInventoryGui.isActive(Minecraft.getInstance().screen)) {
            return;
        }
        ImageButton button = (ImageButton) (Object) this;
        ResourceLocation sprite = new ResourceLocation("recipe_book/"
                + (button.isHoveredOrFocused() ? "button_highlighted" : "button"));
        RenderSystem.enableDepthTest();
        PortGuiSprites.blitSprite(graphics, sprite, button.getX(), button.getY(), button.getWidth(), button.getHeight());
        callback.cancel();
    }
}
