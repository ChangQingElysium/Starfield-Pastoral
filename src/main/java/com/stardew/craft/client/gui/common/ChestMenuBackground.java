package com.stardew.craft.client.gui.common;

import com.stardew.craft.menu.ChestMenuLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Tiles the existing container frame at native pixel size, with a centered player inventory. */
public final class ChestMenuBackground {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    private ChestMenuBackground() {}

    public static void draw(GuiGraphics graphics, int x, int y, ChestMenuLayout layout) {
        strip(graphics, x, y, 0, 17, layout.columns());
        int cursor = y + 17;
        for (int row = 0; row < layout.rows(); row++) {
            if (layout.recoverySlots() > 0 && row * layout.columns() == layout.storageSlots()) {
                strip(graphics, x, cursor, 4, layout.recoveryGap(), layout.columns());
                cursor += layout.recoveryGap();
            }
            strip(graphics, x, cursor, 17, 18, layout.columns());
            int rowSlots = Math.min(layout.columns(), layout.visibleSlots() - row * layout.columns());
            if (rowSlots < layout.columns()) {
                graphics.fill(x + 7 + rowSlots * 18, cursor, x + layout.imageWidth() - 7, cursor + 18, 0xFFC6C6C6);
            }
            cursor += 18;
        }

        // Only the chest widens. The original 9-column inventory stays centered below it.
        graphics.fill(x + 7, cursor, x + layout.imageWidth() - 7, cursor + 96, 0xFFC6C6C6);
        graphics.blit(TEXTURE, x, cursor, 0, 126, 7, 96);
        graphics.blit(TEXTURE, x + layout.imageWidth() - 7, cursor, 169, 126, 7, 96);
        for (int col = 0; col < layout.columns(); col++) {
            graphics.blit(TEXTURE, x + 7 + col * 18, cursor + 89, 7, 215, 18, 7);
        }
        graphics.blit(TEXTURE, x + layout.playerX() - 1, cursor, 7, 126, 162, 96);
    }

    private static void strip(GuiGraphics graphics, int x, int y, int v, int height, int columns) {
        graphics.blit(TEXTURE, x, y, 0, v, 7, height);
        for (int col = 0; col < columns; col++) {
            graphics.blit(TEXTURE, x + 7 + col * 18, y, 7, v, 18, height);
        }
        graphics.blit(TEXTURE, x + 7 + columns * 18, y, 169, v, 7, height);
    }
}
