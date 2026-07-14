package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.MoneyContractTransferSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
public class MoneyContractTransferScreen extends Screen {
    private static final int SDV_W = 620;
    private static final int SDV_H = 440;
    private static final int TEXT_DARK = 0x4E2A12;
    private static final int TEXT_BODY = 0x5E3518;
    private static final int TEXT_MUTED = 0x8A6B46;
    private static final int LINE_DARK = 0xA66B26;
    private static final int LINE_LIGHT = 0xE7B55E;
    private static final int FOCUS_LINE = 0xD0923D;
    private static final int FOCUS_LINE_BRIGHT = 0xEBC96B;
    private static final int BROWN_EDGE = 0xC98235;
    private static final int PAPER_TINT = 0x2CEEC276;
    private static final int PAPER_TINT_STRONG = 0x44F5CD83;

    private final int money;
    private final UUID targetId;
    private final String targetName;
    private final ItemStack contractIcon = new ItemStack(ModItems.MONEY_CONTRACT.get());
    private float guiScale;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int summaryX, summaryY, summaryW, summaryH;
    private int fieldX, fieldY, fieldW, fieldH;
    private int sendX, sendY, sendW, sendH;
    private int iconX, iconY;
    private EditBox amountField;

    public MoneyContractTransferScreen(int money, UUID targetId, String targetName) {
        super(Component.translatable("stardewcraft.money_contract.transfer.title"));
        this.money = money;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        super.init();
        guiScale = (float) Math.max(1.0D, minecraft.getWindow().getGuiScale());
        int maxW = Math.max(1, this.width - 24);
        int maxH = Math.max(1, this.height - 24);
        panelW = clamp(ui(SDV_W), Math.min(312, maxW), maxW);
        panelH = clamp(ui(SDV_H), Math.min(260, maxH), maxH);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int border = Math.max(18, Math.min(34, panelW / 12));
        contentX = panelX + border;
        contentY = panelY + border;
        contentW = panelW - border * 2;
        contentH = panelH - border * 2;

        int iconSize = CommonGuiTextures.itemSize(iconScale());
        iconX = contentX + Math.max(2, contentW / 42);
        iconY = contentY + Math.max(1, contentH / 54);

        int headerBottom = iconY + iconSize + Math.max(14, contentH / 14);
        summaryX = contentX + Math.max(6, contentW / 28);
        summaryY = headerBottom + Math.max(4, contentH / 48);
        summaryW = contentW - (summaryX - contentX) * 2;
        summaryH = Math.max(48, Math.min(60, contentH / 3));

        sendW = Math.max(112, Math.min(148, contentW / 2));
        sendH = Math.max(25, font.lineHeight + 13);
        sendX = contentX + contentW - sendW - Math.max(6, contentW / 30);
        sendY = contentY + contentH - sendH - Math.max(0, contentH / 70);

        fieldH = Math.max(30, font.lineHeight + 17);
        fieldW = contentW - Math.max(12, contentW / 14) * 2;
        fieldX = contentX + Math.max(6, contentW / 28);
        fieldY = sendY - fieldH - Math.max(22, contentH / 9);
        amountField = new EditBox(this.font, fieldX + 28, fieldY + (fieldH - font.lineHeight) / 2,
            Math.max(1, fieldW - 40), font.lineHeight,
            Component.translatable("stardewcraft.money_contract.transfer.amount"));
        amountField.setMaxLength(9);
        amountField.setBordered(false);
        amountField.setTextShadow(false);
        amountField.setTextColor(0xFF3E2723);
        amountField.setTextColorUneditable(0xFF3E2723);
        addWidget(amountField);
        setFocused(amountField);
        amountField.setFocused(true);
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / guiScale));
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    private float iconScale() {
        return Math.max(1.0f, Math.min(1.35f, s4() * 0.95f));
    }

    private float coinScale() {
        return Math.max(0.85f, Math.min(1.0f, s4() * 0.72f));
    }

    private float smallIconScale() {
        return Math.max(0.72f, Math.min(0.9f, s4() * 0.62f));
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        StardewGuiUtil.drawDialogueBoxFrame(graphics, panelX, panelY, panelW, panelH);

        drawInnerParchment(graphics);
        drawHeader(graphics);
        drawSummaryCard(graphics);

        int labelY = fieldY - font.lineHeight - Math.max(5, contentH / 46);
        graphics.drawString(font,
            GuiText.ellipsize(font, Component.translatable("stardewcraft.money_contract.transfer.amount"), contentW),
            fieldX + 2, labelY, TEXT_BODY, false);
        drawFieldFrame(graphics, inside(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH));
        drawAmountText(graphics);

        boolean canSend = parseAmount() > 0;
        boolean hovered = inside(mouseX, mouseY, sendX, sendY, sendW, sendH);
        drawButton(graphics, canSend, hovered);
        drawButtonIcon(graphics, canSend);
        GuiText.drawCenteredClamped(graphics, font,
            Component.translatable("stardewcraft.money_contract.transfer.send"),
            sendX + sendW / 2 + 7, sendY + (sendH - font.lineHeight) / 2, sendW - 38,
            canSend ? TEXT_DARK : 0x806D59, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)) {
            setFocused(amountField);
            amountField.setFocused(true);
            amountField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (inside(mouseX, mouseY, sendX, sendY, sendW, sendH)) {
            submit();
            return true;
        }
        amountField.setFocused(false);
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
        if (amountField != null && amountField.isFocused() && amountField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.isDigit(codePoint) && amountField != null && amountField.isFocused()) {
            return amountField.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void submit() {
        int amount = parseAmount();
        if (amount <= 0) {
            playCancel();
            return;
        }
        PacketDistributor.sendToServer(new MoneyContractTransferSubmitPayload(targetId, amount));
        playSelect();
        onClose();
    }

    private int parseAmount() {
        try {
            return Integer.parseInt(amountField.getValue().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void drawInnerParchment(GuiGraphics graphics) {
        graphics.fill(contentX + 2, contentY + 2, contentX + contentW - 2, contentY + contentH - 2, PAPER_TINT);
        graphics.fill(contentX + 5, contentY + 5, contentX + contentW - 5, Math.max(contentY + 6, summaryY - 6),
            0x18F9D992);
    }

    private void drawHeader(GuiGraphics graphics) {
        int pad = Math.max(3, ui(5));
        int iconSize = CommonGuiTextures.itemSize(iconScale());
        CommonGuiTextures.drawEntryBox(graphics, iconX - pad, iconY - pad, iconSize + pad * 2, iconSize + pad * 2,
            s4(), false);
        graphics.fill(iconX - pad + 3, iconY - pad + 3, iconX + iconSize + pad - 3, iconY + iconSize + pad - 3,
            0x18A66B26);
        CommonGuiTextures.drawItem(graphics, contractIcon, iconX, iconY, iconScale());

        int titleX = iconX + iconSize + Math.max(12, contentW / 24);
        int titleY = iconY + Math.max(2, (iconSize - font.lineHeight * 2) / 2);
        graphics.drawString(font,
            GuiText.ellipsize(font, Component.translatable("stardewcraft.money_contract.transfer.title"),
                contentW - (titleX - contentX) - 4),
            titleX, titleY, TEXT_DARK, false);
        graphics.drawString(font,
            GuiText.ellipsize(font, Component.translatable("stardewcraft.money_contract.transfer.target", targetName),
                contentW - (titleX - contentX) - 4),
            titleX, titleY + font.lineHeight + 4, TEXT_MUTED, false);

        int partitionY = iconY + iconSize + Math.max(8, contentH / 34);
        drawLedgerStroke(graphics, contentX + Math.max(4, contentW / 44), partitionY,
            contentX + contentW - Math.max(4, contentW / 44));
    }

    private void drawLedgerStroke(GuiGraphics graphics, int x0, int y, int x1) {
        if (x1 <= x0) {
            return;
        }
        graphics.fill(x0, y, x1, y + 1, LINE_DARK);
        graphics.fill(x0 + Math.max(12, (x1 - x0) / 6), y + 3,
            x1 - Math.max(8, (x1 - x0) / 8), y + 4, 0x66A66B26);
    }

    private void drawSummaryCard(GuiGraphics graphics) {
        CommonGuiTextures.drawEntryBox(graphics, summaryX, summaryY, summaryW, summaryH, s4(), false);
        graphics.fill(summaryX + 5, summaryY + 5, summaryX + summaryW - 5, summaryY + summaryH - 5,
            PAPER_TINT_STRONG);

        int coinDrawSize = Math.round(14 * coinScale());
        int rowY = summaryY + Math.max(9, (summaryH - font.lineHeight * 2 - 7) / 2);
        int coinX = summaryX + Math.max(11, summaryW / 24);
        CommonGuiTextures.drawGoldCoin16(graphics, coinX, rowY - Math.max(2, (coinDrawSize - font.lineHeight) / 2),
            coinScale());
        graphics.drawString(font,
            GuiText.ellipsize(font, Component.translatable("stardewcraft.money_contract.transfer.money", formatGold(money)),
                summaryW - (coinX - summaryX) - coinDrawSize - 18),
            coinX + coinDrawSize + 8, rowY, TEXT_BODY, false);

        int targetY = rowY + font.lineHeight + 7;
        int pinX = coinX + Math.max(3, coinDrawSize / 4);
        drawRecipientMark(graphics, pinX, targetY + 1);
        graphics.drawString(font,
            GuiText.ellipsize(font, Component.translatable("stardewcraft.money_contract.transfer.target", targetName),
                summaryW - (coinX - summaryX) - coinDrawSize - 18),
            coinX + coinDrawSize + 8, targetY, TEXT_DARK, false);
    }

    private void drawRecipientMark(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 3, y, x + 7, y + 4, LINE_LIGHT);
        graphics.fill(x + 1, y + 2, x + 9, y + 6, BROWN_EDGE);
        graphics.fill(x + 3, y + 4, x + 7, y + 8, 0xFFE0AF58);
    }

    private void drawFieldFrame(GuiGraphics graphics, boolean hovered) {
        boolean focused = amountField.isFocused();
        CommonGuiTextures.drawEntryBox(graphics, fieldX, fieldY, fieldW, fieldH, s4(), false);
        graphics.fill(fieldX + 5, fieldY + 5, fieldX + fieldW - 5, fieldY + fieldH - 5,
            focused ? 0x60F5CD83 : hovered ? 0x50F5CD83 : 0x35F5CD83);
        if (hovered || focused) {
            int outline = focused ? FOCUS_LINE_BRIGHT : FOCUS_LINE;
            graphics.fill(fieldX - 2, fieldY - 2, fieldX + fieldW + 2, fieldY - 1, outline);
            graphics.fill(fieldX - 2, fieldY + fieldH + 1, fieldX + fieldW + 2, fieldY + fieldH + 2, outline);
            graphics.fill(fieldX - 2, fieldY - 1, fieldX - 1, fieldY + fieldH + 1, outline);
            graphics.fill(fieldX + fieldW + 1, fieldY - 1, fieldX + fieldW + 2, fieldY + fieldH + 1, outline);
            graphics.fill(fieldX + 8, fieldY + fieldH - 6, fieldX + fieldW - 8, fieldY + fieldH - 4,
                focused ? 0xCC8B5A2B : 0x99A66B26);
        }
        int coinY = fieldY + (fieldH - Math.round(14 * coinScale())) / 2;
        CommonGuiTextures.drawGoldCoin16(graphics, fieldX + 10, coinY, coinScale());
        graphics.fill(fieldX + 29, fieldY + 7, fieldX + 30, fieldY + fieldH - 7, 0x55A66B26);
    }

    private void drawAmountText(GuiGraphics graphics) {
        String value = amountField.getValue();
        int textColor = value.isEmpty() ? TEXT_MUTED : TEXT_DARK;
        Component shown = value.isEmpty()
            ? Component.translatable("stardewcraft.money_contract.transfer.placeholder")
            : Component.literal(value);
        int textX = fieldX + 37;
        int textY = fieldY + (fieldH - font.lineHeight) / 2;
        graphics.drawString(font,
            GuiText.ellipsize(font, shown, fieldW - 45),
            textX, textY, textColor, false);
        if (amountField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int visibleWidth = value.isEmpty() ? 0 : Math.min(font.width(value), Math.max(0, fieldW - 47));
            int cursorX = textX + visibleWidth + (value.isEmpty() ? -4 : 2);
            graphics.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight + 1, TEXT_DARK);
        }
    }

    private void drawButton(GuiGraphics graphics, boolean enabled, boolean hovered) {
        CommonGuiTextures.drawBillboardAcceptBox(graphics, sendX, sendY, sendW, sendH, s4());
        int fill = enabled
            ? (hovered ? 0x52F2C56B : 0x38E6B45C)
            : 0x3EBAA77E;
        graphics.fill(sendX + 5, sendY + 5, sendX + sendW - 5, sendY + sendH - 5, fill);
        graphics.fill(sendX + 8, sendY + sendH - 5, sendX + sendW - 8, sendY + sendH - 4,
            enabled ? 0x998B5A2B : 0x66806D59);
    }

    private void drawButtonIcon(GuiGraphics graphics, boolean enabled) {
        int y = sendY + (sendH - Math.round(16 * smallIconScale())) / 2;
        if (enabled) {
            CommonGuiTextures.drawOkCheckSmall(graphics, sendX + 10, y, smallIconScale());
        } else {
            CommonGuiTextures.drawOkCheckGreenTint(graphics, sendX + 10, y, smallIconScale(),
                0.48f, 0.42f, 0.33f, 0.55f);
        }
    }

    private String formatGold(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return Math.max(1, max);
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playSelect() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BUTTON_PRESS.get(), 0.42F, 1.06F));
        }
    }

    private void playCancel() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 0.36F, 0.92F));
        }
    }
}
