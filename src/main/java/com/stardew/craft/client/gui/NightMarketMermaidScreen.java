package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.payload.NightMarketMermaidActionPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("null")
public final class NightMarketMermaidScreen extends Screen implements StardewRealtimeScreen {
    private static final ResourceLocation MAP_TILES = texture("mermaid_house_tiles.png");
    private static final ResourceLocation MERMAID_SPRITES = texture("temporary_sprites_1.png");
    private static final ResourceLocation ANIMATIONS = texture("animations.png");
    private static final int MAP_TEXTURE_W = 144;
    private static final int MAP_TEXTURE_H = 208;
    private static final int SPRITE_TEXTURE_W = 512;
    private static final int SPRITE_TEXTURE_H = 640;
    private static final int ANIMATION_TEXTURE_W = 640;
    private static final int ANIMATION_TEXTURE_H = 3328;
    private static final int MAP_W = 144;
    private static final int MAP_H = 192;
    private static final int TILE = 16;
    private static final double FIXED_STEP_MS = 1000.0 / 60.0;

    private static final int[] BACK = {
        1,2,3,4,5,6,7,8,9,
        10,11,12,13,14,15,16,17,18,
        19,20,21,22,23,24,25,26,27,
        28,29,30,31,32,33,34,35,36,
        37,38,39,40,41,42,43,44,45,
        46,47,48,49,50,51,52,53,54,
        55,56,57,58,59,60,61,62,63,
        64,65,66,67,68,69,70,71,72,
        73,74,75,76,77,78,79,80,81,
        82,83,84,85,86,87,88,89,90,
        91,92,93,94,95,96,97,98,99,
        1,1,1,1,1,1,1,1,1
    };
    private static final int[] BUILDINGS = {
        0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,0,
        55,56,57,58,59,60,61,62,63,
        64,0,0,0,0,0,0,0,72,
        73,0,0,0,0,0,0,0,81,
        82,0,0,0,0,0,0,0,90,
        91,1,1,1,0,1,1,1,1,
        1,1,1,1,0,1,1,1,1
    };
    private static final int[] ALWAYS_FRONT = {
        100,101,102,103,104,105,106,107,108,
        109,110,0,0,0,0,0,116,117,
        19,0,0,0,0,0,0,0,27,
        28,0,0,0,0,0,0,0,36,
        37,0,0,0,0,0,0,0,45,
        46,0,0,0,0,0,0,0,54,
        55,0,0,0,0,0,0,0,63,
        64,0,0,0,0,0,0,0,72,
        73,0,0,0,0,0,0,0,81,
        82,111,111,112,0,115,111,111,90,
        91,1,1,113,111,114,1,1,99,
        0,1,1,1,1,1,1,1,0
    };
    private static final int[] MERMAID_FRAMES = {
        1,0,2,0,1,0,2,0,3,3,
        3,4,3,3,3,4,3,3,3,4,
        3,3,3,4,3,3,3,4,3,3,
        4,4,3,3,3,3,0,0,0,0,
        3,3,3,4,3,3,3,4,3,3,
        3,4,3,3,3,4,3,3,3,4,
        3,3,4,4,3,3,3,3,0,0,
        0,0,3,3,3,3,4,4,4,4,
        3,3,3,3,0,0,5,6,5,6,
        7,8,8
    };
    private static final int[] CORRECT_SEQUENCE = {0, 4, 3, 1, 2};
    private static final int[] REPLAY_DELAYS = {885, 1270, 1655, 2040, 2425};
    private static final int[] REPLAY_CLAMS = {0, 4, 3, 1, 2};
    private static final int[] FLYER_X = {28, 108, 88, 48, 68};
    private static final int[] FLYER_Y = {49, 49, 39, 39, 29};
    private static final double[] CLAM_PITCH = {
        0.5946035575, 0.7071067812, 0.7937005260, 0.8908987181, 1.0
    };
    private static final Tint[] CLAM_COLORS = {
        new Tint(1.0f, 105f / 255f, 180f / 255f),
        new Tint(1.0f, 165f / 255f, 0.0f),
        new Tint(1.0f, 1.0f, 0.0f),
        new Tint(0.0f, 1.0f, 1.0f),
        new Tint(0.0f, 1.0f, 0.0f)
    };

    private final Random random = new Random();
    private final ArrayDeque<Integer> lastFiveClams = new ArrayDeque<>(5);
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<ScreenSparkle> sparkles = new ArrayList<>();
    private final List<Sprinkle> sprinkles = new ArrayList<>();
    private final List<ClamFlash> clamFlashes = new ArrayList<>();
    private final boolean[] replayed = new boolean[5];

    private boolean gotPearl;
    private boolean initialized;
    private boolean musicStarted;
    private boolean finaleSpawned;
    private boolean rewardSequence;
    private boolean closeSent;
    private long lastFrameNanos;
    private double entryElapsedMs;
    private double performanceElapsedMs;
    private double simulatedElapsedMs;
    private double simulationAccumulatorMs;
    private double rewardStartedAtMs;
    private float curtainMovement;
    private float curtainOpenPercent;
    private float blackBgAlpha;
    private float bigMermaidAlpha;
    private float finalLeftAlpha;
    private float finalRightAlpha;
    private float finalBigAlpha;
    private float roomScale;
    private float logicalScreenW;
    private float logicalScreenH;
    private float roomLogicalX;
    private float roomLogicalY;

    public NightMarketMermaidScreen(boolean gotPearl) {
        super(Component.translatable("stardewcraft.location.night_market_mermaid_show"));
        this.gotPearl = gotPearl;
    }

    @Override
    protected void init() {
        updateLayout();
        if (!initialized) {
            initialized = true;
            lastFrameNanos = System.nanoTime();
            StardewMusicManager.stopForCutsceneSilence();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        advanceShow();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderRoom(graphics);
        renderAboveAlwaysFront(graphics);
    }

    private void updateLayout() {
        double guiScale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        roomScale = (float) (4.0 / guiScale);
        logicalScreenW = width / roomScale;
        logicalScreenH = height / roomScale;
        roomLogicalX = (logicalScreenW - MAP_W) / 2.0f;
        roomLogicalY = logicalScreenH >= MAP_H
            ? (logicalScreenH - MAP_H) / 2.0f
            : logicalScreenH - MAP_H;
    }

    private void advanceShow() {
        long now = System.nanoTime();
        double deltaMs = Math.max(0.0, (now - lastFrameNanos) / 1_000_000.0);
        lastFrameNanos = now;
        entryElapsedMs += deltaMs;

        if (!musicStarted) {
            if (entryElapsedMs >= 3000.0) {
                musicStarted = true;
                StardewMusicManager.playForCutscene(ModSounds.MUSIC_MERMAID_SONG.get());
            }
            return;
        }

        performanceElapsedMs += deltaMs;
        simulationAccumulatorMs += deltaMs;
        while (simulationAccumulatorMs >= FIXED_STEP_MS) {
            fixedUpdate();
            simulationAccumulatorMs -= FIXED_STEP_MS;
        }
        updateRewardReplay();
    }

    private void fixedUpdate() {
        double oldTime = simulatedElapsedMs;
        simulatedElapsedMs += FIXED_STEP_MS;

        if (curtainMovement != 0.0f) {
            curtainOpenPercent = clamp(curtainOpenPercent + curtainMovement * (float) FIXED_STEP_MS);
        }
        if (simulatedElapsedMs > 0.0 && simulatedElapsedMs < 1000.0) {
            curtainMovement = 0.0004f;
        }
        if (simulatedElapsedMs >= 30_000.0 && simulatedElapsedMs < 50_000.0
                && (blackBgAlpha < 1.0f || bigMermaidAlpha < 1.0f)) {
            blackBgAlpha += 0.01f;
            bigMermaidAlpha += 0.01f;
        }
        if (simulatedElapsedMs > 27_692.0 && simulatedElapsedMs < 55_385.0
                && oldTime % 769.0 > simulatedElapsedMs % 769.0) {
            bubbles.add(new Bubble(random.nextFloat() * Math.max(1.0f, logicalScreenW - 16.0f), simulatedElapsedMs));
        }
        if (simulatedElapsedMs >= 52_308.0 && (blackBgAlpha > 0.0f || bigMermaidAlpha > 0.0f)) {
            blackBgAlpha -= 0.01f;
            bigMermaidAlpha -= 0.01f;
        }
        if (simulatedElapsedMs >= 58_462.0 && simulatedElapsedMs < 60_000.0 && finalLeftAlpha < 1.0f) {
            finalLeftAlpha += 0.01f;
        }
        if (simulatedElapsedMs >= 60_000.0 && simulatedElapsedMs < 62_000.0 && finalRightAlpha < 1.0f) {
            finalRightAlpha += 0.01f;
        }
        if (simulatedElapsedMs >= 61_538.0 && simulatedElapsedMs < 63_538.0 && finalBigAlpha < 1.0f) {
            finalBigAlpha += 0.01f;
        }
        if (simulatedElapsedMs >= 64_615.0) {
            finalBigAlpha = Math.max(0.0f, finalBigAlpha - 0.01f);
            finalRightAlpha = Math.max(0.0f, finalRightAlpha - 0.01f);
            finalLeftAlpha = Math.max(0.0f, finalLeftAlpha - 0.01f);
        }
        if (!finaleSpawned && crossed(oldTime, simulatedElapsedMs, 64_808.0)) {
            finaleSpawned = true;
            spawnFinaleSparkles();
        }
        if (crossed(oldTime, simulatedElapsedMs, 67_500.0)) {
            curtainMovement = -0.0003f;
        }
    }

    private void spawnFinaleSparkles() {
        for (int i = 0; i < 200; i++) {
            float x = -1000.0f;
            float y = -1000.0f;
            for (int attempt = 0; attempt < 30; attempt++) {
                float candidateX = random.nextFloat() * logicalScreenW;
                float candidateY = random.nextFloat() * logicalScreenH;
                if (candidateX < roomLogicalX || candidateX >= roomLogicalX + MAP_W
                        || candidateY < roomLogicalY || candidateY >= roomLogicalY + MAP_H) {
                    x = candidateX;
                    y = candidateY;
                    break;
                }
            }
            sparkles.add(new ScreenSparkle(x, y, simulatedElapsedMs + i * 10.0));
        }
        for (int i = 0; i < 20; i++) {
            int tileX = 1 + random.nextInt(9);
            int tileY = 3 + random.nextInt(5);
            int row = 10 + random.nextInt(2);
            sprinkles.add(new Sprinkle(tileX * 16, tileY * 16, row, simulatedElapsedMs + i * 100.0));
        }
    }

    private void updateRewardReplay() {
        if (!rewardSequence) {
            return;
        }
        double age = performanceElapsedMs - rewardStartedAtMs;
        for (int i = 0; i < replayed.length; i++) {
            if (!replayed[i] && age >= REPLAY_DELAYS[i]) {
                replayed[i] = true;
                playClamTone(REPLAY_CLAMS[i], false);
            }
        }
    }

    private void renderRoom(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(roomScale, roomScale, 1.0f);
        graphics.pose().translate(roomLogicalX, roomLogicalY, 0.0f);

        drawMapLayer(graphics, BACK);
        drawMapLayer(graphics, BUILDINGS);
        drawSprinkles(graphics);
        drawClamFlashes(graphics);
        drawRewardFlyers(graphics);
        drawCentralShow(graphics);
        drawMapLayer(graphics, ALWAYS_FRONT);

        graphics.pose().popPose();
    }

    private void drawMapLayer(GuiGraphics graphics, int[] layer) {
        for (int index = 0; index < layer.length; index++) {
            int gid = layer[index];
            if (gid <= 0) {
                continue;
            }
            int tileIndex = gid - 1;
            int sourceX = tileIndex % 9 * TILE;
            int sourceY = tileIndex / 9 * TILE;
            int x = index % 9 * TILE;
            int y = index / 9 * TILE;
            graphics.blit(MAP_TILES, x, y, TILE, TILE, (float) sourceX, (float) sourceY,
                TILE, TILE, MAP_TEXTURE_W, MAP_TEXTURE_H);
        }
    }

    private void drawCentralShow(GuiGraphics graphics) {
        double elapsed = performanceElapsedMs;
        int frameIndex = Math.min((int) (elapsed / 769.2308), MERMAID_FRAMES.length - 1);
        int mermaidFrame = MERMAID_FRAMES[Math.max(0, frameIndex)];
        blit(graphics, MERMAID_SPRITES, 58, 54, 28, 36, mermaidFrame * 28, 80, 28, 36);

        int companionFrame = (int) (entryElapsedMs % 400.0 / 100.0);
        int companionSourceX = 2 + companionFrame * 19;
        int leftX = Math.round(27.0f + (float) Math.sin(elapsed / 1000.0) * 4.0f);
        int leftY = Math.round(29.0f + (float) Math.cos(elapsed / 1000.0) * 4.0f);
        int rightX = Math.round(97.0f + (float) Math.cos(elapsed / 1000.0 + 0.1) * 4.0f);
        int rightY = Math.round(29.0f + (float) Math.sin(elapsed / 1000.0 + 0.1) * 4.0f);
        blit(graphics, MERMAID_SPRITES, leftX, leftY, 19, 18, companionSourceX, 127, 19, 18);
        blit(graphics, MERMAID_SPRITES, rightX, rightY, 19, 18, companionSourceX, 127, 19, 18);

        int curtainWidth = (int) (57.0f * (1.0f - curtainOpenPercent));
        if (curtainWidth > 0) {
            int leftSourceX = (int) (144.0f + 57.0f * curtainOpenPercent);
            blit(graphics, MERMAID_SPRITES, 16, 16, curtainWidth, 81,
                leftSourceX, 119, curtainWidth, 81);
            int rightXPos = (int) (73.0f + 57.0f * curtainOpenPercent);
            blit(graphics, MERMAID_SPRITES, rightXPos, 16, curtainWidth, 81,
                200, 119, curtainWidth, 81);
        }
    }

    private void drawClamFlashes(GuiGraphics graphics) {
        clamFlashes.removeIf(flash -> flash.alpha(performanceElapsedMs) <= 0.0f);
        for (ClamFlash flash : clamFlashes) {
            float alpha = flash.alpha(performanceElapsedMs);
            Tint tint = CLAM_COLORS[flash.clamIndex];
            drawTinted(graphics, MERMAID_SPRITES, 35 + flash.clamIndex * 16, 98, 11, 12,
                125, 126, 11, 12, tint.r, tint.g, tint.b, alpha);
        }
    }

    private void drawRewardFlyers(GuiGraphics graphics) {
        if (!rewardSequence) {
            return;
        }
        double rewardAge = performanceElapsedMs - rewardStartedAtMs;
        for (int i = 0; i < REPLAY_DELAYS.length; i++) {
            double age = rewardAge - REPLAY_DELAYS[i];
            if (age < 0.0) {
                continue;
            }
            float alpha = age <= 3500.0 - REPLAY_DELAYS[i]
                ? 1.0f
                : Math.max(0.0f, 1.0f - (float) ((rewardAge - 3500.0) / FIXED_STEP_MS) * 0.01f);
            if (alpha <= 0.0f) {
                continue;
            }
            int frame = (int) (age / 96.0) % 4;
            drawTinted(graphics, MERMAID_SPRITES, FLYER_X[i], FLYER_Y[i], 19, 18,
                2 + frame * 19, 127, 19, 18, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private void drawSprinkles(GuiGraphics graphics) {
        for (Sprinkle sprinkle : sprinkles) {
            double age = performanceElapsedMs - sprinkle.startMs;
            if (age < 0.0 || age >= 800.0) {
                continue;
            }
            int frame = (int) (age / 100.0);
            graphics.blit(ANIMATIONS, sprinkle.x, sprinkle.y, 16, 16,
                (float) (frame * 64), (float) (sprinkle.row * 64), 64, 64,
                ANIMATION_TEXTURE_W, ANIMATION_TEXTURE_H);
        }
    }

    private void renderAboveAlwaysFront(GuiGraphics graphics) {
        renderScreenSparkles(graphics);
        float blackAlpha = clamp(blackBgAlpha);
        if (blackAlpha > 0.0f) {
            graphics.fill(0, 0, width, height, alphaColor(blackAlpha));
        }

        graphics.pose().pushPose();
        graphics.pose().scale(roomScale, roomScale, 1.0f);
        drawScrollingOcean(graphics, blackAlpha);
        drawBigMermaid(graphics, blackAlpha);
        drawRisingMermaids(graphics);
        drawBubbles(graphics, blackAlpha);
        drawFinalMermaids(graphics);
        graphics.pose().popPose();
    }

    private void renderScreenSparkles(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(roomScale, roomScale, 1.0f);
        for (ScreenSparkle sparkle : sparkles) {
            double age = performanceElapsedMs - sparkle.startMs;
            if (age < 0.0 || age >= 900.0) {
                continue;
            }
            int frame = (int) (age / 100.0);
            blit(graphics, MERMAID_SPRITES, Math.round(sparkle.x), Math.round(sparkle.y), 16, 13,
                frame * 16, 146, 16, 13);
        }
        graphics.pose().popPose();
    }

    private void drawScrollingOcean(GuiGraphics graphics, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        float spacing = logicalScreenH / 4.0f;
        float scroll = (float) (performanceElapsedMs / 24.0 % 112.0);
        for (float i = -112.0f; i < logicalScreenW + 112.0f; i += 112.0f) {
            drawTinted(graphics, MERMAID_SPRITES, Math.round(i - scroll), Math.round(spacing / 4.0f),
                112, 48, 144, 32, 112, 48, 0.0f, 1.0f, 0.0f, alpha);
            drawTinted(graphics, MERMAID_SPRITES, Math.round(i + 28.0f - scroll),
                (int) Math.round(spacing * 3.0f / 4.0f + Math.sin(performanceElapsedMs / 1000.0) * 16.0),
                16, 16, 177, 0, 16, 16, 1.0f, 1.0f, 1.0f, alpha);

            drawTinted(graphics, MERMAID_SPRITES, Math.round(i + scroll), Math.round(spacing * 5.0f / 4.0f),
                112, 48, 144, 32, 112, 48, 0.0f, 1.0f, 1.0f, alpha);
            drawTintedFlipped(graphics, Math.round(i + 28.0f + scroll),
                (int) Math.round(spacing * 7.0f / 4.0f + Math.sin(performanceElapsedMs / 1000.0 + 4.0) * 16.0),
                161, 0, 16, 16, 1.0f, 1.0f, 1.0f, alpha);

            drawTinted(graphics, MERMAID_SPRITES, Math.round(i - scroll), Math.round(spacing * 9.0f / 4.0f),
                112, 48, 144, 32, 112, 48, 1.0f, 165f / 255f, 0.0f, alpha);
            drawTinted(graphics, MERMAID_SPRITES, Math.round(i + 28.0f - scroll),
                (int) Math.round(spacing * 11.0f / 4.0f + Math.sin(performanceElapsedMs / 1000.0 + 3.0) * 16.0),
                16, 16, 129, 0, 16, 16, 1.0f, 1.0f, 1.0f, alpha);

            drawTinted(graphics, MERMAID_SPRITES, Math.round(i + scroll), Math.round(spacing * 13.0f / 4.0f),
                112, 48, 144, 32, 112, 48, 1.0f, 105f / 255f, 180f / 255f, alpha);
            drawTintedFlipped(graphics, Math.round(i + 28.0f + scroll),
                (int) Math.round(spacing * 15.0f / 4.0f + Math.sin(performanceElapsedMs / 1000.0 + 2.0) * 16.0),
                145, 0, 16, 16, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private void drawBigMermaid(GuiGraphics graphics, float blackAlpha) {
        float alpha = clamp(bigMermaidAlpha);
        if (blackAlpha <= 0.0f || alpha <= 0.0f) {
            return;
        }
        int frame = (int) (performanceElapsedMs % 1538.0 / 769.0);
        int x = Math.round(logicalScreenW / 2.0f - 28.0f
            + (float) Math.sin(performanceElapsedMs / 1000.0) * 32.0f);
        int y = Math.round(logicalScreenH / 2.0f - 35.0f
            + (float) Math.cos(performanceElapsedMs / 500.0 + Math.PI / 2.0) * 16.0f);
        drawTinted(graphics, MERMAID_SPRITES, x, y, 57, 70,
            frame * 57, 0, 57, 70, 1.0f, 1.0f, 1.0f, alpha);
    }

    private void drawRisingMermaids(GuiGraphics graphics) {
        drawRisingMermaid(graphics, 36_923.0, false);
        drawRisingMermaid(graphics, 40_000.0, true);
        drawRisingMermaid(graphics, 43_077.0, false);
        drawRisingMermaid(graphics, 46_154.0, true);
    }

    private void drawRisingMermaid(GuiGraphics graphics, double startMs, boolean right) {
        double age = performanceElapsedMs - startMs;
        if (age < 0.0) {
            return;
        }
        int step = (int) (age / 192.0) % 4;
        int frame = step == 3 ? 1 : step;
        float baseX = right ? logicalScreenW * 3.0f / 4.0f : logicalScreenW / 4.0f;
        int x = Math.round(baseX + (float) Math.sin(age / 2000.0 * Math.PI * 2.0) * 8.0f);
        int y = Math.round(logicalScreenH - 0.25f - (float) (age / FIXED_STEP_MS));
        blit(graphics, MERMAID_SPRITES, x, y, 24, 53, 67 + frame * 24, 189, 24, 53);
    }

    private void drawBubbles(GuiGraphics graphics, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        for (Bubble bubble : bubbles) {
            double age = performanceElapsedMs - bubble.spawnMs;
            double movingAge = Math.max(0.0, Math.min(age, 55_385.0 - bubble.spawnMs));
            float x = bubble.x + (float) Math.sin(performanceElapsedMs / 250.0 + bubble.x * 4.0f) * 6.0f;
            float y = logicalScreenH - (float) movingAge * 0.025f;
            drawTinted(graphics, MERMAID_SPRITES, Math.round(x), Math.round(y), 8, 8,
                132, 20, 8, 8, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private void drawFinalMermaids(GuiGraphics graphics) {
        drawFinalSwimmer(graphics, -20, 50, finalLeftAlpha, false, CLAM_COLORS[1]);
        drawFinalSwimmer(graphics, -30, 90, finalLeftAlpha, false, CLAM_COLORS[3]);
        drawFinalSwimmer(graphics, -40, 130, finalLeftAlpha, false, CLAM_COLORS[4]);
        drawFinalSwimmer(graphics, 150, 50, finalRightAlpha, true, CLAM_COLORS[1]);
        drawFinalSwimmer(graphics, 160, 90, finalRightAlpha, true, CLAM_COLORS[3]);
        drawFinalSwimmer(graphics, 170, 130, finalRightAlpha, true, CLAM_COLORS[4]);

        float alpha = clamp(finalBigAlpha);
        if (alpha > 0.0f) {
            int frame = (int) (performanceElapsedMs % 1538.0 / 769.0);
            drawTinted(graphics, MERMAID_SPRITES,
                Math.round(roomLogicalX + 43.0f), Math.round(roomLogicalY + 180.0f), 57, 70,
                frame * 57, 0, 57, 70, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private void drawFinalSwimmer(GuiGraphics graphics, int mapX, int mapY, float rawAlpha,
                                  boolean flipped, Tint overlay) {
        float alpha = clamp(rawAlpha);
        if (alpha <= 0.0f) {
            return;
        }
        int x = Math.round(roomLogicalX + mapX);
        int y = Math.round(roomLogicalY + mapY);
        if (flipped) {
            drawTintedFlipped(graphics, x, y, 192, 0, 16, 32, 1.0f, 1.0f, 1.0f, alpha);
            drawTintedFlipped(graphics, x, y, 208, 0, 16, 32, overlay.r, overlay.g, overlay.b, alpha);
        } else {
            drawTinted(graphics, MERMAID_SPRITES, x, y, 16, 32,
                192, 0, 16, 32, 1.0f, 1.0f, 1.0f, alpha);
            drawTinted(graphics, MERMAID_SPRITES, x, y, 16, 32,
                208, 0, 16, 32, overlay.r, overlay.g, overlay.b, alpha);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || performanceElapsedMs < 68_000.0 || isRewardLocked()) {
            return true;
        }
        float localX = (float) mouseX / roomScale - roomLogicalX;
        float localY = (float) mouseY / roomScale - roomLogicalY;
        if (localY < 96.0f || localY >= 112.0f || localX < 32.0f || localX >= 112.0f) {
            return true;
        }
        int clamIndex = (int) ((localX - 32.0f) / 16.0f);
        playClamTone(clamIndex, true);
        return true;
    }

    private void playClamTone(int clamIndex, boolean sendToServer) {
        if (clamIndex < 0 || clamIndex > 4) {
            return;
        }
        clamFlashes.add(new ClamFlash(clamIndex, performanceElapsedMs));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
            ModSounds.CLAM_TONE.get(), (float) CLAM_PITCH[clamIndex], 1.0f
        ));
        lastFiveClams.addLast(clamIndex);
        while (lastFiveClams.size() > CORRECT_SEQUENCE.length) {
            lastFiveClams.removeFirst();
        }
        if (sendToServer) {
            PacketDistributor.sendToServer(new NightMarketMermaidActionPayload(clamIndex));
        }
        if (!gotPearl && !rewardSequence && matchesCorrectSequence()) {
            gotPearl = true;
            rewardSequence = true;
            rewardStartedAtMs = performanceElapsedMs;
        }
    }

    private boolean matchesCorrectSequence() {
        if (lastFiveClams.size() != CORRECT_SEQUENCE.length) {
            return false;
        }
        int index = 0;
        for (int value : lastFiveClams) {
            if (value != CORRECT_SEQUENCE[index++]) {
                return false;
            }
        }
        return true;
    }

    private boolean isRewardLocked() {
        return rewardSequence && performanceElapsedMs - rewardStartedAtMs < 4500.0;
    }

    @Override
    public void onClose() {
        if (!isRewardLocked()) {
            super.onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public void removed() {
        if (!closeSent) {
            closeSent = true;
            PacketDistributor.sendToServer(new NightMarketMermaidActionPayload(NightMarketMermaidActionPayload.CLOSE));
        }
        StardewMusicManager.stopForCutsceneSilence();
        StardewMusicManager.releaseCutsceneOverride();
        super.removed();
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                             int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        graphics.blit(texture, x, y, width, height, (float) sourceX, (float) sourceY,
            sourceWidth, sourceHeight, SPRITE_TEXTURE_W, SPRITE_TEXTURE_H);
    }

    private static void drawTinted(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height,
                                   int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                   float red, float green, float blue, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, clamp(alpha));
        graphics.blit(texture, x, y, width, height, (float) sourceX, (float) sourceY,
            sourceWidth, sourceHeight, SPRITE_TEXTURE_W, SPRITE_TEXTURE_H);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawTintedFlipped(GuiGraphics graphics, int x, int y,
                                          int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                          float red, float green, float blue, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, clamp(alpha));
        graphics.pose().pushPose();
        graphics.pose().translate(x + sourceWidth, y, 0.0f);
        graphics.pose().scale(-1.0f, 1.0f, 1.0f);
        graphics.blit(MERMAID_SPRITES, 0, 0, sourceWidth, sourceHeight,
            (float) sourceX, (float) sourceY, sourceWidth, sourceHeight,
            SPRITE_TEXTURE_W, SPRITE_TEXTURE_H);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/night_market/mermaid/" + name
        );
    }

    private static boolean crossed(double oldTime, double newTime, double target) {
        return oldTime < target && newTime >= target;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int alphaColor(float alpha) {
        return Math.round(clamp(alpha) * 255.0f) << 24;
    }

    private record Tint(float r, float g, float b) {
    }

    private record Bubble(float x, double spawnMs) {
    }

    private record ScreenSparkle(float x, float y, double startMs) {
    }

    private record Sprinkle(int x, int y, int row, double startMs) {
    }

    private record ClamFlash(int clamIndex, double startMs) {
        private float alpha(double nowMs) {
            double age = nowMs - startMs;
            return Math.max(0.0f, 1.0f - (float) Math.floor(age / FIXED_STEP_MS) * 0.03f);
        }
    }
}
