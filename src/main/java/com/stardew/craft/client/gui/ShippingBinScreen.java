package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.TrashCanWidget;
import com.stardew.craft.menu.ShippingBinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

@SuppressWarnings("null")
public class ShippingBinScreen extends AbstractContainerScreen<ShippingBinMenu> implements com.stardew.craft.port.PortContainerScreen {
    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int ROWS = 1;
    private static final int SLOT_X = 80;
    private static final int SLOT_Y = 18;
    private final TrashCanWidget.Controller trashCan = new TrashCanWidget.Controller();

    public ShippingBinScreen(ShippingBinMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 114 + ROWS * 18;
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

        graphics.blit(CHEST_TEXTURE, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17);
        graphics.blit(CHEST_TEXTURE, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96);

        // Keep vanilla chest framing but visually expose only the center shipping slot.
        graphics.fill(x + 7, y + 17, x + this.imageWidth - 7, y + 35, 0xFF8B8B8B);
        graphics.blit(CHEST_TEXTURE, x + SLOT_X - 1, y + SLOT_Y - 1, 7, 17, 18, 18);
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
        trashCan.render(graphics, trashX(), trashY(), mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
        trashCan.renderTooltip(graphics, font, menu, trashX(), trashY(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return trashCan.click(menu, trashX(), trashY(), mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return trashCan.deleteKey(menu, keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int trashX() {
        return trashCan.xBeside(leftPos, imageWidth, width);
    }

    private int trashY() {
        return topPos + imageHeight - 34;
    }

    public List<Rect2i> jeiGuiExtraAreas() {
        return List.of(new Rect2i(trashX(), trashY(), 18, 34));
    }
}
