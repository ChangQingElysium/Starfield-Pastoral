package com.stardew.craft.client.gui.festival;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.network.payload.FairFishingResultAdvancePayload;
import com.stardew.craft.network.payload.OpenFairFishingResultPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class FairFishingResultScreen extends Screen implements com.stardew.craft.port.PortScreen {
    private static final int RESULTS_MS = 11100;

    private final OpenFairFishingResultPayload payload;
    private int resultTimerMs = RESULTS_MS;
    private int lastTimerMs = RESULTS_MS;
    private long lastUpdateMs;
    private float guiScale = 1.0F;
    private int score;
    private int perfectionBonus;
    private int starTokensWon;
    private int totalStarTokens;
    private boolean rewardClaimed;

    public FairFishingResultScreen(OpenFairFishingResultPayload payload) {
        super(Component.translatable("stardewcraft.fair.fishing.title"));
        this.payload = payload;
        this.score = payload.baseScore();
        this.totalStarTokens = payload.totalStarTokens();
    }

    @Override
    protected void init() {
        lastUpdateMs = System.currentTimeMillis();
        guiScale = (float) StardewGuiViewport.REFERENCE_SCALE;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateState();
        drawResults(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateState() {
        long now = System.currentTimeMillis();
        int elapsedMs = (int) Math.max(0L, Math.min(250L, now - lastUpdateMs));
        lastUpdateMs = now;
        lastTimerMs = resultTimerMs;
        resultTimerMs = Math.max(0, resultTimerMs - elapsedMs);
        playThresholdSounds();
        if (resultTimerMs <= 0) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    private void playThresholdSounds() {
        if (crossed(11000)) {
            play(ModSounds.SMALL_SELECT.get());
        }
        if (crossed(9000)) {
            play(ModSounds.SMALL_SELECT.get());
        }
        if (crossed(7000)) {
            applyPerfectionBonus();
            play(perfectionBonus > 0 ? ModSounds.NEW_ARTIFACT.get() : ModSounds.SMALL_SELECT.get());
        }
        if (crossed(5000)) {
            applyReward();
            play(starTokensWon > 0 ? ModSounds.REWARD.get() : ModSounds.FISH_ESCAPE.get());
        }
    }

    private boolean crossed(int thresholdMs) {
        return lastTimerMs > thresholdMs && resultTimerMs <= thresholdMs;
    }

    private void drawResults(GuiGraphics graphics) {
        int x = width / 2 - ui(128);
        int y = height / 2 - ui(64);
        if (resultTimerMs <= 11000) {
            drawBordered(graphics, I18n.get("stardewcraft.fair.fishing.score", score), x, y,
                resultTimerMs <= 7000 && perfectionBonus > 0 ? 0xFF00FF00 : 0xFFFFFFFF);
        }
        if (resultTimerMs <= 9000) {
            y += ui(48);
            drawBordered(graphics, I18n.get("stardewcraft.fair.fishing.fish_caught", payload.fishCaught()), x, y, 0xFFFFFFFF);
        }
        if (resultTimerMs <= 7000) {
            y += ui(48);
            if (perfectionBonus > 1) {
                drawBordered(graphics, I18n.get("stardewcraft.fair.fishing.perfection_bonus", perfectionBonus), x, y, 0xFFFFFF00);
            } else {
                drawBordered(graphics, I18n.get("stardewcraft.fair.fishing.no_perfection_bonus"), x, y, 0xFFFF0000);
            }
        }
        if (resultTimerMs <= 5000) {
            y += ui(64);
            if (starTokensWon > 0) {
                String reward = I18n.get("stardewcraft.fair.fishing.reward", starTokensWon);
                float fade = Math.max(0.0F, Math.min(1.0F, (resultTimerMs - 2000) / 4000.0F));
                for (int i = 0; i < 3; i++) {
                    int jitterX = ThreadLocalRandom.current().nextInt(-1, 2) * ui(8);
                    int jitterY = ThreadLocalRandom.current().nextInt(-1, 2) * ui(8);
                    drawBordered(graphics, reward, x + jitterX, y + jitterY,
                        argb(Math.round(255.0F * 0.2F * fade), 75, 65, 55),
                        argb(Math.round(255.0F * 0.3F * fade), 135, 206, 235));
                }
                drawBordered(graphics, reward, x, y, 0xFF4B4137, 0xFF87CEEB);
            } else {
                drawBordered(graphics, I18n.get("stardewcraft.fair.fishing.no_reward"), x, y, 0xFFFF0000);
            }
        }
        if (resultTimerMs <= 1000) {
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * (1.0F - resultTimerMs / 1000.0F))));
            graphics.fill(0, 0, width, height, alpha << 24);
        }
        drawTokenBox(graphics, ui(16), ui(16));
    }

    private void drawTokenBox(GuiGraphics graphics, int x, int y) {
        int w = ui(128 + (totalStarTokens > 999 ? 16 : 0));
        int h = ui(64);
        graphics.fill(x, y, x + w, y + h, 0xBF000000);
        CommonGuiTextures.drawFairStarToken(graphics, x + ui(16), y + ui(16), 4.0F / guiScale);
        drawBordered(graphics, String.valueOf(totalStarTokens), x + ui(56), y + ui(13), 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            advanceResults();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // SDV FishingGame results advance from receiveLeftClick; result-stage key presses are consumed.
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void advanceResults() {
        if (resultTimerMs > 11000) {
            resultTimerMs = 11001;
        } else if (resultTimerMs > 9000) {
            resultTimerMs = 9001;
        } else if (resultTimerMs > 7000) {
            resultTimerMs = 7001;
        } else if (resultTimerMs > 5000) {
            resultTimerMs = 5001;
        } else if (resultTimerMs < 5000 && resultTimerMs > 1000) {
            resultTimerMs = 1500;
            play(ModSounds.SMALL_SELECT.get());
        }
    }

    private void applyPerfectionBonus() {
        if (perfectionBonus > 0 || payload.perfections() <= 0) {
            return;
        }
        score += payload.perfections() * 10;
        perfectionBonus = payload.perfections() * 10;
        if (payload.fishCaught() >= 3 && payload.perfections() >= 3) {
            perfectionBonus += score;
            score *= 2;
        }
    }

    private void applyReward() {
        if (rewardClaimed) {
            return;
        }
        rewardClaimed = true;
        if (score >= 10) {
            starTokensWon = ((score + 5) / 10) * 6;
            starTokensWon *= 2;
            totalStarTokens += starTokensWon;
        }
        PacketDistributor.sendToServer(new FairFishingResultAdvancePayload());
    }

    private void drawBordered(GuiGraphics graphics, String text, int x, int y, int color) {
        drawBordered(graphics, text, x, y, 0xFF000000, color);
    }

    private void drawBordered(GuiGraphics graphics, String text, int x, int y, int borderColor, int color) {
        graphics.drawString(font, text, x + 1, y, borderColor, false);
        graphics.drawString(font, text, x - 1, y, borderColor, false);
        graphics.drawString(font, text, x, y + 1, borderColor, false);
        graphics.drawString(font, text, x, y - 1, borderColor, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private void play(SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(sound, 1.0F, 1.0F);
        }
    }

    private int ui(int sdvPixels) {
        return Math.round(sdvPixels / guiScale);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24)
            | ((red & 0xFF) << 16)
            | ((green & 0xFF) << 8)
            | (blue & 0xFF);
    }
}
