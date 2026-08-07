package com.stardew.craft.client.gui.festival;

import com.mojang.math.Axis;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.FairWheelGameResultPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class FairWheelGameScreen extends Screen implements com.stardew.craft.client.gui.StardewRealtimeScreen {
    private static final int WHEEL_SDV_W = 640;
    private static final int WHEEL_SDV_H = 448;
    private static final int QUESTION_W = 1200;
    private static final int QUESTION_H = 360;
    private static final int NUMBER_W = 900;
    private static final int NUMBER_H = 352;
    private static final int NO_TOKEN_H = 220;
    private static final int BORDER = 64;
    private static final int SIDE_SPACE = 16;

    private static final double HALF_PI = Math.PI / 2.0D;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double LUCK_BUMP_THRESHOLD = Math.PI / 80.0D;
    private static final double LUCK_BUMP_SPEED = Math.PI / 48.0D;
    private static final double ORANGE_END = Math.PI * 1.5D;
    private static final double GREEN_LUCK_END = 4.319689898685965D;
    private static final double DECELERATION = -0.0006283185307179586D;

    private final Random random = new Random();
    private final int luckLevel;
    private int starTokens;
    private int wager;
    private boolean colorChosen;
    private boolean choseGreen;
    private boolean spinning;
    private boolean doneSpinning;
    private boolean resultSent;
    private boolean won;
    private int timerBeforeStartMs;
    private int resultTimerMs;
    private double arrowRotation;
    private double arrowRotationVelocity;
    private double arrowRotationDeceleration;
    private long lastUpdateMs;
    private double frameAccumulator;

    private float guiScale = 1.0F;
    private float s4 = 4.0F;
    private int questionX;
    private int questionY;
    private int questionW;
    private int questionH;
    private Rect orangeButton = Rect.ZERO;
    private Rect greenButton = Rect.ZERO;
    private Rect questionCancelButton = Rect.ZERO;
    private int numberX;
    private int numberY;
    private int numberW;
    private int numberH;
    private Rect leftButton = Rect.ZERO;
    private Rect rightButton = Rect.ZERO;
    private Rect numberBox = Rect.ZERO;
    private Rect okButton = Rect.ZERO;
    private Rect cancelButton = Rect.ZERO;
    private int noTokenX;
    private int noTokenY;
    private int noTokenW;
    private int noTokenH;
    private Rect noTokenOkButton = Rect.ZERO;
    private EditBox wagerInput;

    public FairWheelGameScreen(int starTokens, int luckLevel) {
        super(Component.translatable("stardewcraft.fair.wheel.title"));
        this.starTokens = Math.max(0, starTokens);
        this.luckLevel = luckLevel;
    }

    @Override
    protected void init() {
        lastUpdateMs = System.currentTimeMillis();
        guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        s4 = 4.0F / guiScale;
        computeLayout();
        wagerInput = new EditBox(com.stardew.craft.client.font.StardewFonts.small(),
            numberBox.x() + ui(16),
            numberBox.y() + (numberBox.height() - font.lineHeight) / 2,
            Math.max(12, numberBox.width() - ui(32)),
            font.lineHeight,
            Component.translatable("stardewcraft.fair.wheel.wager"));
        wagerInput.setBordered(false);
        wagerInput.setTextShadow(false);
        wagerInput.setTextColor(0xFF3F2A13);
        wagerInput.setTextColorUneditable(0xFF3F2A13);
        wagerInput.setMaxLength(Math.max(1, String.valueOf(Math.max(1, starTokens)).length()));
        wagerInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        wagerInput.setValue("0");
        wagerInput.visible = false;
        addWidget(wagerInput);
    }

    private void computeLayout() {
        int sdvViewW = Math.round(width * guiScale);
        int sdvViewH = Math.round(height * guiScale);

        int questionSdvX = sdvViewW / 2 - QUESTION_W / 2;
        int questionSdvY = sdvViewH / 2 - QUESTION_H / 2;
        questionX = ui(questionSdvX);
        questionY = ui(questionSdvY);
        questionW = ui(QUESTION_W);
        questionH = ui(QUESTION_H);
        orangeButton = rect(questionSdvX + 120, questionSdvY + 208, 240, 56);
        greenButton = rect(questionSdvX + 420, questionSdvY + 208, 240, 56);
        questionCancelButton = rect(questionSdvX + 720, questionSdvY + 208, 360, 56);

        int numberSdvX = sdvViewW / 2 - NUMBER_W / 2;
        int numberSdvY = sdvViewH / 2 - NUMBER_H / 2;
        numberX = ui(numberSdvX);
        numberY = ui(numberSdvY);
        numberW = ui(NUMBER_W);
        numberH = ui(NUMBER_H);
        leftButton = rect(numberSdvX + BORDER, numberSdvY + BORDER + NUMBER_H / 2, 48, 44);
        numberBox = rect(numberSdvX + BORDER + 56, numberSdvY + BORDER + NUMBER_H / 2 - 2, 192, 52);
        rightButton = rect(numberSdvX + BORDER + 64 + 192, numberSdvY + BORDER + NUMBER_H / 2, 48, 44);
        okButton = rect(numberSdvX + NUMBER_W - BORDER - SIDE_SPACE - 128, numberSdvY + NUMBER_H - BORDER - SIDE_SPACE + 21, 64, 64);
        cancelButton = rect(numberSdvX + NUMBER_W - BORDER - SIDE_SPACE - 64, numberSdvY + NUMBER_H - BORDER - SIDE_SPACE + 21, 64, 64);

        int noTokenSdvX = sdvViewW / 2 - QUESTION_W / 2;
        int noTokenSdvY = sdvViewH / 2 - NO_TOKEN_H / 2;
        noTokenX = ui(noTokenSdvX);
        noTokenY = ui(noTokenSdvY);
        noTokenW = ui(QUESTION_W);
        noTokenH = ui(NO_TOKEN_H);
        noTokenOkButton = rect(noTokenSdvX + QUESTION_W / 2 - 120, noTokenSdvY + 132, 240, 56);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateState();
        graphics.fill(0, 0, width, height, 0x80000000);

        if (spinning) {
            drawWheel(graphics);
        } else if (starTokens <= 0) {
            drawNoTokens(graphics, mouseX, mouseY);
        } else if (!colorChosen) {
            drawColorChoice(graphics, mouseX, mouseY);
        } else {
            drawWagerChoice(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateState() {
        long now = System.currentTimeMillis();
        int elapsedMs = (int) Math.max(0L, Math.min(250L, now - lastUpdateMs));
        lastUpdateMs = now;

        if (!spinning) {
            return;
        }

        if (timerBeforeStartMs > 0) {
            int previous = timerBeforeStartMs;
            timerBeforeStartMs = Math.max(0, timerBeforeStartMs - elapsedMs);
            if (previous > 0 && timerBeforeStartMs == 0) {
                play(ModSounds.COWBOY_MONSTERHIT.get(), 1.0F, 1.0F);
            }
            return;
        }

        if (doneSpinning) {
            resultTimerMs = Math.max(0, resultTimerMs - elapsedMs);
            if (resultTimerMs == 0) {
                onClose();
            }
            return;
        }

        frameAccumulator += elapsedMs / (1000.0D / 60.0D);
        while (frameAccumulator >= 1.0D && !doneSpinning) {
            frameAccumulator -= 1.0D;
            updateSpinFrame();
        }
    }

    private void updateSpinFrame() {
        double oldVelocity = arrowRotationVelocity;
        arrowRotationVelocity += arrowRotationDeceleration;

        if (arrowRotationVelocity <= LUCK_BUMP_THRESHOLD && oldVelocity > LUCK_BUMP_THRESHOLD) {
            applyLuckBump();
        }

        if (arrowRotationVelocity <= 0.0D) {
            finishSpin();
            return;
        }

        double oldQuarter = arrowRotation % HALF_PI;
        double nextRotation = arrowRotation + arrowRotationVelocity;
        double nextQuarter = nextRotation % HALF_PI;
        if (oldQuarter > nextQuarter) {
            play(ModSounds.COWBOY_GUNSHOT.get(), 1.0F, 1.0F);
        }
        arrowRotation = normalize(nextRotation);
    }

    private void applyLuckBump() {
        if (luckLevel <= 0) {
            return;
        }
        if (arrowRotation > HALF_PI
            && arrowRotation <= GREEN_LUCK_END
            && choseGreen
            && random.nextDouble() < luckLevel / 15.0D) {
            arrowRotationVelocity = LUCK_BUMP_SPEED;
            return;
        }

        if (normalize(arrowRotation + Math.PI) <= GREEN_LUCK_END
            && !choseGreen
            && random.nextDouble() < luckLevel / 20.0D) {
            arrowRotationVelocity = LUCK_BUMP_SPEED;
        }
    }

    private void finishSpin() {
        doneSpinning = true;
        arrowRotationVelocity = 0.0D;
        arrowRotationDeceleration = 0.0D;
        won = (arrowRotation > HALF_PI && arrowRotation <= ORANGE_END) ? !choseGreen : choseGreen;
        starTokens = Math.max(0, starTokens + (won ? wager : -wager));
        sendResultOnce();
        play(won ? ModSounds.REWARD.get() : ModSounds.FISH_ESCAPE.get(), 1.0F, 1.0F);
        resultTimerMs = 1700;
    }

    private void sendResultOnce() {
        if (resultSent) {
            return;
        }
        resultSent = true;
        PacketDistributor.sendToServer(new FairWheelGameResultPayload(wager, won));
    }

    private void drawWheel(GuiGraphics graphics) {
        int sdvViewW = Math.round(width * guiScale);
        int sdvViewH = Math.round(height * guiScale);
        int x = ui(sdvViewW / 2 - WHEEL_SDV_W / 2);
        int y = ui(sdvViewH / 2 - WHEEL_SDV_H / 2);
        StardewGuiUtil.drawFromCursors(graphics, x, y, 128, 1184, 160, 112, s4);

        graphics.pose().pushPose();
        graphics.pose().translate(x + ui(320), y + ui(228), 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation((float) arrowRotation));
        graphics.pose().scale(s4, s4, 1.0F);
        graphics.blit(StardewGuiUtil.CURSORS, -4, -15, 120, 1234, 8, 16,
            StardewGuiUtil.CURSORS_WIDTH, StardewGuiUtil.CURSORS_HEIGHT);
        graphics.pose().popPose();

        if (doneSpinning) {
            String result = I18n.get(won ? "stardewcraft.fair.wheel.winner" : "stardewcraft.fair.wheel.lose");
            graphics.drawString(font, result, width / 2 - font.width(result) / 2,
                Math.max(8, y - ui(72)), won ? 0xFFFFD34A : 0xFFFF6D58, false);
        }
    }

    private void drawColorChoice(GuiGraphics graphics, int mouseX, int mouseY) {
        CommonGuiTextures.drawTextureBox(graphics, questionX, questionY, questionW, questionH, s4, true);
        drawWrapped(graphics, I18n.get("stardewcraft.fair.wheel.question"),
            questionX + ui(64), questionY + ui(64), questionW - ui(128), 0xFF3F2A13);
        drawButton(graphics, orangeButton, I18n.get("stardewcraft.fair.wheel.orange"), mouseX, mouseY);
        drawButton(graphics, greenButton, I18n.get("stardewcraft.fair.wheel.green"), mouseX, mouseY);
        drawButton(graphics, questionCancelButton, I18n.get("stardewcraft.fair.wheel.leave"), mouseX, mouseY);
    }

    private void drawWagerChoice(GuiGraphics graphics, int mouseX, int mouseY) {
        CommonGuiTextures.drawTextureBox(graphics, numberX, numberY, numberW, numberH, s4, true);
        drawWrapped(graphics, I18n.get("stardewcraft.fair.wheel.wager"),
            numberX + ui(BORDER), numberY + ui(48), numberW - ui(BORDER * 2), 0xFF3F2A13);
        String tokenText = I18n.get("stardewcraft.fair.wheel.tokens", starTokens);
        graphics.drawString(font, tokenText, numberX + numberW / 2 - font.width(tokenText) / 2,
            numberY + ui(132), 0xFF5B3418, false);

        CommonGuiTextures.drawBackArrow(graphics, leftButton.x(), leftButton.y(), s4);
        CommonGuiTextures.drawEntryBox(graphics, numberBox.x(), numberBox.y(), numberBox.width(), numberBox.height(), s4, false);
        if (wagerInput != null) {
            int centeredTextX = numberBox.x() + numberBox.width() / 2
                - font.width(wagerInput.getValue()) / 2;
            wagerInput.setX(centeredTextX);
            wagerInput.setWidth(Math.max(12, numberBox.x() + numberBox.width() - ui(8) - centeredTextX));
            wagerInput.visible = true;
            wagerInput.active = true;
            wagerInput.render(graphics, mouseX, mouseY, 0.0F);
        }
        CommonGuiTextures.drawForwardArrow(graphics, rightButton.x(), rightButton.y(), s4);

        CommonGuiTextures.drawOkCheckSmall(graphics, okButton.x(), okButton.y(), s4);
        CommonGuiTextures.drawLargeCancelButton(graphics, cancelButton.x(), cancelButton.y(), 1.0F / guiScale);
    }

    private void drawNoTokens(GuiGraphics graphics, int mouseX, int mouseY) {
        CommonGuiTextures.drawTextureBox(graphics, noTokenX, noTokenY, noTokenW, noTokenH, s4, true);
        drawWrapped(graphics, I18n.get("stardewcraft.fair.wheel.no_tokens"),
            noTokenX + ui(64), noTokenY + ui(64), noTokenW - ui(128), 0xFF3F2A13);
        drawButton(graphics, noTokenOkButton, I18n.get("stardewcraft.fair.wheel.ok"), mouseX, mouseY);
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight + ui(12);
        }
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String text, int mouseX, int mouseY) {
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, rect.x(), rect.y(), rect.width(), rect.height(), s4);
        int color = rect.contains(mouseX, mouseY) ? 0xFF8B4F1B : 0xFF3F2A13;
        graphics.drawString(font, text, rect.x() + rect.width() / 2 - font.width(text) / 2,
            rect.y() + (rect.height() - font.lineHeight) / 2, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (doneSpinning) {
            onClose();
            return true;
        }
        if (spinning) {
            return true;
        }
        if (starTokens <= 0) {
            if (noTokenOkButton.contains(mx, my)) {
                onClose();
                return true;
            }
            return true;
        }

        if (!colorChosen) {
            if (questionCancelButton.contains(mx, my)) {
                onClose();
                return true;
            }
            if (orangeButton.contains(mx, my)) {
                chooseColor(false);
                return true;
            }
            if (greenButton.contains(mx, my)) {
                chooseColor(true);
                return true;
            }
            return true;
        }

        if (cancelButton.contains(mx, my)) {
            onClose();
            return true;
        }
        if (leftButton.contains(mx, my)) {
            setWager(Math.max(1, inputWager() - 1));
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            return true;
        }
        if (rightButton.contains(mx, my)) {
            setWager(Math.min(starTokens, inputWager() + 1));
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            return true;
        }
        if (numberBox.contains(mx, my) && wagerInput != null) {
            setFocused(wagerInput);
            wagerInput.setFocused(true);
            return wagerInput.mouseClicked(mouseX, mouseY, button);
        }
        if (okButton.contains(mx, my)) {
            startSpin();
            return true;
        }
        return true;
    }

    private void chooseColor(boolean green) {
        colorChosen = true;
        choseGreen = green;
        setWager(Math.min(1, starTokens));
        play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
    }

    private void startSpin() {
        wager = inputWager();
        if (wager <= 0 || wager > starTokens) {
            return;
        }
        setWager(wager);
        if (wagerInput != null) {
            wagerInput.visible = false;
            wagerInput.setFocused(false);
        }
        spinning = true;
        doneSpinning = false;
        resultSent = false;
        resultTimerMs = 0;
        timerBeforeStartMs = 1000;
        frameAccumulator = 0.0D;
        arrowRotation = 0.0D;
        arrowRotationVelocity = Math.PI / 16.0D + random.nextInt(15) * Math.PI / 256.0D;
        if (random.nextBoolean()) {
            arrowRotationVelocity += Math.PI / 64.0D;
        }
        arrowRotationDeceleration = DECELERATION;
        lastUpdateMs = System.currentTimeMillis();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (!spinning && colorChosen && inputWager() > 0 && starTokens > 0) {
                startSpin();
                return true;
            }
        }
        if (wagerInput != null && wagerInput.visible
                && wagerInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return wagerInput != null && wagerInput.visible && wagerInput.charTyped(codePoint, modifiers)
            || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int ui(int sdvPx) {
        return Math.round(sdvPx / guiScale);
    }

    private int inputWager() {
        if (wagerInput == null || wagerInput.getValue().isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(starTokens, Integer.parseInt(wagerInput.getValue())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void setWager(int value) {
        wager = Math.max(0, Math.min(starTokens, value));
        if (wagerInput != null) {
            wagerInput.setValue(String.valueOf(wager));
        }
    }

    private Rect rect(int sdvX, int sdvY, int sdvW, int sdvH) {
        return new Rect(ui(sdvX), ui(sdvY), ui(sdvW), ui(sdvH));
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(sound, volume, pitch);
        }
    }

    private static double normalize(double value) {
        double out = value % TWO_PI;
        return out < 0.0D ? out + TWO_PI : out;
    }

    private record Rect(int x, int y, int width, int height) {
        private static final Rect ZERO = new Rect(0, 0, 0, 0);

        boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
