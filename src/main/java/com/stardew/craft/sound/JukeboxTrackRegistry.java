package com.stardew.craft.sound;

import net.minecraft.sounds.SoundEvent;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有可在唱片机中播放的曲目。
 * 顺序与星露谷物语 JukeboxTracks.json 中的可用曲目一致。
 */
public final class JukeboxTrackRegistry {

    /** 特殊 ID：关闭唱片机 */
    public static final String TURN_OFF = "turn_off";
    /** 特殊 ID：随机播放 */
    public static final String RANDOM = "random";

    public record Track(String id, String translationKey, DeferredHolder<SoundEvent, SoundEvent> sound) {}

    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();
    private static final List<Track> ORDERED = new ArrayList<>();

    static {
        // 季节户外音乐
        add("spring1",          "stardewcraft.jukebox.spring1",          ModSounds.MUSIC_SPRING1);
        add("spring2",          "stardewcraft.jukebox.spring2",          ModSounds.MUSIC_SPRING2);
        add("spring3",          "stardewcraft.jukebox.spring3",          ModSounds.MUSIC_SPRING3);
        add("summer1",          "stardewcraft.jukebox.summer1",          ModSounds.MUSIC_SUMMER1);
        add("summer2",          "stardewcraft.jukebox.summer2",          ModSounds.MUSIC_SUMMER2);
        add("summer3",          "stardewcraft.jukebox.summer3",          ModSounds.MUSIC_SUMMER3);
        add("fall1",            "stardewcraft.jukebox.fall1",            ModSounds.MUSIC_FALL1);
        add("fall2",            "stardewcraft.jukebox.fall2",            ModSounds.MUSIC_FALL2);
        add("fall3",            "stardewcraft.jukebox.fall3",            ModSounds.MUSIC_FALL3);
        add("winter1",          "stardewcraft.jukebox.winter1",          ModSounds.MUSIC_WINTER1);
        add("winter2",          "stardewcraft.jukebox.winter2",          ModSounds.MUSIC_WINTER2);
        add("winter3",          "stardewcraft.jukebox.winter3",          ModSounds.MUSIC_WINTER3);
        // 镇 / 位置音乐
        add("springtown",       "stardewcraft.jukebox.springtown",       ModSounds.MUSIC_SPRINGTOWN);
        add("saloon",           "stardewcraft.jukebox.saloon",           ModSounds.MUSIC_SALOON);
        add("marnie_shop",      "stardewcraft.jukebox.marnie_shop",      ModSounds.MUSIC_MARNIE_SHOP);
        add("wizard_tower",     "stardewcraft.jukebox.wizard_tower",     ModSounds.MUSIC_WIZARD_TOWER);
        add("library",          "stardewcraft.jukebox.library",          ModSounds.MUSIC_LIBRARY);
        add("adventurer_guild", "stardewcraft.jukebox.adventurer_guild", ModSounds.MUSIC_ADVENTURER_GUILD);
        add("hospital",         "stardewcraft.jukebox.hospital",         ModSounds.MUSIC_HOSPITAL);
        add("elliott_piano",    "stardewcraft.jukebox.elliott_piano",    ModSounds.MUSIC_ELLIOTT_PIANO);
        // 矿洞
        add("earth_mine",       "stardewcraft.jukebox.earth_mine",       ModSounds.MUSIC_EARTH_MINE);
        add("frost_mine",       "stardewcraft.jukebox.frost_mine",       ModSounds.MUSIC_FROST_MINE);
        // 环境
        add("rain",             "stardewcraft.jukebox.rain",             ModSounds.MUSIC_RAIN);
        add("ocean_ambience",   "stardewcraft.jukebox.ocean_ambience",   ModSounds.MUSIC_OCEAN_AMBIENCE);
    }

    private static void add(String id, String translationKey, DeferredHolder<SoundEvent, SoundEvent> sound) {
        Track track = new Track(id, translationKey, sound);
        TRACKS.put(id, track);
        ORDERED.add(track);
    }

    /** 获取有序曲目列表（不含 turn_off / random）。 */
    public static List<Track> getAllTracks() {
        return Collections.unmodifiableList(ORDERED);
    }

    /** 按 ID 查找曲目，找不到返回 null。 */
    public static Track getTrack(String id) {
        return TRACKS.get(id);
    }

    /** 获取曲目数量。 */
    public static int size() {
        return ORDERED.size();
    }

    /**
     * 根据列表索引获取选项 ID。
     * 索引 0 = turn_off，1..N = 曲目，N+1 = random。
     */
    public static String getOptionId(int index) {
        if (index == 0) return TURN_OFF;
        if (index == ORDERED.size() + 1) return RANDOM;
        return ORDERED.get(index - 1).id();
    }

    /** 选项总数（含 turn_off 和 random）。 */
    public static int optionCount() {
        return ORDERED.size() + 2;
    }

    private JukeboxTrackRegistry() {}
}
