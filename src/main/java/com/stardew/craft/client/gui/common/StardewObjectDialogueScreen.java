package com.stardew.craft.client.gui.common;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.StardewCollectivePauseScreen;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class StardewObjectDialogueScreen extends Screen implements StardewCollectivePauseScreen {
    private final List<Component> rawMessages;
    private final List<String> messages = new ArrayList<>();
    private StardewRenderMapping mapping;

    private int messageIndex;
    private int characterIndexInDialogue;
    private int characterAdvanceTimer;
    private int safetyTimer = 750;
    private int iconFrameTick;
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;
    private boolean transitioning = true;
    private boolean transitioningBigger = true;
    private boolean transitionInitialized;
    private boolean openingSoundPlayed;
    private int transitionX;
    private int transitionY;
    private int transitionWidth;
    private int transitionHeight;
    private long lastUpdateMs;
    private long lastRenderMs;
    private Runnable afterCloseAction;

    public StardewObjectDialogueScreen(List<Component> messages) {
        super(Component.literal("Object Dialogue"));
        this.rawMessages = List.copyOf(messages == null || messages.isEmpty() ? List.of(Component.literal("...")) : messages);
    }

    public StardewObjectDialogueScreen withAfterClose(Runnable action) {
        this.afterCloseAction = action;
        return this;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        rebuildMessages();
        this.messageIndex = 0;
        this.characterIndexInDialogue = 0;
        this.characterAdvanceTimer = 90;
        this.safetyTimer = safetyDelayFor(currentMessage());
        this.transitioning = true;
        this.transitioningBigger = true;
        this.transitionInitialized = false;
        this.openingSoundPlayed = false;
        this.iconFrameTick = 0;
        this.lastUpdateMs = Util.getMillis();
        this.lastRenderMs = Util.getMillis();
        recomputeLayout();
    }

    @Override
    public void tick() {
        recomputeLayout();
        long now = Util.getMillis();
        int elapsed = (int) Math.max(0L, now - this.lastUpdateMs);
        this.lastUpdateMs = now;
        if (transitioning) {
            return;
        }

        if (safetyTimer > 0) {
            safetyTimer = Math.max(0, safetyTimer - elapsed);
        }

        String current = currentMessage();
        if (characterIndexInDialogue >= current.length()) {
            return;
        }

        characterAdvanceTimer -= elapsed;
        while (characterAdvanceTimer <= 0 && characterIndexInDialogue < current.length()) {
            characterAdvanceTimer += 30;
            int old = characterIndexInDialogue;
            characterIndexInDialogue = Math.min(current.length(), characterIndexInDialogue + 1);
            if (characterIndexInDialogue != old && characterIndexInDialogue == current.length()) {
                playUiSound(ModSounds.DIALOGUE_CHARACTER_CLOSE.get(), 1.0f, 1.0f);
            } else if (characterIndexInDialogue > 1 && characterIndexInDialogue < current.length()) {
                playUiSound(ModSounds.DIALOGUE_CHARACTER.get(), 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recomputeLayout();
        long now = Util.getMillis();
        int elapsed = (int) Math.max(0L, now - this.lastRenderMs);
        this.lastRenderMs = now;
        this.iconFrameTick += elapsed;
        updateTransition(elapsed);

        if (transitioning) {
            if (transitionWidth > 0 && transitionHeight > 0) {
                drawBox(graphics, transitionX, transitionY, transitionWidth, transitionHeight);
            }
            return;
        }

        drawBox(graphics, boxX, boxY, boxWidth, boxHeight);
        drawDialogueText(graphics);
        drawContinueOrCloseIcon(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        receiveAdvanceClick();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 257 || keyCode == 32 || keyCode == 335) {
            receiveAdvanceClick();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void receiveAdvanceClick() {
        if (transitioning) {
            return;
        }
        String current = currentMessage();
        if (characterIndexInDialogue < current.length()) {
            characterIndexInDialogue = current.length();
            playUiSound(ModSounds.DIALOGUE_CHARACTER_CLOSE.get(), 1.0f, 1.0f);
            return;
        }
        if (safetyTimer > 0) {
            return;
        }

        if (messageIndex + 1 < messages.size()) {
            messageIndex++;
            characterIndexInDialogue = 0;
            characterAdvanceTimer = 90;
            safetyTimer = safetyDelayFor(currentMessage());
            playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
            return;
        }

        beginOutro();
    }

    private void beginOutro() {
        transitioning = true;
        transitioningBigger = false;
        transitionInitialized = false;
        playUiSound(ModSounds.BREATHOUT.get(), 1.0f, 1.0f);
    }

    private void updateTransition(int elapsedMs) {
        if (!transitioning) {
            return;
        }

        if (!transitionInitialized) {
            transitionInitialized = true;
            transitionX = boxX + boxWidth / 2;
            transitionY = boxY + boxHeight / 2;
            transitionWidth = 0;
            transitionHeight = 0;
        }

        float ratio = boxWidth <= 0 ? 1.0f : (float) boxHeight / (float) boxWidth;
        int speed = Math.max(1, elapsedMs * 3);
        int speedY = Math.max(1, Math.round(elapsedMs * 3.0f * ratio));

        if (transitioningBigger) {
            int oldWidth = transitionWidth;
            transitionX = Math.max(boxX, transitionX - speed);
            transitionY = Math.max(boxY, transitionY - speedY);
            transitionWidth = Math.min(boxWidth, transitionWidth + speed * 2);
            transitionHeight = Math.min(boxHeight, transitionHeight + speedY * 2);

            if (!openingSoundPlayed && oldWidth == 0 && transitionWidth > 0) {
                openingSoundPlayed = true;
                playUiSound(ModSounds.BREATHIN.get(), 1.0f, 1.0f);
            }
            if (transitionX == boxX && transitionY == boxY && transitionWidth == boxWidth && transitionHeight == boxHeight) {
                transitioning = false;
            }
            return;
        }

        transitionX = Math.min(boxX + boxWidth / 2, transitionX + speed);
        transitionY = Math.min(boxY + boxHeight / 2, transitionY + speedY);
        transitionWidth = Math.max(0, transitionWidth - speed * 2);
        transitionHeight = Math.max(0, transitionHeight - speedY * 2);
        if (transitionWidth == 0 && transitionHeight == 0 && this.minecraft != null) {
            this.minecraft.setScreen(null);
            if (afterCloseAction != null) {
                Runnable action = afterCloseAction;
                afterCloseAction = null;
                action.run();
            }
        }
    }

    private void rebuildMessages() {
        messages.clear();
        for (Component component : rawMessages) {
            String text = normalize(component.getString());
            String[] pages = text.split("#");
            for (String page : pages) {
                String trimmed = page.strip();
                if (!trimmed.isEmpty()) {
                    messages.add(trimmed);
                }
            }
        }
        if (messages.isEmpty()) {
            messages.add("...");
        }
    }

    private String normalize(String text) {
        String normalized = text == null ? "..." : text;
        return normalized
                .replace("\r", "")
                .replace("^", "\n")
                .replaceAll("#\\$[bBeE]#", "#")
                .replaceAll("\\$[hHsSuUaAlL0-9]+", "")
                .replaceAll("\\$[bBeE]", "#");
    }

    private void recomputeLayout() {
        float guiScale = this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
        this.mapping = new StardewRenderMapping(this.width, this.height, guiScale);
        Layout layout = measureLayout(currentMessage());
        this.boxWidth = layout.width();
        this.boxHeight = layout.height();
        this.boxX = mapping.centerX(boxWidth);
        this.boxY = this.height - boxHeight - mapping.ui(64);
    }

    private Layout measureLayout(String text) {
        int maxWidth = mapping.ui(1200);
        int minWidth = mapping.ui(256);
        int wrapWidth = Math.max(1, Math.round((maxWidth - mapping.ui(16)) / textScale()));
        List<String> lines = splitLines(Component.literal(text), wrapWidth);
        int maxLineWidth = 1;
        Font textFont = dialogueTextFont();
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, textFont.width(line));
        }
        int textWidth = Math.round(maxLineWidth * textScale());
        int textHeight = lines.size() * scaledLineHeight();
        int width = Math.min(maxWidth, Math.max(minWidth, textWidth + mapping.ui(64)));
        int height = Math.max(mapping.ui(64), textHeight + mapping.ui(16));
        return new Layout(width, height);
    }

    private void drawDialogueText(GuiGraphics graphics) {
        String all = currentMessage();
        int end = Math.max(0, Math.min(characterIndexInDialogue, all.length()));
        Component visible = Component.literal(all.substring(0, end));
        int wrapWidth = Math.max(1, Math.round((boxWidth - mapping.ui(16)) / textScale()));
        List<String> lines = splitLines(visible, wrapWidth);
        int maxLineWidth = 0;
        Font textFont = dialogueTextFont();
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, textFont.width(line));
        }

        float scale = textScale();
        int x = Math.round((boxX + boxWidth / 2.0f) / scale) - maxLineWidth / 2;
        int y = Math.round((boxY + mapping.ui(4)) / scale);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        int drawY = y;
        int unscaledLineStep = unscaledLineStep(scale);
        for (String line : lines) {
            graphics.drawString(textFont, line, x, drawY,
                    0xFF000000 | StardewFonts.spriteTextDefaultRgb(), false);
            drawY += unscaledLineStep;
        }
        graphics.pose().popPose();
    }

    private List<String> splitLines(Component text, int wrapWidth) {
        return DialogueTextWrapper.wrap(dialogueTextFont(), text.getString(), wrapWidth);
    }

    private void drawContinueOrCloseIcon(GuiGraphics graphics) {
        if (characterIndexInDialogue < currentMessage().length()) {
            return;
        }

        boolean hasNextPage = messageIndex + 1 < messages.size();
        int x = boxX + boxWidth - mapping.ui(40);
        int y = boxY + boxHeight - mapping.ui(40);
        if (hasNextPage) {
            int frame = (iconFrameTick / 90) % 6;
            int periodic = iconFrameTick % 1500;
            int triangle = periodic <= 750 ? periodic : 1500 - periodic;
            int bob = Math.round((triangle / 750.0f) * mapping.ui(8));
            CommonGuiTextures.drawDialogueNextPage(graphics, x, y + bob, frame, mapping.s4());
        } else {
            int frame = (iconFrameTick / 80) % 11;
            CommonGuiTextures.drawDialogueEnd(graphics, x, y - mapping.ui(4), frame, mapping.s4());
        }
    }

    private void drawBox(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        CommonGuiTextures.drawDialogueBoxFill(graphics, x, y, width, height);
        CommonGuiTextures.drawDialogueBoxTop(graphics, x, y - mapping.ui(20), width, mapping.ui(24));
        CommonGuiTextures.drawDialogueBoxBottom(graphics, x + mapping.ui(12), y + height, Math.max(0, width - mapping.ui(20)), mapping.ui(32));
        CommonGuiTextures.drawDialogueBoxLeft(graphics, x - mapping.ui(32), y + mapping.ui(24), mapping.ui(32), Math.max(0, height - mapping.ui(28)));
        CommonGuiTextures.drawDialogueBoxRight(graphics, x + width, y, mapping.ui(28), height);

        float s4 = mapping.s4();
        CommonGuiTextures.drawDialogueQuestionCornerTl(graphics, x - mapping.ui(44), y - mapping.ui(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerTr(graphics, x + width - mapping.ui(8), y - mapping.ui(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBr(graphics, x + width - mapping.ui(8), y + height - mapping.ui(8), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBl(graphics, x - mapping.ui(44), y + height - mapping.ui(4), s4);
    }

    private String currentMessage() {
        if (messages.isEmpty()) {
            return "...";
        }
        return messages.get(Math.max(0, Math.min(messageIndex, messages.size() - 1)));
    }

    private int safetyDelayFor(String message) {
        return message != null && message.length() <= 20 ? 550 : 750;
    }

    private float textScale() {
        return SdvFontAdapter.scale(
                dialogueTextFont(), languageCode(), guiScale(), SdvFontAdapter.Style.SPRITE_TEXT);
    }

    private int scaledLineHeight() {
        return SdvFontAdapter.lineStep(languageCode(), guiScale(), SdvFontAdapter.Style.SPRITE_TEXT);
    }

    private int unscaledLineStep(float scale) {
        return Math.max(1, Math.round(scaledLineHeight() / Math.max(0.01F, scale)));
    }

    private Font dialogueTextFont() {
        return StardewFonts.spriteText();
    }

    private String languageCode() {
        return this.minecraft == null
                ? "en_us"
                : this.minecraft.getLanguageManager().getSelected();
    }

    private float guiScale() {
        return this.minecraft == null ? 1.0F : (float) this.minecraft.getWindow().getGuiScale();
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private record Layout(int width, int height) {
    }
}
