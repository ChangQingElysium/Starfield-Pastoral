package com.stardew.craft.client.sound;

import com.stardew.craft.client.hud.MiningFloorHud;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.block.utility.totem.TotemPoleBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.festival.nightmarket.NightMarketSubmarineService;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.manager.CoalForestArea;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.ClientWeatherCache;
import com.stardew.craft.world.MutantBugLairArea;
import com.stardew.craft.world.WitchArea;
import com.stardew.craft.world.LocationMusicEnvironment;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side music manager that replicates Stardew Valley's music system.
 * <p>
 * Selects music based on: dimension, season, weather, time-of-day, mine floor.
 * Suppresses vanilla MC MusicManager when in Stardew dimensions.
 */
@SuppressWarnings("null")
public final class StardewMusicManager {

    private static final Logger LOG = LoggerFactory.getLogger(StardewMusicManager.class);

    /** How often (in ticks) to re-evaluate which track should play. */
    private static final int CHECK_INTERVAL = 10; // 0.5 seconds

    /** SDV: nightTime starts around 18:00 (1080 in SDV time units). */
    private static final int NIGHT_TIME = 1080;
    private static final int NIGHT_MARKET_START_TIME = 1020;
    private static final int NIGHT_MARKET_END_TIME = 1560;

    /** SDV: EarthMine = floors 1-39, FrostMine = floors 40-79. */
    private static final int FROST_MINE_START = 40;

    private static final int NO_TIME_LIMIT = -1;
    private static final int COMMUNITY_CENTER_WIDTH = 23;
    private static final int COMMUNITY_CENTER_HEIGHT = 8;
    private static final int COMMUNITY_CENTER_LENGTH = 69;
    private static final int COMMUNITY_CENTER_Z_STRIDE = 128;
    private static final int GREENHOUSE_SLOT_SIZE = 64;

    private record InteriorMusicDefinition(
            @Nullable DeferredHolder<SoundEvent, SoundEvent> music,
            int startTimeHhmm
    ) {}

    private record InteriorTrackChoice(@Nullable SoundEvent track) {}

    private static InteriorMusicDefinition music(@Nullable DeferredHolder<SoundEvent, SoundEvent> music) {
        return music(music, NO_TIME_LIMIT);
    }

    private static InteriorMusicDefinition music(
            @Nullable DeferredHolder<SoundEvent, SoundEvent> music,
            int startTimeHhmm
    ) {
        return new InteriorMusicDefinition(music, startTimeHhmm);
    }

    private static final Map<String, InteriorMusicDefinition> FIXED_INTERIOR_MUSIC = Map.ofEntries(
        Map.entry("pierre_house",     music(ModSounds.MUSIC_SPRINGTOWN)),
        Map.entry("museum",           music(ModSounds.MUSIC_LIBRARY)),
        Map.entry("blacksmith",       music(null)),
        Map.entry("saloon",           music(ModSounds.MUSIC_SALOON, 1700)),
        Map.entry("auction_hall",     music(ModSounds.MUSIC_JAUNTY)),
        Map.entry("mayor_house",      music(null)),
        Map.entry("clinic",           music(ModSounds.MUSIC_DISTANT_BANJO)),
        Map.entry("1_river_road",     music(null)),
        Map.entry("carpenter_shop",   music(ModSounds.MUSIC_MARNIE_SHOP)),
        Map.entry("1_willow_lane",    music(null)),
        Map.entry("2_willow_lane",    music(null)),
        Map.entry("marnie_ranch",     music(ModSounds.MUSIC_MARNIE_SHOP)),
        Map.entry("leah_cottage",     music(ModSounds.MUSIC_DISTANT_BANJO)),
        Map.entry("adventurer_guild", music(ModSounds.MUSIC_ADVENTURER_GUILD)),
        Map.entry("fish_shop",        music(null)),
        Map.entry("elliott_cabin",    music(ModSounds.MUSIC_COMMUNITY_CENTER)),
        Map.entry("wizard_tower",     music(ModSounds.MUSIC_WIZARD_TOWER)),
        Map.entry("oasis",            music(ModSounds.MUSIC_OASIS)),
        Map.entry("joja_mart",        music(ModSounds.MUSIC_HOSPITAL_AMBIENT)),
        Map.entry("trailer",          music(null))
    );

    private static int tickCounter = CHECK_INTERVAL - 1; // evaluate on first tick
    private static int sewerAmbientCooldown = 0;

    /** The currently-playing music instance, or null. */
    @Nullable
    private static StardewMusicInstance currentMusic;

    /** The SoundEvent key of the currently-playing track, for dedup. */
    @Nullable
    private static SoundEvent currentTrackEvent;

    /** Fade-out instance (old track being replaced). */
    @Nullable
    private static StardewMusicInstance fadingOut;
    private static int fadeOutTicks = 0;
    private static final int FADE_OUT_DURATION = 40; // 2 seconds fade
    private static int fadeInTicks = 0;
    private static final int FADE_IN_DURATION = 40;
    private static final float FADE_IN_START_VOLUME = 0.05F;
    private static int musicDuckTicks = 0;
    private static final float MUSIC_DUCK_VOLUME = 0.35F;
    private static boolean nightMarketFestivalOpen = false;

    // ────────────────────────── Jukebox suppression ──────────────────────────

    /** 唱片机有效抑制范围（方块距离的平方）。与 vanilla Jukebox 一致 ~65 blocks。 */
    private static final double JUKEBOX_RANGE_SQ = 65.0 * 65.0;

    /** 客户端跟踪的活跃唱片机。 */
    private static final Map<BlockPos, JukeboxPlayback> activeJukeboxes = new HashMap<>();

    /** When true, the cutscene system owns the music — skip evaluateAndPlay(). */
    private static boolean cutsceneOverride = false;
    private static boolean oneShotCutsceneMusic = false;
    private static int oneShotCutsceneTicks = 0;

    /**
     * Play a track for a cutscene event. Registers as currentMusic so stopAll() works,
     * and sets cutsceneOverride to prevent evaluateAndPlay() from overriding it.
     */
    public static void playForCutscene(SoundEvent event) {
        stopAll();
        cutsceneOverride = true;
        oneShotCutsceneMusic = false;
        Minecraft mc = Minecraft.getInstance();
        StardewMusicInstance instance = new StardewMusicInstance(event);
        currentMusic = instance;
        currentTrackEvent = event;
        mc.getSoundManager().play(instance);
    }

    /** Plays a non-looping cutscene track, then returns music control to the location manager. */
    public static void playOneShotForCutscene(SoundEvent event) {
        stopAll();
        cutsceneOverride = true;
        oneShotCutsceneMusic = true;
        oneShotCutsceneTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        StardewMusicInstance instance = new StardewMusicInstance(event, false);
        currentMusic = instance;
        currentTrackEvent = event;
        mc.getSoundManager().play(instance);
    }

    public static void stopForCutsceneSilence() {
        stopCurrentMusicInstances();
        cutsceneOverride = true;
        oneShotCutsceneMusic = false;
    }

    public static void releaseCutsceneOverride() {
        cutsceneOverride = false;
        oneShotCutsceneMusic = false;
        oneShotCutsceneTicks = 0;
        tickCounter = CHECK_INTERVAL - 1;
    }

    public static void setNightMarketFestivalOpen(boolean open) {
        if (nightMarketFestivalOpen != open) {
            nightMarketFestivalOpen = open;
            tickCounter = CHECK_INTERVAL - 1;
        }
    }

    public static void duckMusic(int ticks) {
        musicDuckTicks = Math.max(musicDuckTicks, Math.max(0, ticks));
    }

    private record JukeboxPlayback(String trackId, StardewMusicInstance sound) {}

    private StardewMusicManager() {}

    // ────────────────────────── Public API ──────────────────────────

    /**
     * 客户端收到唱片机状态广播时调用。
     * trackId 为空表示停止。切换制：同一位置的旧曲目会被立即停止。
     */
    public static void setJukeboxState(BlockPos pos, String trackId) {
        Minecraft mc = Minecraft.getInstance();

        // 停止该位置旧的播放
        JukeboxPlayback old = activeJukeboxes.remove(pos);
        if (old != null && old.sound() != null) {
            old.sound().stopNow();
            mc.getSoundManager().stop(old.sound());
        }

        if (trackId == null || trackId.isEmpty()) {
            LOG.info("[StardewMusic] Jukebox at {} stopped", pos);
            return; // 停止，不创建新实例
        }

        // 查找曲目并播放
        com.stardew.craft.sound.JukeboxTrackRegistry.Track track =
                com.stardew.craft.sound.JukeboxTrackRegistry.getTrack(trackId);
        if (track == null) return;

        SoundEvent soundEvent = track.sound().get();
        StardewMusicInstance instance = new StardewMusicInstance(soundEvent, pos);
        activeJukeboxes.put(pos, new JukeboxPlayback(trackId, instance));
        mc.getSoundManager().play(instance);
        LOG.info("[StardewMusic] Jukebox at {} playing: {}", pos, trackId);
    }

    /**
     * Called every client tick from ModClientEvents.
     */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stopAll();
            return;
        }

        // Only manage music in Stardew dimensions
        boolean inStardew = mc.level.dimension() == ModDimensions.STARDEW_VALLEY;
        boolean inMine = mc.level.dimension() == ModMiningDimensions.STARDEW_MINING;
        if (!inStardew && !inMine) {
            stopAll();
            return;
        }

        // Suppress vanilla MC music when in Stardew dimensions
        mc.getMusicManager().stopPlaying();

        if (musicDuckTicks > 0) {
            musicDuckTicks--;
        }

        // 清除已停止的唱片机条目
        cleanupStoppedJukeboxes();

        // 如果附近有唱片机在播放 → 立即停止背景音乐（不做渐变，避免叠加）
        if (isNearActiveJukebox(mc)) {
            if (currentMusic != null) {
                currentMusic.stopNow();
                mc.getSoundManager().stop(currentMusic);
                currentMusic = null;
                currentTrackEvent = null;
            }
            if (fadingOut != null) {
                fadingOut.stopNow();
                mc.getSoundManager().stop(fadingOut);
                fadingOut = null;
            }
            return;
        }

        // Process fade-out
        tickFadeOut();
        tickFadeIn();

        // Don't override cutscene music. A one-shot cue releases ownership as soon as
        // Minecraft's sound engine reports that its channel has naturally ended.
        if (cutsceneOverride) {
            if (oneShotCutsceneMusic) {
                oneShotCutsceneTicks++;
                if (oneShotCutsceneTicks > 2
                        && (currentMusic == null || !mc.getSoundManager().isActive(currentMusic))) {
                    currentMusic = null;
                    currentTrackEvent = null;
                    releaseCutsceneOverride();
                }
            }
            if (cutsceneOverride) {
                return;
            }
        }

        tickSewerAmbient(mc);
        tickCasinoAmbient(mc);

        // Periodic track evaluation
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            evaluateAndPlay(mc, inMine);
        }
    }

    /**
     * Force stop all music immediately (e.g. on dimension change).
     */
    public static void stopAll() {
        cutsceneOverride = false;
        oneShotCutsceneMusic = false;
        oneShotCutsceneTicks = 0;
        musicDuckTicks = 0;
        stopCurrentMusicInstances();
        // 清除所有唱片机播放
        for (JukeboxPlayback pb : activeJukeboxes.values()) {
            if (pb.sound() != null) {
                pb.sound().stopNow();
                Minecraft.getInstance().getSoundManager().stop(pb.sound());
            }
        }
        activeJukeboxes.clear();
        tickCounter = 0;
    }

    private static void stopCurrentMusicInstances() {
        if (currentMusic != null) {
            currentMusic.stopNow();
            Minecraft.getInstance().getSoundManager().stop(currentMusic);
            currentMusic = null;
            currentTrackEvent = null;
        }
        if (fadingOut != null) {
            fadingOut.stopNow();
            Minecraft.getInstance().getSoundManager().stop(fadingOut);
            fadingOut = null;
        }
    }

    // ────────────────────────── Jukebox helpers ──────────────────────────

    /** 移除已停止播放的唱片机条目，同时清理超出范围的唱片机声音。 */
    private static void cleanupStoppedJukeboxes() {
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<BlockPos, JukeboxPlayback>> it = activeJukeboxes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, JukeboxPlayback> entry = it.next();
            JukeboxPlayback pb = entry.getValue();
            if (pb.sound() != null && pb.sound().isStopped()) {
                mc.getSoundManager().stop(pb.sound());
                it.remove();
                continue;
            }
            // 超出最大范围 → 主动停止声音并移除
            if (mc.player != null) {
                BlockPos pos = entry.getKey();
                double dx = pos.getX() + 0.5 - mc.player.getX();
                double dy = pos.getY() + 0.5 - mc.player.getY();
                double dz = pos.getZ() + 0.5 - mc.player.getZ();
                if (dx * dx + dy * dy + dz * dz > JUKEBOX_RANGE_SQ) {
                    if (pb.sound() != null) {
                        pb.sound().stopNow();
                        mc.getSoundManager().stop(pb.sound());
                    }
                    it.remove();
                }
            }
        }
    }

    /** 玩家是否在某个活跃唱片机的有效范围内。 */
    private static boolean isNearActiveJukebox(Minecraft mc) {
        if (activeJukeboxes.isEmpty() || mc.player == null) return false;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        for (BlockPos pos : activeJukeboxes.keySet()) {
            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz <= JUKEBOX_RANGE_SQ) {
                return true;
            }
        }
        return false;
    }

    // ────────────────────────── Core Logic ──────────────────────────

    private static void evaluateAndPlay(Minecraft mc, boolean inMine) {
        SoundEvent desired = pickTrack(mc, inMine);
        if (desired == null) {
            // No suitable track -> stop
            if (currentMusic != null) {
                crossfadeTo(null);
            }
            return;
        }

        // Same track already playing?
        if (currentTrackEvent != null && currentTrackEvent.equals(desired)) {
            // Check if it actually stopped (e.g. finished or error)
            if (currentMusic != null && currentMusic.isStopped()) {
                LOG.info("[StardewMusic] Restarting stopped track: {}", desired.getLocation());
                startPlaying(desired);
            }
            return;
        }

        // Different track -> crossfade
        crossfadeTo(desired);
    }

    /**
     * Picks the appropriate music track based on current conditions.
     * Priority: Mine > Interior > Rain > Night ambient > Seasonal day music
     */
    @Nullable
    private static SoundEvent pickTrack(Minecraft mc, boolean inMine) {
        // ── Mine music ──
        if (inMine) {
            return pickMineTrack();
        }

        if (mc.player != null && NightMarketSubmarineService.isInsideSubmarineBounds(mc.player.blockPosition())) {
            return ModSounds.MUSIC_SUBMARINE.get();
        }

        if (isPlayerInOpenNightMarket(mc)) {
            return ModSounds.MUSIC_NIGHT_MARKET.get();
        }

        if (mc.player != null && TotemPoleBlock.isInsideBeachBounds(mc.player.blockPosition())) {
            return ModSounds.MUSIC_OCEAN_AMBIENCE.get();
        }

        // ── Interior music (highest priority in Stardew overworld) ──
        InteriorTrackChoice interiorTrack = pickInteriorTrack(mc);
        if (interiorTrack != null) {
            return interiorTrack.track();
        }

        if (mc.player != null && MutantBugLairArea.contains(mc.player.blockPosition())) {
            return ModSounds.MUSIC_BUG_LEVEL_LOOP.get();
        }

        if (mc.player != null && WitchArea.isInHut(mc.player.blockPosition())) {
            return ModSounds.MUSIC_UPPER_AMBIENT.get();
        }

        if (mc.player != null && WitchArea.isInSwamp(mc.player.blockPosition())) {
            return ModSounds.MUSIC_LAVA_AMBIENT.get();
        }

        if (isPlayerInSewerRegion(mc)) {
            return null;
        }

        // 露天温泉氛围音乐：玩家落在 mistBounds 内 → pool_ambient。
        // 优先级高于普通室外/天气/季节音乐，低于矿洞、室内、剧情/事件音乐。
        if (isPlayerInHotSpringMist(mc)) {
            return ModSounds.MUSIC_POOL_AMBIENT.get();
        }

        if (isPlayerInSecretWoods(mc)) {
            return ModSounds.MUSIC_WOODS.get();
        }

        // ── Desert outdoor music (Calico Desert uses "wavy") ──
        if (isPlayerInDesert(mc)) {
            return ModSounds.MUSIC_DESERT.get();
        }

        // ── Weather: Rain ──
        String weather = ClientWeatherCache.getCurrentWeather(ModDimensions.STARDEW_VALLEY);
        boolean isRaining = "Rain".equals(weather) || "Storm".equals(weather);
        if (isRaining) {
            return ModSounds.MUSIC_RAIN.get();
        }

        // ── Get time and season info ──
        StardewTimeManager timeCache = StardewTimeHud.getClientTimeCache();
        if (timeCache == null) {
            return null;
        }

        int currentTime = timeCache.getCurrentTime();
        int season = timeCache.getCurrentSeason();
        boolean isNight = currentTime >= NIGHT_TIME;

        // ── Night ambient (all non-winter seasons, after 18:00) ──
        if (isNight && season != 3) {
            return ModSounds.MUSIC_SPRING_NIGHT_AMBIENT.get();
        }

        // ── Daytime: pick a seasonal track ──
        // SDV rotates tracks by non-rainy day count
        int day = timeCache.getCurrentDay();
        return pickSeasonalTrack(season, day);
    }

    /**
     * Picks seasonal outdoor music. Rotates through 3 tracks per season.
     */
    private static SoundEvent pickSeasonalTrack(int season, int day) {
        int index = day % 3;
        return switch (season) {
            case 0 -> getSeasonal(ModSounds.MUSIC_SPRING1, ModSounds.MUSIC_SPRING2, ModSounds.MUSIC_SPRING3, index);
            case 1 -> getSeasonal(ModSounds.MUSIC_SUMMER1, ModSounds.MUSIC_SUMMER2, ModSounds.MUSIC_SUMMER3, index);
            case 2 -> getSeasonal(ModSounds.MUSIC_FALL1, ModSounds.MUSIC_FALL2, ModSounds.MUSIC_FALL3, index);
            case 3 -> getSeasonal(ModSounds.MUSIC_WINTER1, ModSounds.MUSIC_WINTER2, ModSounds.MUSIC_WINTER3, index);
            default -> null;
        };
    }

    private static SoundEvent getSeasonal(
            DeferredHolder<SoundEvent, SoundEvent> a,
            DeferredHolder<SoundEvent, SoundEvent> b,
            DeferredHolder<SoundEvent, SoundEvent> c,
            int index) {
        return switch (index) {
            case 0 -> a.get();
            case 1 -> b.get();
            default -> c.get();
        };
    }

    /**
     * Picks mine music based on current floor.
     * SDV: EarthMine (1-39) randomly picks Crystal Bells / Cavern / Secret Gnomes.
     *      FrostMine (40-79) randomly picks Cloth / Icicles / XOR.
     */
    private static SoundEvent pickMineTrack() {
        int floor = MiningFloorHud.getCurrentFloor();
        if (floor < FROST_MINE_START) {
            return ModSounds.MUSIC_EARTH_MINE.get();
        } else {
            return ModSounds.MUSIC_FROST_MINE.get();
        }
    }

    /**
     * Checks if the player is in the Calico Desert outdoor area.
     */
    private static boolean isPlayerInDesert(Minecraft mc) {
        if (mc.player == null) return false;
        return DesertConstants.isInDesertRegion(mc.player.blockPosition());
    }

    private static boolean isPlayerInOpenNightMarket(Minecraft mc) {
        if (mc.player == null || !TotemPoleBlock.isInsideBeachBounds(mc.player.blockPosition())) {
            return false;
        }
        StardewTimeManager timeCache = StardewTimeHud.getClientTimeCache();
        if (timeCache == null) {
            return false;
        }
        int time = timeCache.getCurrentTime();
        boolean scheduledDate = timeCache.getCurrentSeason() == 3
            && timeCache.getCurrentDay() >= 15
            && timeCache.getCurrentDay() <= 17;
        return (nightMarketFestivalOpen || scheduledDate)
            && time >= NIGHT_MARKET_START_TIME
            && time < NIGHT_MARKET_END_TIME;
    }

    private static boolean isPlayerInSecretWoods(Minecraft mc) {
        if (mc.player == null) return false;
        return CoalForestArea.containsColumn(mc.player.blockPosition());
    }

    private static boolean isPlayerInHotSpringMist(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        return com.stardew.craft.hotspring.HotSpringAreaRegistry.isInMistArea(
            mc.level.dimension(),
            mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private static boolean isPlayerInSewerRegion(Minecraft mc) {
        if (mc.player == null) return false;
        BlockPos pos = mc.player.blockPosition();
        return pos.getX() >= -1 && pos.getX() <= 39
            && pos.getY() >= 49 && pos.getY() <= 58
            && pos.getZ() >= 36 && pos.getZ() <= 67;
    }

    private static void tickSewerAmbient(Minecraft mc) {
        if (!isPlayerInSewerRegion(mc) || mc.level == null || mc.player == null) {
            sewerAmbientCooldown = 0;
            return;
        }
        if (sewerAmbientCooldown > 0) {
            sewerAmbientCooldown--;
            return;
        }
        if (mc.level.random.nextInt(900) == 0) {
            mc.level.playLocalSound(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                ModSounds.CAVEDRIP.get(),
                SoundSource.AMBIENT,
                0.8F,
                1.0F,
                false
            );
            sewerAmbientCooldown = 200;
        }
    }

    /**
     * StardewValley.Locations.Club.checkForMusic: every environment check has a
     * 0.002 chance to play the local "boop" cue.
     */
    private static void tickCasinoAmbient(Minecraft mc) {
        if (mc.level == null || mc.player == null
                || !isPlayerInFixedInterior(mc, "casino")) {
            return;
        }
        if (mc.level.random.nextDouble() < 0.002D) {
            mc.level.playLocalSound(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    ModSounds.BOOP.get(),
                    SoundSource.AMBIENT,
                    1.0F,
                    1.0F,
                    false
            );
        }
    }

    private static boolean isPlayerInFixedInterior(Minecraft mc, String interiorId) {
        return mc.player != null
                && InteriorRegionRegistry.fixedInteriorAt(mc.player.blockPosition())
                        .map(region -> region.id().equals(interiorId))
                        .orElse(false);
    }

    /**
     * Resolves inherited location music properties first, then legacy fixed
     * interiors and per-player interior slots as compatibility fallbacks.
     */
    @Nullable
    private static InteriorTrackChoice pickInteriorTrack(Minecraft mc) {
        if (mc.player == null) return null;

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();

        if (mc.level != null) {
            var location = StardewLocations.find(
                    mc.level.dimension().location(),
                    new BlockPos(px, py, pz)).orElse(null);
            StardewTimeManager clock =
                    StardewTimeHud.getClientTimeCache();
            var environment = LocationMusicEnvironment.resolve(
                    location,
                    clock == null ? -1 : clock.getCurrentTime());
            if (environment.decision()
                    == LocationMusicEnvironment.Decision.SILENCE) {
                return new InteriorTrackChoice(null);
            }
            if (environment.decision()
                    == LocationMusicEnvironment.Decision.TRACK) {
                return new InteriorTrackChoice(
                        BuiltInRegistries.SOUND_EVENT.get(
                                environment.track()));
            }
        }

        InteriorMusicDefinition fixedInteriorMusic = InteriorRegionRegistry.fixedInteriorAt(new BlockPos(px, py, pz))
                .map(region -> FIXED_INTERIOR_MUSIC.get(region.id()))
                .orElse(null);
        if (fixedInteriorMusic != null) {
            if (!isInteriorMusicTimeActive(fixedInteriorMusic)) {
                return null;
            }
            if (fixedInteriorMusic.music == null) {
                return null;
            }
            return new InteriorTrackChoice(fixedInteriorMusic.music.get());
        }
        InteriorTrackChoice perPlayerInteriorTrack = pickPerPlayerInteriorTrack(px, py, pz);
        if (perPlayerInteriorTrack != null) {
            return perPlayerInteriorTrack;
        }
        return null;
    }

    @Nullable
    private static InteriorTrackChoice pickPerPlayerInteriorTrack(int x, int y, int z) {
        if (isInsideRepeatedZSlot(
                x,
                y,
                z,
                InteriorSubspaceManager.CC_ORIGIN,
                COMMUNITY_CENTER_WIDTH,
                COMMUNITY_CENTER_HEIGHT,
                COMMUNITY_CENTER_LENGTH,
                COMMUNITY_CENTER_Z_STRIDE
        )) {
            // Vanilla CommunityCenter.resetLocalState only starts the ruined-building
            // ambient cue while at least one area remains incomplete. Once refurbished,
            // the interior is silent instead of continuing the wind/creaking loop.
            boolean refurbished = com.stardew.craft.communitycenter.network.BundleClientData
                    .INSTANCE.isInteriorRefurbished();
            return new InteriorTrackChoice(
                    refurbished ? null : ModSounds.MUSIC_COMMUNITY_CENTER.get());
        }
        if (isInsideRepeatedZSlot(
                x,
                y,
                z,
                InteriorSubspaceManager.GREENHOUSE_INTERIOR_ORIGIN,
                GREENHOUSE_SLOT_SIZE,
                GREENHOUSE_SLOT_SIZE,
                GREENHOUSE_SLOT_SIZE,
                GREENHOUSE_SLOT_SIZE
        )) {
            return new InteriorTrackChoice(null);
        }
        return null;
    }

    private static boolean isInsideRepeatedZSlot(
            int x,
            int y,
            int z,
            BlockPos base,
            int width,
            int height,
            int length,
            int zStride
    ) {
        int rx = x - base.getX();
        int ry = y - base.getY();
        int rz = z - base.getZ();
        if (rx < 0 || rx >= width || ry < 0 || ry >= height || rz < 0) {
            return false;
        }
        return rz % zStride < length;
    }

    private static boolean isInteriorMusicTimeActive(InteriorMusicDefinition definition) {
        if (definition.startTimeHhmm == NO_TIME_LIMIT) return true;
        StardewTimeManager timeCache = StardewTimeHud.getClientTimeCache();
        if (timeCache == null) return false;
        int minutes = timeCache.getCurrentTime();
        int hhmm = (minutes / 60) * 100 + (minutes % 60);
        return hhmm >= definition.startTimeHhmm;
    }

    // ────────────────────────── Playback ──────────────────────────

    private static void crossfadeTo(@Nullable SoundEvent newTrack) {
        // Move current to fade-out
        if (currentMusic != null && !currentMusic.isStopped()) {
            if (fadingOut != null) {
                fadingOut.stopNow();
            }
            fadingOut = currentMusic;
            fadeOutTicks = 0;
        }
        currentMusic = null;
        currentTrackEvent = null;

        if (newTrack != null) {
            startPlaying(newTrack, true);
        }
    }

    private static void startPlaying(SoundEvent event) {
        startPlaying(event, false);
    }

    private static void startPlaying(SoundEvent event, boolean fadeIn) {
        LOG.info("[StardewMusic] Playing: {}", event.getLocation());
        StardewMusicInstance instance = new StardewMusicInstance(event);
        if (fadeIn) {
            instance.setFadeVolume(FADE_IN_START_VOLUME);
            fadeInTicks = 0;
        } else {
            fadeInTicks = FADE_IN_DURATION;
        }
        Minecraft.getInstance().getSoundManager().play(instance);
        currentMusic = instance;
        currentTrackEvent = event;
    }

    private static void tickFadeOut() {
        StardewMusicInstance fading = fadingOut;
        if (fading == null) return;

        fadeOutTicks++;
        float progress = (float) fadeOutTicks / FADE_OUT_DURATION;
        if (progress >= 1.0f) {
            fading.stopNow();
            Minecraft.getInstance().getSoundManager().stop(fading);
            fadingOut = null;
        } else {
            fading.setFadeVolume(1.0f - progress);
        }
    }

    private static void tickFadeIn() {
        StardewMusicInstance current = currentMusic;
        if (current == null || current.isStopped() || fadeInTicks >= FADE_IN_DURATION) return;

        fadeInTicks++;
        float progress = (float) fadeInTicks / FADE_IN_DURATION;
        current.setFadeVolume(Math.max(FADE_IN_START_VOLUME, progress));
    }

    // ────────────────────────── Sound Instance ──────────────────────────

    /**
     * A looping, streaming music sound instance with volume fade support.
     */
    public static final class StardewMusicInstance extends AbstractSoundInstance implements TickableSoundInstance {
        private volatile boolean stopped;
        private float fadeVolume = 1.0f;

        /** 背景音乐模式（跟随玩家）。 */
        public StardewMusicInstance(SoundEvent sound) {
            this(sound, true);
        }

        /** Background music loops; restoration cues use the non-looping variant. */
        private StardewMusicInstance(SoundEvent sound, boolean looping) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = looping;
            this.delay = 0;
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.relative = true;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        }

        /** 唱片机模式（定位在方块位置，RECORDS 声道）。 */
        public StardewMusicInstance(SoundEvent sound, BlockPos pos) {
            super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 4.0f;
            this.pitch = 1.0f;
            this.relative = false;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.attenuation = Attenuation.NONE;
        }

        void setFadeVolume(float fade) {
            this.fadeVolume = Math.max(0.0f, Math.min(1.0f, fade));
        }

        public void stopNow() {
            this.stopped = true;
        }

        @Override
        public float getVolume() {
            float duckScale = musicDuckTicks > 0 ? MUSIC_DUCK_VOLUME : 1.0F;
            // 唱片机模式：根据与玩家距离衰减音量
            if (!this.relative) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double dx = this.x - mc.player.getX();
                    double dy = this.y - mc.player.getY();
                    double dz = this.z - mc.player.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double maxDist = 64.0;
                    if (dist >= maxDist) {
                        return 0.0f;
                    }
                    float distFactor = 1.0f - (float)(dist / maxDist);
                    return this.volume * this.fadeVolume * duckScale * distFactor;
                }
            }
            return this.volume * this.fadeVolume * duckScale;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
            // 唱片机模式：超出范围自动停止
            if (!this.relative) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    double dx = this.x - mc.player.getX();
                    double dy = this.y - mc.player.getY();
                    double dz = this.z - mc.player.getZ();
                    if (dx * dx + dy * dy + dz * dz > 65.0 * 65.0) {
                        this.stopped = true;
                    }
                }
            }
        }
    }
}
