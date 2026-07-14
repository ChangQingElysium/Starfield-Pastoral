package com.stardew.craft.client.gui.festival;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.FairStarTokenPurchaseSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class FairStarTokenNumberSelectionScreen extends Screen {
    private static final int TEXT_DARK = 0xFF3E2723;
    private static final int TEXT_MUTED = 0xFF6D5942;
    private static final int PAPER_TINT = 0xFFF7DFAE;
    private static final int BUTTON_FILL = 0xFFECC26E;
    private static final int BUTTON_EDGE = 0xFF8A5A2B;

    private final Component question;
    private final int price;
    private final int minValue;
    private final int maxValue;
    private final int defaultValue;

    private EditBox amountField;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int fieldX;
    private int fieldY;
    private int fieldW;
    private int fieldH;
    private int minusX;
    private int plusX;
    private int stepY;
    private int stepSize;
    private int okX;
    private int okY;
    private int okW;
    private int okH;
    private boolean waitingForServer;

    public FairStarTokenNumberSelectionScreen(Component question, int price, int minValue, int maxValue, int defaultValue) {
        super(question);
        this.question = question == null ? Component.empty() : question;
        this.price = Math.max(0, price);
        this.minValue = Math.max(0, minValue);
        this.maxValue = Math.max(this.minValue, maxValue);
        this.defaultValue = Math.max(this.minValue, Math.min(this.maxValue, defaultValue));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        recomputeLayout();
        amountField = new EditBox(this.font, fieldX, fieldY + (fieldH - font.lineHeight) / 2,
            fieldW, font.lineHeight, Component.empty());
        amountField.setBordered(false);
        amountField.setTextShadow(false);
        amountField.setMaxLength(3);
        amountField.setTextColor(TEXT_DARK);
        amountField.setTextColorUneditable(TEXT_DARK);
        amountField.setValue(String.valueOf(defaultValue));
        addWidget(amountField);
        setFocused(amountField);
        amountField.setFocused(true);
    }

    private void recomputeLayout() {
        panelW = Math.min(width - 32, Math.max(220, Math.round(width * 0.42F)));
        panelH = 136;
        panelX = (width - panelW) / 2;
        panelY = Math.max(24, height - panelH - 48);
        fieldW = 54;
        fieldH = 24;
        fieldX = panelX + (panelW - fieldW) / 2;
        fieldY = panelY + 62;
        stepSize = 22;
        stepY = fieldY + 1;
        minusX = fieldX - stepSize - 8;
        plusX = fieldX + fieldW + 8;
        okW = 74;
        okH = 24;
        okX = panelX + (panelW - okW) / 2;
        okY = panelY + panelH - 38;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        recomputeLayout();
        StardewGuiUtil.drawDialogueBoxFrame(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX + 8, panelY + 8, panelX + panelW - 8, panelY + panelH - 8, PAPER_TINT);

        int textW = panelW - 32;
        int questionY = panelY + 22;
        for (net.minecraft.util.FormattedCharSequence line : font.split(question, textW)) {
            graphics.drawString(font, line, panelX + 16, questionY, TEXT_DARK, false);
            questionY += font.lineHeight + 2;
        }

        drawStepper(graphics, minusX, stepY, "-", inside(mouseX, mouseY, minusX, stepY, stepSize, stepSize));
        drawField(graphics);
        drawStepper(graphics, plusX, stepY, "+", inside(mouseX, mouseY, plusX, stepY, stepSize, stepSize));

        int total = amount() * price;
        graphics.drawString(font, Component.literal(total + "g"),
            fieldX + fieldW + 42, fieldY + (fieldH - font.lineHeight) / 2, TEXT_MUTED, false);

        boolean canSubmit = !waitingForServer;
        drawButton(graphics, okX, okY, okW, okH, inside(mouseX, mouseY, okX, okY, okW, okH), canSubmit);
        GuiText.drawCenteredClamped(graphics, font, Component.translatable("gui.done"),
            okX + okW / 2, okY + (okH - font.lineHeight) / 2, okW - 12, canSubmit ? TEXT_DARK : TEXT_MUTED, false);

        if (amountField != null) {
            amountField.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawField(GuiGraphics graphics) {
        CommonGuiTextures.drawEntryBox(graphics, fieldX - 7, fieldY - 5, fieldW + 14, fieldH + 10, 1.0F, true);
        graphics.fill(fieldX - 1, fieldY + fieldH - 3, fieldX + fieldW + 1, fieldY + fieldH - 2, 0x808A5A2B);
    }

    private void drawStepper(GuiGraphics graphics, int x, int y, String label, boolean hovered) {
        drawButton(graphics, x, y, stepSize, stepSize, hovered, true);
        GuiText.drawCenteredClamped(graphics, font, Component.literal(label),
            x + stepSize / 2, y + (stepSize - font.lineHeight) / 2, stepSize - 4, TEXT_DARK, false);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, boolean hovered, boolean active) {
        int fill = active ? (hovered ? 0xFFFFD98A : BUTTON_FILL) : 0xFFB99A69;
        graphics.fill(x, y, x + w, y + h, BUTTON_EDGE);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
        graphics.fill(x + 3, y + 3, x + w - 3, y + 4, 0x66FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || waitingForServer) {
            return true;
        }
        if (inside(mouseX, mouseY, minusX, stepY, stepSize, stepSize)) {
            changeAmount(-1);
            return true;
        }
        if (inside(mouseX, mouseY, plusX, stepY, stepSize, stepSize)) {
            changeAmount(1);
            return true;
        }
        if (inside(mouseX, mouseY, okX, okY, okW, okH)) {
            submit();
            return true;
        }
        if (inside(mouseX, mouseY, fieldX - 7, fieldY - 5, fieldW + 14, fieldH + 10)) {
            setFocused(amountField);
            amountField.setFocused(true);
            return amountField.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        if (amountField != null && amountField.keyPressed(keyCode, scanCode, modifiers)) {
            clampField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.isDigit(codePoint) && amountField != null && amountField.charTyped(codePoint, modifiers)) {
            clampField();
            return true;
        }
        return false;
    }

    private void submit() {
        if (waitingForServer) {
            return;
        }
        waitingForServer = true;
        PacketDistributor.sendToServer(new FairStarTokenPurchaseSubmitPayload(amount()));
    }

    public void handlePurchaseResult(boolean success) {
        waitingForServer = false;
        if (success) {
            playUi(ModSounds.PURCHASE.get(), 1.0F, 1.0F);
            onClose();
        } else {
            playUi(ModSounds.CANCEL.get(), 1.0F, 1.0F);
        }
    }

    private void changeAmount(int delta) {
        setAmount(amount() + delta);
        playUi(ModSounds.SMALL_SELECT.get(), 0.8F, 1.0F);
    }

    private void clampField() {
        if (amountField != null && !amountField.getValue().isBlank()) {
            setAmount(amount());
        }
    }

    private int amount() {
        if (amountField == null) {
            return defaultValue;
        }
        try {
            return Math.max(minValue, Math.min(maxValue, Integer.parseInt(amountField.getValue().trim())));
        } catch (NumberFormatException ignored) {
            return minValue;
        }
    }

    private void setAmount(int value) {
        if (amountField != null) {
            amountField.setValue(String.valueOf(Math.max(minValue, Math.min(maxValue, value))));
            amountField.setFocused(true);
        }
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void playUi(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }
}
