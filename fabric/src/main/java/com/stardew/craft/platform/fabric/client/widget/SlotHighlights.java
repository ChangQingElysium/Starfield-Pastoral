package com.stardew.craft.platform.fabric.client.widget;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

@Environment(EnvType.CLIENT)
public final class SlotHighlights {
    private static Highlighter highlighter;
    private SlotHighlights() {}
    @FunctionalInterface public interface Highlighter {
        boolean render(Screen screen, GuiGraphics graphics, Slot slot);
    }
    public static synchronized void install(Highlighter callback) {
        if (highlighter != null) throw new IllegalStateException("Slot highlight callback already installed");
        highlighter = Objects.requireNonNull(callback);
    }
    public static void render(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        render(screen instanceof SlotHighlightProvider provider ? provider : null, graphics, slot, mouseX, mouseY, partialTick,
                () -> renderDefault(screen, graphics, slot, mouseX, mouseY, partialTick));
    }
    static void render(SlotHighlightProvider provider, GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick, Runnable vanilla) {
        if (provider != null) provider.stardewcraft$renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        else vanilla.run();
    }
    public static void renderDefault(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        renderDefault(Objects.requireNonNull(highlighter, "Slot highlight callback not installed"), screen, graphics, slot,
                () -> AbstractContainerScreen.renderSlotHighlight(graphics, slot.x, slot.y, 0));
    }
    static void renderDefault(Highlighter callback, Screen screen, GuiGraphics graphics, Slot slot, Runnable vanilla) {
        if (!callback.render(screen, graphics, slot) && slot.isHighlightable()) vanilla.run();
    }
}
