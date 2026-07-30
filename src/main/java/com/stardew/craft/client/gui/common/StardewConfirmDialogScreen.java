package com.stardew.craft.client.gui.common;

import com.stardew.craft.network.payload.SleepConfirmChoicePayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class StardewConfirmDialogScreen extends Screen {
    private final StardewQuestionDialogSpec spec;
    private StardewRenderMapping mapping;

    private int selected = -1;
    private boolean resolved;
    private int boxX;
    private int boxDrawY;
    private int boxWidth;
    private int boxHeight;
    private int questionHeight;
    private final List<WrappedResponse> wrappedResponses = new ArrayList<>();
    private boolean transitioning = true;
    private boolean transitioningBigger = true;
    private boolean transitionInitialized;
    private int transitionX;
    private int transitionY;
    private int transitionWidth;
    private int transitionHeight;
    private int pendingAnswerIndex = -1;
    private long lastUpdateMs;
    private long lastRenderMs;
    private boolean openingSoundPlayed;
    private int characterIndexInDialogue;
    private int characterAdvanceTimer;
    private int safetyTimer = 750;

    private record WrappedResponse(Component original, List<net.minecraft.util.FormattedCharSequence> lines, int lineHeight) {
    }

    private StardewConfirmDialogScreen(StardewQuestionDialogSpec spec) {
        super(spec.question());
        this.spec = spec;
        this.selected = spec.defaultSelectedIndex();
    }

    public static StardewConfirmDialogScreen createQuestionDialog(StardewQuestionDialogSpec spec) {
        return new StardewConfirmDialogScreen(spec);
    }

    public static StardewConfirmDialogScreen createSleepConfirm(int currentMinute) {
        return createQuestionDialog(
            StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.sleep.confirm.message"),
                List.of(
                    Component.translatable("stardewcraft.sleep.confirm.yes"),
                    Component.translatable("stardewcraft.sleep.confirm.no")
                ),
                index -> {
                    if (index == 0) {
                        com.stardew.craft.cutscene.runtime.EventScreenFade.startFadeToBlack(12);
                    }
                    PacketDistributor.sendToServer(new SleepConfirmChoicePayload(index == 0, currentMinute));
                    // 确认后关闭对话框；服务端会调用 startSleeping → 客户端自动弹出原版 InBedChatScreen
                },

                -1
            )
        );
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private boolean moneyContractSounds() {
        return spec.soundTheme() == StardewQuestionDialogSpec.SoundTheme.MONEY_CONTRACT;
    }

    private void playOpeningSound() {
        if (moneyContractSounds()) {
            playUiSound(ModSounds.BOOK_READ.get(), 0.58f, 1.08f);
        } else {
            playUiSound(ModSounds.BREATHIN.get(), 1.0f, 1.0f);
        }
    }

    private void playHoverSound() {
        if (moneyContractSounds()) {
            playUiSound(ModSounds.BUTTON_TAP.get(), 0.42f, 1.16f);
        } else {
            // Match the rest of the UI: option hover uses the soft SDV "small select" tick, not the loud gunshot.
            playUiSound(ModSounds.SMALL_SELECT.get(), 0.5f, 1.0f);
        }
    }

    private void playSkipSound() {
        if (moneyContractSounds()) {
            playUiSound(ModSounds.BUTTON_TAP.get(), 0.48f, 1.10f);
        } else {
            playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
        }
    }

    private void playDecisionSound(int index) {
        if (!moneyContractSounds()) {
            playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
            playUiSound(ModSounds.BREATHOUT.get(), 1.0f, 1.0f);
            return;
        }
        if (index == spec.responses().size() - 1) {
            playUiSound(ModSounds.CANCEL.get(), 0.46f, 0.94f);
        } else {
            playUiSound(ModSounds.BOOK_READ.get(), 0.52f, 1.18f);
            playUiSound(ModSounds.COIN.get(), 0.36f, 1.20f);
        }
    }

    private float guiScale() {
        return this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
    }

    private int px(int stardewPixels) {
        return (mapping == null) ? Math.round(stardewPixels / guiScale()) : mapping.ui(stardewPixels);
    }

    private int measureWrappedHeight(List<net.minecraft.util.FormattedCharSequence> lines) {
        int lineCount = Math.max(1, lines.size());
        return lineCount * this.font.lineHeight;
    }

    private String getCurrentString() {
        return spec.question().getString();
    }

    private boolean isQuestionBlank() {
        return getCurrentString().isBlank();
    }

    private int getQuestionWrapWidth() {
        return Math.max(1, boxWidth - px(16));
    }

    private void recomputeLayout() {
        mapping = new StardewRenderMapping(this.width, this.height, guiScale());
        boxWidth = px(spec.dialogWidth());
        int questionWrapWidth = getQuestionWrapWidth();
        questionHeight = isQuestionBlank() ? 0 : measureWrappedHeight(this.font.split(spec.question(), questionWrapWidth));

        wrappedResponses.clear();
        int totalHeight = questionHeight;
        for (Component response : spec.responses()) {
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(response, boxWidth);
            int responseHeight = measureWrappedHeight(lines);
            wrappedResponses.add(new WrappedResponse(response, lines, responseHeight));
            totalHeight += responseHeight + px(16);
        }
        totalHeight += px(40);
        boxHeight = totalHeight;

        boxX = mapping.centerX(boxWidth);
        boxDrawY = mapping.bottomY(boxHeight, spec.dialogBottomMargin());
    }

    private int optionStartY() {
        if (isQuestionBlank()) {
            return boxDrawY + px(12);
        }
        return boxDrawY + questionHeight + px(48);
    }

    private int optionRowHeight(int index) {
        return wrappedResponses.get(index).lineHeight() + px(16);
    }

    private int optionAt(double mouseX, double mouseY) {
        int y = optionStartY();
        for (int i = 0; i < wrappedResponses.size(); i++) {
            int rowHeight = optionRowHeight(i);
            if (mouseX >= boxX + px(8) && mouseX <= boxX + boxWidth - px(8) && mouseY >= y - px(8) && mouseY < y + rowHeight - px(8)) {
                return i;
            }
            y += rowHeight;
        }
        return -1;
    }

    private void drawWrappedLeftAligned(GuiGraphics graphics, List<net.minecraft.util.FormattedCharSequence> lines, int x, int y, int color) {
        int drawY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, drawY, color, false);
            drawY += this.font.lineHeight;
        }
    }

    // Code-level port of Stardew DialogueBox.drawBox for question dialogues.
    private void drawQuestionBoxFrame(GuiGraphics graphics, int xPos, int yPos, int frameWidth, int frameHeight) {
        CommonGuiTextures.drawDialogueQuestionFill(graphics, xPos, yPos, frameWidth, frameHeight);

        CommonGuiTextures.drawDialogueQuestionTop(graphics, xPos, yPos - px(20), frameWidth, px(24));
        CommonGuiTextures.drawDialogueQuestionBottom(graphics, xPos + px(12), yPos + frameHeight, Math.max(0, frameWidth - px(20)), px(32));
        CommonGuiTextures.drawDialogueQuestionLeft(graphics, xPos - px(32), yPos + px(24), px(32), Math.max(0, frameHeight - px(28)));
        CommonGuiTextures.drawDialogueQuestionRight(graphics, xPos + frameWidth, yPos, px(28), frameHeight);

        float s4 = mapping.s4();
        CommonGuiTextures.drawDialogueQuestionCornerTl(graphics, xPos - px(44), yPos - px(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerTr(graphics, xPos + frameWidth - px(8), yPos - px(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBr(graphics, xPos + frameWidth - px(8), yPos + frameHeight - px(8), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBl(graphics, xPos - px(44), yPos + frameHeight - px(4), s4);
    }

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
        lastUpdateMs = Util.getMillis();
        lastRenderMs = Util.getMillis();
        characterIndexInDialogue = isQuestionBlank() ? getCurrentString().length() : 0;
        characterAdvanceTimer = 90;
        safetyTimer = 750;
    }

    private void updateTransition(long elapsedMs) {
        if (!transitioning) {
            return;
        }

        if (!transitionInitialized) {
            transitionInitialized = true;
            transitionX = boxX + boxWidth / 2;
            transitionY = boxDrawY + boxHeight / 2;
            transitionWidth = 0;
            transitionHeight = 0;
        }

        float ratio = (boxWidth <= 0) ? 1.0f : ((float) boxHeight / (float) boxWidth);
        int speed = (int) (elapsedMs * 3L);
        int speedY = (int) (elapsedMs * 3L * ratio);

        if (transitioningBigger) {
            int oldWidth = transitionWidth;
            transitionX -= speed;
            transitionY -= speedY;
            transitionX = Math.max(boxX, transitionX);
            transitionY = Math.max(boxDrawY, transitionY);
            transitionWidth += speed * 2;
            transitionHeight += speedY * 2;
            transitionWidth = Math.min(boxWidth, transitionWidth);
            transitionHeight = Math.min(boxHeight, transitionHeight);

            if (!openingSoundPlayed && oldWidth == 0 && transitionWidth > 0) {
                openingSoundPlayed = true;
                playOpeningSound();
            }

            if (transitionX == boxX && transitionY == boxDrawY) {
                transitioning = false;
                transitionX = boxX;
                transitionY = boxDrawY;
                transitionWidth = boxWidth;
                transitionHeight = boxHeight;
            }
        } else {
            transitionX += speed;
            transitionY += speedY;
            transitionX = Math.min(boxX + boxWidth / 2, transitionX);
            transitionY = Math.min(boxDrawY + boxHeight / 2, transitionY);
            transitionWidth -= speed * 2;
            transitionHeight -= speedY * 2;
            transitionWidth = Math.max(0, transitionWidth);
            transitionHeight = Math.max(0, transitionHeight);

            if (transitionWidth == 0 && transitionHeight == 0) {
                transitioning = false;
                int answerIndex = pendingAnswerIndex;
                if (this.minecraft != null && this.minecraft.screen == this) {
                    this.minecraft.setScreen(null);
                }
                if (!resolved && pendingAnswerIndex >= 0) {
                    resolved = true;
                    spec.onAnswer().accept(answerIndex);
                }
            }
        }
    }

    @Override
    public void tick() {
        recomputeLayout();
        long now = Util.getMillis();
        long elapsed = Math.max(0L, now - lastUpdateMs);
        lastUpdateMs = now;

        if (safetyTimer > 0) {
            safetyTimer -= (int) elapsed;
        }

        if (!transitioning && pendingAnswerIndex < 0 && characterIndexInDialogue < getCurrentString().length()) {
            characterAdvanceTimer -= (int) elapsed;
            if (characterAdvanceTimer <= 0) {
                characterAdvanceTimer = 30;
                int oldIndex = characterIndexInDialogue;
                characterIndexInDialogue = Math.min(characterIndexInDialogue + 1, getCurrentString().length());
                if (characterIndexInDialogue != oldIndex && characterIndexInDialogue == getCurrentString().length()) {
                    playUiSound(ModSounds.DIALOGUE_CHARACTER_CLOSE.get(), 1.0f, 1.0f);
                }
                if (characterIndexInDialogue > 1 && characterIndexInDialogue < getCurrentString().length()) {
                    playUiSound(ModSounds.DIALOGUE_CHARACTER.get(), 1.0f, 1.0f);
                }
            }
        }
    }

    private String getVisibleQuestionText() {
        String text = getCurrentString();
        int end = Math.max(0, Math.min(characterIndexInDialogue, text.length()));
        return text.substring(0, end);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recomputeLayout();
        long now = Util.getMillis();
        long renderElapsed = Math.max(0L, now - lastRenderMs);
        lastRenderMs = now;
        updateTransition(renderElapsed);

        if (transitioning) {
            if (transitionWidth > 0 && transitionHeight > 0) {
                drawQuestionBoxFrame(graphics, transitionX, transitionY, transitionWidth, transitionHeight);
            }
            return;
        }

        boolean showOptions = characterIndexInDialogue >= getCurrentString().length();
        int hoverIndex = showOptions ? optionAt(mouseX, mouseY) : -1;
        if (hoverIndex >= 0) {
            if (hoverIndex != selected) {
                playHoverSound();
            }
            selected = hoverIndex;
        }

        drawQuestionBoxFrame(graphics, boxX, boxDrawY, boxWidth, boxHeight);
        float s4 = mapping.s4();
        CommonGuiTextures.drawQuestionExclamation16(graphics, boxX + boxWidth - px(72), boxDrawY - px(88), s4);
        int arrowFrame = (int) ((System.currentTimeMillis() % 900L) / 150L);
        CommonGuiTextures.drawQuestionArrow16(graphics, boxX + boxWidth - px(52), boxDrawY - px(72), arrowFrame, s4);

        int textX = boxX + px(8);
        if (!isQuestionBlank()) {
            List<net.minecraft.util.FormattedCharSequence> questionLines = this.font.split(Component.literal(getVisibleQuestionText()), getQuestionWrapWidth());
            drawWrappedLeftAligned(graphics, questionLines, textX, boxDrawY + px(12), 0x3A2A1A);
        }

        if (showOptions) {
            int responseY = optionStartY();
            for (int i = 0; i < wrappedResponses.size(); i++) {
                WrappedResponse response = wrappedResponses.get(i);
                if (i == selected) {
                    CommonGuiTextures.drawOptionHighlightBox(
                        graphics,
                        boxX + px(4),
                        responseY - px(8),
                        boxWidth - px(8),
                        response.lineHeight() + px(16),
                        s4);
                }

                float alpha = (selected == i) ? 1.0f : 0.6f;
                graphics.setColor(alpha, alpha, alpha, 1.0f);
                drawWrappedLeftAligned(graphics, response.lines(), textX, responseY, 0x3A2A1A);
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                responseY += response.lineHeight() + px(16);
            }
        }
    }

    private void submitDecision(int index) {
        if (resolved || transitioning || pendingAnswerIndex >= 0) {
            return;
        }
        pendingAnswerIndex = index;
        playDecisionSound(index);
        transitioning = true;
        transitioningBigger = false;
        transitionInitialized = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transitioning || pendingAnswerIndex >= 0) {
            return true;
        }
        if (button == 0) {
            if (characterIndexInDialogue < getCurrentString().length()) {
                characterIndexInDialogue = getCurrentString().length();
                playSkipSound();
                return true;
            }
            if (safetyTimer > 0) {
                return true;
            }
            int hit = optionAt(mouseX, mouseY);
            if (hit >= 0) {
                submitDecision(hit);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (transitioning || pendingAnswerIndex >= 0) {
            return true;
        }
        if (characterIndexInDialogue < getCurrentString().length()) {
            characterIndexInDialogue = getCurrentString().length();
            playSkipSound();
            return true;
        }
        if (safetyTimer > 0) {
            return true;
        }
        if (keyCode == 265 || keyCode == 264 || keyCode == 263 || keyCode == 262) {
            if (selected < 0) {
                selected = 0;
            } else {
                int delta = (keyCode == 265 || keyCode == 263) ? -1 : 1;
                selected = Math.floorMod(selected + delta, spec.responses().size());
            }
            return true;
        }

        // Y hotkey mirrors vanilla createYesNoResponses behavior for the first response.
        if (keyCode == 89) {
            submitDecision(0);
            return true;
        }

        // Escape/N maps to the last response in vanilla yes-no dialogs.
        if (keyCode == 78 || keyCode == 256) {
            submitDecision(spec.responses().size() - 1);
            return true;
        }

        if (keyCode == 257 || keyCode == 32 || keyCode == 335) {
            submitDecision((selected >= 0) ? selected : 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
