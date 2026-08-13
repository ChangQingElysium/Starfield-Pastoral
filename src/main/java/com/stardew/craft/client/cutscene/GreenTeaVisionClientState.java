package com.stardew.craft.client.cutscene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.cutscene.runtime.EventScreenFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/** Source-faithful client presentation for {@code EventScript_GreenTea}. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class GreenTeaVisionClientState {
    private static final ResourceLocation SPRITES = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/night_market/mermaid/temporary_sprites_1.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 640;
    private static final int SCENE_WIDTH = 480;
    private static final int SCENE_HEIGHT = 270;
    private static final String[] TEXT_KEYS = {
            "event.caroline.719926.vision.1",
            "event.caroline.719926.vision.2",
            "event.caroline.719926.vision.3"
    };
    private static final int[][] STARS = {
            {152, 57}, {161, 91}, {219, 64}, {185, 113}, {263, 118},
            {301, 63}, {297, 100}, {184, 62}, {280, 64}
    };
    private static final int[][] LIGHT_BOTTOM_LEAVES = {
            {124, 213}, {154, 205}, {200, 213}, {244, 209}, {290, 205}, {325, 213}
    };
    private static final int[][] LIGHT_LEFT_LEAVES = {
            {148, 27}, {142, 40}, {148, 70}, {138, 102}, {148, 150}, {135, 186}
    };
    private static final int[][] LIGHT_RIGHT_LEAVES = {
            {332, 67}, {338, 80}, {332, 110}, {342, 142}, {332, 190}, {345, 226}
    };
    private static final int[][] LIGHT_TOP_LEAVES = {
            {164, 62}, {214, 55}, {240, 59}, {274, 55}, {320, 57}, {365, 62}
    };
    private static final int[][] DARK_BOTTOM_LEAVES = {
            {94, 241}, {124, 241}, {153, 235}, {200, 242},
            {244, 237}, {290, 233}, {325, 241}, {350, 241}
    };
    private static final int[][] DARK_LEFT_LEAVES = {
            {108, 0}, {108, 27}, {102, 40}, {108, 70},
            {98, 102}, {108, 150}, {95, 186}, {108, 220}
    };
    private static final int[][] DARK_RIGHT_LEAVES = {
            {373, 57}, {373, 67}, {379, 80}, {373, 110},
            {383, 142}, {373, 190}, {386, 226}, {373, 260}
    };
    private static final int[][] DARK_TOP_LEAVES = {
            {124, 34}, {164, 34}, {214, 27}, {240, 31},
            {274, 26}, {320, 30}, {365, 34}, {394, 34}
    };
    private static final int[][] BRANCHES_BOTTOM = {
            {111, 228}, {159, 214}, {226, 232}, {294, 218}, {358, 221}
    };
    private static final int[][] BRANCHES_LEFT = {
            {128, 156}, {108, 200}, {130, 78}, {117, 33}
    };
    private static final int[][] BRANCHES_TOP = {
            {184, 44}, {228, 42}, {311, 38}, {123, 39}
    };
    private static final int[][] BRANCHES_RIGHT = {
            {353, 101}, {366, 140}, {352, 183}, {352, 50}
    };
    private static final int[][] TRUNKS = {
            {121, 16}, {106, 93}, {361, 153}, {341, 22}, {326, 0}
    };

    private static boolean active;
    private static int phase;
    private static int phaseTick;
    private static int totalTick;

    private GreenTeaVisionClientState() {
    }

    public static void start() {
        EventScreenFade.clear();
        active = true;
        phase = 0;
        phaseTick = 0;
        totalTick = 0;
    }

    public static void tick() {
        if (!active) {
            return;
        }
        totalTick++;
        phaseTick++;

        if (phase == 0 && phaseTick >= 100) {
            nextPhase();
            return;
        }
        if (phase >= 1 && phase <= 3) {
            String text = Component.translatable(TEXT_KEYS[phase - 1]).getString();
            int characters = text.codePointCount(0, text.length());
            if (phaseTick >= characters * 2 + 50) {
                nextPhase();
            }
            return;
        }
        if (phase == 4) {
            tickBuddy();
            if (phaseTick >= 238) {
                nextPhase();
            }
            return;
        }
        if (phase == 5 && phaseTick >= 60) {
            clear();
        }
    }

    private static void tickBuddy() {
        switch (phaseTick) {
            case 1 -> play("pull_item_from_water");
            case 42 -> play("coin");
            case 92 -> play("dwop");
            case 122 -> play("siptea");
            case 142 -> play("gulp");
            case 220 -> play("fireball");
            default -> {
            }
        }
    }

    private static void nextPhase() {
        phase++;
        phaseTick = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static void clear() {
        active = false;
        phase = 0;
        phaseTick = 0;
        totalTick = 0;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!active) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = Math.min(screenWidth / (float) SCENE_WIDTH, screenHeight / (float) SCENE_HEIGHT);
        float left = (screenWidth - SCENE_WIDTH * scale) / 2.0F;
        float top = (screenHeight - SCENE_HEIGHT * scale) / 2.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.fill(0, 0, screenWidth, screenHeight, 0xFF000000);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 1000.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        drawScene(graphics);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void drawScene(GuiGraphics graphics) {
        graphics.fill(52, 2, 428, 268, 0xFF146852);
        for (int i = 0; i < 5; i++) {
            blit(graphics, 52 + i * 71, 135, 386, 472, 71, 16);
        }
        graphics.fill(52, 150, 428, 268, 0xFF374435);
        blit(graphics, 276, 110, 0, 315, 72, 69);
        blit(graphics, 196, 144, 145, 440, 129, 72);
        int cupFrame = (totalTick / 4) % 4;
        blit(graphics, 200, 152, 336 + cupFrame * 44, 493, 44, 19);
        blit(graphics, 215, 170, 278, 482, 19, 30);

        drawStars(graphics);
        drawSteam(graphics);
        drawLeafFrame(graphics);

        if (phase >= 1 && phase <= 3) {
            drawText(graphics);
        }
        if (phase == 4) {
            drawBuddy(graphics);
        }
        if (phase == 5) {
            int alpha = Mth.clamp(Math.round(255.0F * phaseTick / 60.0F), 0, 255);
            graphics.fill(0, 0, SCENE_WIDTH, SCENE_HEIGHT, alpha << 24 | 0x00050304);
        }
    }

    private static void drawStars(GuiGraphics graphics) {
        int frame = (totalTick / 3) % 6;
        for (int[] star : STARS) {
            blit(graphics, star[0], star[1], 408 + frame * 7, 459, 7, 7);
        }
    }

    private static void drawSteam(GuiGraphics graphics) {
        for (int i = 0; i < 4; i++) {
            int age = Math.floorMod(totalTick + i * 17, 56);
            float alpha = 0.28F * (1.0F - age / 56.0F);
            int x = 222 + ((i * 13 + age / 8) % 24) - 12;
            int y = 162 - age / 3;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            blit(graphics, x, y, 472, 450, 16, 14);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawLeafFrame(GuiGraphics graphics) {
        graphics.fill(52, 2, 126, 268, 0xFF0B3827);
        graphics.fill(354, 2, 428, 268, 0xFF0B3827);
        graphics.fill(126, 2, 360, 47, 0xFF0B3827);
        graphics.fill(126, 225, 360, 270, 0xFF0B3827);

        tint(11, 56, 39, 255);
        drawMany(graphics, LIGHT_BOTTOM_LEAVES, 462, 470, 50, 22, 0.0F);
        drawMany(graphics, LIGHT_LEFT_LEAVES, 462, 470, 50, 22, 90.0F);
        drawMany(graphics, LIGHT_RIGHT_LEAVES, 462, 470, 50, 22, 270.0F);
        drawMany(graphics, LIGHT_TOP_LEAVES, 462, 470, 50, 22, 180.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.fill(52, 2, 87, 268, 0xFF050304);
        graphics.fill(395, 2, 428, 268, 0xFF050304);
        graphics.fill(87, 2, 395, 17, 0xFF050304);
        graphics.fill(85, 255, 395, 270, 0xFF050304);

        tint(5, 3, 4, 255);
        drawMany(graphics, DARK_BOTTOM_LEAVES, 462, 470, 50, 22, 0.0F);
        drawMany(graphics, DARK_LEFT_LEAVES, 462, 470, 50, 22, 90.0F);
        drawMany(graphics, DARK_RIGHT_LEAVES, 462, 470, 50, 22, 270.0F);
        drawMany(graphics, DARK_TOP_LEAVES, 462, 470, 50, 22, 180.0F);
        drawMany(graphics, BRANCHES_BOTTOM, 79, 354, 41, 27, 0.0F);
        drawMany(graphics, BRANCHES_LEFT, 79, 354, 41, 27, 90.0F);
        drawMany(graphics, BRANCHES_TOP, 79, 354, 41, 27, 180.0F);
        drawMany(graphics, BRANCHES_RIGHT, 79, 354, 41, 27, 270.0F);
        drawMany(graphics, TRUNKS, 129, 353, 12, 46, 0.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawMany(
            GuiGraphics graphics,
            int[][] positions,
            int u,
            int v,
            int width,
            int height,
            float rotationDegrees
    ) {
        for (int[] position : positions) {
            if (rotationDegrees == 0.0F) {
                blit(graphics, position[0], position[1], u, v, width, height);
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(position[0], position[1], 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
            blit(graphics, 0, 0, u, v, width, height);
            graphics.pose().popPose();
        }
    }

    private static void drawText(GuiGraphics graphics) {
        String full = Component.translatable(TEXT_KEYS[phase - 1]).getString();
        int codePoints = full.codePointCount(0, full.length());
        int visible = Math.min(codePoints, phaseTick / 2);
        int end = full.offsetByCodePoints(0, visible);
        Component shown = Component.literal(full.substring(0, end));
        Font font = StardewFonts.spriteText();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(shown, 250);
        int y = 80 - Math.max(0, lines.size() - 1) * font.lineHeight / 2;
        int color = 0xFFDC8A33;
        for (var line : lines) {
            int x = (SCENE_WIDTH - font.width(line)) / 2;
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
    }

    private static void drawBuddy(GuiGraphics graphics) {
        int frame;
        int y = 152;
        if (phaseTick < 42) {
            frame = 7;
            float progress = phaseTick / 42.0F;
            y = Math.round(Mth.lerp(progress, 170.0F, 152.0F) - Mth.sin(progress * Mth.PI) * 48.0F);
        } else if (phaseTick < 92) {
            frame = (phaseTick / 10) % 2;
        } else if (phaseTick < 122) {
            frame = 5;
            int sparkleFrame = (phaseTick / 3) % 8;
            blit(graphics, 206, 145, sparkleFrame * 16, 384, 16, 16);
        } else if (phaseTick < 142) {
            frame = 6;
        } else if (phaseTick < 172) {
            frame = phaseTick < 152 ? 8 : 9;
        } else if (phaseTick < 220) {
            frame = phaseTick < 205 ? 2 + (phaseTick / 5) % 2 : 4;
        } else {
            for (int i = 0; i < 8; i++) {
                float alpha = Math.max(0.0F, 1.0F - (phaseTick - 220) / 18.0F);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.5F);
                blit(graphics, 205 + (i * 11) % 32, 144 + (i * 7) % 28, 472, 450, 16, 14);
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        int u = frame % 5 * 27;
        int v = 242 + frame / 5 * 32;
        blit(graphics, 213, y, u, v, 27, 32);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(SPRITES, x, y, u, v, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void tint(int red, int green, int blue, int alpha) {
        RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
    }

    private static void play(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 1.0F));
    }
}
