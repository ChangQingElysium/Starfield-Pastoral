package com.stardew.craft.client.gui.festival;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.network.payload.FairStrengthGameResultPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class FairStrengthGameScreen extends Screen implements com.stardew.craft.client.gui.StardewRealtimeScreen {
    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = 136;
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 176;

    private final int initialChangeSpeed;
    private final Random random = new Random();
    private int power = 0;
    private int changeSpeed;
    private int endTimerMs = 0;
    private int swingTimerMs = 0;
    private float transparency = 1.0F;
    private int barColor = 0xFFFF2F1F;
    private boolean clicked = false;
    private boolean stopping = false;
    private boolean showedResult = false;
    private boolean victorySound = false;
    private String resultKey = "";
    private String resultArgKey = "";
    private long lastUpdateMs = 0L;
    private double frameAccumulator = 0.0D;

    public FairStrengthGameScreen(int changeSpeed) {
        super(Component.translatable("stardewcraft.fair.strength.title"));
        this.initialChangeSpeed = Math.max(3, Math.min(4, changeSpeed));
        this.changeSpeed = this.initialChangeSpeed;
    }

    @Override
    protected void init() {
        lastUpdateMs = System.currentTimeMillis();
        play(ModSounds.COWBOY_MONSTERHIT.get(), 1.0F, 1.0F);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateState();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 1.0F);

        int barX = panelX + (PANEL_WIDTH - BAR_WIDTH) / 2;
        int barY = panelY + 20;
        graphics.fill(barX - 3, barY - 3, barX + BAR_WIDTH + 3, barY + BAR_HEIGHT + 3, 0xFF5A3218);
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFFE7B86A);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF2A1710);

        int fillHeight = Math.round((power / 100.0F) * BAR_HEIGHT);
        int alpha = Math.max(0, Math.min(255, Math.round(transparency * 255.0F)));
        int color = (alpha << 24) | (barColor & 0x00FFFFFF);
        graphics.fill(barX, barY + BAR_HEIGHT - fillHeight, barX + BAR_WIDTH, barY + BAR_HEIGHT, color);

        if (stopping && power >= 99 && endTimerMs > 0 && endTimerMs < 1500) {
            drawSparkles(graphics, barX, barY);
        }

        String powerText = String.valueOf(power);
        graphics.drawString(font, powerText, panelX + PANEL_WIDTH / 2 - font.width(powerText) / 2,
            barY + BAR_HEIGHT + 10, 0xFF3F2A13, false);

        if (resultKey.isEmpty()) {
            String hint = clicked
                ? I18n.get("stardewcraft.fair.strength.swinging")
                : I18n.get("stardewcraft.fair.strength.click");
            graphics.drawString(font, hint, panelX + PANEL_WIDTH / 2 - font.width(hint) / 2,
                panelY + PANEL_HEIGHT - 18, 0xFF5B3418, false);
        } else {
            drawResult(graphics, panelX, panelY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateState() {
        long now = System.currentTimeMillis();
        int elapsedMs = (int) Math.max(0L, Math.min(250L, now - lastUpdateMs));
        lastUpdateMs = now;

        if (changeSpeed != 0) {
            frameAccumulator += elapsedMs / (1000.0D / 60.0D);
            while (frameAccumulator >= 1.0D) {
                frameAccumulator -= 1.0D;
                power += changeSpeed;
                if (power >= 100) {
                    power = 100;
                    changeSpeed = -Math.abs(changeSpeed);
                } else if (power <= 0) {
                    power = 0;
                    changeSpeed = Math.abs(changeSpeed);
                }
            }
        }

        if (swingTimerMs > 0) {
            swingTimerMs = Math.max(0, swingTimerMs - elapsedMs);
            if (swingTimerMs == 0) {
                stopMeter();
            }
        }

        if (endTimerMs > 0) {
            endTimerMs = Math.max(0, endTimerMs - elapsedMs);
            if (power >= 99 && endTimerMs < 1500 && !victorySound) {
                victorySound = true;
                barColor = 0xFFFFA000;
                play(ModSounds.NEW_ARTIFACT.get(), 1.0F, 1.0F);
            }
            if (power < 99) {
                transparency = Math.max(0.0F, transparency - 0.02F);
            }
            if (endTimerMs == 0 && !showedResult) {
                showResult();
            }
        }
    }

    private void stopMeter() {
        changeSpeed = 0;
        stopping = true;
        play(ModSounds.HAMMER.get(), 1.0F, 1.0F);
        endTimerMs = power >= 99 ? 2000 : 1000;
    }

    private void showResult() {
        showedResult = true;
        PacketDistributor.sendToServer(new FairStrengthGameResultPayload(power));
        if (power >= 99) {
            resultKey = "stardewcraft.fair.strength.result.perfect";
            play(ModSounds.PURCHASE.get(), 1.0F, 1.0F);
        } else if (power < 2) {
            resultKey = "stardewcraft.fair.strength.result.zero";
            play(ModSounds.PURCHASE.get(), 1.0F, 1.0F);
        } else {
            resultKey = "stardewcraft.fair.strength.result.level";
            resultArgKey = strengthLevelKey(power);
            play(ModSounds.DWOP.get(), 1.0F, 1.0F);
        }
    }

    private void drawSparkles(GuiGraphics graphics, int barX, int barY) {
        for (int i = 0; i < 8; i++) {
            int x = barX - 16 + random.nextInt(BAR_WIDTH + 32);
            int y = barY - 8 + random.nextInt(28);
            graphics.fill(x, y, x + 2, y + 2, 0xFFFFF08A);
        }
    }

    private void drawResult(GuiGraphics graphics, int panelX, int panelY) {
        int boxX = panelX + 14;
        int boxY = panelY + PANEL_HEIGHT - 68;
        int boxW = PANEL_WIDTH - 28;
        int boxH = 52;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, boxX, boxY, boxW, boxH, 1.0F);
        String text = resultArgKey.isEmpty()
            ? I18n.get(resultKey)
            : I18n.get(resultKey, I18n.get(resultArgKey));
        List<FormattedCharSequence> lines = font.split(Component.literal(text), boxW - 20);
        int y = boxY + 9;
        for (FormattedCharSequence line : lines) {
            if (y + font.lineHeight > boxY + boxH - 6) {
                break;
            }
            graphics.drawString(font, line, boxX + 10, y, 0xFF3F2A13, false);
            y += font.lineHeight + 2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (showedResult) {
            onClose();
            return true;
        }
        if (!clicked) {
            clicked = true;
            stopMeter();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(sound, volume, pitch);
        }
    }

    private static String strengthLevelKey(int power) {
        return switch (power) {
            case 97, 98 -> "stardewcraft.fair.strength.level.mammoth";
            case 93, 94, 95, 96 -> "stardewcraft.fair.strength.level.gorilla";
            case 91, 92 -> "stardewcraft.fair.strength.level.bulldozer";
            case 88, 90 -> "stardewcraft.fair.strength.level.ox";
            case 87, 89 -> "stardewcraft.fair.strength.level.horse";
            case 84, 85, 86 -> "stardewcraft.fair.strength.level.lumberjack";
            case 82, 83 -> "stardewcraft.fair.strength.level.mountain_troll";
            case 80, 81 -> "stardewcraft.fair.strength.level.bodybuilder";
            case 77, 78, 79 -> "stardewcraft.fair.strength.level.iron";
            case 73, 74, 75, 76 -> "stardewcraft.fair.strength.level.tree_trunk";
            case 69, 70, 71, 72 -> "stardewcraft.fair.strength.level.orc";
            case 64, 65, 66, 67, 68 -> "stardewcraft.fair.strength.level.gym_teacher";
            case 60, 61, 62, 63 -> "stardewcraft.fair.strength.level.boulder";
            case 56, 57, 58, 59 -> "stardewcraft.fair.strength.level.angry_hog";
            case 54, 55 -> "stardewcraft.fair.strength.level.small_donkey";
            case 52, 53 -> "stardewcraft.fair.strength.level.sheep_dog";
            case 50, 51 -> "stardewcraft.fair.strength.level.sandstone";
            case 48, 49 -> "stardewcraft.fair.strength.level.scrap_metal";
            case 46, 47 -> "stardewcraft.fair.strength.level.hot_mustard";
            case 44, 45 -> "stardewcraft.fair.strength.level.leather_boot";
            case 42, 43 -> "stardewcraft.fair.strength.level.prairie_dog";
            case 40, 41 -> "stardewcraft.fair.strength.level.hardened_clay";
            case 38, 39 -> "stardewcraft.fair.strength.level.balsa_wood";
            case 36, 37 -> "stardewcraft.fair.strength.level.cardboard";
            case 34, 35 -> "stardewcraft.fair.strength.level.pancake";
            case 32, 33 -> "stardewcraft.fair.strength.level.buttermilk";
            case 30, 31 -> "stardewcraft.fair.strength.level.paper_mache";
            case 28, 29 -> "stardewcraft.fair.strength.level.trout";
            case 26, 27 -> "stardewcraft.fair.strength.level.twig";
            case 24, 25 -> "stardewcraft.fair.strength.level.carrot_stick";
            case 22, 23 -> "stardewcraft.fair.strength.level.mouse";
            case 20, 21 -> "stardewcraft.fair.strength.level.george_knee";
            case 18, 19 -> "stardewcraft.fair.strength.level.baby_duck";
            case 16, 17 -> "stardewcraft.fair.strength.level.soggy_spaghetti";
            case 14, 15 -> "stardewcraft.fair.strength.level.goldfish";
            case 12, 13 -> "stardewcraft.fair.strength.level.shrimp";
            case 10, 11 -> "stardewcraft.fair.strength.level.toothpick";
            case 8, 9 -> "stardewcraft.fair.strength.level.alfredo_sauce";
            case 6, 7 -> "stardewcraft.fair.strength.level.wet_tissue";
            case 4, 5 -> "stardewcraft.fair.strength.level.skim_milk";
            case 2, 3 -> "stardewcraft.fair.strength.level.plankton";
            default -> "stardewcraft.fair.strength.level.plankton";
        };
    }
}
