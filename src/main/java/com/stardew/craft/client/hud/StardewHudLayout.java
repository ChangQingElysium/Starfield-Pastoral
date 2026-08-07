package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Responsive, persisted layout shared by every movable Stardew HUD element. */
public final class StardewHudLayout {
    public static final int TIME_BG_WIDTH = 71;
    public static final int TIME_BG_HEIGHT = 43;
    public static final int GROUP_WIDTH = 72;
    public static final int GROUP_HEIGHT = 104;

    public static final int MIN_SCALE_PERCENT = 25;
    public static final int MAX_SCALE_PERCENT = 200;
    public static final int DEFAULT_SCALE_PERCENT = 100;
    public static final int DEFAULT_MARGIN = 10;

    private StardewHudLayout() {
    }

    public static Placement current(int screenWidth, int screenHeight) {
        return current(Config.HudElement.MAIN, screenWidth, screenHeight);
    }

    public static Placement current(Config.HudElement element, int screenWidth, int screenHeight) {
        return current(element, screenWidth, screenHeight, element.baseWidth(), element.baseHeight());
    }

    public static Placement current(Config.HudElement element, int screenWidth, int screenHeight,
                                    int baseWidth, int baseHeight) {
        Config.HudElementSettings settings = settings(element);
        int configuredPercent = Mth.clamp(settings.scalePercent().get(), MIN_SCALE_PERCENT, MAX_SCALE_PERCENT);
        return calculateAtScale(
                screenWidth,
                screenHeight,
                baseWidth,
                baseHeight,
                configuredPercent / 100.0F * visualScaleFactor(element),
                settings.horizontalAnchor().get(),
                settings.verticalAnchor().get(),
                settings.offsetX().get(),
                settings.offsetY().get()
        );
    }

    /** SDV HUD sprites are authored for pixelZoom=4; convert that framebuffer scale into MC GUI units. */
    public static float visualScaleFactor(Config.HudElement element) {
        if (element != Config.HudElement.MAIN) {
            return 1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft == null || minecraft.getWindow() == null
                ? 1.0D
                : minecraft.getWindow().getGuiScale();
        return 4.0F / (float) Math.max(1.0D, guiScale);
    }

    static Placement calculate(int screenWidth, int screenHeight, int scalePercent,
                               Config.HudHorizontalAnchor horizontalAnchor,
                               Config.HudVerticalAnchor verticalAnchor,
                               int offsetX, int offsetY) {
        return calculate(screenWidth, screenHeight, GROUP_WIDTH, GROUP_HEIGHT, scalePercent,
                horizontalAnchor, verticalAnchor, offsetX, offsetY);
    }

    static Placement calculate(int screenWidth, int screenHeight, int baseWidth, int baseHeight, int scalePercent,
                               Config.HudHorizontalAnchor horizontalAnchor,
                               Config.HudVerticalAnchor verticalAnchor,
                               int offsetX, int offsetY) {
        int safeScale = Mth.clamp(scalePercent, MIN_SCALE_PERCENT, MAX_SCALE_PERCENT);
        float scale = safeScale / 100.0F;
        return calculateAtScale(screenWidth, screenHeight, baseWidth, baseHeight, scale,
                horizontalAnchor, verticalAnchor, offsetX, offsetY);
    }

    private static Placement calculateAtScale(int screenWidth, int screenHeight, int baseWidth, int baseHeight,
                                              float scale,
                                              Config.HudHorizontalAnchor horizontalAnchor,
                                              Config.HudVerticalAnchor verticalAnchor,
                                              int offsetX, int offsetY) {
        int width = Math.max(1, Math.round(baseWidth * scale));
        int height = Math.max(1, Math.round(baseHeight * scale));

        int x = switch (horizontalAnchor) {
            case LEFT -> offsetX;
            case CENTER -> screenWidth / 2 - width / 2 + offsetX;
            case RIGHT -> screenWidth - width - offsetX;
        };
        int y = switch (verticalAnchor) {
            case TOP -> offsetY;
            case CENTER -> screenHeight / 2 - height / 2 + offsetY;
            case BOTTOM -> screenHeight - height - offsetY;
        };

        x = Mth.clamp(x, 0, Math.max(0, screenWidth - width));
        y = Mth.clamp(y, 0, Math.max(0, screenHeight - height));
        return new Placement(x, y, width, height, scale);
    }

    public static int scalePercent() {
        return scalePercent(Config.HudElement.MAIN);
    }

    public static int scalePercent(Config.HudElement element) {
        return settings(element).scalePercent().get();
    }

    public static void setScalePercent(int scalePercent) {
        setScalePercent(Config.HudElement.MAIN, scalePercent);
    }

    public static void setScalePercent(Config.HudElement element, int scalePercent) {
        settings(element).scalePercent().set(Mth.clamp(scalePercent, MIN_SCALE_PERCENT, MAX_SCALE_PERCENT));
        save();
    }

    public static void saveDraggedPosition(int screenWidth, int screenHeight, int x, int y,
                                           int width, int height) {
        saveDraggedPosition(Config.HudElement.MAIN, screenWidth, screenHeight, x, y, width, height);
    }

    public static void saveDraggedPosition(Config.HudElement element, int screenWidth, int screenHeight, int x, int y,
                                           int width, int height) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        Config.HudHorizontalAnchor horizontalAnchor;
        int offsetX;
        if (centerX <= screenWidth / 3) {
            horizontalAnchor = Config.HudHorizontalAnchor.LEFT;
            offsetX = x;
        } else if (centerX >= screenWidth * 2 / 3) {
            horizontalAnchor = Config.HudHorizontalAnchor.RIGHT;
            offsetX = screenWidth - x - width;
        } else {
            horizontalAnchor = Config.HudHorizontalAnchor.CENTER;
            offsetX = x - (screenWidth / 2 - width / 2);
        }

        Config.HudVerticalAnchor verticalAnchor;
        int offsetY;
        if (centerY <= screenHeight / 3) {
            verticalAnchor = Config.HudVerticalAnchor.TOP;
            offsetY = y;
        } else if (centerY >= screenHeight * 2 / 3) {
            verticalAnchor = Config.HudVerticalAnchor.BOTTOM;
            offsetY = screenHeight - y - height;
        } else {
            verticalAnchor = Config.HudVerticalAnchor.CENTER;
            offsetY = y - (screenHeight / 2 - height / 2);
        }

        Config.HudElementSettings settings = settings(element);
        settings.horizontalAnchor().set(horizontalAnchor);
        settings.verticalAnchor().set(verticalAnchor);
        settings.offsetX().set(offsetX);
        settings.offsetY().set(offsetY);
        save();
    }

    public static void reset() {
        for (Config.HudElement element : Config.HudElement.values()) {
            reset(element, false);
        }
        save();
    }

    public static void reset(Config.HudElement element) {
        reset(element, true);
    }

    private static void reset(Config.HudElement element, boolean persist) {
        Config.HudElementSettings settings = settings(element);
        settings.scalePercent().set(element.defaultScalePercent());
        settings.horizontalAnchor().set(element.defaultHorizontalAnchor());
        settings.verticalAnchor().set(element.defaultVerticalAnchor());
        settings.offsetX().set(element.defaultOffsetX());
        settings.offsetY().set(element.defaultOffsetY());
        if (persist) {
            save();
        }
    }

    private static Config.HudElementSettings settings(Config.HudElement element) {
        Config.HudElementSettings settings = Config.CLIENT.HUD_ELEMENTS.get(element);
        if (settings == null) {
            throw new IllegalArgumentException("Missing HUD config for " + element);
        }
        return settings;
    }

    public static void save() {
        Config.CLIENT_SPEC.save();
    }

    public record Placement(int x, int y, int width, int height, float scale) {
    }
}
