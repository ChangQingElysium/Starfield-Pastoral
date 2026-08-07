package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.TemporaryGuiVisibility;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.SdvEditBoxRenderer;
import com.stardew.craft.client.gui.common.SdvFontAdapter;
import com.stardew.craft.client.gui.common.SdvTexture;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.hud.StardewHudMessageManager;
import com.stardew.craft.network.payload.AnimalPurchaseSubmitPayload;
import com.stardew.craft.network.payload.IncubatorClaimSubmitPayload;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * SDV-framed replacement for the original in-world building placement stage.
 * Buildings are presented as the single vertical list requested by the mod,
 * while the frame, partitions, controls, naming prompt, sounds, and scaling
 * follow the same source and extracted-texture rules as the other SDV screens.
 */
@SuppressWarnings("null")
public final class AnimalPurchaseBuildingScreen extends Screen {
    private static final int SDV_W = 1000;
    private static final int SDV_H = 760;
    private static final int BACKGROUND_TINT = 0xBF000000;
    private static final int TEXT_COLOR = 0xFF5B3A1A;
    private static final int SUBTEXT_COLOR = 0xFF8B7355;
    private static final int DISABLED_COLOR = 0xFF9E9282;
    private static final int FULL_COLOR = 0xFFC14935;
    private static final int AVAILABLE_COLOR = 0xFF3D7A38;
    private static final long GLOBAL_FADE_MS = 833L;
    private static final float TEXT_VISUAL_SCALE = 1.5F;
    private static final float NAMING_HEADER_SCALE = 1.25F;

    private enum ExitTransition {
        NONE,
        SHOP,
        SUCCESS
    }

    private static final SdvTexture OK_BUTTON = texture("ok_button", 64, 64);
    private static final SdvTexture CANCEL_BUTTON = texture("cancel_button", 64, 64);
    private static final SdvTexture RANDOM_BUTTON = texture("random_name", 10, 10);

    private final OpenAnimalPurchaseScreenPayload payload;
    private final OpenAnimalPurchaseScreenPayload.AnimalOption animal;
    private final List<OpenAnimalPurchaseScreenPayload.BuildingOption> buildings = new ArrayList<>();
    private final Random random = new Random();

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
    private int closeX;
    private int closeY;
    private int closeSize;
    private int upX;
    private int upY;
    private int downX;
    private int downY;
    private float closeScale = 1.0F;

    private EditBox nameField;
    private boolean naming;
    private boolean submitting;
    private int nameBoxX;
    private int nameBoxY;
    private int nameBoxW;
    private int nameBoxH;
    private int nameFieldBoxX;
    private int nameFieldBoxY;
    private int nameFieldBoxW;
    private int nameFieldBoxH;
    private int nameOkX;
    private int nameOkY;
    private int nameOkSize;
    private int randomX;
    private int randomY;
    private int randomSize;
    private float nameOkScale = 1.0F;
    private float randomScale = 1.0F;
    private long openedAtMs;
    private long exitTransitionStartedAtMs;
    private ExitTransition exitTransition = ExitTransition.NONE;
    private String purchasedAnimalName = "";

    public AnimalPurchaseBuildingScreen(OpenAnimalPurchaseScreenPayload payload,
                                        OpenAnimalPurchaseScreenPayload.AnimalOption animal) {
        super(Component.translatable("stardewcraft.animal.purchase.buildings"));
        this.payload = payload;
        this.animal = animal;
        for (OpenAnimalPurchaseScreenPayload.BuildingOption building : payload.buildingOptions()) {
            if (animal.family().equalsIgnoreCase(building.family())
                && building.tier() >= animal.requiredTier()) {
                this.buildings.add(building);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        captureHudVisibility();
        this.guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());
        this.panelW = Math.min(ui(SDV_W), this.width - 8);
        this.panelH = Math.min(ui(SDV_H), this.height - 8);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int border = Math.max(1, ui(64));
        this.contentX = this.panelX + border;
        this.contentY = this.panelY + border;
        this.contentW = this.panelW - border * 2;
        this.contentH = this.panelH - border * 2;
        int titleH = Math.max(this.font.lineHeight + 10, ui(64));
        this.partitionY = this.contentY + titleH;

        int pad = Math.max(6, ui(20));
        this.listX = this.contentX + pad;
        this.listY = this.partitionY + pad;
        this.listW = this.contentW - pad * 2;
        this.listH = this.contentY + this.contentH - this.listY - pad;
        this.rowH = Math.max(this.font.lineHeight * 2 + 12, ui(88));
        this.maxVisible = Math.max(1, this.listH / this.rowH);
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll());

        this.closeSize = ui(64);
        this.closeX = this.panelX + this.panelW + ui(4);
        this.closeY = this.panelY + this.panelH - ui(104);

        this.upX = this.panelX + this.panelW - border - ui(44);
        this.upY = this.listY;
        this.downX = this.upX;
        this.downY = this.listY + this.listH - Math.round(12 * s4());

        this.nameBoxW = ui(512);
        this.nameBoxH = ui(112);
        this.nameBoxX = this.width / 2 - ui(256);
        this.nameBoxY = this.height / 2 - ui(160);
        this.nameOkSize = ui(64);
        this.randomSize = Math.round(10 * s4());
        this.nameFieldBoxW = ui(336);
        this.nameFieldBoxH = ui(96);
        this.nameFieldBoxX = this.width / 2 - this.nameFieldBoxW / 2;
        this.nameFieldBoxY = this.height / 2 - ui(32);
        int textBoxX = this.nameFieldBoxX + ui(32);
        int textBoxW = this.nameFieldBoxW - ui(64);
        int renderedTextHeight = Math.max(1, Math.round(this.font.lineHeight * dialogueTextScale()));
        int textBoxY = this.nameFieldBoxY + (this.nameFieldBoxH - renderedTextHeight) / 2;
        this.nameField = new EditBox(com.stardew.craft.client.font.StardewFonts.dialogue(),
            textBoxX, textBoxY, textBoxW, this.font.lineHeight + 6,
            Component.translatable("stardewcraft.animal.purchase.name_hint"));
        this.nameField.setMaxLength(128);
        this.nameField.setBordered(false);
        this.nameField.setTextShadow(false);
        this.nameField.setTextColor(TEXT_COLOR);
        this.nameField.visible = this.naming;
        addWidget(this.nameField);

        this.nameOkX = this.nameFieldBoxX + this.nameFieldBoxW + ui(16);
        this.nameOkY = this.nameFieldBoxY + (this.nameFieldBoxH - this.nameOkSize) / 2;
        this.randomX = this.nameOkX + this.nameOkSize + ui(12);
        this.randomY = this.nameFieldBoxY + (this.nameFieldBoxH - this.randomSize) / 2;

        if (this.payload.incubatorMode()) {
            this.selectedIndex = firstAvailableBuilding();
            if (this.selectedIndex >= 0) {
                openNaming(false);
            }
        }
        this.openedAtMs = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // SDV supplies the fade and panel itself.
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND_TINT);
        updateHover(mouseX, mouseY);

        if (this.naming) {
            drawPlacementHeader(graphics);
            drawNamingPrompt(graphics, mouseX, mouseY, partialTick);
        } else {
            StardewGuiUtil.drawDialogueBoxFrame(
                graphics, this.panelX, this.panelY, this.panelW, this.panelH);
            drawHeader(graphics);
            StardewGuiUtil.drawHorizontalPartition(
                graphics, this.panelX, this.partitionY, this.panelW, s4());
            drawBuildingList(graphics, mouseX, mouseY);
            drawScrollControls(graphics);
            drawCloseButton(graphics);
        }
        drawFade(graphics);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.exitTransition == ExitTransition.NONE
            || System.currentTimeMillis() - this.exitTransitionStartedAtMs < GLOBAL_FADE_MS) {
            return;
        }
        ExitTransition completed = this.exitTransition;
        this.exitTransition = ExitTransition.NONE;
        if (completed == ExitTransition.SHOP) {
            this.minecraft.setScreen(new AnimalPurchaseScreen(this.payload));
            return;
        }
        String dialogue = Component.translatable(
            "stardewcraft.animal.purchase.marnie_success", this.purchasedAnimalName).getString();
        this.minecraft.setScreen(new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
            "marnie", dialogue, 0));
    }

    private void drawHeader(GuiGraphics graphics) {
        Component title = placementTitle();
        GuiText.drawCenteredClamped(graphics, this.font, title, this.panelX + this.panelW / 2,
            this.contentY + (this.partitionY - this.contentY - this.font.lineHeight) / 2,
            this.contentW, TEXT_COLOR, false);
    }

    private Component placementTitle() {
        String house = Component.translatable(
            "stardewcraft.manager.building." + this.animal.family().toLowerCase(Locale.ROOT)).getString();
        return Component.translatable(
            "stardewcraft.animal.shop.choose_home", house, this.animal.displayName());
    }

    private void drawPlacementHeader(GuiGraphics graphics) {
        float textScale = SdvFontAdapter.scale(this.font,
            this.minecraft.getLanguageManager().getSelected(), this.guiScale / NAMING_HEADER_SCALE,
            SdvFontAdapter.Style.SPRITE_TEXT);
        int maxFontWidth = Math.max(1, (int) Math.floor((this.width - ui(128)) / textScale));
        Font spriteFont = SdvFontAdapter.font(SdvFontAdapter.Style.SPRITE_TEXT);
        Component title = GuiText.ellipsize(spriteFont, placementTitle(), maxFontWidth);
        int textWidth = SdvFontAdapter.width(this.font, title, textScale,
            SdvFontAdapter.Style.SPRITE_TEXT);
        int textX = this.width / 2 - textWidth / 2;
        float bannerScale = s4() * NAMING_HEADER_SCALE;
        int bannerY = ui(16) - Math.round(3 * bannerScale);
        CommonGuiTextures.drawScrollBanner(
            graphics, textX, bannerY, Math.max(1, textWidth), bannerScale);
        int renderedHeight = Math.max(1, Math.round(this.font.lineHeight * textScale));
        int textY = bannerY + (Math.round(18 * bannerScale) - renderedHeight) / 2;
        SdvFontAdapter.draw(graphics, this.font, title, textX, textY, textScale, TEXT_COLOR,
            SdvFontAdapter.Style.SPRITE_TEXT);
    }

    private void drawBuildingList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.buildings.isEmpty()) {
            GuiText.drawCenteredClamped(graphics, this.font,
                Component.translatable("stardewcraft.animal.purchase.no_building"),
                this.panelX + this.panelW / 2, this.listY + this.listH / 2,
                this.listW, DISABLED_COLOR, false);
            return;
        }

        int visible = Math.min(this.maxVisible, this.buildings.size() - this.scrollOffset);
        graphics.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
        for (int row = 0; row < visible; row++) {
            int index = this.scrollOffset + row;
            OpenAnimalPurchaseScreenPayload.BuildingOption building = this.buildings.get(index);
            int y = this.listY + row * this.rowH;
            boolean full = building.animalCount() >= building.capacity();
            boolean hovered = inside(mouseX, mouseY, this.listX, y, this.listW, this.rowH);
            boolean selected = index == this.selectedIndex;

            if (selected || hovered) {
                CommonGuiTextures.drawOptionHighlightBox(graphics,
                    this.listX, y + 2, this.listW, this.rowH - 4, s4());
            }
            if (selected) {
                graphics.fill(this.listX + 2, y + 5, this.listX + 4, y + this.rowH - 5, 0xAA8A4B20);
            }

            int textX = this.listX + Math.max(8, ui(24));
            int nameY = y + Math.max(5, (this.rowH - this.font.lineHeight * 2 - 3) / 2);
            int statusRight = this.listX + this.listW - Math.max(8, ui(24));
            int capacityW = this.font.width(building.animalCount() + "/" + building.capacity());
            int nameMax = Math.max(1, statusRight - capacityW - ui(36) - textX);
            int color = full ? DISABLED_COLOR : TEXT_COLOR;
            graphics.drawString(this.font,
                GuiText.ellipsize(this.font, Component.literal(building.displayName()), nameMax),
                textX, nameY, color, false);

            Component tier = Component.translatable("stardewcraft.animal.shop.require_tier", building.tier());
            graphics.drawString(this.font, GuiText.ellipsize(this.font, tier, nameMax),
                textX, nameY + this.font.lineHeight + 3, SUBTEXT_COLOR, false);

            String capacity = building.animalCount() + "/" + building.capacity();
            graphics.drawString(this.font, capacity, statusRight - this.font.width(capacity), nameY,
                full ? FULL_COLOR : AVAILABLE_COLOR, false);
            Component status = Component.translatable(full
                ? "stardewcraft.ui.building.full" : "stardewcraft.ui.building.available");
            graphics.drawString(this.font, status,
                statusRight - this.font.width(status), nameY + this.font.lineHeight + 3,
                full ? FULL_COLOR : AVAILABLE_COLOR, false);
        }
        graphics.disableScissor();
    }

    private void drawScrollControls(GuiGraphics graphics) {
        if (this.buildings.size() <= this.maxVisible) {
            return;
        }
        if (this.scrollOffset > 0) {
            CommonGuiTextures.drawScrollArrowUp(graphics, this.upX, this.upY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowUpTint(graphics, this.upX, this.upY, s4(), 1, 1, 1, 0.4F);
        }
        if (this.scrollOffset < maxScroll()) {
            CommonGuiTextures.drawScrollArrowDown(graphics, this.downX, this.downY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowDownTint(graphics, this.downX, this.downY, s4(), 1, 1, 1, 0.4F);
        }
    }

    private void drawCloseButton(GuiGraphics graphics) {
        if (this.naming) {
            return;
        }
        float scale = (1.0F / this.guiScale) * this.closeScale;
        int size = Math.round(64 * scale);
        CANCEL_BUTTON.drawPixelZoom(graphics,
            this.closeX + (this.closeSize - size) / 2,
            this.closeY + (this.closeSize - size) / 2,
            scale);
    }

    private void drawNamingPrompt(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        StardewGuiUtil.drawDialogueBoxFrame(
            graphics, this.nameBoxX, this.nameBoxY, this.nameBoxW, this.nameBoxH);

        Component label = Component.translatable("stardewcraft.animal.shop.name_new");
        float dialogueScale = dialogueTextScale();
        int labelMaxWidth = ui(432);
        dialogueScale = Math.min(dialogueScale,
            labelMaxWidth / (float) Math.max(1, this.font.width(label)));
        SdvFontAdapter.draw(graphics, this.font, label,
            this.nameBoxX + ui(40),
            this.nameBoxY + (this.nameBoxH - Math.round(this.font.lineHeight * dialogueScale)) / 2,
            dialogueScale, TEXT_COLOR);

        StardewGuiUtil.drawDialogueBoxFrame(graphics,
            this.nameFieldBoxX, this.nameFieldBoxY, this.nameFieldBoxW, this.nameFieldBoxH);
        SdvEditBoxRenderer.draw(graphics, this.font, this.nameField,
            this.nameField.getX(), this.nameField.getY(), this.nameField.getWidth(),
            dialogueScale, this.guiScale, TEXT_COLOR);

        float okScale = (1.0F / this.guiScale) * this.nameOkScale;
        int okSize = Math.round(64 * okScale);
        OK_BUTTON.drawPixelZoom(graphics,
            this.nameOkX + (this.nameOkSize - okSize) / 2,
            this.nameOkY + (this.nameOkSize - okSize) / 2,
            okScale);

        float diceScale = s4() * this.randomScale;
        int diceSize = Math.round(10 * diceScale);
        RANDOM_BUTTON.drawPixelZoom(graphics,
            this.randomX + (this.randomSize - diceSize) / 2,
            this.randomY + (this.randomSize - diceSize) / 2,
            diceScale);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isTransitionActive()) {
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (this.naming) {
            if (!this.submitting && inside(mx, my, this.nameOkX, this.nameOkY, this.nameOkSize, this.nameOkSize)) {
                submit();
                return true;
            }
            if (!this.submitting && inside(mx, my, this.randomX, this.randomY, this.randomSize, this.randomSize)) {
                rerollName();
                play(ModSounds.DRUMKIT6.get(), 1.0F, 1.0F);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (inside(mx, my, this.closeX, this.closeY, this.closeSize, this.closeSize)) {
            returnToShop();
            return true;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            OpenAnimalPurchaseScreenPayload.BuildingOption building = this.buildings.get(row);
            if (building.animalCount() >= building.capacity()) {
                StardewHudMessageManager.showError(Component.translatable("stardewcraft.animal.purchase.building_full"));
                return true;
            }
            this.selectedIndex = row;
            openNaming(true);
            return true;
        }
        int arrowW = Math.round(11 * s4());
        int arrowH = Math.round(12 * s4());
        if (inside(mx, my, this.upX, this.upY, arrowW, arrowH) && this.scrollOffset > 0) {
            this.scrollOffset--;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        if (inside(mx, my, this.downX, this.downY, arrowW, arrowH) && this.scrollOffset < maxScroll()) {
            this.scrollOffset++;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isTransitionActive()) {
            return true;
        }
        if (this.naming) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY > 0 && this.scrollOffset > 0) {
            this.scrollOffset--;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        if (scrollY < 0 && this.scrollOffset < maxScroll()) {
            this.scrollOffset++;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTransitionActive()) {
            return true;
        }
        if (this.naming) {
            if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && !this.submitting) {
                submit();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE && !this.submitting) {
                if (this.payload.incubatorMode()) {
                    onClose();
                } else {
                    closeNaming();
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            returnToShop();
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
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
            && this.selectedIndex >= 0) {
            OpenAnimalPurchaseScreenPayload.BuildingOption selected = this.buildings.get(this.selectedIndex);
            if (selected.animalCount() < selected.capacity()) {
                openNaming(true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int delta) {
        if (this.buildings.isEmpty()) return;
        int next = this.selectedIndex < 0 ? firstAvailableBuilding() : this.selectedIndex + delta;
        next = Math.max(0, Math.min(this.buildings.size() - 1, next));
        while (next >= 0 && next < this.buildings.size()
            && this.buildings.get(next).animalCount() >= this.buildings.get(next).capacity()) {
            int candidate = next + delta;
            if (candidate < 0 || candidate >= this.buildings.size()) break;
            next = candidate;
        }
        this.selectedIndex = next;
        if (this.selectedIndex < this.scrollOffset) this.scrollOffset = this.selectedIndex;
        if (this.selectedIndex >= this.scrollOffset + this.maxVisible) {
            this.scrollOffset = this.selectedIndex - this.maxVisible + 1;
        }
        play(ModSounds.SMALL_SELECT.get(), 0.8F, 1.0F);
    }

    private void openNaming(boolean playAnimal) {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.buildings.size()) return;
        this.naming = true;
        this.submitting = false;
        this.nameField.visible = true;
        rerollName();
        setFocused(this.nameField);
        this.nameField.setFocused(true);
        if (playAnimal) playAnimalSound();
    }

    private void closeNaming() {
        this.naming = false;
        this.nameField.visible = false;
        this.nameField.setFocused(false);
        setFocused(null);
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
    }

    private void rerollName() {
        String language = this.minecraft.getLanguageManager().getSelected();
        String value = SdvAnimalNameGenerator.randomName(language, this.random);
        this.nameField.setValue(value);
        this.nameField.setCursorPosition(value.length());
    }

    private void submit() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            StardewHudMessageManager.showError(Component.translatable("stardewcraft.animal.purchase.name_unavailable"));
            return;
        }
        if (this.selectedIndex < 0 || this.selectedIndex >= this.buildings.size()) {
            StardewHudMessageManager.showError(Component.translatable("stardewcraft.animal.purchase.no_building"));
            return;
        }
        this.submitting = true;
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        if (this.payload.incubatorMode()) {
            PacketDistributor.sendToServer(new IncubatorClaimSubmitPayload(
                BlockPos.of(this.payload.contextBlockPos()), name));
            onClose();
            return;
        }
        PacketDistributor.sendToServer(new AnimalPurchaseSubmitPayload(
            this.animal.animalTypeId(), this.buildings.get(this.selectedIndex).buildingId(), name));
    }

    public void handlePurchaseFailure(String messageKey) {
        this.submitting = false;
        StardewHudMessageManager.showError(Component.translatable(messageKey));
        setFocused(this.nameField);
        this.nameField.setFocused(true);
    }

    public void handlePurchaseSuccess(String animalName) {
        this.purchasedAnimalName = animalName == null ? "" : animalName;
        this.exitTransition = ExitTransition.SUCCESS;
        this.exitTransitionStartedAtMs = System.currentTimeMillis();
    }

    private void returnToShop() {
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        if (this.payload.incubatorMode()) {
            onClose();
        } else {
            this.exitTransition = ExitTransition.SHOP;
            this.exitTransitionStartedAtMs = System.currentTimeMillis();
        }
    }

    private void drawFade(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        if (this.exitTransition != ExitTransition.NONE) {
            float progress = Math.min(1.0F,
                (now - this.exitTransitionStartedAtMs) / (float) GLOBAL_FADE_MS);
            graphics.fill(0, 0, this.width, this.height, ((int) (progress * 255.0F)) << 24);
            return;
        }
        float progress = Math.min(1.0F, (now - this.openedAtMs) / (float) GLOBAL_FADE_MS);
        if (progress < 1.0F) {
            graphics.fill(0, 0, this.width, this.height,
                ((int) ((1.0F - progress) * 255.0F)) << 24);
        }
    }

    private boolean isTransitionActive() {
        return this.exitTransition != ExitTransition.NONE
            || System.currentTimeMillis() - this.openedAtMs < GLOBAL_FADE_MS;
    }

    private void updateHover(int mouseX, int mouseY) {
        this.closeScale = approach(this.closeScale,
            !this.naming && inside(mouseX, mouseY, this.closeX, this.closeY, this.closeSize, this.closeSize)
                ? 1.1F : 1.0F, 0.05F);
        this.nameOkScale = approach(this.nameOkScale,
            this.naming && inside(mouseX, mouseY, this.nameOkX, this.nameOkY, this.nameOkSize, this.nameOkSize)
                ? 1.1F : 1.0F, 0.05F);
        this.randomScale = approach(this.randomScale,
            this.naming && inside(mouseX, mouseY, this.randomX, this.randomY, this.randomSize, this.randomSize)
                ? 1.125F : 1.0F, 0.01F);
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, this.listX, this.listY, this.listW, this.listH)) return -1;
        int row = (mouseY - this.listY) / this.rowH;
        int index = this.scrollOffset + row;
        return index >= 0 && index < this.buildings.size() && row < this.maxVisible ? index : -1;
    }

    private int firstAvailableBuilding() {
        for (int i = 0; i < this.buildings.size(); i++) {
            OpenAnimalPurchaseScreenPayload.BuildingOption building = this.buildings.get(i);
            if (building.animalCount() < building.capacity()) return i;
        }
        return -1;
    }

    private int maxScroll() {
        return Math.max(0, this.buildings.size() - this.maxVisible);
    }

    private void playAnimalSound() {
        ResourceLocation soundId =
                ResourceLocation.tryParse(
                        this.animal.soundEventId());
        SoundEvent sound = soundId != null
                && BuiltInRegistries.SOUND_EVENT
                        .containsKey(soundId)
                ? BuiltInRegistries.SOUND_EVENT.get(soundId)
                : ModSounds.SMALL_SELECT.get();
        play(sound, 1.0F, 1.2F + (this.random.nextFloat() - 0.5F) * 0.4F);
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / this.guiScale));
    }

    private float s4() {
        return 4.0F / this.guiScale;
    }

    private float dialogueTextScale() {
        return SdvFontAdapter.scale(this.font,
            this.minecraft.getLanguageManager().getSelected(), this.guiScale,
            SdvFontAdapter.Style.DIALOGUE) * TEXT_VISUAL_SCALE;
    }

    private void captureHudVisibility() {
        TemporaryGuiVisibility.acquire(TemporaryGuiVisibility.Owner.ANIMAL_PURCHASE_BUILDING);
    }

    @Override
    public void removed() {
        try {
            super.removed();
        } finally {
            TemporaryGuiVisibility.release(TemporaryGuiVisibility.Owner.ANIMAL_PURCHASE_BUILDING);
        }
    }

    private static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(sound, volume, pitch);
        }
    }

    private static SdvTexture texture(String name, int width, int height) {
        return SdvTexture.full(ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_purchase/" + name + ".png"), width, height);
    }
}
