package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplay;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplays;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.SdvFontAdapter;
import com.stardew.craft.client.gui.common.SdvTexture;
import com.stardew.craft.client.gui.common.SdvTooltipRenderer;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.hud.StardewHudMessageManager;
import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client port of SDV {@code PurchaseAnimalsMenu} shop stage.
 *
 * <p>The layout intentionally follows the source constants: a 384px content
 * width plus borders, three 128px columns, three compact rows, and 32x16
 * animal shop sprites rendered at 4x. Every texture used here is a standalone
 * crop from the original atlas; this screen never samples an atlas at runtime.</p>
 */
@SuppressWarnings("null")
public final class AnimalPurchaseScreen extends Screen {
    private static final int CONTENT_WIDTH = 384;
    private static final int BORDER_WIDTH = 40;
    private static final int TOP_CLEARANCE = 96;
    private static final int PANEL_WIDTH = CONTENT_WIDTH + BORDER_WIDTH * 2;
    private static final int PANEL_HEIGHT = 320 + BORDER_WIDTH + 64;
    private static final int TOTAL_LAYOUT_HEIGHT = PANEL_HEIGHT + 176;
    private static final float PREFERRED_DISPLAY_SCALE = 1.5F;
    private static final int COLUMNS = 3;
    private static final int VISIBLE_ROWS = 3;
    private static final int CELL_WIDTH = 128;
    private static final int CELL_HEIGHT = 64;
    private static final int ROW_STEP = 85;
    private static final int BACKGROUND_TINT = 0xBF000000;
    private static final int TEXT_COLOR = 0xFF5B3A1A;
    private static final long GLOBAL_FADE_MS = 833L;

    private static final SdvTexture CANCEL_BUTTON = texture("cancel_button", 64, 64);
    /**
     * Legacy addon texture injection surface. Payload and registry displays
     * take precedence; this map is consulted only as a compatibility fallback.
     */
    private static final Map<String, SdvTexture> ANIMAL_TEXTURES = Map.of();
    private final OpenAnimalPurchaseScreenPayload payload;
    private final List<OpenAnimalPurchaseScreenPayload.AnimalOption> animals;
    private float guiScale;
    private float displayScale;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int closeX;
    private int closeY;
    private int closeSize;
    private int upX;
    private int upY;
    private int downX;
    private int downY;
    private int scrollRow;
    private int hoveredIndex = -1;
    private final float[] hoverScale;
    private float closeScale = 1.0F;
    private OpenAnimalPurchaseScreenPayload.AnimalOption pendingAnimal;
    private long transitionStartedAtMs;
    private boolean previousHideGui;
    private boolean hudVisibilityCaptured;

    public AnimalPurchaseScreen(OpenAnimalPurchaseScreenPayload payload) {
        super(Component.translatable("container.stardew_craft.animal_purchase"));
        this.payload = payload;
        this.animals = List.copyOf(payload.animalOptions());
        this.hoverScale = new float[this.animals.size()];
        java.util.Arrays.fill(this.hoverScale, 1.0F);
    }

    @Override
    protected void init() {
        super.init();
        captureHudVisibility();
        this.guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());
        int sourceWidth = PANEL_WIDTH + (maxScrollRows() > 0 ? 44 : 0) + 80;
        float widthScale = (this.width - 12) * this.guiScale / sourceWidth;
        float heightScale = (this.height - 12) * this.guiScale / TOTAL_LAYOUT_HEIGHT;
        this.displayScale = Math.max(0.75F,
            Math.min(PREFERRED_DISPLAY_SCALE, Math.min(widthScale, heightScale)));
        this.panelW = ui(PANEL_WIDTH + (maxScrollRows() > 0 ? 44 : 0));
        this.panelH = ui(PANEL_HEIGHT);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - ui(TOTAL_LAYOUT_HEIGHT)) / 2;

        this.closeSize = ui(64);
        this.closeX = this.panelX + this.panelW + ui(4);
        this.closeY = this.panelY + this.panelH - ui(64 + BORDER_WIDTH);

        int arrowsX = this.panelX + this.panelW - ui(64 + 24);
        this.upX = arrowsX;
        this.upY = this.panelY + ui(TOP_CLEARANCE + 16);
        this.downX = arrowsX;
        this.downY = this.panelY + this.panelH - ui(64 + 24);
        this.scrollRow = Math.min(this.scrollRow, maxScrollRows());
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // SDV draws fadeToBlackRect itself and never asks Minecraft for blur/dirt.
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND_TINT);
        updateHover(mouseX, mouseY);

        StardewGuiUtil.drawDialogueBoxFrame(
            graphics, this.panelX, this.panelY, this.panelW, this.panelH);
        drawTitle(graphics);
        drawMoneyBox(graphics);
        drawAnimals(graphics);
        drawScrollControls(graphics);
        drawCloseButton(graphics);

        if (this.hoveredIndex >= 0 && this.hoveredIndex < this.animals.size()) {
            drawHoverDetails(graphics, this.animals.get(this.hoveredIndex), mouseX, mouseY);
        }
        drawTransition(graphics);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.pendingAnimal != null
            && System.currentTimeMillis() - this.transitionStartedAtMs >= GLOBAL_FADE_MS) {
            OpenAnimalPurchaseScreenPayload.AnimalOption selected = this.pendingAnimal;
            this.pendingAnimal = null;
            this.minecraft.setScreen(new AnimalPurchaseBuildingScreen(this.payload, selected));
        }
    }

    private void drawTitle(GuiGraphics graphics) {
        Component title = Component.translatable("stardewcraft.animal.purchase.animals");
        drawScrollTextCentered(graphics, title, title.getString(),
            this.panelX + this.panelW / 2, this.panelY, 1.0F);
    }

    private void drawAnimals(GuiGraphics graphics) {
        int first = this.scrollRow * COLUMNS;
        int last = Math.min(this.animals.size(), first + COLUMNS * VISIBLE_ROWS);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int col = visible % COLUMNS;
            int row = visible / COLUMNS;
            int cellX = this.panelX + ui(BORDER_WIDTH) + ui(col * CELL_WIDTH);
            int cellY = this.panelY + ui(TOP_CLEARANCE + BORDER_WIDTH / 2) + ui(row * ROW_STEP);
            drawAnimal(graphics, this.animals.get(index), index, cellX, cellY);
        }
    }

    private void drawAnimal(GuiGraphics graphics, OpenAnimalPurchaseScreenPayload.AnimalOption animal,
                            int index, int cellX, int cellY) {
        SdvTexture texture = null;
        int textureWidth = animal.shopTextureWidth();
        int textureHeight = animal.shopTextureHeight();
        ResourceLocation textureId =
                ResourceLocation.tryParse(animal.shopTextureId());
        if (textureId != null
                && textureWidth > 0
                && textureHeight > 0) {
            texture = SdvTexture.full(
                    textureId, textureWidth, textureHeight);
        } else {
            StardewAnimalPurchaseDisplay display =
                    StardewAnimalPurchaseDisplays.display(animal.animalTypeId());
            if (display != null) {
                textureWidth = display.textureWidth();
                textureHeight = display.textureHeight();
                texture = SdvTexture.full(
                        display.texture(),
                        textureWidth,
                        textureHeight);
            }
        }
        if (texture == null) {
            texture = ANIMAL_TEXTURES.get(
                    animal.animalTypeId()
                            .toLowerCase(Locale.ROOT));
            if (texture != null) {
                textureWidth = 32;
                textureHeight = 16;
            }
        }
        if (texture == null) {
            return;
        }
        float scale = s4() * this.hoverScale[index];
        int drawW = Math.round(textureWidth * scale);
        int drawH = Math.round(textureHeight * scale);
        int drawX = cellX + (ui(CELL_WIDTH) - drawW) / 2;
        int drawY = cellY + (ui(CELL_HEIGHT) - drawH) / 2;
        if (animal.unlocked()) {
            texture.drawPixelZoom(graphics, drawX, drawY, scale);
        } else {
            texture.drawPixelZoomTint(graphics, drawX, drawY, scale, 0.0F, 0.0F, 0.0F, 0.4F);
        }
    }

    private void drawScrollControls(GuiGraphics graphics) {
        if (maxScrollRows() <= 0) {
            return;
        }
        if (this.scrollRow > 0) {
            CommonGuiTextures.drawScrollArrowUp(graphics, this.upX, this.upY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowUpTint(graphics, this.upX, this.upY, s4(), 1, 1, 1, 0.4F);
        }
        if (this.scrollRow < maxScrollRows()) {
            CommonGuiTextures.drawScrollArrowDown(graphics, this.downX, this.downY, s4());
        } else {
            CommonGuiTextures.drawScrollArrowDownTint(graphics, this.downX, this.downY, s4(), 1, 1, 1, 0.4F);
        }
    }

    private void drawCloseButton(GuiGraphics graphics) {
        float scale = (1.0F / effectiveGuiScale()) * this.closeScale;
        int size = Math.round(64 * scale);
        int x = this.closeX + (this.closeSize - size) / 2;
        int y = this.closeY + (this.closeSize - size) / 2;
        CANCEL_BUTTON.drawPixelZoom(graphics, x, y, scale);
    }

    private void drawMoneyBox(GuiGraphics graphics) {
        int bannerW = Math.round(65 * s4());
        int bannerX = this.width - bannerW - ui(20);
        int bannerY = ui(16);
        CommonGuiTextures.drawMoneyBox(graphics, bannerX, bannerY, s4());

        String digits = Integer.toString(Math.max(0, this.payload.playerMoney()));
        int spacing = Math.round(6 * s4());
        int digitX = bannerX + ui(40) + Math.max(0, 8 - digits.length()) * spacing;
        int digitY = bannerY + ui(24);
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            CommonGuiTextures.drawMoneyDigitTint(graphics, digitX + i * spacing, digitY,
                digit, s4(), 0.502F, 0.0F, 0.0F, 1.0F);
        }
    }

    private void drawHoverDetails(GuiGraphics graphics, OpenAnimalPurchaseScreenPayload.AnimalOption animal,
                                  int mouseX, int mouseY) {
        if (!animal.unlocked()) {
            drawSdvTooltip(graphics, Component.translatable(animal.lockReasonKey()), mouseX, mouseY);
            return;
        }
        Component name = Component.literal(animal.displayName());
        int nameY = this.panelY + this.panelH + ui(-32 + TOP_CLEARANCE / 2 + 8);
        drawScrollTextCentered(graphics, name, "Truffle Pig",
            this.panelX + this.panelW / 2, nameY, 1.0F);

        Component price = Component.literal("$" + animal.price() + "g");
        int priceY = this.panelY + this.panelH + ui(64 + TOP_CLEARANCE / 2 + 8);
        float alpha = this.payload.playerMoney() >= animal.price() ? 1.0F : 0.5F;
        drawScrollTextCentered(graphics, price, "$99999999g",
            this.panelX + this.panelW / 2, priceY, alpha);

        Component description = Component.translatable(animal.descriptionKey());
        SdvTooltipRenderer.drawAnimalShop(graphics, this.font,
            name, description, animal.price(), mouseX, mouseY,
            this.width, this.height, effectiveGuiScale(),
            this.minecraft.getLanguageManager().getSelected());
    }

    private void drawSdvTooltip(GuiGraphics graphics, Component description, int mouseX, int mouseY) {
        SdvTooltipRenderer.draw(graphics, this.font, description,
            mouseX, mouseY, this.width, this.height, effectiveGuiScale(),
            this.minecraft.getLanguageManager().getSelected());
    }

    private void drawScrollTextCentered(GuiGraphics graphics, Component text, String placeholder,
                                        int centerX, int sourceTextY, float alpha) {
        float effectiveScale = effectiveGuiScale();
        float textScale = SdvFontAdapter.scale(this.font,
            this.minecraft.getLanguageManager().getSelected(), effectiveScale,
            SdvFontAdapter.Style.SPRITE_TEXT);
        int middleW = Math.max(1, Math.round(this.font.width(placeholder) * textScale));
        int textX = centerX - middleW / 2;
        int bannerY = sourceTextY - Math.round(3 * s4());
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        CommonGuiTextures.drawScrollBanner(graphics, textX, bannerY, middleW, s4());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int shownWidth = SdvFontAdapter.width(this.font, text, textScale);
        int shownX = centerX - shownWidth / 2;
        int renderedHeight = Math.max(1, Math.round(this.font.lineHeight * textScale));
        int textY = bannerY + (Math.round(18 * s4()) - renderedHeight) / 2;
        int color = ((Math.round(alpha * 255.0F) & 0xFF) << 24) | (TEXT_COLOR & 0xFFFFFF);
        SdvFontAdapter.draw(graphics, this.font, text, shownX, textY, textScale, color);
    }

    private float effectiveGuiScale() {
        return this.guiScale / Math.max(0.01F, this.displayScale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.pendingAnimal != null) {
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (inside(mx, my, this.closeX, this.closeY, this.closeSize, this.closeSize)) {
            play(ModSounds.BIG_DESELECT.get(), 1.0F, 1.0F);
            onClose();
            return true;
        }
        int arrowW = Math.round(11 * s4());
        int arrowH = Math.round(12 * s4());
        if (inside(mx, my, this.upX, this.upY, arrowW, arrowH) && this.scrollRow > 0) {
            this.scrollRow--;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        if (inside(mx, my, this.downX, this.downY, arrowW, arrowH) && this.scrollRow < maxScrollRows()) {
            this.scrollRow++;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        int clicked = animalAt(mx, my);
        if (clicked >= 0) {
            chooseAnimal(this.animals.get(clicked));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.pendingAnimal != null) {
            return true;
        }
        if (scrollY > 0 && this.scrollRow > 0) {
            this.scrollRow--;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        if (scrollY < 0 && this.scrollRow < maxScrollRows()) {
            this.scrollRow++;
            play(ModSounds.SHWIP.get(), 1.0F, 1.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pendingAnimal != null) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void chooseAnimal(OpenAnimalPurchaseScreenPayload.AnimalOption animal) {
        if (!animal.unlocked()) {
            return;
        }
        if (this.payload.playerMoney() < animal.price()) {
            StardewHudMessageManager.showError(Component.translatable("stardewcraft.animal.purchase.no_money"));
            return;
        }
        boolean hasCompatibleBuilding = this.payload.buildingOptions().stream().anyMatch(building ->
            animal.family().equalsIgnoreCase(building.family())
                && building.tier() >= animal.requiredTier());
        if (!hasCompatibleBuilding) {
            StardewHudMessageManager.showError(Component.translatable("stardewcraft.animal.purchase.no_building"));
            return;
        }
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        this.pendingAnimal = animal;
        this.transitionStartedAtMs = System.currentTimeMillis();
    }

    private void drawTransition(GuiGraphics graphics) {
        if (this.pendingAnimal == null) return;
        float progress = Math.min(1.0F,
            (System.currentTimeMillis() - this.transitionStartedAtMs) / (float) GLOBAL_FADE_MS);
        graphics.fill(0, 0, this.width, this.height, ((int) (progress * 255.0F)) << 24);
    }

    private void updateHover(int mouseX, int mouseY) {
        this.hoveredIndex = animalAt(mouseX, mouseY);
        for (int i = 0; i < this.hoverScale.length; i++) {
            float target = i == this.hoveredIndex ? 1.025F : 1.0F;
            float step = i == this.hoveredIndex ? 0.0125F : 0.00625F;
            this.hoverScale[i] = approach(this.hoverScale[i], target, step);
        }
        this.closeScale = approach(this.closeScale,
            inside(mouseX, mouseY, this.closeX, this.closeY, this.closeSize, this.closeSize) ? 1.1F : 1.0F,
            0.05F);
    }

    private int animalAt(int mouseX, int mouseY) {
        int first = this.scrollRow * COLUMNS;
        int last = Math.min(this.animals.size(), first + COLUMNS * VISIBLE_ROWS);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int col = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = this.panelX + ui(BORDER_WIDTH) + ui(col * CELL_WIDTH);
            int y = this.panelY + ui(TOP_CLEARANCE + BORDER_WIDTH / 2) + ui(row * ROW_STEP);
            if (inside(mouseX, mouseY, x, y, ui(CELL_WIDTH), ui(CELL_HEIGHT))) {
                return index;
            }
        }
        return -1;
    }

    private int maxScrollRows() {
        int totalRows = (this.animals.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, totalRows - VISIBLE_ROWS);
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / effectiveGuiScale()));
    }

    private float s4() {
        return 4.0F / effectiveGuiScale();
    }

    private void captureHudVisibility() {
        if (!this.hudVisibilityCaptured) {
            this.previousHideGui = this.minecraft.options.hideGui;
            this.hudVisibilityCaptured = true;
        }
        this.minecraft.options.hideGui = true;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.hudVisibilityCaptured) {
            this.minecraft.options.hideGui = this.previousHideGui;
            this.hudVisibilityCaptured = false;
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
