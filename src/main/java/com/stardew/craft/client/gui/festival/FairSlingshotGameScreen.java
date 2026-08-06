package com.stardew.craft.client.gui.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.payload.FairSlingshotGameActionPayload;
import com.stardew.craft.sound.ModSounds;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FairSlingshotGameScreen extends Screen implements com.stardew.craft.client.gui.StardewRealtimeScreen {
    private static final ResourceLocation BACK = tex("back");
    private static final ResourceLocation BUILDINGS = tex("buildings");
    private static final ResourceLocation FRONT = tex("front");
    private static final ResourceLocation FARMER = tex("farmer_default");
    private static final ResourceLocation FARMER_SLINGSHOT_DOWN = tex("farmer_slingshot_down");
    private static final ResourceLocation FARMER_SLINGSHOT_RIGHT = tex("farmer_slingshot_right");
    private static final ResourceLocation FARMER_SLINGSHOT_LEFT = tex("farmer_slingshot_left");
    private static final ResourceLocation FARMER_SLINGSHOT_UP = tex("farmer_slingshot_up");
    private static final ResourceLocation SHADOW = tex("shadow");
    private static final ResourceLocation STAR_TOKEN = tex("star_token");
    private static final ResourceLocation STONE_PROJECTILE = tex("stone_projectile");
    private static final ResourceLocation FARMER_SLINGSHOT_HAND = tex("farmer_slingshot_down_hand");
    private static final ResourceLocation FARMER_SLINGSHOT_FRAME = tex("farmer_slingshot_down_frame");
    private static final ResourceLocation FARMER_SLINGSHOT_SIDE_ARM = tex("farmer_slingshot_side_arm");
    private static final ResourceLocation FARMER_SLINGSHOT_SIDE_FRAME = tex("farmer_slingshot_side_frame");
    private static final ResourceLocation FARMER_SLINGSHOT_UP_FRAME = tex("farmer_slingshot_up_frame");
    private static final ResourceLocation[] TARGETS = { tex("target_basic"), tex("target_bonus"), tex("target_deluxe") };
    private static final ResourceLocation[] SHATTERS = { tex("target_basic_shatter"), tex("target_bonus_shatter"), tex("target_deluxe_shatter") };

    private static final int WORLD = 1024;
    private static final int TARGET_SIZE = 56;
    private static final int PROJECTILE_BOUNDS = 29;
    private static final int PLAYER_W = 64;
    private static final int PLAYER_H = 128;
    private static final int FACING_UP = 0;
    private static final int FACING_RIGHT = 1;
    private static final int FACING_DOWN = 2;
    private static final int FACING_LEFT = 3;
    private static final int GAME_MS = 50000;
    private static final int START_MS = 1000;
    private static final int END_DELAY_MS = 1000;
    private static final int RESULTS_MS = 16100;
    private static final int SLINGSHOT_CURSOR_TILE_U = 640;
    private static final int SLINGSHOT_CURSOR_TILE_V = 192;
    private static final float SLINGSHOT_REQUIRED_CHARGE_SECONDS = 0.3F;

    private final List<Target> targets = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Shatter> shatters = new ArrayList<>();
    private final List<ScorePopup> scorePopups = new ArrayList<>();
    private int starTokens;
    private int startTimerMs = START_MS;
    private int gameTimerMs = GAME_MS;
    private int endingDelayMs = -1;
    private int resultTimerMs = -1;
    private int score = 0;
    private int shotsFired = 0;
    private int successShots = 0;
    private int accuracy = -1;
    private float modifierBonus = 1.0F;
    private int starTokensWon = -1;
    private boolean sentResult = false;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean aiming;
    private long aimingStartedMs = -1L;
    private boolean canPlaySlingshotSound;
    private boolean musicRestored;
    private float playerX = 8 * 64;
    private float playerY = 13 * 64;
    private long lastUpdateMs;
    private double frameAccumulator;
    private float guiScale = 1.0F;
    private float viewportX;
    private float viewportY;

    public FairSlingshotGameScreen(int starTokens) {
        super(Component.translatable("stardewcraft.fair.slingshot.title"));
        this.starTokens = Math.max(0, starTokens);
    }

    @Override
    protected void init() {
        lastUpdateMs = System.currentTimeMillis();
        guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        computeViewport();
        StardewMusicManager.stopForCutsceneSilence();
        if (targets.isEmpty()) {
            addTargets();
        }
    }

    private void computeViewport() {
        int sdvViewW = Math.round(width * guiScale);
        int sdvViewH = Math.round(height * guiScale);
        viewportX = (WORLD - sdvViewW) / 2.0F;
        viewportY = (WORLD - sdvViewH) / 2.0F;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateState();
        drawGame(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateState() {
        long now = System.currentTimeMillis();
        int elapsedMs = (int) Math.max(0L, Math.min(250L, now - lastUpdateMs));
        lastUpdateMs = now;
        frameAccumulator += elapsedMs / (1000.0D / 60.0D);
        while (frameAccumulator >= 1.0D) {
            frameAccumulator -= 1.0D;
            updateFrame(1000.0F / 60.0F);
        }
    }

    private void updateFrame(float frameMs) {
        int elapsed = Math.round(frameMs);
        if (startTimerMs > 0) {
            int previous = startTimerMs;
            startTimerMs = Math.max(0, startTimerMs - elapsed);
            if (previous > 0 && startTimerMs == 0) {
                play(ModSounds.WHISTLE.get(), 1.0F, 1.0F);
                StardewMusicManager.playForCutscene(ModSounds.MUSIC_TICK_TOCK.get());
            }
            return;
        }
        if (resultTimerMs >= 0) {
            updateScorePopups(elapsed);
            updateResults(elapsed);
            return;
        }
        if (endingDelayMs >= 0) {
            endingDelayMs = Math.max(0, endingDelayMs - elapsed);
            updateProjectiles();
            updateShatters(elapsed);
            updateScorePopups(elapsed);
            if (endingDelayMs == 0) {
                resultTimerMs = RESULTS_MS;
            }
            return;
        }

        gameTimerMs = Math.max(0, gameTimerMs - elapsed);
        if (gameTimerMs == 0) {
            endingDelayMs = END_DELAY_MS;
            play(ModSounds.WHISTLE.get(), 1.0F, 1.0F);
            return;
        }
        updateProjectiles();
        updatePlayer();
        updateTargets(elapsed);
        updateShatters(elapsed);
        updateScorePopups(elapsed);
        updateAimingCharge();
    }

    private void updateAimingCharge() {
        if (!aiming || !canPlaySlingshotSound || chargeProgress() < 1.0F) {
            return;
        }
        play(ModSounds.SLINGSHOT.get(), 1.0F, 1.0F);
        canPlaySlingshotSound = false;
    }

    private void updatePlayer() {
        if (aiming) {
            return;
        }
        float speed = 4.0F;
        if (up) {
            playerY -= speed;
        }
        if (down) {
            playerY += speed;
        }
        if (left) {
            playerX -= speed;
        }
        if (right) {
            playerX += speed;
        }
        playerX = clamp(playerX, 0, WORLD - PLAYER_W);
        playerY = clamp(playerY, 0, WORLD - PLAYER_H);
    }

    private void updateTargets(int elapsedMs) {
        Iterator<Target> iterator = targets.iterator();
        while (iterator.hasNext()) {
            Target target = iterator.next();
            if (target.update(elapsedMs) || tryHitTarget(target)) {
                iterator.remove();
            }
        }
    }

    private void updateScorePopups(int elapsedMs) {
        Iterator<ScorePopup> iterator = scorePopups.iterator();
        while (iterator.hasNext()) {
            ScorePopup popup = iterator.next();
            if (popup.update(elapsedMs)) {
                iterator.remove();
            }
        }
    }

    private boolean tryHitTarget(Target target) {
        if (!target.isVisible()) {
            return false;
        }
        boolean projectileHit = false;
        for (Iterator<Projectile> iterator = projectiles.iterator(); iterator.hasNext();) {
            Projectile projectile = iterator.next();
            if (!projectile.intersects(target.x, target.y, TARGET_SIZE, TARGET_SIZE)) {
                continue;
            }
            projectileHit = true;
            boolean removeProjectile = hitTarget(target, projectile);
            if (removeProjectile) {
                iterator.remove();
            }
        }
        return projectileHit;
    }

    private void updateProjectiles() {
        Iterator<Projectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            Projectile projectile = iterator.next();
            projectile.rotation += projectile.rotationVelocity;
            projectile.x += projectile.vx;
            projectile.y += projectile.vy;
            boolean remove = projectile.x < -64
                || projectile.y < -32
                || projectile.x > WORLD + 64
                || projectile.y > WORLD + 32;
            if (remove) {
                iterator.remove();
            }
        }
    }

    private boolean hitTarget(Target target, Projectile projectile) {
        target.hit = true;
        int add = target.type == 2 ? 5 : target.type == 1 ? 2 : 1;
        score += add;
        if (!projectile.scored) {
            successShots++;
            projectile.scored = true;
        }
        shatters.add(new Shatter(target.x, target.y, target.type));
        scorePopups.add(new ScorePopup(add, target.x + TARGET_SIZE / 2.0F, target.y + TARGET_SIZE / 2.0F, playerX, playerY));
        play(target.type == 0 ? ModSounds.BREAKING_GLASS.get() : ModSounds.POTTERY_SMASH.get(), 1.0F, 1.0F);
        return target.type != 0;
    }

    private void updateShatters(int elapsedMs) {
        Iterator<Shatter> iterator = shatters.iterator();
        while (iterator.hasNext()) {
            Shatter shatter = iterator.next();
            shatter.timerMs += elapsedMs;
            if (shatter.timerMs >= 180) {
                iterator.remove();
            }
        }
    }

    private void updateResults(int elapsedMs) {
        int previous = resultTimerMs;
        resultTimerMs = Math.max(0, resultTimerMs - elapsedMs);
        if (previous > 16000 && resultTimerMs <= 16000) {
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        }
        if (previous > 14000 && resultTimerMs <= 14000) {
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
            accuracy = accuracyPercent();
        }
        if (previous > 11000 && resultTimerMs <= 11000) {
            applyAccuracyMultiplier();
        }
        if (previous > 9000 && resultTimerMs <= 9000) {
            awardStarTokens();
        }
        if (resultTimerMs == 0) {
            onClose();
        }
    }

    private void applyAccuracyMultiplier() {
        int currentAccuracy = resultAccuracy();
        float modifier = currentAccuracy >= 100 ? 4.0F
            : currentAccuracy >= 95 ? 3.0F
            : currentAccuracy >= 90 ? 2.5F
            : currentAccuracy >= 85 ? 2.0F
            : currentAccuracy >= 75 ? 1.5F
            : 1.0F;
        modifierBonus = modifier;
        if (modifier > 1.0F) {
            score = (int) (score * modifier);
            play(ModSounds.NEW_ARTIFACT.get(), 1.0F, 1.0F);
        } else {
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        }
    }

    private void awardStarTokens() {
        if (starTokensWon >= 0) {
            return;
        }
        starTokensWon = starTokensForScore(score);
        if (starTokensWon > 0) {
            starTokens += starTokensWon;
            play(ModSounds.REWARD.get(), 1.0F, 1.0F);
        } else {
            play(ModSounds.FISH_ESCAPE.get(), 1.0F, 1.0F);
        }
        sendResultOnce();
    }

    private void sendResultOnce() {
        if (sentResult) {
            return;
        }
        sentResult = true;
        PacketDistributor.sendToServer(new FairSlingshotGameActionPayload(
            FairSlingshotGameActionPayload.ACTION_COMPLETE,
            score
        ));
    }

    private void drawGame(GuiGraphics graphics, int mouseX, int mouseY) {
        if (resultTimerMs >= 0) {
            drawResults(graphics);
            return;
        }
        drawWorldLayer(graphics, BACK);
        drawFarmerShadow(graphics);
        drawWorldLayer(graphics, BUILDINGS);
        for (Shatter shatter : shatters) {
            int frame = Math.min(2, shatter.timerMs / 60);
            drawWorldTexturePart(graphics, SHATTERS[shatter.type], Math.round(shatter.x - 4), Math.round(shatter.y - 4), 64, 64, frame * 16, 0, 16, 16, 48, 16);
        }
        for (ScorePopup popup : scorePopups) {
            drawNumberSprite(graphics, popup.value, popup.x, popup.y, popup.scale * 0.75F, popup.alpha, popup.secondDigitOffset());
        }
        int facing = aiming ? facingForMouse(mouseX, mouseY) : FACING_DOWN;
        if (aiming && facing == FACING_DOWN) {
            drawSlingshotDownArm(graphics);
        }
        drawFarmer(graphics, facing);
        if (aiming) {
            drawSlingshot(graphics, mouseX, mouseY, facing);
        }
        for (Target target : targets) {
            if (target.isVisible()) {
                drawTargetShadow(graphics, target);
                drawWorldTexture(graphics, TARGETS[target.type], Math.round(target.x), Math.round(target.y), TARGET_SIZE, TARGET_SIZE, 14, 14);
            }
        }
        drawWorldLayer(graphics, FRONT);
        for (Projectile projectile : projectiles) {
            drawWorldTextureAnchored(graphics, STONE_PROJECTILE,
                projectile.x + 32.0F,
                projectile.y + 32.0F,
                16, 16, 8.0F, 8.0F, projectile.rotation, false);
        }
        if (aiming) {
            drawSlingshotCursor(graphics, mouseX, mouseY);
        }
        drawHud(graphics);
    }

    private void drawWorldLayer(GuiGraphics graphics, ResourceLocation texture) {
        drawWorldTexture(graphics, texture, 0, 0, WORLD, WORLD, 256, 256);
    }

    private void drawFarmer(GuiGraphics graphics, int facing) {
        drawWorldTexture(graphics, farmerTexture(facing), Math.round(playerX), Math.round(playerY - 96.0F), PLAYER_W, PLAYER_H, 16, 32);
    }

    private void drawFarmerShadow(GuiGraphics graphics) {
        drawWorldTextureAnchored(graphics, SHADOW,
            playerX + 32.0F,
            playerY + 24.0F,
            12, 7, 6.0F, 3.0F, 0.0F, false);
    }

    private void drawSlingshotCursor(GuiGraphics graphics, int mouseX, int mouseY) {
        float aimX = viewportX + mouseX * guiScale;
        float aimY = viewportY + mouseY * guiScale;
        float shootX = shootOriginX();
        float shootY = shootOriginY();
        float dx = aimX - shootX;
        float dy = aimY - shootY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float cursorX = shootX;
        float cursorY = shootY;
        if (length >= 0.1F) {
            cursorX += dx / length * 181.0F;
            cursorY += dy / length * 181.0F;
        }
        drawWorldTexturePart(graphics, StardewGuiUtil.CURSORS,
            Math.round(cursorX - 32.0F),
            Math.round(cursorY - 32.0F),
            64, 64,
            SLINGSHOT_CURSOR_TILE_U, SLINGSHOT_CURSOR_TILE_V,
            64, 64,
            StardewGuiUtil.CURSORS_WIDTH, StardewGuiUtil.CURSORS_HEIGHT);
    }

    private void drawSlingshot(GuiGraphics graphics, int mouseX, int mouseY, int facing) {
        if (facing == FACING_RIGHT || facing == FACING_LEFT) {
            drawSlingshotSide(graphics, mouseX, mouseY, facing == FACING_LEFT);
            return;
        }
        if (facing == FACING_UP) {
            drawSlingshotUp(graphics, mouseX, mouseY);
            return;
        }
        drawSlingshotDownTool(graphics, mouseX, mouseY);
    }

    private void drawSlingshotDownArm(GuiGraphics graphics) {
        float positionX = playerX;
        float positionY = playerY;
        int backArmDistance = backArmDistance();
        drawWorldTexture(graphics, FARMER_SLINGSHOT_HAND,
            Math.round(positionX + 4.0F),
            Math.round(positionY - 32.0F - backArmDistance / 2.0F),
            16, 16, 4, 4);
    }

    private void drawSlingshotDownTool(GuiGraphics graphics, int mouseX, int mouseY) {
        float positionX = playerX;
        float positionY = playerY;
        float frontArmRotation = frontArmRotation(mouseX, mouseY);
        int backArmDistance = backArmDistance();
        drawWorldLine(graphics,
            positionX + 16.0F,
            positionY - 28.0F - backArmDistance / 2.0F,
            positionX + 44.0F - frontArmRotation * 10.0F,
            positionY - 24.0F,
            0xFFFFFFFF);
        drawWorldLine(graphics,
            positionX + 16.0F,
            positionY - 28.0F - backArmDistance / 2.0F,
            positionX + 56.0F - frontArmRotation * 10.0F,
            positionY - 24.0F,
            0xFFFFFFFF);
        drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_FRAME,
            positionX + 44.0F - frontArmRotation * 10.0F,
            positionY - 16.0F,
            7, 9, 3.0F, 5.0F, 0.0F, false);
    }

    private void drawSlingshotSide(GuiGraphics graphics, int mouseX, int mouseY, boolean left) {
        float positionX = playerX;
        float positionY = playerY;
        float frontArmRotation = frontArmRotation(mouseX, mouseY);
        int backArmDistance = backArmDistance();
        if (left) {
            drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_SIDE_ARM,
                positionX + 40.0F + backArmDistance,
                positionY - 32.0F,
                10, 4, 9.0F, 4.0F, 0.0F, true);
            drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_SIDE_FRAME,
                positionX + 24.0F,
                positionY - 40.0F,
                9, 10, 8.0F, 3.0F, frontArmRotation + (float) Math.PI, true);
            int attachX = (int) (Math.cos(frontArmRotation + (float) Math.PI * 2.0F / 5.0F) * (20 + backArmDistance - 8)
                - Math.sin(frontArmRotation + (float) Math.PI * 2.0F / 5.0F) * -68.0F);
            int attachY = (int) (Math.sin(frontArmRotation + (float) Math.PI * 2.0F / 5.0F) * (20 + backArmDistance - 8)
                + Math.cos(frontArmRotation + (float) Math.PI * 2.0F / 5.0F) * -68.0F);
            drawWorldLine(graphics,
                positionX + 4.0F + backArmDistance,
                positionY - 40.0F,
                positionX + 26.0F + attachX * 4.0F / 10.0F,
                positionY - 40.0F + attachY * 4.0F / 10.0F,
                0xFFFFFFFF);
            return;
        }
        drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_SIDE_ARM,
            positionX + 52.0F - backArmDistance,
            positionY - 32.0F,
            10, 4, 8.0F, 3.0F, 0.0F, false);
        drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_SIDE_FRAME,
            positionX + 36.0F,
            positionY - 44.0F,
            9, 10, 0.0F, 3.0F, frontArmRotation, false);
        int attachX = (int) (Math.cos(frontArmRotation + (float) Math.PI / 2.0F) * (20 - backArmDistance - 8)
            - Math.sin(frontArmRotation + (float) Math.PI / 2.0F) * -68.0F);
        int attachY = (int) (Math.sin(frontArmRotation + (float) Math.PI / 2.0F) * (20 - backArmDistance - 8)
            + Math.cos(frontArmRotation + (float) Math.PI / 2.0F) * -68.0F);
        drawWorldLine(graphics,
            positionX + 52.0F - backArmDistance,
            positionY - 36.0F,
            positionX + 32.0F + attachX / 2.0F,
            positionY - 44.0F + attachY / 2.0F,
            0xFFFFFFFF);
    }

    private void drawSlingshotUp(GuiGraphics graphics, int mouseX, int mouseY) {
        float positionX = playerX;
        float positionY = playerY;
        drawWorldTextureAnchored(graphics, FARMER_SLINGSHOT_UP_FRAME,
            positionX + 4.0F + frontArmRotation(mouseX, mouseY) * 8.0F,
            positionY - 44.0F,
            9, 14, 4.0F, 11.0F, 0.0F, false);
    }

    private float frontArmRotation(int mouseX, int mouseY) {
        float aimX = viewportX + mouseX * guiScale;
        float aimY = viewportY + mouseY * guiScale;
        float rotation = (float) Math.atan2(aimY - shootOriginY(), aimX - shootOriginX());
        if (rotation < 0.0F) {
            rotation += (float) Math.PI * 2.0F;
        }
        return rotation;
    }

    private void drawTargetShadow(GuiGraphics graphics, Target target) {
        drawWorldTexture(graphics, SHADOW, Math.round(target.x), Math.round(target.y + TARGET_SIZE + 32), 48, 28, 12, 7);
    }

    private ResourceLocation farmerTexture(int facing) {
        if (!aiming) {
            return FARMER;
        }
        return switch (facing) {
            case FACING_UP -> FARMER_SLINGSHOT_UP;
            case FACING_RIGHT -> FARMER_SLINGSHOT_RIGHT;
            case FACING_LEFT -> FARMER_SLINGSHOT_LEFT;
            default -> FARMER_SLINGSHOT_DOWN;
        };
    }

    private int facingForMouse(int mouseX, int mouseY) {
        float aimX = viewportX + mouseX * guiScale;
        float aimY = viewportY + mouseY * guiScale;
        float shootX = shootOriginX();
        float shootY = shootOriginY();
        float dx = aimX - shootX;
        float dy = aimY - shootY;
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx < 0.0F ? FACING_LEFT : FACING_RIGHT;
        }
        return dy < 0.0F ? FACING_UP : FACING_DOWN;
    }

    private void drawWorldTexture(GuiGraphics graphics, ResourceLocation texture, int worldX, int worldY, int worldW, int worldH,
                                  int texW, int texH) {
        drawWorldTexturePart(graphics, texture, worldX, worldY, worldW, worldH, 0, 0, texW, texH, texW, texH);
    }

    private void drawWorldTexturePart(GuiGraphics graphics, ResourceLocation texture, int worldX, int worldY, int worldW, int worldH,
                                      int u, int v, int srcW, int srcH, int texW, int texH) {
        graphics.blit(texture, ui(Math.round(worldX - viewportX)), ui(Math.round(worldY - viewportY)), ui(worldW), ui(worldH), u, v, srcW, srcH, texW, texH);
    }

    private void drawWorldTextureAnchored(GuiGraphics graphics, ResourceLocation texture,
                                          float anchorWorldX, float anchorWorldY,
                                          int srcW, int srcH,
                                          float originX, float originY,
                                          float rotation, boolean flipX) {
        int anchorX = ui(Math.round(anchorWorldX - viewportX));
        int anchorY = ui(Math.round(anchorWorldY - viewportY));
        int drawX = -ui(Math.round(originX * 4.0F));
        int drawY = -ui(Math.round(originY * 4.0F));
        int drawW = ui(srcW * 4);
        int drawH = ui(srcH * 4);
        graphics.pose().pushPose();
        graphics.pose().translate(anchorX, anchorY, 0.0F);
        if (rotation != 0.0F) {
            graphics.pose().mulPose(Axis.ZP.rotation(rotation));
        }
        if (flipX) {
            graphics.pose().scale(-1.0F, 1.0F, 1.0F);
        }
        graphics.blit(texture, drawX, drawY, drawW, drawH, 0, 0, srcW, srcH, srcW, srcH);
        graphics.pose().popPose();
    }

    private void drawWorldLine(GuiGraphics graphics, float startX, float startY, float endX, float endY, int color) {
        int x0 = ui(Math.round(startX - viewportX));
        int y0 = ui(Math.round(startY - viewportY));
        int x1 = ui(Math.round(endX - viewportX));
        int y1 = ui(Math.round(endY - viewportY));
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void drawNumberSprite(GuiGraphics graphics, int number, float worldX, float worldY, float scale, float alpha, int secondDigitOffset) {
        int digit = 1;
        int remaining = Math.max(0, number);
        float drawWorldX = worldX;
        do {
            int currentDigit = remaining % 10;
            remaining /= 10;
            float digitWorldY = worldY + (digit == 2 ? Math.min(0, secondDigitOffset) : 0);
            int anchorX = ui(Math.round(drawWorldX - viewportX));
            int anchorY = ui(Math.round(digitWorldY - viewportY));
            float uiScale = 4.0F * scale / guiScale;
            graphics.pose().pushPose();
            graphics.pose().translate(anchorX, anchorY, 0.0F);
            graphics.pose().scale(uiScale, uiScale, 1.0F);
            CommonGuiTextures.drawNumberDigitAtCurrentPoseTint(graphics, currentDigit, -4, -4,
                1.0F, 130.0F / 255.0F, 0.0F, alpha);
            graphics.pose().popPose();
            drawWorldX -= 8.0F * scale * 4.0F - 4.0F;
            digit++;
        } while (remaining > 0);
    }

    private void drawHud(GuiGraphics graphics) {
        drawString(graphics, I18n.get("stardewcraft.fair.slingshot.score", score), 32, 32);
        int timer = endingDelayMs >= 0 ? endingDelayMs : gameTimerMs;
        int seconds = Math.max(0, timer / 1000);
        drawString(graphics, I18n.get("stardewcraft.fair.slingshot.time", seconds), 32, 64);
        if (shotsFired > 1) {
            drawString(graphics, I18n.get("stardewcraft.fair.slingshot.acc_short", accuracyPercent()), 32, 96);
        }
    }

    private void drawResults(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        int x = width / 2 - ui(128);
        int y = height / 2 - ui(64);
        if (resultTimerMs <= 16000) {
            drawBordered(graphics, I18n.get("stardewcraft.fair.slingshot.score", score), x, y,
                resultTimerMs <= 11000 && modifierBonus > 1.0F ? 0xFF00FF00 : 0xFFFFFFFF);
        }
        if (resultTimerMs <= 14000) {
            y += ui(48);
            drawBordered(graphics, I18n.get("stardewcraft.fair.slingshot.accuracy", resultAccuracy(), successShots, shotsFired), x, y, 0xFFFFFFFF);
        }
        if (resultTimerMs <= 11000) {
            y += ui(48);
            String key = modifierBonus > 1.0F
                ? "stardewcraft.fair.slingshot.multiplier"
                : "stardewcraft.fair.slingshot.no_bonus";
            String text = modifierBonus > 1.0F ? I18n.get(key, multiplierText()) : I18n.get(key);
            drawBordered(graphics, text, x, y, modifierBonus > 1.0F ? 0xFFFFFF00 : 0xFFFF0000);
        }
        if (resultTimerMs <= 9000) {
            y += ui(64);
            if (starTokensWon > 0) {
                String reward = I18n.get("stardewcraft.fair.slingshot.reward", starTokensWon);
                float fade = Math.min(1.0F, (resultTimerMs - 2000) / 4000.0F);
                for (int i = 0; i < 3; i++) {
                    int jitterX = ThreadLocalRandom.current().nextInt(-1, 2) * ui(8);
                    int jitterY = ThreadLocalRandom.current().nextInt(-1, 2) * ui(8);
                    drawBordered(graphics, reward, x + jitterX, y + jitterY,
                        argb(Math.round(255.0F * 0.3F * fade), 135, 206, 235),
                        argb(Math.round(255.0F * 0.2F * fade), 75, 65, 55));
                }
                drawBordered(graphics, reward, x, y, 0xFF87CEEB, 0xFF4B4137);
            } else {
                drawBordered(graphics, I18n.get("stardewcraft.fair.slingshot.no_reward"), x, y, 0xFFFF0000);
            }
        }
        if (resultTimerMs <= 1000) {
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * (1.0F - resultTimerMs / 1000.0F))));
            graphics.fill(0, 0, width, height, alpha << 24);
        }
        drawTokenBox(graphics, ui(16), ui(16));
    }

    private void drawTokenBox(GuiGraphics graphics, int x, int y) {
        int w = ui(128 + (starTokens > 999 ? 16 : 0));
        int h = ui(64);
        graphics.fill(x, y, x + w, y + h, 0xBF000000);
        drawScaledTexture(graphics, STAR_TOKEN, x + ui(16), y + ui(16), ui(32), ui(32), 8, 8);
        drawBordered(graphics, String.valueOf(starTokens), x + ui(56), y + ui(13), 0xFFFFFFFF);
    }

    private void drawString(GuiGraphics graphics, String text, int sdvX, int sdvY) {
        int x = ui(sdvX);
        int y = ui(sdvY);
        drawBordered(graphics, text, x, y, 0xFFFFFFFF);
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

    private void drawScaledTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        graphics.blit(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (resultTimerMs >= 0) {
            advanceResults();
            return true;
        }
        if (startTimerMs == 0 && endingDelayMs < 0) {
            aiming = true;
            aimingStartedMs = System.currentTimeMillis();
            canPlaySlingshotSound = true;
            up = false;
            down = false;
            left = false;
            right = false;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || resultTimerMs >= 0 || !aiming) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        aiming = false;
        if (backArmDistance() > 4 && !canPlaySlingshotSound) {
            fire(mouseX, mouseY);
        }
        aimingStartedMs = -1L;
        return true;
    }

    private void fire(double mouseX, double mouseY) {
        if (startTimerMs > 0) {
            return;
        }
        double targetX = viewportX + mouseX * guiScale;
        double targetY = viewportY + mouseY * guiScale;
        double startX = shootOriginX();
        double startY = shootOriginY();
        double dx = targetX - startX;
        double dy = targetY - startY;
        double len = Math.max(0.001D, Math.sqrt(dx * dx + dy * dy));
        double speed = ThreadLocalRandom.current().nextInt(19, 21);
        float rotationVelocity = (float) (Math.PI / ThreadLocalRandom.current().nextInt(1, 128));
        projectiles.add(new Projectile(
            (float) startX - 32.0F,
            (float) startY - 32.0F,
            (float) (dx / len * speed),
            (float) (dy / len * speed),
            rotationVelocity
        ));
        shotsFired++;
        canPlaySlingshotSound = true;
    }

    private void advanceResults() {
        if (resultTimerMs > 16000) {
            resultTimerMs = 16001;
        } else if (resultTimerMs > 14000) {
            resultTimerMs = 14001;
        } else if (resultTimerMs > 11000) {
            resultTimerMs = 11001;
        } else if (resultTimerMs > 9000) {
            resultTimerMs = 9001;
        } else if (resultTimerMs < 9000 && resultTimerMs > 1000) {
            resultTimerMs = 1500;
            play(ModSounds.SMALL_SELECT.get(), 1.0F, 1.0F);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            play(ModSounds.FISH_ESCAPE.get(), 1.0F, 1.0F);
            resultTimerMs = 1;
            return true;
        }
        if (startTimerMs > 0 || endingDelayMs >= 0 || resultTimerMs >= 0) {
            clearMovement();
            return true;
        }
        if (!aiming && setMovement(keyCode, scanCode, true)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (setMovement(keyCode, scanCode, false)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean setMovement(int keyCode, int scanCode, boolean value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyUp.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_UP) {
            up = value;
            return true;
        }
        if (minecraft.options.keyDown.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_DOWN) {
            down = value;
            return true;
        }
        if (minecraft.options.keyLeft.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_LEFT) {
            left = value;
            return true;
        }
        if (minecraft.options.keyRight.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_RIGHT) {
            right = value;
            return true;
        }
        return false;
    }

    private void clearMovement() {
        up = false;
        down = false;
        left = false;
        right = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        restoreFairMusic();
    }

    private void restoreFairMusic() {
        if (musicRestored) {
            return;
        }
        musicRestored = true;
        StardewMusicManager.playForCutscene(ModSounds.MUSIC_FALL_FEST.get());
    }

    private void addTargets() {
        addRow(0, 320, 1500, 5, 4, false, 0);
        addRow(4000, 448, 1000, 5, 4, true, 0);
        addRow(8000, 128, 2000, 5, 4, false, 1);
        addTwinPausers(8000, 576, 384, 5, 2000, 1);
        addTwinPausers(15000, 576, 128, 4, 4000, 1);
        addRow(18000, 320, 1500, 5, 4, false, 0);
        addRow(21000, 448, 1000, 5, 4, true, 0);
        addTwinPausers(25000, 832, 128, 5, 1500, 2);
        addRow(27000, 576, 500, 8, 2, true, 0);
        addRow(28000, 448, 500, 8, 2, true, 0);
        addRow(29000, 320, 500, 8, 2, true, 0);
        addRow(30000, 128, 500, 8, 2, true, 0);
        addTwinPausers(36000, 832, 128, 5, 2000, 2);
        addRow(41000, 320, 1500, 5, 4, false, 0);
        addRow(42000, 448, 1000, 5, 4, true, 0);
        addRow(43000, 128, 1000, 4, 4, false, 0);
    }

    private void addRow(int startMs, int laneY, int gapMs, int count, int speed, boolean fromRight, int type) {
        for (int i = 0; i < count; i++) {
            targets.add(Target.moving(startMs + gapMs * i, laneY, speed, fromRight, type));
        }
    }

    private void addTwinPausers(int startMs, int laneY, int pauseX, int speed, int pauseMs, int type) {
        int otherPauseX = switch (pauseX) {
            case 128 -> 832;
            case 256 -> 704;
            case 384 -> 576;
            case 576 -> 384;
            case 704 -> 256;
            case 832 -> 128;
            default -> -1;
        };
        boolean firstIsSpawnLeft = pauseX == 128 || pauseX == 256 || pauseX == 384;
        targets.add(Target.pausing(startMs, laneY, speed, !firstIsSpawnLeft, type, pauseX, pauseMs));
        targets.add(Target.pausing(startMs, laneY, speed, firstIsSpawnLeft, type, otherPauseX, pauseMs));
    }

    private int accuracyPercent() {
        if (shotsFired <= 1) {
            return 0;
        }
        double ratio = successShots / (double) (shotsFired - 1);
        return (int) Math.max(0.0D, BigDecimal.valueOf(ratio)
            .setScale(2, RoundingMode.HALF_EVEN)
            .doubleValue() * 100.0D);
    }

    private int resultAccuracy() {
        return accuracy >= 0 ? accuracy : accuracyPercent();
    }

    private int backArmDistance() {
        return (int) (20.0F * chargeProgress());
    }

    private float shootOriginX() {
        return playerX + PLAYER_W / 2.0F;
    }

    private float shootOriginY() {
        return playerY + 24.0F;
    }

    private float chargeProgress() {
        if (aimingStartedMs < 0L) {
            return 0.0F;
        }
        float elapsedSeconds = (System.currentTimeMillis() - aimingStartedMs) / 1000.0F;
        return clamp(elapsedSeconds / SLINGSHOT_REQUIRED_CHARGE_SECONDS, 0.0F, 1.0F);
    }

    private static int starTokensForScore(int score) {
        if (score < 40) {
            return 0;
        }
        int tokens = (int) (((score * 2 - 30) / 10) * 2.5F);
        tokens *= 2;
        return tokens > 280 ? 500 : tokens;
    }

    private String multiplierText() {
        if (Math.abs(modifierBonus - Math.round(modifierBonus)) < 0.001F) {
            return String.valueOf(Math.round(modifierBonus));
        }
        return String.valueOf(modifierBonus);
    }

    private int ui(int sdvPx) {
        return Math.round(sdvPx / guiScale);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(sound, volume, pitch);
        }
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fair/target_game/" + name + ".png");
    }

    private static final class Target {
        private float x;
        private final float y;
        private int velocity;
        private final int type;
        private int countdownMs;
        private int pauseX;
        private int pauseMs;
        private boolean paused;
        private boolean spawned;
        private boolean hit;

        private Target(int countdownMs, int laneY, int speed, boolean fromRight, int type, int pauseX, int pauseMs) {
            this.countdownMs = countdownMs;
            this.y = laneY;
            this.velocity = speed * (fromRight ? -1 : 1);
            this.type = type;
            this.x = fromRight ? 960 : 0;
            this.pauseX = pauseX;
            this.pauseMs = pauseMs;
        }

        static Target moving(int countdownMs, int laneY, int speed, boolean fromRight, int type) {
            return new Target(countdownMs, laneY, speed, fromRight, type, -1, 0);
        }

        static Target pausing(int countdownMs, int laneY, int speed, boolean fromRight, int type, int pauseX, int pauseMs) {
            return new Target(countdownMs, laneY, speed, fromRight, type, pauseX, pauseMs);
        }

        boolean update(int elapsedMs) {
            if (hit) {
                return true;
            }
            if (countdownMs > 0) {
                countdownMs -= elapsedMs;
                if (countdownMs <= 0) {
                    spawned = true;
                }
            } else {
                spawned = true;
            }
            if (!spawned) {
                return false;
            }
            if (paused) {
                pauseMs = Math.max(0, pauseMs - elapsedMs);
                if (pauseMs > 0) {
                    return false;
                }
                velocity = -velocity;
                paused = false;
                pauseX = -1;
                return false;
            }
            if (!paused) {
                x += velocity;
                if (pauseX >= 0 && Math.abs(pauseX - x) <= Math.abs(velocity)) {
                    paused = true;
                }
            }
            return x < 0 || x + TARGET_SIZE > 1024;
        }

        boolean isVisible() {
            return spawned && !hit;
        }

    }

    private static final class Projectile {
        private float x;
        private float y;
        private final float vx;
        private final float vy;
        private final float rotationVelocity;
        private float rotation;
        private boolean scored;

        private Projectile(float x, float y, float vx, float vy, float rotationVelocity) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rotationVelocity = rotationVelocity;
        }

        private boolean intersects(float x, float y, int width, int height) {
            float boundsX = this.x + 32.0F - PROJECTILE_BOUNDS / 2.0F;
            float boundsY = this.y + 32.0F - PROJECTILE_BOUNDS / 2.0F;
            return boundsX < x + width
                && boundsX + PROJECTILE_BOUNDS > x
                && boundsY < y + height
                && boundsY + PROJECTILE_BOUNDS > y;
        }
    }

    private static final class Shatter {
        private final float x;
        private final float y;
        private final int type;
        private int timerMs;

        private Shatter(float x, float y, int type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    private static final class ScorePopup {
        private static final int LIFETIME_MS = 1800;
        private static final int FADE_START_MS = 600;
        private final int value;
        private final int finalY;
        private float x;
        private float y;
        private float vx;
        private float vy;
        private float scale = 1.0F;
        private float alpha = 1.0F;
        private int bounces;
        private int elapsedMs;
        private boolean passedRestingLine;

        private ScorePopup(int value, float originX, float originY, float playerX, float playerY) {
            this.value = value;
            float reflectedPlayerX = originX * 2.0F - playerX;
            float reflectedPlayerY = originY * 2.0F - playerY;
            int minXVelocity;
            int maxXVelocity;
            int minYVelocity;
            int maxYVelocity;
            if (reflectedPlayerY >= originY - 32.0F && reflectedPlayerY <= originY + 32.0F) {
                finalY = (int) originY - 32;
                minYVelocity = 250;
                maxYVelocity = 300;
                if (reflectedPlayerX < originX) {
                    minXVelocity = 20;
                    maxXVelocity = 110;
                } else {
                    minXVelocity = -110;
                    maxXVelocity = -20;
                }
            } else if (reflectedPlayerY < originY - 32.0F) {
                finalY = (int) originY + 32;
                minYVelocity = 180;
                maxYVelocity = 230;
                minXVelocity = -50;
                maxXVelocity = 50;
            } else {
                finalY = (int) originY - 1;
                minYVelocity = 350;
                maxYVelocity = 400;
                minXVelocity = -50;
                maxXVelocity = 50;
            }
            this.x = originX - 32.0F;
            this.y = originY - 32.0F;
            this.vx = ThreadLocalRandom.current().nextInt(minXVelocity, maxXVelocity) / 40.0F;
            this.vy = ThreadLocalRandom.current().nextInt(minYVelocity, maxYVelocity) / 40.0F;
        }

        private boolean update(int elapsed) {
            elapsedMs += elapsed;
            if (elapsedMs >= LIFETIME_MS) {
                return true;
            }
            if (elapsedMs > FADE_START_MS) {
                alpha = Math.max(0.0F, (LIFETIME_MS - elapsedMs) / 1000.0F);
            }
            x += vx;
            y -= vy;
            if (bounces <= 2) {
                vy -= 0.4F;
            }
            if (y >= finalY && passedRestingLine) {
                if (bounces <= 2) {
                    bounces++;
                    vy = Math.abs(vy * 2.0F / 3.0F);
                    vx -= vx / 2.0F;
                }
            }
            if (y < finalY) {
                passedRestingLine = true;
            }
            if (bounces > 2) {
                vy = 0.0F;
                vx = 0.0F;
            }
            return false;
        }

        private int secondDigitOffset() {
            return -1 * (int) ((float) finalY - y) / 2;
        }
    }
}
