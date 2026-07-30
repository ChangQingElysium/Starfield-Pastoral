package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.overnight.ClientOvernightHandler;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client presentation of Stardew Valley's {@code SaveGameMenu}.
 *
 * <p>The authoritative Minecraft save has already completed its overnight
 * transaction before this screen is sent. This screen intentionally preserves
 * the original visible cadence: a short "Saving" phase, the money cue and
 * "Game saved", then return control to the player.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public final class SaveGameMenuScreen extends Screen {
    private static final int SAVING_TICKS = 10;       // original post-save margin: 500 ms
    private static final int COMPLETE_PAUSE_TICKS = 30; // original completePause: 1500 ms

    private final List<Screen> siblingScreens;
    private int ticksOpen;
    private int ellipsisDelayTicks = 10;
    private int ellipsisCount;
    private int sparklingAmplitude = 32;
    private float sparklingOffsetDecay = 1.0F;
    private boolean completionSoundPlayed;
    private boolean finished;

    public SaveGameMenuScreen(List<Screen> siblingScreens) {
        super(Component.translatable("stardewcraft.overnight.saving"));
        this.siblingScreens = siblingScreens;
    }

    @Override
    public void tick() {
        ticksOpen++;
        if (ticksOpen < SAVING_TICKS && --ellipsisDelayTicks <= 0) {
            ellipsisDelayTicks = 15;
            ellipsisCount = ellipsisCount % 3 + 1;
        }
        if (!completionSoundPlayed && ticksOpen >= SAVING_TICKS) {
            completionSoundPlayed = true;
            if (minecraft != null) {
                minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.MONEY.get(), 1.0f, 1.0f));
            }
        }
        if (completionSoundPlayed) {
            // SparklingText is updated at Stardew's 60 Hz cadence. Minecraft's
            // menu tick is 20 Hz, so apply three source updates per tick.
            for (int i = 0; i < 3; i++) {
                sparklingOffsetDecay -= 0.001F;
                sparklingAmplitude = (int) (sparklingAmplitude * sparklingOffsetDecay);
            }
        }
        if (ticksOpen >= SAVING_TICKS + COMPLETE_PAUSE_TICKS) {
            finish();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ticksOpen < SAVING_TICKS) {
            Component text = Component.translatable("stardewcraft.overnight.saving")
                .copy().append(".".repeat(ellipsisCount));
            Component shown = GuiText.ellipsize(font, text, Math.max(1, width - px(128)));
            graphics.drawString(font, shown, px(64), height - px(64), 0xFFFFFFFF, false);
        } else {
            drawSavedText(graphics, partialTick);
        }
    }

    private void drawSavedText(GuiGraphics graphics, float partialTick) {
        String text = Component.translatable("stardewcraft.overnight.saved").getString();
        float elapsedMs = Math.max(0.0F, (ticksOpen - SAVING_TICKS + partialTick) * 50.0F);
        float remainingMs = Math.max(0.0F, COMPLETE_PAUSE_TICKS * 50.0F - elapsedMs);
        drawSparklingSavedText(
            graphics, font, text, px(64), height - px(64),
            sparklingAmplitude, remainingMs, px(2), Math.max(1, width - px(128))
        );
    }

    static void drawSparklingSavedText(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font font,
            String text,
            int startX,
            int baselineY,
            int amplitude,
            float remainingMs,
            int shadowOffset,
            int maxWidth
    ) {
        int alpha = remainingMs <= 500.0F
            ? Math.max(0, Math.min(255, Math.round(255.0F * remainingMs / 500.0F)))
            : 255;
        int limeGreen = alpha << 24 | 50 << 16 | 205 << 8 | 50;
        int x = startX;
        String shown = font.plainSubstrByWidth(text, maxWidth);

        for (int i = 0; i < shown.length(); i++) {
            String character = shown.substring(i, i + 1);
            int yOffset = Math.round(
                amplitude / 2.0F
                    * Mth.sin((float) (Math.PI * 2.0D / 500.0D * (remainingMs - i * 100.0F)))
            );
            graphics.drawString(font, character, x - shadowOffset, baselineY + yOffset, 0xFF000000, false);
            graphics.drawString(font, character, x + shadowOffset, baselineY + yOffset, 0xFF000000, false);
            graphics.drawString(font, character, x, baselineY + yOffset - shadowOffset, 0xFF000000, false);
            graphics.drawString(font, character, x, baselineY + yOffset + shadowOffset, 0xFF000000, false);
            graphics.drawString(font, character, x, baselineY + yOffset, limeGreen, false);
            x += font.width(character);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public void onClose() {
        // SaveGameMenu.readyToClose() is false in the original.
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (siblingScreens != null) {
            ClientOvernightHandler.openNextScreen("save");
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int px(int stardewPixels) {
        float guiScale = minecraft == null
            ? 1.0f
            : (float) minecraft.getWindow().getGuiScale();
        return Math.round(stardewPixels / guiScale);
    }
}
