package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.stardew.craft.port.net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;

/** Includes MinecraftForge's pre/post render events so extension overlays share the same coordinates. */
@Mixin(ClientHooks.class)
public abstract class StardewGuiRenderMixin {
    @WrapMethod(method = "drawScreenInternal", remap = false)
    private static void stardewcraft$render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick, Operation<Void> original) {
        if (!StardewGuiViewport.supports(screen)) {
            original.call(screen, graphics, mouseX, mouseY, partialTick);
            return;
        }
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        var layout = StardewGuiViewport.forScreen(screen, window);
        var previous = StardewGuiViewport.enter(layout);
        var zoom = com.stardew.craft.client.gui.common.StardewReadingZoom.current(screen);
        if (zoom != null) {
            graphics.fill(0, 0, (int) Math.ceil(layout.projectedWidth()), (int) Math.ceil(layout.projectedHeight()), 0xB0000000);
            graphics.enableScissor(0, 0, (int) Math.floor(zoom.viewWidth() / layout.windowScale()),
                    (int) Math.floor(zoom.viewHeight() / layout.windowScale()));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(layout.x(), layout.y(), 0);
        graphics.pose().scale((float) layout.scale(), (float) layout.scale(), 1);
        try {
            // The caller has already rounded mouse coordinates in the real GUI grid.
            // Derive hover from the same raw position as MouseHandler's click/drag path.
            original.call(screen, graphics,
                    (int) Math.floor(layout.rawMouseX(minecraft.mouseHandler.xpos(), window.getScreenWidth())),
                    (int) Math.floor(layout.rawMouseY(minecraft.mouseHandler.ypos(), window.getScreenHeight())), partialTick);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            if (zoom != null) graphics.disableScissor();
            StardewGuiViewport.restore(previous);
        }
        com.stardew.craft.client.gui.common.StardewReadingZoom.drawScrollbars(screen, graphics);
    }
}
