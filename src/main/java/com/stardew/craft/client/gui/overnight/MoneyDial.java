package com.stardew.craft.client.gui.overnight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class MoneyDial {
    private static final ResourceLocation ORIGINAL_ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/overnight/animations.png");
    private static final int ANIMATION_TEXTURE_WIDTH = 640;
    private static final int ANIMATION_TEXTURE_HEIGHT = 3328;
    private static final int SPARKLE_FRAME_SIZE = 64;
    private static final int SPARKLE_FRAME_COUNT = 8;
    private static final int SPARKLE_FRAME_DURATION = 100;

    public int numDigits;
    public int currentValue;
    public int previousTargetValue;

    private final boolean playSounds;
    private final List<MoneySparkle> animations = new ArrayList<>();
    private int speed;
    private int soundTimer;
    private int moneyMadeAccumulator;
    private int moneyShineTimer;
    private long lastDrawMs;

    public MoneyDial(int numDigits) {
        this(numDigits, true);
    }

    public MoneyDial(int numDigits, boolean playSounds) {
        this.numDigits = numDigits;
        this.playSounds = playSounds;
        this.currentValue = 0;
    }

    public void draw(GuiGraphics graphics, int x, int y, int target) {
        long now = Util.getMillis();
        int elapsedMs = lastDrawMs == 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, now - lastDrawMs);
        lastDrawMs = now;

        float guiScale = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float digitScale = 4.0f / guiScale;
        int digitStep = Math.max(1, Math.round(24.0f / guiScale));

        if (previousTargetValue != target) {
            speed = (target - currentValue) / 100;
            previousTargetValue = target;
            soundTimer = Math.max(6, 100 / (Math.abs(speed) + 1));
        }

        if (moneyShineTimer > 0 && currentValue == target) {
            moneyShineTimer = Math.max(0, moneyShineTimer - elapsedMs);
        }

        if (moneyMadeAccumulator > 0) {
            moneyMadeAccumulator -= (Math.abs(speed / 2) + 1) * (animations.isEmpty() ? 100 : 1);
            if (moneyMadeAccumulator <= 0) {
                moneyShineTimer = numDigits * 60;
            }
        }
        
        if (currentValue != target) {
            currentValue += speed + ((currentValue < target) ? 1 : -1);

            if (currentValue < target) {
                moneyMadeAccumulator += Math.abs(speed);
            }
            
            soundTimer--;
            
            if (Math.abs(target - currentValue) <= speed + 1 || (speed != 0 && Math.signum(target - currentValue) != Math.signum(speed))) {
                currentValue = target;
            }
            
            if (soundTimer <= 0) {
                if (playSounds && target > currentValue && net.minecraft.client.Minecraft.getInstance() != null) {
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.MONEY_DIAL.get(), 1.0f, 1.0f));
                }
                soundTimer = Math.max(6, 100 / (Math.abs(speed) + 1));
                if (ThreadLocalRandom.current().nextDouble() < 0.4D && target > currentValue) {
                    animations.add(new MoneySparkle(
                        ThreadLocalRandom.current().nextInt(10, 12),
                        x + Math.round(ThreadLocalRandom.current().nextInt(30, 190) / guiScale),
                        y + Math.round(ThreadLocalRandom.current().nextInt(-32, 48) / guiScale)
                    ));
                }
            }
        }

        for (int i = animations.size() - 1; i >= 0; i--) {
            MoneySparkle sparkle = animations.get(i);
            if (sparkle.update(elapsedMs)) {
                animations.remove(i);
            } else {
                sparkle.draw(graphics, guiScale);
            }
        }
        
        int xPosition = 0;
        int digitStrip = (int) Math.pow(10.0, numDigits - 1);
        boolean significant = false;
        
        for (int j = 0; j < numDigits; j++) {
            int currentDigit = (currentValue / digitStrip) % 10;
            if (currentDigit > 0 || j == numDigits - 1) {
                significant = true;
            }
            
            if (significant) {
                float yOffset = 0.0f;
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof ShippingMenuScreen && currentValue >= 1_000_000) {
                    yOffset = Mth.sin((float) (System.currentTimeMillis() / 100.53096771240234D + j)) * (currentValue / 1_000_000.0f);
                }
                float scale = digitScale + ((moneyShineTimer / 60 == numDigits - j) ? (0.3f / guiScale) : 0.0f);
                ShippingMenuTextures.drawDigit(graphics, x + xPosition, (int) (y + yOffset), currentDigit, scale,
                        128.0F / 255.0F, 0.0F, 0.0F, 1.0F);
            }
            xPosition += digitStep;
            digitStrip /= 10;
        }
    }

    private static final class MoneySparkle {
        private final int row;
        private final int x;
        private final int y;
        private int ageMs;

        private MoneySparkle(int row, int x, int y) {
            this.row = row;
            this.x = x;
            this.y = y;
        }

        private boolean update(int elapsedMs) {
            ageMs += elapsedMs;
            return ageMs >= SPARKLE_FRAME_COUNT * SPARKLE_FRAME_DURATION;
        }

        private void draw(GuiGraphics graphics, float guiScale) {
            int frame = Math.min(SPARKLE_FRAME_COUNT - 1, ageMs / SPARKLE_FRAME_DURATION);
            float scale = 1.0F / guiScale;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0F, 215.0F / 255.0F, 0.0F, 1.0F);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.blit(
                ORIGINAL_ANIMATIONS,
                0,
                0,
                frame * SPARKLE_FRAME_SIZE,
                row * SPARKLE_FRAME_SIZE,
                SPARKLE_FRAME_SIZE,
                SPARKLE_FRAME_SIZE,
                ANIMATION_TEXTURE_WIDTH,
                ANIMATION_TEXTURE_HEIGHT
            );
            graphics.pose().popPose();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
