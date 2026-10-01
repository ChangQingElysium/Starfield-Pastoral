package com.stardew.craft.communitycenter.client;

import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.menu.BundleRewardMenu;
import com.stardew.craft.client.gui.common.GuiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nonnull;

/**
 * Simple chest-style screen for bundle rewards.
 * 1 row of reward slots on top, player inventory on bottom.
 * Uses the vanilla generic_54 container texture (cropped to 1 row).
 */
@SuppressWarnings("null")
public class BundleRewardScreen extends AbstractContainerScreen<BundleRewardMenu> implements com.stardew.craft.port.PortContainerScreen {

    /** Vanilla container texture (9×6 chest texture, we only render 1 row portion) */
    private static final ResourceLocation CONTAINER_BG = new ResourceLocation(
            "textures/gui/container/generic_54.png");

    public BundleRewardScreen(BundleRewardMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Vanilla 1-row chest: imageHeight = 1*18 + 114 = 132
        this.imageWidth = 176;
        this.imageHeight = 132;
        // Inventory label: y = 1*18 + 20 = 38 (matching vanilla ContainerScreen)
        this.inventoryLabelY = 38;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int rows = 1;

        // Vanilla ContainerScreen renderBg for generic_54:
        // Top half: (0, 0, imageWidth, rows*18 + 17)
        g.blit(CONTAINER_BG, x, y, 0, 0, this.imageWidth, rows * 18 + 17);
        // Bottom half: player inv section (0, 126, imageWidth, 96)
        g.blit(CONTAINER_BG, x, y + rows * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        int areaId = this.menu.getAreaId();
        String displayKey = BundleDataManager.getAreaDisplayNameKey(areaId);
        Component title = (displayKey != null)
                ? Component.translatable("stardewcraft.bundle.rewards.title",
                    Component.translatable(displayKey))
                : Component.translatable("stardewcraft.bundle.rewards");
        int titleMaxWidth = this.imageWidth - this.titleLabelX - 8;
        int inventoryMaxWidth = this.imageWidth - this.inventoryLabelX - 8;
        g.drawString(this.font, GuiText.ellipsize(this.font, title, titleMaxWidth),
            this.titleLabelX, this.titleLabelY, 0x404040, false);
        g.drawString(this.font, GuiText.ellipsize(this.font, this.playerInventoryTitle, inventoryMaxWidth),
            this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
