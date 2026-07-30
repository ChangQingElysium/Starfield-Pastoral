package com.stardew.craft.network.overnight;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Input-capturing, non-pausing screen used while the player collapses and waits for the new day.
 */
@OnlyIn(Dist.CLIENT)
final class OvernightCollapseScreen extends Screen {

    OvernightCollapseScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float alpha = OvernightCollapseClientState.blackAlpha(partialTick);
        if (alpha <= 0.0F) {
            return;
        }
        int channel = Math.min(255, Math.max(0, Math.round(alpha * 255.0F)));
        graphics.fill(0, 0, width, height, channel << 24);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }
}
