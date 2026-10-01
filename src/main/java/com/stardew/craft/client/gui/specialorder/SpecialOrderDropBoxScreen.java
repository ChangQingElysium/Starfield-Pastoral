package com.stardew.craft.client.gui.specialorder;

import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.menu.SpecialOrderDropBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings("null")
public class SpecialOrderDropBoxScreen extends AbstractContainerScreen<SpecialOrderDropBoxMenu> implements com.stardew.craft.port.PortContainerScreen {
    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    public SpecialOrderDropBoxScreen(SpecialOrderDropBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 114 + SpecialOrderDropBoxMenu.ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(CHEST_TEXTURE, x, y, 0, 0, this.imageWidth, SpecialOrderDropBoxMenu.ROWS * 18 + 17);
        graphics.blit(CHEST_TEXTURE, x, y + SpecialOrderDropBoxMenu.ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiText.drawCenteredClamped(graphics, this.font, this.title, this.imageWidth / 2,
            this.titleLabelY, this.imageWidth - 16, 0x404040, false);
        graphics.drawString(this.font, GuiText.ellipsize(this.font, this.playerInventoryTitle, this.imageWidth - this.inventoryLabelX - 8),
            this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
