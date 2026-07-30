package com.stardew.craft;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final Client CLIENT = new Client(CLIENT_BUILDER);
    public static final General GENERAL = new General(COMMON_BUILDER);
    public static final TotemPole TOTEM_POLE = new TotemPole(COMMON_BUILDER);
    public static final BuildingManager COOP_MANAGER = new BuildingManager(COMMON_BUILDER, "coopManager", "coop_manager",
            new TierDefaults(4, 0, 1, 0, 216),
            new TierDefaults(8, 0, 1, 1, 288),
            new TierDefaults(0, 12, 1, 1, 360));
    public static final BuildingManager BARN_MANAGER = new BuildingManager(COMMON_BUILDER, "barnManager", "barn_manager",
            new TierDefaults(4, 0, 1, 0, 252),
            new TierDefaults(8, 0, 1, 0, 336),
            new TierDefaults(0, 12, 1, 0, 420));
    public static final Mining MINING = new Mining(COMMON_BUILDER);
    public static final Fishing FISHING = new Fishing(COMMON_BUILDER);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    static final ModConfigSpec SPEC = COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS = CLIENT.ENABLE_WEAPON_SPECIAL_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS = CLIENT.ENABLE_WEAPON_POST_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_UI_INFO_SUITE = CLIENT.ENABLE_UI_INFO_SUITE;

    public static final ModConfigSpec.IntValue MAX_STACK_SIZE = GENERAL.MAX_STACK_SIZE;
    public static final ModConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER = GENERAL.TIME_SPEED_MULTIPLIER;

    public static final ModConfigSpec.BooleanValue TOTEM_POLE_ENFORCE_PLACEMENT_RULES = TOTEM_POLE.ENFORCE_PLACEMENT_RULES;

    public static final ModConfigSpec.IntValue COOP_SCAN_RANGE_XZ = COOP_MANAGER.SCAN_RANGE_XZ;
    public static final ModConfigSpec.IntValue COOP_SCAN_RANGE_UP = COOP_MANAGER.SCAN_RANGE_UP;
    public static final ModConfigSpec.IntValue COOP_SCAN_RANGE_DOWN = COOP_MANAGER.SCAN_RANGE_DOWN;
    public static final ModConfigSpec.BooleanValue COOP_REQUIRE_ENCLOSED = COOP_MANAGER.REQUIRE_ENCLOSED;
    public static final ModConfigSpec.BooleanValue COOP_REQUIRE_DOOR = COOP_MANAGER.REQUIRE_DOOR;
    public static final ModConfigSpec.IntValue COOP_MIN_DOOR_COUNT = COOP_MANAGER.MIN_DOOR_COUNT;
    public static final ModConfigSpec.IntValue COOP_T1_FEED_TROUGH = COOP_MANAGER.TIER1.FEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T1_AUTOFEED_TROUGH = COOP_MANAGER.TIER1.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T1_HAY_HOPPER = COOP_MANAGER.TIER1.HAY_HOPPER;
    public static final ModConfigSpec.IntValue COOP_T1_INCUBATOR = COOP_MANAGER.TIER1.INCUBATOR;
    public static final ModConfigSpec.IntValue COOP_T1_MIN_INTERIOR_BLOCKS = COOP_MANAGER.TIER1.MIN_INTERIOR_BLOCKS;
    public static final ModConfigSpec.IntValue COOP_T2_FEED_TROUGH = COOP_MANAGER.TIER2.FEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T2_AUTOFEED_TROUGH = COOP_MANAGER.TIER2.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T2_HAY_HOPPER = COOP_MANAGER.TIER2.HAY_HOPPER;
    public static final ModConfigSpec.IntValue COOP_T2_INCUBATOR = COOP_MANAGER.TIER2.INCUBATOR;
    public static final ModConfigSpec.IntValue COOP_T2_MIN_INTERIOR_BLOCKS = COOP_MANAGER.TIER2.MIN_INTERIOR_BLOCKS;
    public static final ModConfigSpec.IntValue COOP_T3_FEED_TROUGH = COOP_MANAGER.TIER3.FEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T3_AUTOFEED_TROUGH = COOP_MANAGER.TIER3.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue COOP_T3_HAY_HOPPER = COOP_MANAGER.TIER3.HAY_HOPPER;
    public static final ModConfigSpec.IntValue COOP_T3_INCUBATOR = COOP_MANAGER.TIER3.INCUBATOR;
    public static final ModConfigSpec.IntValue COOP_T3_MIN_INTERIOR_BLOCKS = COOP_MANAGER.TIER3.MIN_INTERIOR_BLOCKS;

    public static final ModConfigSpec.IntValue BARN_SCAN_RANGE_XZ = BARN_MANAGER.SCAN_RANGE_XZ;
    public static final ModConfigSpec.IntValue BARN_SCAN_RANGE_UP = BARN_MANAGER.SCAN_RANGE_UP;
    public static final ModConfigSpec.IntValue BARN_SCAN_RANGE_DOWN = BARN_MANAGER.SCAN_RANGE_DOWN;
    public static final ModConfigSpec.BooleanValue BARN_REQUIRE_ENCLOSED = BARN_MANAGER.REQUIRE_ENCLOSED;
    public static final ModConfigSpec.BooleanValue BARN_REQUIRE_DOOR = BARN_MANAGER.REQUIRE_DOOR;
    public static final ModConfigSpec.IntValue BARN_MIN_DOOR_COUNT = BARN_MANAGER.MIN_DOOR_COUNT;
    public static final ModConfigSpec.IntValue BARN_T1_FEED_TROUGH = BARN_MANAGER.TIER1.FEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T1_AUTOFEED_TROUGH = BARN_MANAGER.TIER1.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T1_HAY_HOPPER = BARN_MANAGER.TIER1.HAY_HOPPER;
    public static final ModConfigSpec.IntValue BARN_T1_INCUBATOR = BARN_MANAGER.TIER1.INCUBATOR;
    public static final ModConfigSpec.IntValue BARN_T1_MIN_INTERIOR_BLOCKS = BARN_MANAGER.TIER1.MIN_INTERIOR_BLOCKS;
    public static final ModConfigSpec.IntValue BARN_T2_FEED_TROUGH = BARN_MANAGER.TIER2.FEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T2_AUTOFEED_TROUGH = BARN_MANAGER.TIER2.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T2_HAY_HOPPER = BARN_MANAGER.TIER2.HAY_HOPPER;
    public static final ModConfigSpec.IntValue BARN_T2_INCUBATOR = BARN_MANAGER.TIER2.INCUBATOR;
    public static final ModConfigSpec.IntValue BARN_T2_MIN_INTERIOR_BLOCKS = BARN_MANAGER.TIER2.MIN_INTERIOR_BLOCKS;
    public static final ModConfigSpec.IntValue BARN_T3_FEED_TROUGH = BARN_MANAGER.TIER3.FEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T3_AUTOFEED_TROUGH = BARN_MANAGER.TIER3.AUTOFEED_TROUGH;
    public static final ModConfigSpec.IntValue BARN_T3_HAY_HOPPER = BARN_MANAGER.TIER3.HAY_HOPPER;
    public static final ModConfigSpec.IntValue BARN_T3_INCUBATOR = BARN_MANAGER.TIER3.INCUBATOR;
    public static final ModConfigSpec.IntValue BARN_T3_MIN_INTERIOR_BLOCKS = BARN_MANAGER.TIER3.MIN_INTERIOR_BLOCKS;

    public static final ModConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR = MINING.SHOW_MONSTER_HP_BAR;
    public static final ModConfigSpec.DoubleValue MINE_LADDER_BASE_CHANCE = MINING.LADDER_BASE_CHANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_FISHING_MINIGAME = FISHING.ENABLE_MINIGAME;

    private Config() {
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue ENABLE_WEAPON_SPECIAL_EFFECTS;
        public final ModConfigSpec.BooleanValue ENABLE_WEAPON_POST_EFFECTS;
        public final ModConfigSpec.BooleanValue ENABLE_UI_INFO_SUITE;
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

            builder.push("uiInfoSuite");
            ENABLE_UI_INFO_SUITE = builder
                    .comment("Enable UI Info Suite features (Experience bars, tooltips, luck, NPC locations, etc.)")
                    .translation("config.stardewcraft.client.ui_info_suite")
                    .define("enabled", true);
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
        public final ModConfigSpec.IntValue MAX_STACK_SIZE;
        public final ModConfigSpec.DoubleValue TIME_SPEED_MULTIPLIER;

        private General(ModConfigSpec.Builder builder) {
            builder.push("general");
            MAX_STACK_SIZE = builder
                    .comment("Maximum stack size for stackable items.",
                            "Set to 64 to use vanilla behavior, or up to 999 for Stardew Valley parity.",
                            "Requires restart to take full effect.")
                    .translation("config.stardewcraft.general.max_stack_size")
                    .defineInRange("maxStackSize", 999, 64, 999);
            TIME_SPEED_MULTIPLIER = builder
                    .comment("Stardew Valley clock speed multiplier.",
                            "1.0 is the normal speed; fractional values are accumulated exactly.")
                    .translation("config.stardewcraft.general.time_speed_multiplier")
                    .defineInRange("timeSpeedMultiplier", 1.0D, 0.1D, 100.0D);
            builder.pop();
        }
    }

    public static final class TotemPole {
        public final ModConfigSpec.BooleanValue ENFORCE_PLACEMENT_RULES;

        private TotemPole(ModConfigSpec.Builder builder) {
            builder.push("totemPole");
            ENFORCE_PLACEMENT_RULES = builder
                    .comment("Restrict totem poles to Stardew Valley and their configured placement areas")
                    .translation("config.stardewcraft.totem_pole.enforce_placement_rules")
                    .define("enforcePlacementRules", true);
            builder.pop();
        }
    }

    public static final class BuildingManager {
        public final ModConfigSpec.IntValue SCAN_RANGE_XZ;
        public final ModConfigSpec.IntValue SCAN_RANGE_UP;
        public final ModConfigSpec.IntValue SCAN_RANGE_DOWN;
        public final ModConfigSpec.BooleanValue REQUIRE_ENCLOSED;
        public final ModConfigSpec.BooleanValue REQUIRE_DOOR;
        public final ModConfigSpec.IntValue MIN_DOOR_COUNT;
        public final Tier TIER1;
        public final Tier TIER2;
        public final Tier TIER3;

        private BuildingManager(ModConfigSpec.Builder builder, String path, String translationSection,
                                TierDefaults tier1, TierDefaults tier2, TierDefaults tier3) {
            builder.push(path);
            String prefix = "config.stardewcraft." + translationSection;

            SCAN_RANGE_XZ = builder
                    .comment("Horizontal scan range for building manager validation")
                    .translation(prefix + ".scan_range_xz")
                    .defineInRange("scanRangeXZ", 12, 4, 64);

            SCAN_RANGE_UP = builder
                    .comment("Vertical scan range above building manager")
                    .translation(prefix + ".scan_range_up")
                    .defineInRange("scanRangeUp", 12, 1, 32);

            SCAN_RANGE_DOWN = builder
                    .comment("Vertical scan range below building manager")
                    .translation(prefix + ".scan_range_down")
                    .defineInRange("scanRangeDown", 12, 0, 32);

            REQUIRE_ENCLOSED = builder
                    .comment("Whether building shell must be enclosed (walls + roof + floor)")
                    .translation(prefix + ".require_enclosed")
                    .define("requireEnclosed", true);

            REQUIRE_DOOR = builder
                    .comment("Whether building shell must contain door/fence gate on boundary")
                    .translation(prefix + ".require_door")
                    .define("requireDoor", true);

            MIN_DOOR_COUNT = builder
                    .comment("Minimum number of doors/fence gates required on building boundary")
                    .translation(prefix + ".min_door_count")
                    .defineInRange("minDoorCount", 1, 0, 8);

            TIER1 = new Tier(builder, prefix, "tier1", tier1);
            TIER2 = new Tier(builder, prefix, "tier2", tier2);
            TIER3 = new Tier(builder, prefix, "tier3", tier3);
            builder.pop();
        }
    }

    public static final class Tier {
        public final ModConfigSpec.IntValue FEED_TROUGH;
        public final ModConfigSpec.IntValue AUTOFEED_TROUGH;
        public final ModConfigSpec.IntValue HAY_HOPPER;
        public final ModConfigSpec.IntValue INCUBATOR;
        public final ModConfigSpec.IntValue MIN_INTERIOR_BLOCKS;

        private Tier(ModConfigSpec.Builder builder, String prefix, String path, TierDefaults defaults) {
            builder.push(path);
            FEED_TROUGH = builder
                    .translation(prefix + "." + path + ".feed_trough")
                    .defineInRange("feedTrough", defaults.feedTrough(), 0, 64);
            AUTOFEED_TROUGH = builder
                    .translation(prefix + "." + path + ".autofeed_trough")
                    .defineInRange("autofeedTrough", defaults.autofeedTrough(), 0, 64);
            HAY_HOPPER = builder
                    .translation(prefix + "." + path + ".hay_hopper")
                    .defineInRange("hayHopper", defaults.hayHopper(), 0, 16);
            INCUBATOR = builder
                    .translation(prefix + "." + path + ".incubator")
                    .defineInRange("incubator", defaults.incubator(), 0, 16);
            MIN_INTERIOR_BLOCKS = builder
                    .translation(prefix + "." + path + ".min_interior_blocks")
                    .defineInRange("minInteriorBlocks", defaults.minInteriorBlocks(), 1, 4096);
            builder.pop();
        }
    }

    private record TierDefaults(int feedTrough, int autofeedTrough, int hayHopper, int incubator,
                                int minInteriorBlocks) {
    }

    public static final class Mining {
        public final ModConfigSpec.BooleanValue SHOW_MONSTER_HP_BAR;
        public final ModConfigSpec.DoubleValue LADDER_BASE_CHANCE;

        private Mining(ModConfigSpec.Builder builder) {
            builder.push("mining");
            SHOW_MONSTER_HP_BAR = builder
                    .comment("Show monster name and HP bar above their heads in the mine")
                    .translation("config.stardewcraft.mining.show_monster_hp_bar")
                    .define("showMonsterHpBar", true);

            LADDER_BASE_CHANCE = builder
                    .comment("Base chance for a mine ladder to appear after breaking a countable mine stone.",
                            "The final chance also includes stones-left, luck, enemy-clear, and buff modifiers.",
                            "Value is a decimal chance: 0.012 means 1.2%.")
                    .translation("config.stardewcraft.mining.ladder_base_chance")
                    .defineInRange("ladderBaseChance", 0.012D, 0.0D, 1.0D);
            builder.pop();
        }
    }

    public static final class Fishing {
        public final ModConfigSpec.BooleanValue ENABLE_MINIGAME;

        private Fishing(ModConfigSpec.Builder builder) {
            builder.push("fishing");
            ENABLE_MINIGAME = builder
                    .comment("Enable the fishing minigame for fish catches.",
                            "If disabled, fish are caught immediately after biting; non-fish catchables still behave as instant catches.")
                    .translation("config.stardewcraft.fishing.enable_minigame")
                    .define("enableMinigame", true);
            builder.pop();
        }
    }
}
