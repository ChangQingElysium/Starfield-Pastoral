package com.stardew.craft.sound;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Stardew Valley cue -> Minecraft SoundEvent mapping.
 *
 * Note: Minecraft sound IDs must be lowercase, so we use snake_case.
 */
public final class ModSounds {
	@SuppressWarnings("null")
	public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, StardewCraft.MODID);

	// FishingRod.cs / BobberBar.cs cues
	public static final DeferredHolder<SoundEvent, SoundEvent> SIN_WAVE = register("sin_wave");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAST = register("cast");
	// Stardew: Farmer.PlayFishBiteChime() -> fishBite / fishBite_alternate_0..2
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_BITE = register("fish_bite");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_BITE_ALTERNATE_0 = register("fish_bite_alternate_0");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_BITE_ALTERNATE_1 = register("fish_bite_alternate_1");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_BITE_ALTERNATE_2 = register("fish_bite_alternate_2");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_HIT = register("fish_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISHING_ROD_BEND = register("fishing_rod_bend");
	public static final DeferredHolder<SoundEvent, SoundEvent> SHINY4 = register("shiny4");
	public static final DeferredHolder<SoundEvent, SoundEvent> FAST_REEL = register("fast_reel");
	public static final DeferredHolder<SoundEvent, SoundEvent> SLOW_REEL = register("slow_reel");
	public static final DeferredHolder<SoundEvent, SoundEvent> JINGLE1 = register("jingle1");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_ESCAPE = register("fish_escape");
	public static final DeferredHolder<SoundEvent, SoundEvent> TINY_WHIP = register("tiny_whip");
	public static final DeferredHolder<SoundEvent, SoundEvent> SLINGSHOT = register("slingshot");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREAKING_GLASS = register("breaking_glass");
	public static final DeferredHolder<SoundEvent, SoundEvent> POTTERY_SMASH = register("pottery_smash");
	public static final DeferredHolder<SoundEvent, SoundEvent> WATER_SLOSH = register("water_slosh");
	public static final DeferredHolder<SoundEvent, SoundEvent> DWOP = register("dwop");
	public static final DeferredHolder<SoundEvent, SoundEvent> PULL_ITEM_FROM_WATER = register("pull_item_from_water");
	public static final DeferredHolder<SoundEvent, SoundEvent> DROP_ITEM_IN_WATER = register("drop_item_in_water");
	public static final DeferredHolder<SoundEvent, SoundEvent> NEW_ARTIFACT = register("new_artifact");
	public static final DeferredHolder<SoundEvent, SoundEvent> STARDROP = register("stardrop");
	public static final DeferredHolder<SoundEvent, SoundEvent> SPRING_BIRDS = register("spring_birds");
	// 宝箱打开音效
	public static final DeferredHolder<SoundEvent, SoundEvent> OPEN_CHEST = register("open_chest");
	public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_OPEN = register("door_open");
	public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_CLOSE = register("door_close");
	public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_CREAK = register("door_creak");
	public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_CREAK_REVERSE = register("door_creak_reverse");
	public static final DeferredHolder<SoundEvent, SoundEvent> WOODY_STEP = register("woody_step");
	public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_IN = register("backpack_in");
	public static final DeferredHolder<SoundEvent, SoundEvent> SHWIP = register("shwip");
	public static final DeferredHolder<SoundEvent, SoundEvent> BIG_SELECT = register("big_select");
	public static final DeferredHolder<SoundEvent, SoundEvent> BIG_DESELECT = register("big_deselect");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATHIN = register("breathin");
	public static final DeferredHolder<SoundEvent, SoundEvent> BREATHOUT = register("breathout");
	public static final DeferredHolder<SoundEvent, SoundEvent> COWBOY_GUNSHOT = register("cowboy_gunshot");
	public static final DeferredHolder<SoundEvent, SoundEvent> COWBOY_MONSTERHIT = register("cowboy_monsterhit");
	public static final DeferredHolder<SoundEvent, SoundEvent> WEED_CUT = register("weed_cut");
	public static final DeferredHolder<SoundEvent, SoundEvent> AXCHOP = register("axchop");
	public static final DeferredHolder<SoundEvent, SoundEvent> BOULDER_CRACK = register("boulder_crack");
	public static final DeferredHolder<SoundEvent, SoundEvent> METAL_TAP = register("metal_tap");
	public static final DeferredHolder<SoundEvent, SoundEvent> CLANK = register("clank");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAFTING = register("crafting");
	public static final DeferredHolder<SoundEvent, SoundEvent> SLIMEDEAD = register("slimedead");
	public static final DeferredHolder<SoundEvent, SoundEvent> GLUG = register("glug");
	public static final DeferredHolder<SoundEvent, SoundEvent> GHOST = register("ghost");
	public static final DeferredHolder<SoundEvent, SoundEvent> SECRET1 = register("secret1");
	public static final DeferredHolder<SoundEvent, SoundEvent> DEBUFF_HIT = register("debuff_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIALOGUE_CHARACTER = register("dialogue_character");
	public static final DeferredHolder<SoundEvent, SoundEvent> DIALOGUE_CHARACTER_CLOSE = register("dialogue_character_close");
	public static final DeferredHolder<SoundEvent, SoundEvent> DOG_BARK = register("dog_bark");
	public static final DeferredHolder<SoundEvent, SoundEvent> SHADOW_DIE = register("shadow_die");
	public static final DeferredHolder<SoundEvent, SoundEvent> CAVEDRIP = register("cavedrip");
	public static final DeferredHolder<SoundEvent, SoundEvent> CACKLING_WITCH = register("cackling_witch");
	public static final DeferredHolder<SoundEvent, SoundEvent> THUD_STEP = register("thud_step");
	public static final DeferredHolder<SoundEvent, SoundEvent> STONE_STEP = register("stone_step");
	public static final DeferredHolder<SoundEvent, SoundEvent> CUT = register("cut");
	public static final DeferredHolder<SoundEvent, SoundEvent> HARVEST = register("harvest");
	public static final DeferredHolder<SoundEvent, SoundEvent> LEAFRUSTLE = register("leafrustle");
	public static final DeferredHolder<SoundEvent, SoundEvent> BUTTON1 = register("button1");
	public static final DeferredHolder<SoundEvent, SoundEvent> HAMMER = register("hammer");
	public static final DeferredHolder<SoundEvent, SoundEvent> COIN = register("coin");
	public static final DeferredHolder<SoundEvent, SoundEvent> EAT = register("eat");
	public static final DeferredHolder<SoundEvent, SoundEvent> GULP = register("gulp");
	public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE = register("whistle");
	public static final DeferredHolder<SoundEvent, SoundEvent> MONEY_DIAL = register("money_dial");
	public static final DeferredHolder<SoundEvent, SoundEvent> TRASHCANLID = register("trashcanlid");
	public static final DeferredHolder<SoundEvent, SoundEvent> TRASHCAN = register("trashcan");
	public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION = register("explosion");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRIT = register("crit");
	public static final DeferredHolder<SoundEvent, SoundEvent> THROW_DOWN_ITEM = register("throw_down_item");

	// 武器音效
	public static final DeferredHolder<SoundEvent, SoundEvent> MEOW = register("weapon.meow");

	// Tree fall (from StardewValley.TerrainFeatures.Tree.performTreeFall)
	public static final DeferredHolder<SoundEvent, SoundEvent> TREE_CRACK = register("tree_crack");
	public static final DeferredHolder<SoundEvent, SoundEvent> TREE_THUD = register("tree_thud");

	// Utility / Machine cues
	public static final DeferredHolder<SoundEvent, SoundEvent> SHIP = register("ship");
	public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLES = register("bubbles");
	public static final DeferredHolder<SoundEvent, SoundEvent> SIP_TEA = register("siptea");
	public static final DeferredHolder<SoundEvent, SoundEvent> YOBA = register("yoba");
	public static final DeferredHolder<SoundEvent, SoundEvent> STATUE_OF_BLESSINGS = register("statue_of_blessings");
	public static final DeferredHolder<SoundEvent, SoundEvent> TREASURE_TOTEM = register("treasure_totem");
	public static final DeferredHolder<SoundEvent, SoundEvent> STONE_BUTTON = register("stone_button");
	public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_BELL = register("machine_bell");
	public static final DeferredHolder<SoundEvent, SoundEvent> BUTTON_PRESS = register("button_press");
	public static final DeferredHolder<SoundEvent, SoundEvent> BUTTON_TAP = register("button_tap");
	public static final DeferredHolder<SoundEvent, SoundEvent> TICKET_MACHINE_WHIR = register("ticket_machine_whir");
	public static final DeferredHolder<SoundEvent, SoundEvent> FISH_SLAP = register("fishslap");
	public static final DeferredHolder<SoundEvent, SoundEvent> FAIRY_HEAL = register("fairy_heal");
	public static final DeferredHolder<SoundEvent, SoundEvent> FROG_SLAP = register("frog_slap");
	public static final DeferredHolder<SoundEvent, SoundEvent> CROAK = register("croak");
	public static final DeferredHolder<SoundEvent, SoundEvent> FURNACE = register("furnace");
	public static final DeferredHolder<SoundEvent, SoundEvent> OPENBOX = register("openbox");
	public static final DeferredHolder<SoundEvent, SoundEvent> FIREBALL = register("fireball");
	public static final DeferredHolder<SoundEvent, SoundEvent> CANCEL = register("cancel");
	public static final DeferredHolder<SoundEvent, SoundEvent> SELECT = register("select");
	public static final DeferredHolder<SoundEvent, SoundEvent> BOOK_READ = register("book_read");
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUMKIT4 = register("drumkit4");
	public static final DeferredHolder<SoundEvent, SoundEvent> STEAM = register("steam");
	public static final DeferredHolder<SoundEvent, SoundEvent> SKELETON_STEP = register("skeleton_step");
	public static final DeferredHolder<SoundEvent, SoundEvent> SKELETON_HIT = register("skeleton_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> SKELETON_DIE = register("skeleton_die");
	public static final DeferredHolder<SoundEvent, SoundEvent> DWARVISH_SENTRY = register("dwarvish_sentry");

	// AnimalQueryMenu / FarmAnimal original cues
	public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_SELECT = register("small_select");
	public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL = register("crystal");
	public static final DeferredHolder<SoundEvent, SoundEvent> GIVE_GIFT = register("give_gift");
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUMKIT6 = register("drumkit6");
	public static final DeferredHolder<SoundEvent, SoundEvent> NEW_RECIPE = register("new_recipe");
	public static final DeferredHolder<SoundEvent, SoundEvent> MONEY = register("money");

	// ShopMenu cues
	public static final DeferredHolder<SoundEvent, SoundEvent> PURCHASE = register("purchase");
	public static final DeferredHolder<SoundEvent, SoundEvent> SELL = register("sell");
	public static final DeferredHolder<SoundEvent, SoundEvent> PURCHASE_CLICK = register("purchase_click");
	public static final DeferredHolder<SoundEvent, SoundEvent> PURCHASE_REPEAT = register("purchase_repeat");

	public static final DeferredHolder<SoundEvent, SoundEvent> CLUCK = register("cluck");
	public static final DeferredHolder<SoundEvent, SoundEvent> DUCK = register("duck");
	public static final DeferredHolder<SoundEvent, SoundEvent> RABBIT = register("rabbit");
	public static final DeferredHolder<SoundEvent, SoundEvent> OSTRICH = register("ostrich");
	public static final DeferredHolder<SoundEvent, SoundEvent> COW = register("cow");
	public static final DeferredHolder<SoundEvent, SoundEvent> GOAT = register("goat");
	public static final DeferredHolder<SoundEvent, SoundEvent> SHEEP = register("sheep");
	public static final DeferredHolder<SoundEvent, SoundEvent> PIG = register("pig");

    // Level up sound
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = register("level_up");

    // Geode processing
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_CRACK = register("stone_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCOVER_MINERAL = register("discover_mineral");

    // Mine sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> HOE_HIT = register("hoe_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAIRS_DOWN = register("stairsdown");

    // Totem sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> WARRIOR = register("warrior");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAND = register("wand");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_SOUND = register("rain_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER = register("thunder");

    // Bomb sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSE = register("fuse");
    // EXPLOSION is already registered above

    // ── Music tracks ──────────────────────────────────────────────
    // Outdoor seasonal (12)
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRING1 = register("music_spring1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRING2 = register("music_spring2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRING3 = register("music_spring3");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUMMER1 = register("music_summer1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUMMER2 = register("music_summer2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUMMER3 = register("music_summer3");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FALL1 = register("music_fall1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FALL2 = register("music_fall2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FALL3 = register("music_fall3");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WINTER1 = register("music_winter1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WINTER2 = register("music_winter2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WINTER3 = register("music_winter3");
    // Rain ambient
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_RAIN = register("music_rain");
    // Night ambient
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRING_NIGHT_AMBIENT = register("music_spring_night_ambient");
    // Day ambient (4)
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRING_DAY_AMBIENT = register("music_spring_day_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUMMER_DAY_AMBIENT = register("music_summer_day_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FALL_DAY_AMBIENT = register("music_fall_day_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WINTER_DAY_AMBIENT = register("music_winter_day_ambient");
    // Town
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPRINGTOWN = register("music_springtown");
    // Shop
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MARNIE_SHOP = register("music_marnie_shop");
    // Mine
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EARTH_MINE = register("music_earth_mine");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FROST_MINE = register("music_frost_mine");
    // Indoor locations
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SALOON = register("music_saloon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WIZARD_TOWER = register("music_wizard_tower");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LIBRARY = register("music_library");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ADVENTURER_GUILD = register("music_adventurer_guild");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HOSPITAL = register("music_hospital");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HOSPITAL_AMBIENT = register("music_hospital_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_POOL_AMBIENT = register("music_pool_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ELLIOTT_PIANO = register("music_elliott_piano");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COMMUNITY_CENTER = register("music_community_center");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_50S = register("music_50s");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISTANT_BANJO = register("music_distant_banjo");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_CLUBLOOP = register("music_clubloop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_OCEAN_AMBIENCE = register("music_ocean_ambience");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_GRANDPAS_THEME = register("music_grandpas_theme");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EVENT1 = register("music_event1");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_EVENT2 = register("music_event2");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_TICK_TOCK = register("music_tick_tock");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_CHRISTMAS_THEME = register("music_christmas_theme");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FALL_FEST = register("music_fall_fest");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FLOWER_DANCE = register("music_flower_dance");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MOONLIGHT_JELLIES = register("music_moonlight_jellies");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SPIRITS_EVE = register("music_spirits_eve");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SETTLINGIN = register("music_settlingin");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_JAUNTY = register("music_jaunty");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_PLAYFUL = register("music_playful");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NIGHT_MARKET = register("music_night_market");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUBMARINE = register("music_submarine");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MERMAID_SONG = register("music_mermaid_song");
	public static final DeferredHolder<SoundEvent, SoundEvent> CLAM_TONE = register("clam_tone");
    // Desert / Oasis
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DESERT = register("music_desert");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_OASIS = register("music_oasis");
	public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_WOODS = register("music_woods");

    // ─── Quest ───
    public static final DeferredHolder<SoundEvent, SoundEvent> QUEST_COMPLETE = register("questcomplete");

    // ─── Community Center ───
    public static final DeferredHolder<SoundEvent, SoundEvent> REWARD = register("reward");

    // ─── Crow / Bird ───
    /** SDV cue "crow" — a singular crow caw. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROW_CAW  = register("crow_caw");
    /** SDV cue "batFlap" — wings flapping (used by crows in SDV). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CROW_FLAP = register("crow_flap");

	@SuppressWarnings("null")
	private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
		@SuppressWarnings("null")
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
		return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
	}
}
