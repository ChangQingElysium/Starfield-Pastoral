package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.StardewGuiContentSize;
import com.stardew.craft.menu.AquariumMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Native ItemStacks on the shared design canvas and separately extracted Stardew UI sprites. */
@OnlyIn(Dist.CLIENT)
public final class AquariumScreen extends AbstractContainerScreen<AquariumMenu> implements StardewGuiContentSize {
    public AquariumScreen(AquariumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 194; imageHeight = 232;
    }
    @Override public int minimumCanvasWidth() { return 230; }
    @Override public int minimumCanvasHeight() { return 264; }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        CommonGuiTextures.drawTextureBox(graphics, leftPos, topPos, imageWidth, imageHeight, 1, false);
        for (var slot : menu.slots) CommonGuiTextures.drawItemSlot18(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 1);
    }
    private void label(GuiGraphics g, Component text, int x, int y, int width) {
        g.drawString(font, GuiText.ellipsize(font, text, width), x, y, 0x542C24, false);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        label(g, title, 12, 8, 170);
        int y = 35 - StardewFonts.lineHeight(font) - 3;
        label(g, Component.translatable("stardewcraft.aquarium.swim"), 12, y, 53);
        label(g, Component.translatable("stardewcraft.aquarium.ground"), 70, y, 53);
        label(g, Component.translatable("stardewcraft.aquarium.hats"), 128, y, 54);
        label(g, Component.translatable("stardewcraft.aquarium.decorations"), 34, 77 - StardewFonts.lineHeight(font) - 3, 130);
        label(g, Component.translatable("stardewcraft.aquarium.hint"), 12, 117, 172);
        label(g, playerInventoryTitle, 16, 145 - StardewFonts.lineHeight(font) - 3, 162);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
