package com.stardew.craft;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final Client CLIENT = new Client(CLIENT_BUILDER);
    public static final Server SERVER = new Server(SERVER_BUILDER);
    public static final General GENERAL = new General(COMMON_BUILDER);
    public static final Mining MINING = new Mining(COMMON_BUILDER);
    public static final Fishing FISHING = new Fishing(COMMON_BUILDER);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static final ModConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS = CLIENT.ENABLE_WEAPON_SPECIAL_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS = CLIENT.ENABLE_WEAPON_POST_EFFECTS;
    public static final ModConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR = CLIENT.SHOW_MONSTER_HP_BAR;

    public static final ModConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER = SERVER.TIME_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_FISHING_MINIGAME = SERVER.ENABLE_FISHING_MINIGAME;
    public static final ModConfigSpec.BooleanValue ENABLE_UPDATE_CHECKS = SERVER.ENABLE_UPDATE_CHECKS;
    public static final ModConfigSpec.BooleanValue SHOW_COMMUNITY_ANNOUNCEMENT = SERVER.SHOW_COMMUNITY_ANNOUNCEMENT;

    private Config() {
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS;
        public final ModConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS;
        public final ModConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR;
        public final ModConfigSpec.BooleanValue LEGACY_COMMON_IMPORTED;
        public final ModConfigSpec.IntValue HUD_SCALE_PERCENT;
        public final ModConfigSpec.EnumValue<HudHorizontalAnchor> HUD_HORIZONTAL_ANCHOR;
        public final ModConfigSpec.EnumValue<HudVerticalAnchor> HUD_VERTICAL_ANCHOR;
        public final ModConfigSpec.IntValue HUD_OFFSET_X;
        public final ModConfigSpec.IntValue HUD_OFFSET_Y;
        public final Map<HudElement, HudElementSettings> HUD_ELEMENTS = new EnumMap<>(HudElement.class);

        private Client(ModConfigSpec.Builder builder) {
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

            builder.push("migration");
            LEGACY_COMMON_IMPORTED = builder
                    .comment("Internal marker: player-facing values were imported from the legacy common config")
                    .define("legacyCommonImported", false);
            builder.pop();

            builder.push("hud");
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
                ModConfigSpec.IntValue scale = builder
                        .comment("HUD element scale percentage")
                        .defineInRange("scalePercent", element.defaultScalePercent(), 25, 200);
                ModConfigSpec.EnumValue<HudHorizontalAnchor> horizontalAnchor = builder
                        .comment("Horizontal anchor used when the window size changes")
                        .defineEnum("horizontalAnchor", element.defaultHorizontalAnchor());
                ModConfigSpec.EnumValue<HudVerticalAnchor> verticalAnchor = builder
                        .comment("Vertical anchor used when the window size changes")
                        .defineEnum("verticalAnchor", element.defaultVerticalAnchor());
                ModConfigSpec.IntValue offsetX = builder
                        .comment("Horizontal offset from the selected anchor")
                        .defineInRange("offsetX", element.defaultOffsetX(), -9999, 9999);
                ModConfigSpec.IntValue offsetY = builder
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
        public final ModConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER;
        public final ModConfigSpec.BooleanValue ENABLE_FISHING_MINIGAME;
        public final ModConfigSpec.BooleanValue ENABLE_UPDATE_CHECKS;
        public final ModConfigSpec.BooleanValue SHOW_COMMUNITY_ANNOUNCEMENT;
        public final ModConfigSpec.BooleanValue LEGACY_COMMON_IMPORTED;

        private Server(ModConfigSpec.Builder builder) {
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

    public record HudElementSettings(ModConfigSpec.IntValue scalePercent,
                                     ModConfigSpec.EnumValue<HudHorizontalAnchor> horizontalAnchor,
                                     ModConfigSpec.EnumValue<HudVerticalAnchor> verticalAnchor,
                                     ModConfigSpec.IntValue offsetX,
                                     ModConfigSpec.IntValue offsetY) {
    }

    public enum HudElement {
        MAIN("main", 72, 84, 100, HudHorizontalAnchor.RIGHT, HudVerticalAnchor.TOP, 10, 10),
        PLAYER_BARS("playerBars", 278, 18, 100, HudHorizontalAnchor.CENTER, HudVerticalAnchor.BOTTOM, 0, 31),
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
        public final ModConfigSpec.DoubleValue LEGACY_TIME_SPEED_MULTIPLIER;

        private General(ModConfigSpec.Builder builder) {
            builder.push("general");
            LEGACY_TIME_SPEED_MULTIPLIER = builder
                    .comment("Legacy value imported once into each world's server config.")
                    .defineInRange("timeSpeedMultiplier", 1.0D, 0.1D, 100.0D);
            builder.pop();
        }
    }

    public static final class Mining {
        public final ModConfigSpec.BooleanValue LEGACY_SHOW_MONSTER_HP_BAR;

        private Mining(ModConfigSpec.Builder builder) {
            builder.push("mining");
            LEGACY_SHOW_MONSTER_HP_BAR = builder
                    .comment("Legacy value imported once into the client config.")
                    .define("showMonsterHpBar", true);

            builder.pop();
        }
    }

    public static final class Fishing {
        public final ModConfigSpec.BooleanValue LEGACY_ENABLE_MINIGAME;

        private Fishing(ModConfigSpec.Builder builder) {
            builder.push("fishing");
            LEGACY_ENABLE_MINIGAME = builder
                    .comment("Legacy value imported once into each world's server config.")
                    .define("enableMinigame", true);
            builder.pop();
        }
    }
}
