package com.stardew.craft.client.gui;

import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.ChestColorWheel;
import com.stardew.craft.client.gui.common.ChestModelPreview;
import com.stardew.craft.client.gui.common.ChestMenuBackground;
import com.stardew.craft.menu.StoneChestMenu;
import com.stardew.craft.network.payload.InventoryOrganizePayload;
import com.stardew.craft.network.payload.StoneChestColorSelectPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.Rect2i;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class StoneChestScreen extends AbstractContainerScreen<StoneChestMenu> {

    private static final ResourceLocation COLOR_WHEEL = new ResourceLocation("stardewcraft", "textures/gui/color_wheel.png");
    private static final int BUTTON_SIZE = 18;

    private int colorButtonX;
    private int colorButtonY;
    private int organizeButtonX;
    private int organizeButtonY;
    private final com.stardew.craft.client.gui.common.ChestExtraActions extraActions = new com.stardew.craft.client.gui.common.ChestExtraActions(menu);
    private final ChestModelPreview modelPreview = new ChestModelPreview();
    private final ChestColorWheel wheel = new ChestColorWheel(ChestColorWheel.chestOptions(),
            color -> PacketDistributor.sendToServer(new StoneChestColorSelectPayload(color)),
            (graphics, x, y, color) -> modelPreview.draw(graphics, x, y, color, false, true));

    public StoneChestScreen(StoneChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = menu.layout().imageWidth();
        this.imageHeight = menu.layout().imageHeight();
        this.inventoryLabelX = menu.layout().playerX();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.colorButtonX = this.leftPos + this.imageWidth + 6;
        this.colorButtonY = this.topPos + 16;
        this.organizeButtonX = this.colorButtonX;
        this.organizeButtonY = this.colorButtonY + 30;
        layoutExtraActions();
        wheel.layout(this.width, this.height, this.leftPos, this.leftPos + this.imageWidth, colorButtonY);
    }

    private void layoutExtraActions() {
        extraActions.layout(organizeButtonX, organizeButtonY, true, true);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        layoutExtraActions();
        extraActions.render(graphics, mouseX, mouseY);

        ChestMenuBackground.draw(graphics, x, y, menu.layout());

        boolean hovered = isHoveringColorButton(mouseX, mouseY);
        if (hovered) {
            graphics.fill(colorButtonX - 1, colorButtonY - 1, colorButtonX + BUTTON_SIZE + 1, colorButtonY + BUTTON_SIZE + 1, 0x40FFFFFF);
        }
        graphics.blit(COLOR_WHEEL, colorButtonX, colorButtonY, 0, 0, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);

        int selected = this.menu.getColorSelection();
        if (selected >= 0) {
            int rgb = WoodenChestColorPalette.rgbAt(selected) | 0xFF000000;
            int indW = 10;
            int indH = 3;
            graphics.fill(colorButtonX + BUTTON_SIZE / 2 - indW / 2, colorButtonY + BUTTON_SIZE + 2,
                colorButtonX + BUTTON_SIZE / 2 + indW / 2, colorButtonY + BUTTON_SIZE + 2 + indH, rgb);
            graphics.fill(colorButtonX + BUTTON_SIZE / 2 - indW / 2 - 1, colorButtonY + BUTTON_SIZE + 1,
                colorButtonX + BUTTON_SIZE / 2 + indW / 2 + 1, colorButtonY + BUTTON_SIZE + 2, 0x40000000);
        }

        boolean organizeHovered = isHoveringOrganizeButton(mouseX, mouseY);
        if (organizeHovered) {
            graphics.fill(organizeButtonX - 1, organizeButtonY - 1, organizeButtonX + BUTTON_SIZE + 1, organizeButtonY + BUTTON_SIZE + 1, 0x40FFFFFF);
        }
        CommonGuiTextures.drawGameMenuOrganize(graphics, organizeButtonX + 1, organizeButtonY + 1, 1.0F);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.layout().recoverySlots() > 0) {
            graphics.drawString(this.font, GuiText.ellipsize(this.font,
                    Component.translatable("stardewcraft.chest.recover_old_items"), this.imageWidth - 16),
                    8, 72, 0x404040, false);
        }
        graphics.drawString(this.font, GuiText.ellipsize(this.font, this.title, this.imageWidth - this.titleLabelX - 8),
            this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, GuiText.ellipsize(this.font, this.playerInventoryTitle, 162),
            this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean selecting = wheel.active();
        super.render(graphics, selecting ? -10000 : mouseX, selecting ? -10000 : mouseY, partialTick);

        if (wheel.active()) {
            wheel.render(graphics, mouseX, mouseY);
            return;
        }

        this.renderTooltip(graphics, mouseX, mouseY);
        extraActions.tooltip(graphics, this.font, mouseX, mouseY);

        if (isHoveringColorButton(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.stone_chest.color_picker"), mouseX, mouseY);
        }
        if (isHoveringOrganizeButton(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("stardewcraft.game_menu.inventory.organize"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (wheel.click(mouseX, mouseY, button, isHoveringColorButton(mouseX, mouseY))) return true;
        if (isHoveringColorButton(mouseX, mouseY)) {
            wheel.claimButton(button);
            if (!menu.getCarried().isEmpty()) return true;
            if (button == 0) wheel.open(menu.getColorSelection());
            else if (button == 1) PacketDistributor.sendToServer(new StoneChestColorSelectPayload(-1));
            return true;
        }
        if (button == 0 && isHoveringOrganizeButton(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new InventoryOrganizePayload(InventoryOrganizePayload.TARGET_OPEN_CONTAINER));
            playOrganizeSound();
            return true;
        }
        layoutExtraActions();
        if (extraActions.click(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return wheel.release(button) || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return wheel.dragging(button) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        double horizontal = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2
        return wheel.scroll(vertical) || super.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return wheel.key(key) || extraActions.keyPressed(key) || super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        return wheel.keyReleased(key) || super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return wheel.active() || super.charTyped(character, modifiers);
    }

    private boolean isHoveringColorButton(double mouseX, double mouseY) {
        return mouseX >= colorButtonX && mouseX < colorButtonX + BUTTON_SIZE
            && mouseY >= colorButtonY && mouseY < colorButtonY + BUTTON_SIZE;
    }

    private boolean isHoveringOrganizeButton(double mouseX, double mouseY) {
        return mouseX >= organizeButtonX && mouseX < organizeButtonX + BUTTON_SIZE
            && mouseY >= organizeButtonY && mouseY < organizeButtonY + BUTTON_SIZE;
    }

    private void playOrganizeSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHIP.get(), 1.0F));
        }
    }

    /** Includes both side actions and the palette only while it is visible. */
    public List<Rect2i> jeiGuiExtraAreas() {
        layoutExtraActions();
        List<Rect2i> areas = new ArrayList<>(3);
        areas.add(new Rect2i(colorButtonX - 1, colorButtonY - 1, BUTTON_SIZE + 2, BUTTON_SIZE + 6));
        areas.add(new Rect2i(organizeButtonX - 1, organizeButtonY - 1, BUTTON_SIZE + 2, BUTTON_SIZE + 2));
        if (wheel.active()) areas.add(wheel.bounds());
        areas.addAll(extraActions.bounds());
        return List.copyOf(areas);
    }

}
