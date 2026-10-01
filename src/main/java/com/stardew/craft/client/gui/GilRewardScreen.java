package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewGuiContentSize;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.shop.GilRewardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Stardew ItemGrab-style upper reward tray and lower player inventory; native ItemStack slots. */
public final class GilRewardScreen extends AbstractContainerScreen<GilRewardMenu> implements StardewGuiContentSize, com.stardew.craft.port.PortContainerScreen {
    public GilRewardScreen(GilRewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 186; imageHeight = 166;
    }
    @Override public int minimumCanvasWidth() { return 210; }
    @Override public int minimumCanvasHeight() { return 190; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) { g.fill(0,0,width,height,0x80000000); }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        StardewGuiUtil.drawDialogueBoxFrame(g, leftPos, topPos, imageWidth, 64);
        StardewGuiUtil.drawDialogueBoxFrame(g, leftPos, topPos + 68, imageWidth, 98);
        for (var slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF9C633C);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFFF0C584);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial); renderTooltip(g, mx, my);
    }
}
