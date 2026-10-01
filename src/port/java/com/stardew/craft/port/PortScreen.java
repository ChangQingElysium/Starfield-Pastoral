package com.stardew.craft.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): the 1.21.1 {@code Screen} background contract, implemented by every StardewCraft screen.
 *
 * <p>1.21 {@code Screen#render} starts with {@code renderBackground(graphics, mouseX, mouseY, partialTick)} and
 * {@code AbstractContainerScreen#renderBackground} is "transparent gradient + renderBg". 1.20.1 has neither: its
 * {@code Screen#render} draws only widgets and screens call the one-argument {@code renderBackground} themselves.
 * {@code PortScreenBackgroundMixin} / {@code PortContainerScreenMixin} call {@link #renderBackground} from the same
 * point in {@code render} as 1.21, so overriding it (including with an empty body) and {@code super.render} keep
 * their 1.21 meaning. Inside a direct implementor, 1.21's {@code super.renderBackground(...)} is written
 * {@code PortScreen.super.renderBackground(...)}.
 */
@OnlyIn(Dist.CLIENT)
public interface PortScreen {
    /** 1.21.1 {@code Screen#renderBackground(GuiGraphics, int, int, float)} (container screens: gradient + renderBg). */
    default void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PortScreens.renderBackground((Screen) this, graphics, mouseX, mouseY, partialTick);
    }

    /** 1.21.1 {@code Screen#renderTransparentBackground}. */
    default void renderTransparentBackground(GuiGraphics graphics) {
        PortScreens.renderTransparentBackground((Screen) this, graphics);
    }
}
