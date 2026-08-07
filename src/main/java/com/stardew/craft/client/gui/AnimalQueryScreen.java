package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.AnimalQueryClientDetails;
import com.stardew.craft.client.TemporaryGuiVisibility;
import com.stardew.craft.client.gui.common.SdvEditBoxRenderer;
import com.stardew.craft.client.gui.common.SdvFontAdapter;
import com.stardew.craft.client.gui.common.SdvTexture;
import com.stardew.craft.client.gui.common.SdvTooltipRenderer;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.menu.AnimalQueryMenu;
import com.stardew.craft.network.payload.AnimalQueryActionPayload;
import com.stardew.craft.network.payload.AnimalRenamePayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Stardew Valley's AnimalQueryMenu, adapted only where Minecraft needs a
 * different interaction (the move-home button opens the building list).
 */
@SuppressWarnings("null")
public final class AnimalQueryScreen extends Screen implements MenuAccess<AnimalQueryMenu> {
    private static final int SDV_WIDTH = 384;
    private static final int SDV_RUSSIAN_WIDTH = 416;
    private static final int SDV_HEIGHT = 512;
    private static final int SDV_BORDER = 40;
    private static final int BACKGROUND_TINT = 0xBF000000;
    private static final int TEXT_COLOR = 0xFF5B3A1A;
    private static final float TEXT_VISUAL_SCALE = 1.5F;

    private static final SdvTexture SELL = queryTexture("sell_icon", 16, 16);
    private static final SdvTexture MOVE = queryTexture("move_icon", 16, 16);
    private static final SdvTexture REPRO_ON = queryTexture("repro_on", 9, 9);
    private static final SdvTexture REPRO_OFF = queryTexture("repro_off", 9, 9);
    private static final SdvTexture HEART_FILLED = queryTexture("heart_filled", 7, 6);
    private static final SdvTexture HEART_EMPTY = queryTexture("heart_empty", 7, 6);
    private static final SdvTexture HEART_HALF_FILL = queryTexture("heart_half_fill", 4, 6);
    private static final SdvTexture GOLDEN_CRACKER = queryTexture("golden_animal_cracker", 16, 16);
    private static final SdvTexture OK = purchaseTexture("ok_button", 64, 64);
    private static final SdvTexture CANCEL = purchaseTexture("cancel_button", 64, 64);

    private final AnimalQueryMenu menu;
    private float guiScale;
    private int sourceWidth;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int nameX;
    private int nameY;
    private int nameW;
    private int nameFrameX;
    private int nameFrameY;
    private int nameFrameW;
    private int nameFrameH;
    private int okX;
    private int okY;
    private int sellX;
    private int sellY;
    private int moveX;
    private int moveY;
    private int reproX;
    private int reproY;
    private int buttonSize;
    private int reproSize;

    private EditBox nameField;
    private String submittedName;
    private boolean confirmingSell;
    private boolean animalSoundPlayed;
    private float okScale = 1.0F;
    private float sellScale = 4.0F;
    private float moveScale = 4.0F;
    private float reproScale = 4.0F;
    private float yesScale = 1.0F;
    private float noScale = 1.0F;

    public AnimalQueryScreen(AnimalQueryMenu menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
        this.submittedName = title.getString();
    }

    @Override
    public AnimalQueryMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        super.init();
        captureHudVisibility();
        computeLayout();

        this.nameField = new EditBox(
            com.stardew.craft.client.font.StardewFonts.dialogue(),
            this.nameX,
            this.nameY,
            this.nameW,
            this.font.lineHeight + 6,
            Component.empty()
        );
        this.nameField.setMaxLength(128);
        this.nameField.setBordered(false);
        this.nameField.setTextShadow(false);
        this.nameField.setTextColor(TEXT_COLOR);
        this.nameField.setValue(this.submittedName);
        this.nameField.setFocused(false);
        addWidget(this.nameField);

        if (!this.animalSoundPlayed) {
            this.animalSoundPlayed = true;
            SoundEvent animalSound = resolveAnimalSound();
            if (animalSound != null) {
                play(animalSound, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This is deliberately a plain Screen: the query has no inventory
        // slots, and treating it as a container makes JEI cover the menu.
        renderMenu(graphics, mouseX, mouseY);
    }

    private void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        computeLayout();
        updateHoverScales(mouseX, mouseY);

        graphics.fill(0, 0, this.width, this.height, BACKGROUND_TINT);
        StardewGuiUtil.drawDialogueBoxFrame(
            graphics,
            this.panelX,
            this.panelY + ui(128),
            this.panelW,
            this.panelH - ui(128)
        );
        drawNameFieldFrame(graphics);
        drawAge(graphics);
        drawFriendship(graphics);
        drawMood(graphics);
        drawAuthorityDetails(graphics);
        drawGoldenCracker(graphics);
        drawButtons(graphics);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.nameField != null) {
            this.nameField.visible = !this.confirmingSell;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.confirmingSell) {
            drawSellConfirmation(graphics, mouseX, mouseY);
        } else {
            drawNameFieldText(graphics);
            drawHoverText(graphics, mouseX, mouseY);
        }
    }

    private void drawNameFieldText(GuiGraphics graphics) {
        float scale = dialogueTextScale();
        int maxWidth = this.nameW - ui(21);
        SdvEditBoxRenderer.draw(graphics, this.font, this.nameField,
            this.nameX, this.nameY, maxWidth,
            scale, this.guiScale, TEXT_COLOR);
    }

    private void drawNameFieldFrame(GuiGraphics graphics) {
        StardewGuiUtil.drawDialogueBoxFrame(
            graphics,
            this.nameFrameX,
            this.nameFrameY,
            this.nameFrameW,
            this.nameFrameH
        );
    }

    private void drawAge(GuiGraphics graphics) {
        int months = (this.menu.getDaysOwned() + 1) / 28 + 1;
        Component age = months <= 1
            ? Component.translatable("stardewcraft.animal.query.age_one_month")
            : Component.translatable("stardewcraft.animal.query.age_months", months);
        if (this.menu.isBaby()) {
            age = Component.literal(age.getString())
                .append(Component.translatable("stardewcraft.animal.query.age_baby_suffix"));
        }
        float scale = smallTextScale();
        int maxWidth = ui(this.sourceWidth - 96);
        Font smallFont = SdvFontAdapter.font(SdvFontAdapter.Style.SMALL);
        scale = Math.min(scale, maxWidth / (float) Math.max(1, smallFont.width(age)));
        SdvFontAdapter.draw(graphics, this.font, age,
            this.panelX + ui(SDV_BORDER + 32), this.panelY + ui(240), scale, TEXT_COLOR,
            SdvFontAdapter.Style.SMALL);
    }

    private void drawFriendship(GuiGraphics graphics) {
        int friendship = Math.max(0, Math.min(1000, this.menu.getFriendship()));
        int halfHeart = friendship % 200 >= 100 ? friendship / 200 : -1;
        for (int i = 0; i < 5; i++) {
            int x = this.panelX + ui(96 + 32 * i);
            int y = this.panelY + ui(288);
            SdvTexture base = friendship <= (i + 1) * 195 ? HEART_EMPTY : HEART_FILLED;
            base.drawPixelZoom(graphics, x, y, s4());
            if (halfHeart == i) {
                HEART_HALF_FILL.drawPixelZoom(graphics, x, y, s4());
            }
        }
    }

    private void drawMood(GuiGraphics graphics) {
        Component mood = Component.translatable(
            this.menu.getMoodTranslationKey(),
            displayedName()
        );
        int x = this.panelX + ui(SDV_BORDER + 32);
        int y = this.panelY + ui(324);
        int maxWidth = ui(this.sourceWidth - 96);
        float scale = smallTextScale();
        int wrapWidth = Math.max(1, (int) Math.floor(maxWidth / scale));
        Font smallFont = SdvFontAdapter.font(SdvFontAdapter.Style.SMALL);
        List<FormattedCharSequence> lines = smallFont.split(mood, wrapWidth);
        int lineStep = SdvFontAdapter.lineStep(
            this.minecraft.getLanguageManager().getSelected(), this.guiScale,
            SdvFontAdapter.Style.SMALL);
        lineStep = Math.max(lineStep, Math.round(this.font.lineHeight * scale + ui(6)));
        for (FormattedCharSequence line : lines) {
            SdvFontAdapter.draw(graphics, this.font, line, x, y, scale, TEXT_COLOR,
                SdvFontAdapter.Style.SMALL);
            y += lineStep;
        }
    }

    private void drawGoldenCracker(GuiGraphics graphics) {
        if (!this.menu.hasEatenAnimalCracker()) {
            return;
        }
        int x = this.panelX + this.panelW - ui(106);
        int y = this.panelY + ui(224);
        GOLDEN_CRACKER.drawPixelZoomTint(graphics, x + ui(2), y + ui(2), s4(), 0.0F, 0.0F, 0.0F, 0.35F);
        GOLDEN_CRACKER.drawPixelZoom(graphics, x, y, s4());
    }

    private void drawAuthorityDetails(GuiGraphics graphics) {
        int x = this.panelX + ui(SDV_BORDER + 32);
        int y = this.panelY + ui(400);
        int maxWidth = ui(this.sourceWidth - 96);
        float scale = smallTextScale() * 0.82F;

        AnimalQueryClientDetails.Details details =
                AnimalQueryClientDetails.get(
                        this.menu.getAnimalId());
        String parentName = details == null
                ? ""
                : details.parentName();
        Component parent = null;
        if (details != null && !parentName.isBlank()) {
            parent = Component.translatable(
                    "stardewcraft.animal.query.parent",
                    parentName);
        } else if (details == null
                && this.menu.getParentAnimalId() > 0) {
            parent = Component.translatable(
                    "stardewcraft.animal.query.parent.pending",
                    this.menu.getParentAnimalId());
        }
        int devicesY = y;
        if (parent != null) {
            scale = Math.min(
                    scale,
                    maxWidth / (float) Math.max(
                            1, SdvFontAdapter.font(SdvFontAdapter.Style.SMALL).width(parent)));
            SdvFontAdapter.draw(
                    graphics, this.font, parent, x, y, scale, TEXT_COLOR,
                    SdvFontAdapter.Style.SMALL);
            devicesY += ui(30);
        }

        Component devices = Component.translatable(
                "stardewcraft.animal.query.devices",
                statusText(this.menu.hasAutoPetter()),
                statusText(this.menu.hasAutoGrabber()),
                statusText(this.menu.hasAutoFeeder())
        );
        float deviceScale = Math.min(
                smallTextScale() * 0.72F,
                maxWidth / (float) Math.max(
                        1, SdvFontAdapter.font(SdvFontAdapter.Style.SMALL).width(devices)));
        SdvFontAdapter.draw(
                graphics, this.font, devices, x,
                devicesY, deviceScale, TEXT_COLOR, SdvFontAdapter.Style.SMALL);
    }

    private static Component statusText(boolean available) {
        return Component.translatable(
                available
                        ? "stardewcraft.animal.query.device.present"
                        : "stardewcraft.animal.query.device.absent");
    }

    private void drawButtons(GuiGraphics graphics) {
        drawCentered(graphics, OK, this.okX, this.okY,
            this.buttonSize, 64, this.okScale / this.guiScale);
        drawCentered(graphics, SELL, this.sellX, this.sellY,
            this.buttonSize, 16, this.sellScale / this.guiScale);
        drawCentered(graphics, MOVE, this.moveX, this.moveY,
            this.buttonSize, 16, this.moveScale / this.guiScale);
        if (this.menu.canToggleReproduction()) {
            drawCentered(
                graphics,
                this.menu.allowReproduction() ? REPRO_ON : REPRO_OFF,
                this.reproX,
                this.reproY,
                this.reproSize,
                9,
                this.reproScale / this.guiScale
            );
        }
    }

    private void drawSellConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND_TINT);
        int boxX = this.width / 2 - ui(160);
        int boxY = this.height / 2 - ui(192);
        int boxW = ui(320);
        int boxH = ui(256);
        StardewGuiUtil.drawDialogueBoxFrame(graphics, boxX, boxY, boxW, boxH);

        Component text = Component.translatable("stardewcraft.animal.query.confirm_sell");
        float scale = dialogueTextScale();
        int maxTextWidth = boxW - ui(64);
        scale = Math.min(scale, maxTextWidth / (float) Math.max(1, this.font.width(text)));
        int textX = this.width / 2 - SdvFontAdapter.width(this.font, text, scale,
            SdvFontAdapter.Style.DIALOGUE) / 2;
        SdvFontAdapter.draw(graphics, this.font, text,
            textX, this.height / 2 - ui(88), scale, TEXT_COLOR,
            SdvFontAdapter.Style.DIALOGUE);

        int yesX = this.width / 2 - ui(68);
        int noX = this.width / 2 + ui(4);
        int buttonY = this.height / 2 - ui(32);
        drawCentered(graphics, OK, yesX, buttonY,
            this.buttonSize, 64, this.yesScale / this.guiScale);
        drawCentered(graphics, CANCEL, noX, buttonY,
            this.buttonSize, 64, this.noScale / this.guiScale);
    }

    private void drawHoverText(GuiGraphics graphics, int mouseX, int mouseY) {
        Component hover = null;
        if (inside(mouseX, mouseY, this.sellX, this.sellY, this.buttonSize, this.buttonSize)) {
            hover = Component.translatable(
                "stardewcraft.animal.query.hover.sell",
                this.menu.getEstimatedSellPrice()
            );
        } else if (inside(mouseX, mouseY, this.moveX, this.moveY, this.buttonSize, this.buttonSize)) {
            hover = Component.translatable("stardewcraft.animal.query.hover.move");
        } else if (this.menu.canToggleReproduction()
            && inside(mouseX, mouseY, this.reproX, this.reproY, this.reproSize, this.reproSize)) {
            hover = Component.translatable("stardewcraft.animal.query.hover.repro");
        }
        if (hover != null) {
            SdvTooltipRenderer.draw(graphics, this.font, hover,
                mouseX, mouseY, this.width, this.height, this.guiScale,
                this.minecraft.getLanguageManager().getSelected());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (button == 1 && !this.confirmingSell) {
            closeFromButton();
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (this.confirmingSell) {
            int yesX = this.width / 2 - ui(68);
            int noX = this.width / 2 + ui(4);
            int buttonY = this.height / 2 - ui(32);
            if (inside(mx, my, yesX, buttonY, this.buttonSize, this.buttonSize)) {
                submitRenameIfChanged();
                PacketDistributor.sendToServer(new AnimalQueryActionPayload(
                    AnimalQueryActionPayload.Action.SELL, false));
                return true;
            }
            if (inside(mx, my, noX, buttonY, this.buttonSize, this.buttonSize)) {
                this.confirmingSell = false;
                this.nameField.visible = true;
                play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
                return true;
            }
            return true;
        }

        if (inside(mx, my, this.okX, this.okY, this.buttonSize, this.buttonSize)) {
            closeFromButton();
            return true;
        }
        if (inside(mx, my, this.sellX, this.sellY, this.buttonSize, this.buttonSize)) {
            this.confirmingSell = true;
            this.nameField.setFocused(false);
            this.nameField.visible = false;
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            return true;
        }
        if (inside(mx, my, this.moveX, this.moveY, this.buttonSize, this.buttonSize)) {
            submitRenameIfChanged();
            this.nameField.setFocused(false);
            PacketDistributor.sendToServer(new AnimalQueryActionPayload(
                AnimalQueryActionPayload.Action.MOVE_HOME, false));
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            return true;
        }
        if (this.menu.canToggleReproduction()
            && inside(mx, my, this.reproX, this.reproY, this.reproSize, this.reproSize)) {
            boolean allow = !this.menu.allowReproduction();
            this.menu.setAllowReproductionValue(allow);
            PacketDistributor.sendToServer(new AnimalQueryActionPayload(
                AnimalQueryActionPayload.Action.TOGGLE_REPRODUCTION, allow));
            play(ModSounds.DRUMKIT6.get(), 1.0F, 1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.confirmingSell) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                this.confirmingSell = false;
                this.nameField.visible = true;
                play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (this.nameField.isFocused()) {
                this.nameField.setFocused(false);
            } else {
                closeFromButton();
            }
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
            && this.nameField.isFocused()) {
            this.nameField.setFocused(false);
            submitRenameIfChanged();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeFromButton();
    }

    private void closeFromButton() {
        submitRenameIfChanged();
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        PacketDistributor.sendToServer(new AnimalQueryActionPayload(
            AnimalQueryActionPayload.Action.CLOSE, false));
        this.minecraft.setScreen(null);
    }

    private void submitRenameIfChanged() {
        if (this.nameField == null) {
            return;
        }
        String value = this.nameField.getValue().trim();
        if (value.isBlank()) {
            this.nameField.setValue(this.submittedName);
            return;
        }
        if (value.equals(this.submittedName)) {
            return;
        }
        this.submittedName = value;
        PacketDistributor.sendToServer(new AnimalRenamePayload(this.menu.getAnimalId(), value));
    }

    private void updateHoverScales(int mouseX, int mouseY) {
        this.okScale = approach(this.okScale,
            inside(mouseX, mouseY, this.okX, this.okY, this.buttonSize, this.buttonSize) ? 1.1F : 1.0F);
        this.sellScale = approach(this.sellScale,
            inside(mouseX, mouseY, this.sellX, this.sellY, this.buttonSize, this.buttonSize) ? 4.1F : 4.0F);
        this.moveScale = approach(this.moveScale,
            inside(mouseX, mouseY, this.moveX, this.moveY, this.buttonSize, this.buttonSize) ? 4.1F : 4.0F);
        this.reproScale = approach(this.reproScale,
            this.menu.canToggleReproduction()
                && inside(mouseX, mouseY, this.reproX, this.reproY, this.reproSize, this.reproSize)
                ? 4.1F : 4.0F);

        int yesX = this.width / 2 - ui(68);
        int noX = this.width / 2 + ui(4);
        int buttonY = this.height / 2 - ui(32);
        this.yesScale = approach(this.yesScale,
            this.confirmingSell && inside(mouseX, mouseY, yesX, buttonY, this.buttonSize, this.buttonSize)
                ? 1.1F : 1.0F);
        this.noScale = approach(this.noScale,
            this.confirmingSell && inside(mouseX, mouseY, noX, buttonY, this.buttonSize, this.buttonSize)
                ? 1.1F : 1.0F);
    }

    private void computeLayout() {
        this.guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());
        this.sourceWidth = "ru_ru".equals(this.minecraft.getLanguageManager().getSelected())
            ? SDV_RUSSIAN_WIDTH : SDV_WIDTH;
        this.panelW = ui(this.sourceWidth);
        this.panelH = ui(SDV_HEIGHT);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        this.nameFrameW = ui(336);
        this.nameFrameH = Math.max(ui(96), this.font.lineHeight + ui(48));
        this.nameFrameX = this.width / 2 - this.nameFrameW / 2;
        this.nameFrameY = this.panelY + ui(64);
        this.nameX = this.nameFrameX + ui(32);
        this.nameW = this.nameFrameW - ui(64);
        int renderedNameHeight = Math.max(1, Math.round(this.font.lineHeight * dialogueTextScale()));
        this.nameY = this.nameFrameY + (this.nameFrameH - renderedNameHeight) / 2;
        this.buttonSize = ui(64);
        this.reproSize = ui(36);
        this.okX = this.panelX + this.panelW + ui(4);
        this.okY = this.panelY + this.panelH - ui(64 + SDV_BORDER);
        this.sellX = this.panelX + this.panelW + ui(4);
        this.sellY = this.panelY + this.panelH - ui(192 + SDV_BORDER);
        this.moveX = this.panelX + this.panelW + ui(4);
        this.moveY = this.panelY + this.panelH - ui(256 + SDV_BORDER);
        this.reproX = this.panelX + this.panelW + ui(16);
        this.reproY = this.panelY + this.panelH - ui(128 + SDV_BORDER) + ui(8);
    }

    private String displayedName() {
        if (this.nameField == null || this.nameField.getValue().isBlank()) {
            return this.submittedName;
        }
        return this.nameField.getValue().trim();
    }

    private float smallTextScale() {
        return SdvFontAdapter.scale(this.font,
            this.minecraft.getLanguageManager().getSelected(), this.guiScale,
            SdvFontAdapter.Style.SMALL) * TEXT_VISUAL_SCALE;
    }

    private float dialogueTextScale() {
        return SdvFontAdapter.scale(this.font,
            this.minecraft.getLanguageManager().getSelected(), this.guiScale,
            SdvFontAdapter.Style.DIALOGUE) * TEXT_VISUAL_SCALE;
    }

    @Nullable
    private SoundEvent resolveAnimalSound() {
        return switch (this.menu.getVariantIndex()) {
            case 0, 1, 3 -> ModSounds.CLUCK.get();
            case 2 -> ModSounds.DUCK.get();
            case 4 -> ModSounds.RABBIT.get();
            case 5 -> ModSounds.OSTRICH.get();
            case 7 -> ModSounds.COW.get();
            case 8 -> ModSounds.GOAT.get();
            case 9, 10 -> ModSounds.SHEEP.get();
            case 11 -> ModSounds.PIG.get();
            default -> null;
        };
    }

    private void captureHudVisibility() {
        TemporaryGuiVisibility.acquire(TemporaryGuiVisibility.Owner.ANIMAL_QUERY);
    }

    @Override
    public void removed() {
        try {
            submitRenameIfChanged();
            AnimalQueryClientDetails.remove(this.menu.getAnimalId());
            super.removed();
        } finally {
            TemporaryGuiVisibility.release(TemporaryGuiVisibility.Owner.ANIMAL_QUERY);
        }
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / this.guiScale));
    }

    private float s4() {
        return 4.0F / this.guiScale;
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(sound, volume, pitch);
        }
    }

    private static void drawCentered(GuiGraphics graphics, SdvTexture texture,
                                     int boundsX, int boundsY, int boundsSize,
                                     int sourceSize, float scale) {
        int size = Math.round(sourceSize * scale);
        texture.drawPixelZoom(
            graphics,
            boundsX + (boundsSize - size) / 2,
            boundsY + (boundsSize - size) / 2,
            scale
        );
    }

    private static float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.05F);
        if (current > target) return Math.max(target, current - 0.05F);
        return current;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static SdvTexture queryTexture(String name, int width, int height) {
        return SdvTexture.full(ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/" + name + ".png"), width, height);
    }

    private static SdvTexture purchaseTexture(String name, int width, int height) {
        return SdvTexture.full(ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_purchase/" + name + ".png"), width, height);
    }
}
