package com.stardew.craft.platform.mixin.client.render.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.client.render.FabricGuiRenderEvents;
import com.stardew.craft.platform.fabric.client.render.GuiHudLayout;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class GuiRenderEventsMixin {
    @WrapMethod(method = "render")
    private void stardewcraft$renderGui(GuiGraphics graphics, DeltaTracker partialTick, Operation<Void> original) {
        GuiHudLayout.reset((Gui) (Object) this);
        FabricGuiRenderEvents.render(graphics, partialTick, () -> original.call(graphics, partialTick));
    }
}
