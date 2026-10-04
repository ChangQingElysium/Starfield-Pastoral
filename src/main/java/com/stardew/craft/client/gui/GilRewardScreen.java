package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewGuiContentSize;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.client.gui.common.TrashCanWidget;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.shop.GilRewardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Stardew ItemGrab-style upper reward tray and lower player inventory; native ItemStack slots. */
public final class GilRewardScreen extends AbstractContainerScreen<GilRewardMenu> implements StardewGuiContentSize {
    private static final int ACTION_SIZE = 16;
    private final TrashCanWidget.Controller trashCan = new TrashCanWidget.Controller();

    public GilRewardScreen(GilRewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 186; imageHeight = 166;
    }
    @Override public int minimumCanvasWidth() { return imageWidth + 48; }
    @Override public int minimumCanvasHeight() { return 190; }
    @Override protected void init() {
        super.init();
        // ItemGrabMenu's checkmark shares the same source tile as the geode menu.
        Button done = new Button(actionX(), topPos + imageHeight - 24, ACTION_SIZE, ACTION_SIZE,
                Component.translatable("gui.done"), button -> onClose(), narration -> narration.get()) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                g.pose().pushPose();
                if (isHoveredOrFocused()) {
                    g.pose().translate(getX() + getWidth() / 2.0F, getY() + getHeight() / 2.0F, 0);
                    g.pose().scale(1.1F, 1.1F, 1);
                    g.pose().translate(-getX() - getWidth() / 2.0F, -getY() - getHeight() / 2.0F, 0);
                }
                GeodeMenuTextures.drawOkButton(g, getX(), getY(), (float) (1 / StardewGuiViewport.REFERENCE_SCALE));
                g.pose().popPose();
            }
        };
        done.setTooltip(Tooltip.create(done.getMessage()));
        addRenderableWidget(done);
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x80000000);
        // Since 1.21, AbstractContainerScreen.render dispatches renderBg through this method.
        renderBg(g, partial, mx, my);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        StardewGuiUtil.drawDialogueBoxFrame(g, leftPos, topPos, imageWidth, 64);
        StardewGuiUtil.drawDialogueBoxFrame(g, leftPos, topPos + 68, imageWidth, 98);
        for (var slot : menu.slots) {
            CommonGuiTextures.drawMenuTile(g, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, 10);
        }
        trashCan.render(g, actionX(), trashY(), mx, my);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial); renderTooltip(g, mx, my);
        trashCan.renderTooltip(g, font, menu, actionX(), trashY(), mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        return trashCan.click(menu, actionX(), trashY(), mx, my, button) || super.mouseClicked(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        return trashCan.deleteKey(menu, key) || super.keyPressed(key, scan, modifiers);
    }
    private int actionX() { return leftPos + imageWidth + 4; }
    private int trashY() { return topPos + imageHeight - 66; }
}
