package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.FarmSelectionSubmitPayload;
import com.stardew.craft.network.payload.PlayerProfileSubmitPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** Mandatory three-question profile used by gendered dialogue and favorite-name tokens. */
@SuppressWarnings("null")
public final class PlayerProfileSetupScreen extends Screen {
    private static final int TEXT_DARK = 0xFF582A11;
    private static final int TEXT_BODY = 0xFF6B3E21;
    private static final int TEXT_MUTED = 0xFF8A5A2B;
    private static final int TEXT_ERROR = 0xFFD03020;

    // CharacterCustomization uses these two 16x16 regions from mouseCursors.
    private static final int MALE_ICON_U = 128;
    private static final int FEMALE_ICON_U = 144;
    private static final int GENDER_ICON_V = 192;
    // Game1.getSourceRectForStandardTileSheet(Game1.mouseCursors, 34) uses
    // the method's default 64x64 tile size, yielding (64, 192, 64, 64).
    private static final int SELECTED_ICON_U = 64;
    private static final int SELECTED_ICON_V = 192;

    private final @Nullable PendingFarm pendingFarm;
    private EditBox preferredNameField;
    private EditBox favoriteThingField;
    private boolean male = true;

    private float guiScale;
    private float stardewScale;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentW;
    private int formX;
    private int formW;
    private int titleBannerY;
    private int introY;
    private int preferredLabelY;
    private int favoriteLabelY;
    private int genderLabelY;
    private int maleIconX;
    private int femaleIconX;
    private int genderIconY;
    private int genderIconSize;
    private int confirmX;
    private int confirmY;
    private int confirmSize;
    private int validationY;
    private Component validationMessage = Component.empty();

    /** Existing-save profile repair. */
    public PlayerProfileSetupScreen() {
        this(null);
    }

    private PlayerProfileSetupScreen(@Nullable PendingFarm pendingFarm) {
        super(Component.translatable(pendingFarm == null
                ? "gui.stardewcraft.player_profile.legacy_title"
                : "gui.stardewcraft.player_profile.new_farm_title"));
        this.pendingFarm = pendingFarm;
    }

    public static PlayerProfileSetupScreen forNewFarm(
            String farmTypeId, String farmName, boolean forceCancelPending) {
        return new PlayerProfileSetupScreen(new PendingFarm(farmTypeId, farmName, forceCancelPending));
    }

    @Override
    protected void init() {
        String preferredValue = preferredNameField == null ? defaultPlayerName() : preferredNameField.getValue();
        String favoriteValue = favoriteThingField == null ? "" : favoriteThingField.getValue();

        guiScale = minecraft == null ? 2.0F : (float) minecraft.getWindow().getGuiScale();
        stardewScale = 4.0F / Math.max(1.0F, guiScale);

        panelW = Math.min(width - 24, Math.max(420, Math.round(880.0F / guiScale)));
        panelH = Math.min(height - 20, Math.max(294, Math.round(500.0F / guiScale)));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int frameInset = Math.max(24, Math.round(56.0F / guiScale));
        contentX = panelX + frameInset;
        contentW = panelW - frameInset * 2;
        formW = Math.min(contentW, Math.max(260, Math.round(640.0F / guiScale)));
        formX = width / 2 - formW / 2;

        int bannerHeight = Math.round(18.0F * stardewScale);
        titleBannerY = panelY + Math.max(4, Math.round(12.0F / guiScale));
        introY = titleBannerY + bannerHeight + Math.max(6, Math.round(16.0F / guiScale));

        Component intro = introText();
        int introLines = GuiText.wrappedLineCount(font, intro, contentW, 2);
        int formTop = introY + introLines * (font.lineHeight + 2) + 10;

        confirmSize = Math.round(16.0F * stardewScale);
        int bottomInset = Math.max(20, Math.round(48.0F / guiScale));
        confirmX = formX + formW - confirmSize;
        confirmY = panelY + panelH - bottomInset - confirmSize;
        validationY = confirmY + (confirmSize - font.lineHeight) / 2;

        int formBottom = confirmY - 10;
        int rowStep = Math.max(font.lineHeight + 24, (formBottom - formTop) / 3);
        preferredLabelY = formTop;
        favoriteLabelY = formTop + rowStep;
        genderLabelY = formTop + rowStep * 2;

        int fieldH = font.lineHeight + 6;
        preferredNameField = createField(preferredLabelY + font.lineHeight + 3, fieldH,
                Component.translatable("gui.stardewcraft.player_profile.preferred_name"), 48, preferredValue);
        favoriteThingField = createField(favoriteLabelY + font.lineHeight + 3, fieldH,
                Component.translatable("gui.stardewcraft.player_profile.favorite_thing"), 64, favoriteValue);

        genderIconSize = Math.round(16.0F * stardewScale);
        int iconGap = Math.max(18, Math.round(24.0F / guiScale));
        maleIconX = width / 2 - genderIconSize - iconGap / 2;
        femaleIconX = width / 2 + iconGap / 2;
        genderIconY = genderLabelY + font.lineHeight + 3;

        setInitialFocus(preferredNameField);
    }

    private EditBox createField(int y, int height, Component narration, int maxLength, String value) {
        EditBox field = new EditBox(font, formX, y, formW, height, narration);
        field.setBordered(false);
        field.setTextShadow(false);
        field.setTextColor(TEXT_DARK);
        field.setTextColorUneditable(TEXT_MUTED);
        field.setMaxLength(maxLength);
        field.setValue(value);
        field.setResponder(ignored -> validationMessage = Component.empty());
        addWidget(field);
        return field;
    }

    private Component introText() {
        return Component.translatable(pendingFarm == null
                ? "gui.stardewcraft.player_profile.legacy_intro"
                : "gui.stardewcraft.player_profile.new_farm_intro");
    }

    private String defaultPlayerName() {
        if (minecraft != null && minecraft.player != null) {
            return minecraft.player.getName().getString();
        }
        return "";
    }

    private void selectGender(boolean male) {
        this.male = male;
        validationMessage = Component.empty();
    }

    private void submit() {
        String preferredName = preferredNameField.getValue().trim();
        String favoriteThing = favoriteThingField.getValue().trim();
        if (preferredName.isBlank() || favoriteThing.isBlank()) {
            validationMessage = Component.translatable("stardewcraft.player_profile.validation.required");
            EditBox emptyField = preferredName.isBlank() ? preferredNameField : favoriteThingField;
            setFocused(emptyField);
            emptyField.setFocused(true);
            return;
        }
        if (pendingFarm == null) {
            PacketDistributor.sendToServer(new PlayerProfileSubmitPayload(preferredName, favoriteThing, male));
        } else {
            PacketDistributor.sendToServer(new FarmSelectionSubmitPayload(
                    pendingFarm.farmTypeId(), pendingFarm.farmName(), pendingFarm.forceCancelPending(),
                    preferredName, favoriteThing, male));
        }
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) return true;
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int labelHitHeight = genderIconSize + font.lineHeight + 4;
            if (inside(mouseX, mouseY, maleIconX, genderIconY, genderIconSize, labelHitHeight)) {
                selectGender(true);
                return true;
            }
            if (inside(mouseX, mouseY, femaleIconX, genderIconY, genderIconSize, labelHitHeight)) {
                selectGender(false);
                return true;
            }
            if (inside(mouseX, mouseY, confirmX, confirmY, confirmSize, confirmSize)) {
                submit();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        StardewGuiUtil.drawDialogueBoxFrame(graphics, panelX, panelY, panelW, panelH);

        int bannerTextW = Mth.clamp(font.width(title) + 16, 80,
                Math.max(80, contentW - Math.round(24.0F * stardewScale)));
        int bannerTextX = width / 2 - bannerTextW / 2;
        CommonGuiTextures.drawScrollBanner(graphics, bannerTextX, titleBannerY, bannerTextW, stardewScale);
        GuiText.drawCenteredClamped(graphics, font, title, width / 2,
                titleBannerY + (Math.round(18.0F * stardewScale) - font.lineHeight) / 2,
                bannerTextW - 8, TEXT_DARK, false);

        GuiText.drawWrappedCentered(graphics, font, introText(), width / 2, introY,
                contentW, TEXT_BODY, false, 2);

        drawLabel(graphics, "gui.stardewcraft.player_profile.preferred_name", preferredLabelY);
        drawUnderlineField(graphics, preferredNameField, mouseX, mouseY, partialTick);

        drawLabel(graphics, "gui.stardewcraft.player_profile.favorite_thing", favoriteLabelY);
        drawUnderlineField(graphics, favoriteThingField, mouseX, mouseY, partialTick);

        drawLabel(graphics, "gui.stardewcraft.player_profile.gender", genderLabelY);
        drawGenderChoice(graphics, mouseX, mouseY, true, maleIconX);
        drawGenderChoice(graphics, mouseX, mouseY, false, femaleIconX);

        if (!validationMessage.getString().isBlank()) {
            GuiText.drawCenteredClamped(graphics, font, validationMessage,
                    (formX + confirmX) / 2, validationY,
                    Math.max(1, confirmX - formX - 12), TEXT_ERROR, false);
        }

        drawConfirm(graphics, mouseX, mouseY);
        // Widgets are rendered manually. Calling super.render() here applies Minecraft's
        // second blurred background pass after the panel and makes the foreground unreadable.
    }

    private void drawLabel(GuiGraphics graphics, String key, int y) {
        graphics.drawString(font, GuiText.ellipsize(font, Component.translatable(key), formW),
                formX, y, TEXT_DARK, false);
    }

    private void drawUnderlineField(GuiGraphics graphics, EditBox field,
                                    int mouseX, int mouseY, float partialTick) {
        field.render(graphics, mouseX, mouseY, partialTick);
        int lineY = field.getY() + field.getHeight() + 1;
        boolean focused = field.isFocused();
        int lineColor = focused ? 0xFFEADB8C : 0xAA8B7D63;
        graphics.fill(field.getX(), lineY, field.getX() + field.getWidth(),
                lineY + (focused ? 2 : 1), lineColor);
        if (focused) {
            graphics.fillGradient(field.getX(), lineY + 2,
                    field.getX() + field.getWidth(), lineY + 5,
                    0x44EADB8C, 0x00EADB8C);
        }
    }

    private void drawGenderChoice(GuiGraphics graphics, int mouseX, int mouseY,
                                  boolean maleChoice, int x) {
        boolean selected = male == maleChoice;
        boolean hovered = inside(mouseX, mouseY, x, genderIconY,
                genderIconSize, genderIconSize + font.lineHeight + 4);
        int drawY = genderIconY - (hovered ? 1 : 0);

        StardewGuiUtil.drawFromCursors(graphics, x, drawY,
                maleChoice ? MALE_ICON_U : FEMALE_ICON_U, GENDER_ICON_V,
                16, 16, stardewScale);
        if (selected) {
            StardewGuiUtil.drawFromCursors(graphics, x, drawY,
                    SELECTED_ICON_U, SELECTED_ICON_V, 64, 64, 1.0F / guiScale);
        }

        Component label = Component.translatable(maleChoice
                ? "gui.stardewcraft.player_profile.gender.male"
                : "gui.stardewcraft.player_profile.gender.female");
        GuiText.drawCenteredClamped(graphics, font, label,
                x + genderIconSize / 2, genderIconY + genderIconSize + 3,
                Math.max(genderIconSize + 20, font.width(label)),
                selected ? TEXT_DARK : TEXT_MUTED, false);
    }

    private void drawConfirm(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, confirmX, confirmY, confirmSize, confirmSize);
        float scale = stardewScale * (hovered ? 1.08F : 1.0F);
        int drawnSize = Math.round(16.0F * scale);
        int drawX = confirmX + (confirmSize - drawnSize) / 2;
        int drawY = confirmY + (confirmSize - drawnSize) / 2;
        CommonGuiTextures.drawOkCheckGreen(graphics, drawX, drawY, scale);

        Component label = Component.translatable("gui.stardewcraft.player_profile.confirm");
        int labelX = confirmX - font.width(label) - 7;
        graphics.drawString(font, label, labelX,
                confirmY + (confirmSize - font.lineHeight) / 2,
                hovered ? TEXT_DARK : TEXT_MUTED, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    private record PendingFarm(String farmTypeId, String farmName, boolean forceCancelPending) {}
}
