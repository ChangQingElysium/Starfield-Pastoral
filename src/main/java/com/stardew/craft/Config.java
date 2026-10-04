package com.stardew.craft;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class Config {
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

    public static final Client CLIENT = new Client(CLIENT_BUILDER);
    public static final Server SERVER = new Server(SERVER_BUILDER);
    public static final General GENERAL = new General(COMMON_BUILDER);
    public static final Mining MINING = new Mining(COMMON_BUILDER);
    public static final Fishing FISHING = new Fishing(COMMON_BUILDER);

    public static final ForgeConfigSpec COMMON_SPEC = COMMON_BUILDER.build();
    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static final ForgeConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS = CLIENT.ENABLE_WEAPON_SPECIAL_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS = CLIENT.ENABLE_WEAPON_POST_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR = CLIENT.SHOW_MONSTER_HP_BAR;
    public static final ForgeConfigSpec.BooleanValue USE_LEGACY_PLAYER_BARS = CLIENT.USE_LEGACY_PLAYER_BARS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STARDEW_FONTS = CLIENT.ENABLE_STARDEW_FONTS;
    public static final ForgeConfigSpec.BooleanValue USE_CHINESE_SMOOTH_FONT = CLIENT.USE_CHINESE_SMOOTH_FONT;

    public static final ForgeConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER = SERVER.TIME_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FISHING_MINIGAME = SERVER.ENABLE_FISHING_MINIGAME;
    public static final ForgeConfigSpec.BooleanValue ENABLE_UPDATE_CHECKS = SERVER.ENABLE_UPDATE_CHECKS;
    public static final ForgeConfigSpec.BooleanValue SHOW_COMMUNITY_ANNOUNCEMENT = SERVER.SHOW_COMMUNITY_ANNOUNCEMENT;

    private Config() {
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS;
        public final ForgeConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS;
        public final ForgeConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR;
        public final ForgeConfigSpec.BooleanValue USE_LEGACY_PLAYER_BARS;
        public final ForgeConfigSpec.BooleanValue ENABLE_STARDEW_FONTS;
        public final ForgeConfigSpec.BooleanValue USE_CHINESE_SMOOTH_FONT;
        public final ForgeConfigSpec.IntValue READING_TEXT_SCALE_PERCENT;
        public final ForgeConfigSpec.BooleanValue LEGACY_COMMON_IMPORTED;
        public final ForgeConfigSpec.BooleanValue PLAYER_VITALS_LAYOUT_IMPORTED;
        public final ForgeConfigSpec.IntValue HUD_SCALE_PERCENT;
        public final ForgeConfigSpec.EnumValue<HudHorizontalAnchor> HUD_HORIZONTAL_ANCHOR;
        public final ForgeConfigSpec.EnumValue<HudVerticalAnchor> HUD_VERTICAL_ANCHOR;
        public final ForgeConfigSpec.IntValue HUD_OFFSET_X;
        public final ForgeConfigSpec.IntValue HUD_OFFSET_Y;
        public final Map<HudElement, HudElementSettings> HUD_ELEMENTS = new EnumMap<>(HudElement.class);

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("client");
            ENABLE_WEAPON_SPECIAL_EFFECTS = builder
                    .comment("Enable weapon special effects (rings, rifts, meteors, cores)")
                    .translation("config.stardewcraft.client.weapon_special_effects")
                    .define("weaponSpecialEffects", true);

            ENABLE_WEAPON_POST_EFFECTS = builder
                    .comment("Enable weapon post-processing effects (reserved for future shaders)")
                    .translation("config.stardewcraft.client.weapon_post_effects")
                    .define("weaponPostEffects", true);

            SHOW_MONSTER_HP_BAR = builder
                    .comment("Show monster name and HP bar above their heads in the mine")
                    .translation("config.stardewcraft.client.show_monster_hp_bar")
                    .define("showMonsterHpBar", true);

            ENABLE_STARDEW_FONTS = builder
                    .comment("Use Stardew Valley fonts in StardewCraft interfaces and overlays.")
                    .translation("config.stardewcraft.client.enable_stardew_fonts")
                    .define("enableStardewFonts", true);

            USE_CHINESE_SMOOTH_FONT = builder
                    .comment("Use Stardew Valley's optional rounded Chinese font.",
                            "A resource reload or game restart is required after changing this value.")
                    .define("useChineseSmoothFont", false);

            READING_TEXT_SCALE_PERCENT = builder
                    .comment("Reading size of all StardewCraft screens and HUD elements. Text and its container scale together; oversized pages remain scrollable. Applies to every language without a resource reload.")
                    .translation("config.stardewcraft.client.reading_text_scale")
                    .defineInRange("readingTextScalePercent", 100, 75, 200);

            builder.push("migration");
            LEGACY_COMMON_IMPORTED = builder
                    .comment("Internal marker: player-facing values were imported from the legacy common config")
                    .define("legacyCommonImported", false);
            PLAYER_VITALS_LAYOUT_IMPORTED = builder
                    .comment("Internal marker: the old default player bars were moved to the native icon row")
                    .define("playerVitalsLayoutImported", false);
            builder.pop();

            builder.push("hud");
            USE_LEGACY_PLAYER_BARS = builder
                    .comment("Use the original continuous health and energy bars instead of the ten-icon HUD. Applies immediately and only to this client.")
                    .translation("config.stardewcraft.client.use_legacy_player_bars")
                    .define("useLegacyPlayerBars", false);
            HUD_SCALE_PERCENT = builder
                    .comment("Scale of the Stardew time, date, money, and quest HUD")
                    .translation("config.stardewcraft.client.hud_scale")
                    .defineInRange("scalePercent", 100, 25, 200);
            HUD_HORIZONTAL_ANCHOR = builder
                    .comment("Horizontal anchor used when the window size changes")
                    .defineEnum("horizontalAnchor", HudHorizontalAnchor.RIGHT);
            HUD_VERTICAL_ANCHOR = builder
                    .comment("Vertical anchor used when the window size changes")
                    .defineEnum("verticalAnchor", HudVerticalAnchor.TOP);
            HUD_OFFSET_X = builder
                    .comment("Horizontal offset from the selected HUD anchor")
                    .defineInRange("offsetX", 10, -9999, 9999);
            HUD_OFFSET_Y = builder
                    .comment("Vertical offset from the selected HUD anchor")
                    .defineInRange("offsetY", 10, -9999, 9999);
            HUD_ELEMENTS.put(HudElement.MAIN,
                    new HudElementSettings(HUD_SCALE_PERCENT, HUD_HORIZONTAL_ANCHOR,
                            HUD_VERTICAL_ANCHOR, HUD_OFFSET_X, HUD_OFFSET_Y));

            for (HudElement element : HudElement.values()) {
                if (element == HudElement.MAIN) {
                    continue;
                }
                builder.push(element.configKey());
                ForgeConfigSpec.IntValue scale = builder
                        .comment("HUD element scale percentage")
                        .defineInRange("scalePercent", element.defaultScalePercent(), 25, 200);
                ForgeConfigSpec.EnumValue<HudHorizontalAnchor> horizontalAnchor = builder
                        .comment("Horizontal anchor used when the window size changes")
                        .defineEnum("horizontalAnchor", element.defaultHorizontalAnchor());
                ForgeConfigSpec.EnumValue<HudVerticalAnchor> verticalAnchor = builder
                        .comment("Vertical anchor used when the window size changes")
                        .defineEnum("verticalAnchor", element.defaultVerticalAnchor());
                ForgeConfigSpec.IntValue offsetX = builder
                        .comment("Horizontal offset from the selected anchor")
                        .defineInRange("offsetX", element.defaultOffsetX(), -9999, 9999);
                ForgeConfigSpec.IntValue offsetY = builder
                        .comment("Vertical offset from the selected anchor")
                        .defineInRange("offsetY", element.defaultOffsetY(), -9999, 9999);
                HUD_ELEMENTS.put(element,
                        new HudElementSettings(scale, horizontalAnchor, verticalAnchor, offsetX, offsetY));
                builder.pop();
            }
            builder.pop();
            builder.pop();
        }
    }

    /** Settings owned by the logical server and stored with each world. */
    public static final class Server {
        public final ForgeConfigSpec.IntValue GROUND_STONE_TICKS_PER_SWING;
        public final ForgeConfigSpec.IntValue GROUND_STONE_MIN_TICKS;
        public final ForgeConfigSpec.DoubleValue GROUND_STONE_ENERGY_PER_SWING;
        public final ForgeConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue ENABLE_FISHING_MINIGAME;
        public final ForgeConfigSpec.BooleanValue ENABLE_UPDATE_CHECKS;
        public final ForgeConfigSpec.BooleanValue SHOW_COMMUNITY_ANNOUNCEMENT;
        public final ForgeConfigSpec.BooleanValue LEGACY_COMMON_IMPORTED;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("groundStoneMining");
            GROUND_STONE_TICKS_PER_SWING = builder.comment("Continuous mining ticks per required source swing. See docs/mine-node-mining.md.")
                    .translation("config.stardewcraft.server.ground_stone_ticks")
                    .defineInRange("ticksPerSwing", 12, 1, 1200);
            GROUND_STONE_MIN_TICKS = builder.comment("Minimum continuous breaking duration, including upgraded pickaxes.")
                    .translation("config.stardewcraft.server.ground_stone_min_ticks")
                    .defineInRange("minimumTicks", 6, 1, 1200);
            GROUND_STONE_ENERGY_PER_SWING = builder.comment("Energy per required source swing at mining level zero. Subtract 0.1 per mining level; pay once on completion.")
                    .translation("config.stardewcraft.server.ground_stone_energy")
                    .defineInRange("energyPerSwing", 2.0D, 0.0D, 100.0D);
            builder.pop();
            builder.push("gameplay");
            TIME_SPEED_MULTIPLIER = builder
                    .comment("Stardew Valley clock speed multiplier.",
                            "1.0 is the normal speed; fractional values are accumulated exactly.")
                    .translation("config.stardewcraft.server.time_speed_multiplier")
                    .defineInRange("timeSpeedMultiplier", 1.0D, 0.1D, 100.0D);
            ENABLE_FISHING_MINIGAME = builder
                    .comment("Enable the fishing minigame for fish catches.",
                            "If disabled, fish are caught immediately after biting; non-fish catchables remain instant catches.")
                    .translation("config.stardewcraft.server.enable_fishing_minigame")
                    .define("enableFishingMinigame", true);
            builder.pop();

            builder.push("communications");
            ENABLE_UPDATE_CHECKS = builder
                    .comment("Check Modrinth asynchronously for newer Starfield Pastoral versions.")
                    .translation("config.stardewcraft.server.enable_update_checks")
                    .define("enableUpdateChecks", true);
            SHOW_COMMUNITY_ANNOUNCEMENT = builder
                    .comment("Show the community links announcement until each player dismisses it.",
                            "Outdated-version warnings remain visible when update checks are enabled.")
                    .translation("config.stardewcraft.server.show_community_announcement")
                    .define("showCommunityAnnouncement", true);
            builder.pop();

            builder.push("migration");
            LEGACY_COMMON_IMPORTED = builder
                    .comment("Internal marker: gameplay values were imported from the legacy common config")
                    .define("legacyCommonImported", false);
            builder.pop();
        }
    }

    public record HudElementSettings(ForgeConfigSpec.IntValue scalePercent,
                                     ForgeConfigSpec.EnumValue<HudHorizontalAnchor> horizontalAnchor,
                                     ForgeConfigSpec.EnumValue<HudVerticalAnchor> verticalAnchor,
                                     ForgeConfigSpec.IntValue offsetX,
                                     ForgeConfigSpec.IntValue offsetY) {
    }

    public enum HudElement {
        MAIN("main", 72, 90, 100, HudHorizontalAnchor.RIGHT, HudVerticalAnchor.TOP, 10, 10),
        PLAYER_BARS("playerBars", 223, 14, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.BOTTOM, 0, 28),
        MINING_FLOOR("miningFloor", 32, 32, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.BOTTOM, -143, 1),
        FESTIVAL_SCORE("festivalScore", 220, 48, 100, HudHorizontalAnchor.LEFT, HudVerticalAnchor.TOP, 16, 32),
        FESTIVAL_CURRENCY("festivalCurrency", 96, 32, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.BOTTOM, -159, 37),
        // Keep the historical itemPickup config path so existing client layouts migrate
        // transparently, but use one placement for every lower-left popup notification.
        NOTIFICATIONS("itemPickup", 220, 45, 70, HudHorizontalAnchor.LEFT, HudVerticalAnchor.BOTTOM, 10, 48),
        SKILL_XP("skillXp", 100, 28, 100, HudHorizontalAnchor.LEFT, HudVerticalAnchor.BOTTOM, 10, 10),
        SKILL_LEVEL_UP("skillLevelUp", 180, 40, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.TOP, 0, 20),
        INTERACTION_HINT("interactionHint", 280, 32, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.CENTER, 0, 30),
        WEAPON_SKILLS("weaponSkills", 44, 90, 100, HudHorizontalAnchor.LEFT, HudVerticalAnchor.CENTER, 0, 0),
        TOOL_HINT("toolHint", 220, 32, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.BOTTOM, 0, 72),
        FISHING_CAST("fishingCast", 188, 48, 30, HudHorizontalAnchor.CENTER, HudVerticalAnchor.CENTER, 0, 22);

        private final String configKey;
        private final int baseWidth;
        private final int baseHeight;
        private final int defaultScalePercent;
        private final HudHorizontalAnchor defaultHorizontalAnchor;
        private final HudVerticalAnchor defaultVerticalAnchor;
        private final int defaultOffsetX;
        private final int defaultOffsetY;

        HudElement(String configKey, int baseWidth, int baseHeight, int defaultScalePercent,
                   HudHorizontalAnchor defaultHorizontalAnchor, HudVerticalAnchor defaultVerticalAnchor,
                   int defaultOffsetX, int defaultOffsetY) {
            this.configKey = configKey;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.defaultScalePercent = defaultScalePercent;
            this.defaultHorizontalAnchor = defaultHorizontalAnchor;
            this.defaultVerticalAnchor = defaultVerticalAnchor;
            this.defaultOffsetX = defaultOffsetX;
            this.defaultOffsetY = defaultOffsetY;
        }

        public String configKey() { return configKey; }
        public int baseWidth() { return baseWidth; }
        public int baseHeight() { return baseHeight; }
        public int defaultScalePercent() { return defaultScalePercent; }
        public HudHorizontalAnchor defaultHorizontalAnchor() { return defaultHorizontalAnchor; }
        public HudVerticalAnchor defaultVerticalAnchor() { return defaultVerticalAnchor; }
        public int defaultOffsetX() { return defaultOffsetX; }
        public int defaultOffsetY() { return defaultOffsetY; }
    }

    public enum HudHorizontalAnchor {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum HudVerticalAnchor {
        TOP,
        CENTER,
        BOTTOM
    }

    public static final class General {
        public final ForgeConfigSpec.DoubleValue LEGACY_TIME_SPEED_MULTIPLIER;

        private General(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            LEGACY_TIME_SPEED_MULTIPLIER = builder
                    .comment("Legacy value imported once into each world's server config.")
                    .defineInRange("timeSpeedMultiplier", 1.0D, 0.1D, 100.0D);
            builder.pop();
        }
    }

    public static final class Mining {
        public final ForgeConfigSpec.BooleanValue LEGACY_SHOW_MONSTER_HP_BAR;

        private Mining(ForgeConfigSpec.Builder builder) {
            builder.push("mining");
            LEGACY_SHOW_MONSTER_HP_BAR = builder
                    .comment("Legacy value imported once into the client config.")
                    .define("showMonsterHpBar", true);

            builder.pop();
        }
    }

    public static final class Fishing {
        public final ForgeConfigSpec.BooleanValue LEGACY_ENABLE_MINIGAME;

        private Fishing(ForgeConfigSpec.Builder builder) {
            builder.push("fishing");
            LEGACY_ENABLE_MINIGAME = builder
                    .comment("Legacy value imported once into each world's server config.")
                    .define("enableMinigame", true);
            builder.pop();
        }
    }
}
