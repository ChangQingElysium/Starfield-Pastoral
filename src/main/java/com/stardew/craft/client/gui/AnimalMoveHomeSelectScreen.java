package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.SdvTexture;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.AnimalMoveHomeSelectPayload;
import com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

/** Uses the same SDV building-list language as animal purchasing. */
@SuppressWarnings("null")
public final class AnimalMoveHomeSelectScreen extends Screen {
    private static final int SDV_W = 1000;
    private static final int SDV_H = 760;
    private static final int TEXT_COLOR = 0xFF5B3A1A;
    private static final int SUBTEXT_COLOR = 0xFF8B7355;
    private static final int DISABLED_COLOR = 0xFF9E9282;
    private static final int FULL_COLOR = 0xFFC14935;
    private static final int AVAILABLE_COLOR = 0xFF3D7A38;
    private static final int BACKGROUND_TINT = 0xBF000000;

    private static final SdvTexture OK_BUTTON = texture("ok_button", 64, 64);
    private static final SdvTexture CANCEL_BUTTON = texture("cancel_button", 64, 64);

    private final OpenAnimalMoveHomeScreenPayload payload;
    private final List<OpenAnimalMoveHomeScreenPayload.BuildingOption> options;

    private float guiScale;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int partitionY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int rowH;
    private int maxVisible;
    private int scrollOffset;
    private int selectedIndex = -1;
    private int upX;
    private int upY;
    private int downX;
    private int downY;
    private int okX;
    private int okY;
    private int cancelX;
    private int cancelY;
    private int buttonSize;
    private float okScale = 1.0F;
    private float cancelScale = 1.0F;
    private boolean previousHideGui;
    private boolean hudVisibilityCaptured;

    public AnimalMoveHomeSelectScreen(OpenAnimalMoveHomeScreenPayload payload) {
        super(Component.translatable("container.stardew_craft.animal_move_home"));
        this.payload = payload;
        this.options = List.copyOf(payload.options());
    }

    @Override
    protected void init() {
        super.init();
        if (!this.hudVisibilityCaptured) {
            this.previousHideGui = this.minecraft.options.hideGui;
            this.hudVisibilityCaptured = true;
        }
        this.minecraft.options.hideGui = true;
        this.guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());
        this.buttonSize = ui(64);
        int buttonGap = ui(4);
        this.panelW = Math.min(ui(SDV_W), this.width - this.buttonSize - buttonGap - 8);
        this.panelH = Math.min(ui(SDV_H), this.height - 12);
        this.panelX = (this.width - (this.panelW + buttonGap + this.buttonSize)) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int border = Math.max(1, ui(64));
        int pad = Math.max(6, ui(20));
        this.contentX = this.panelX + border;
        this.contentY = this.panelY + border;
        this.contentW = this.panelW - border * 2;
        this.contentH = this.panelH - border * 2;
        this.partitionY = this.contentY + Math.max(this.font.lineHeight + 10, ui(64));
        this.listX = this.contentX + pad;
        this.listY = this.partitionY + pad;
        this.listW = this.contentW - pad * 2;
        this.listH = this.contentY + this.contentH - this.listY - pad;
        this.rowH = Math.max(this.font.lineHeight * 2 + 12, ui(88));
        this.maxVisible = Math.max(1, this.listH / this.rowH);
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll());

        this.upX = this.panelX + this.panelW - border - ui(44);
        this.upY = this.listY;
        this.downX = this.upX;
        this.downY = this.listY + this.listH - Math.round(12 * s4());
        this.cancelX = this.panelX + this.panelW + buttonGap;
        this.cancelY = this.panelY + this.panelH - ui(104);
        this.okX = this.cancelX;
        this.okY = this.cancelY - this.buttonSize - ui(12);

        if (this.selectedIndex < 0 || this.selectedIndex >= this.options.size()
            || !this.options.get(this.selectedIndex).selectable()) {
            this.selectedIndex = firstSelectable();
        }
        keepSelectionVisible();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // SDV frame and fade are rendered explicitly.
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHover(mouseX, mouseY);
        graphics.fill(0, 0, this.width, this.height, BACKGROUND_TINT);
        StardewGuiUtil.drawDialogueBoxFrame(graphics,
            this.panelX, this.panelY, this.panelW, this.panelH);
        drawHeader(graphics);
        StardewGuiUtil.drawHorizontalPartition(
            graphics, this.panelX, this.partitionY, this.panelW, s4());
        drawRows(graphics, mouseX, mouseY);
        drawScrollControls(graphics);
        drawButtons(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        Component title = Component.translatable(
            "stardewcraft.animal.query.move_target", this.payload.animalName());
        GuiText.drawCenteredClamped(graphics, this.font, title,
            this.panelX + this.panelW / 2,
            this.contentY + (this.partitionY - this.contentY - this.font.lineHeight) / 2,
            this.contentW, TEXT_COLOR, false);
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.options.isEmpty()) {
            GuiText.drawCenteredClamped(graphics, this.font,
                Component.translatable("stardewcraft.animal.query.move_empty"),
                this.panelX + this.panelW / 2, this.listY + this.listH / 2,
                this.listW, DISABLED_COLOR, false);
            return;
        }

        int visible = Math.min(this.maxVisible, this.options.size() - this.scrollOffset);
        graphics.enableScissor(this.listX, this.listY,
            this.listX + this.listW, this.listY + this.listH);
        for (int row = 0; row < visible; row++) {
            int index = this.scrollOffset + row;
            OpenAnimalMoveHomeScreenPayload.BuildingOption option = this.options.get(index);
            int y = this.listY + row * this.rowH;
            boolean hovered = inside(mouseX, mouseY, this.listX, y, this.listW, this.rowH);
            boolean selected = index == this.selectedIndex;
            if (hovered || selected) {
                CommonGuiTextures.drawOptionHighlightBox(graphics,
                    this.listX, y + 2, this.listW, this.rowH - 4, s4());
            }
            if (selected) {
                graphics.fill(this.listX + 2, y + 5,
                    this.listX + 4, y + this.rowH - 5, 0xAA8A4B20);
            }

            int textX = this.listX + Math.max(8, ui(24));
            int textY = y + Math.max(5, (this.rowH - this.font.lineHeight * 2 - 3) / 2);
            int statusRight = this.listX + this.listW - Math.max(8, ui(24));
            String capacity = option.animalCount() + "/" + option.capacity();
            int capacityW = this.font.width(capacity);
            int nameMax = Math.max(1, statusRight - capacityW - ui(36) - textX);
            int rowColor = option.selectable() ? TEXT_COLOR : DISABLED_COLOR;
            graphics.drawString(this.font,
                GuiText.ellipsize(this.font, Component.literal(option.displayName()), nameMax),
                textX, textY, rowColor, false);
            graphics.drawString(this.font,
                GuiText.ellipsize(this.font, Component.literal(option.buildingId()), nameMax),
                textX, textY + this.font.lineHeight + 3, SUBTEXT_COLOR, false);

            Component status = Component.translatable(statusKey(option));
            int statusColor = option.selectable() ? AVAILABLE_COLOR
                : (isCurrent(option) ? SUBTEXT_COLOR : FULL_COLOR);
            graphics.drawString(this.font, capacity,
                statusRight - capacityW, textY, statusColor, false);
            graphics.drawString(this.font, status,
                statusRight - this.font.width(status),
                textY + this.font.lineHeight + 3, statusColor, false);
        }
        graphics.disableScissor();
    }

    private void drawScrollControls(GuiGraphics graphics) {
        if (this.options.size() <= this.maxVisible) return;
        if (this.scrollOffset > 0) {
            CommonGuiTextures.drawScrollArrowUp(graphics, this.upX, this.upY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowUpTint(
                graphics, this.upX, this.upY, s4(), 1, 1, 1, 0.4F);
        }
        if (this.scrollOffset < maxScroll()) {
            CommonGuiTextures.drawScrollArrowDown(graphics, this.downX, this.downY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowDownTint(
                graphics, this.downX, this.downY, s4(), 1, 1, 1, 0.4F);
        }
    }

    private void drawButtons(GuiGraphics graphics) {
        drawButton(graphics, OK_BUTTON, this.okX, this.okY, this.okScale,
            canMoveSelection() ? 1.0F : 0.45F);
        drawButton(graphics, CANCEL_BUTTON, this.cancelX, this.cancelY,
            this.cancelScale, 1.0F);
    }

    private void drawButton(GuiGraphics graphics, SdvTexture texture,
                            int x, int y, float hoverScale, float alpha) {
        float scale = (1.0F / this.guiScale) * hoverScale;
        int size = Math.round(64 * scale);
        if (alpha < 1.0F) {
            texture.drawPixelZoomTint(graphics,
                x + (this.buttonSize - size) / 2,
                y + (this.buttonSize - size) / 2,
                scale, 1, 1, 1, alpha);
        } else {
            texture.drawPixelZoom(graphics,
                x + (this.buttonSize - size) / 2,
                y + (this.buttonSize - size) / 2, scale);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (inside(mx, my, this.cancelX, this.cancelY, this.buttonSize, this.buttonSize)) {
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            onClose();
            return true;
        }
        if (inside(mx, my, this.okX, this.okY, this.buttonSize, this.buttonSize)) {
            confirmMove();
            return true;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            if (this.options.get(row).selectable()) {
                this.selectedIndex = row;
                play(ModSounds.SMALL_SELECT.get(), 0.8F, 1.05F);
            }
            return true;
        }
        int arrowW = Math.round(11 * s4());
        int arrowH = Math.round(12 * s4());
        if (inside(mx, my, this.upX, this.upY, arrowW, arrowH) && this.scrollOffset > 0) {
            this.scrollOffset--;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        if (inside(mx, my, this.downX, this.downY, arrowW, arrowH)
            && this.scrollOffset < maxScroll()) {
            this.scrollOffset++;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && this.scrollOffset > 0) {
            this.scrollOffset--;
            return true;
        }
        if (scrollY < 0 && this.scrollOffset < maxScroll()) {
            this.scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            confirmMove();
            return true;
        }
        if (keyCode == InputConstants.KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        if (this.options.isEmpty()) return;
        int next = this.selectedIndex;
        for (int i = 0; i < this.options.size(); i++) {
            next = Math.max(0, Math.min(this.options.size() - 1, next + direction));
            if (this.options.get(next).selectable()) {
                this.selectedIndex = next;
                keepSelectionVisible();
                play(ModSounds.SMALL_SELECT.get(), 0.8F, 1.0F);
                return;
            }
            if (next == 0 || next == this.options.size() - 1) return;
        }
    }

    private void confirmMove() {
        if (!canMoveSelection()) {
            play(ModSounds.SMALL_SELECT.get(), 0.7F, 0.8F);
            return;
        }
        OpenAnimalMoveHomeScreenPayload.BuildingOption selected =
            this.options.get(this.selectedIndex);
        PacketDistributor.sendToServer(new AnimalMoveHomeSelectPayload(
            this.payload.animalId(), selected.buildingId()));
        play(ModSounds.NEW_RECIPE.get(), 0.9F, 1.0F);
        onClose();
    }

    private void updateHover(int mouseX, int mouseY) {
        this.okScale = approach(this.okScale,
            inside(mouseX, mouseY, this.okX, this.okY, this.buttonSize, this.buttonSize)
                ? 1.1F : 1.0F);
        this.cancelScale = approach(this.cancelScale,
            inside(mouseX, mouseY, this.cancelX, this.cancelY, this.buttonSize, this.buttonSize)
                ? 1.1F : 1.0F);
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, this.listX, this.listY, this.listW, this.listH)) return -1;
        int visibleRow = (mouseY - this.listY) / this.rowH;
        int index = this.scrollOffset + visibleRow;
        return visibleRow < this.maxVisible && index < this.options.size() ? index : -1;
    }

    private int firstSelectable() {
        for (int i = 0; i < this.options.size(); i++) {
            if (this.options.get(i).selectable()) return i;
        }
        return -1;
    }

    private void keepSelectionVisible() {
        if (this.selectedIndex < 0) return;
        if (this.selectedIndex < this.scrollOffset) this.scrollOffset = this.selectedIndex;
        if (this.selectedIndex >= this.scrollOffset + this.maxVisible) {
            this.scrollOffset = this.selectedIndex - this.maxVisible + 1;
        }
    }

    private boolean canMoveSelection() {
        return this.selectedIndex >= 0
            && this.selectedIndex < this.options.size()
            && this.options.get(this.selectedIndex).selectable();
    }

    private boolean isCurrent(OpenAnimalMoveHomeScreenPayload.BuildingOption option) {
        return option.buildingId().equals(this.payload.currentBuildingId());
    }

    private String statusKey(OpenAnimalMoveHomeScreenPayload.BuildingOption option) {
        if (option.selectable()) return "stardewcraft.animal.query.move_status.available";
        if (isCurrent(option)) return "stardewcraft.animal.query.move_status.current";
        return "stardewcraft.animal.query.move_status.full";
    }

    private int maxScroll() {
        return Math.max(0, this.options.size() - this.maxVisible);
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / this.guiScale));
    }

    private float s4() {
        return 4.0F / this.guiScale;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.hudVisibilityCaptured) {
            this.minecraft.options.hideGui = this.previousHideGui;
            this.hudVisibilityCaptured = false;
        }
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(sound, volume, pitch);
        }
    }

    private static float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.05F);
        if (current > target) return Math.max(target, current - 0.05F);
        return current;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static SdvTexture texture(String name, int width, int height) {
        return SdvTexture.full(ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_purchase/" + name + ".png"), width, height);
    }
}
