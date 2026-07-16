package com.stardew.craft.festival.desert;

import com.stardew.craft.network.payload.DesertFestivalRaceActionPayload;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import com.stardew.craft.network.payload.DesertFestivalRaceStatePayload;
import com.stardew.craft.network.payload.OpenDesertFestivalRacePayload;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class DesertFestivalRaceService {
    public static final String RACE_MAN_TARGET_ID = "desert_festival_race_man";
    public static final String SHADY_GUY_TARGET_ID = "desert_festival_shady_guy";
    public static final String RACE_MAN_MARKER_TAG = "sdv_festival_marker:desert_race_man";
    public static final String SHADY_GUY_MARKER_TAG = "sdv_festival_marker:desert_shady_guy";

    public static final BlockPos RACE_MAN_INTERACTION_MIN = new BlockPos(-186, 64, -166);
    public static final BlockPos RACE_MAN_INTERACTION_MAX = new BlockPos(-184, 65, -165);
    public static final BlockPos SHADY_GUY_INTERACTION_MIN = new BlockPos(-260, 64, -146);
    public static final BlockPos SHADY_GUY_INTERACTION_MAX = new BlockPos(-259, 65, -146);
    public static final BlockPos RACE_MAN_INTERACTION_POS = RACE_MAN_INTERACTION_MIN;
    public static final BlockPos SHADY_GUY_INTERACTION_POS = SHADY_GUY_INTERACTION_MIN;

    private static final String DATA_NAME = "stardew_desert_festival_race";
    private static final int RACER_COUNT = 3;
    private static final int TOTAL_RACERS = 5;
    private static final int FRAMES_PER_SERVER_TICK = 3;
    private static final int START_TIME = 600;
    private static final int LAST_RACE_START_TIME = 1320;
    private static final int RACE_INTERVAL_MINUTES = 120;

    private static final float[][][] RACE_TRACK = new float[][][] {
        {{41f, 39f, 0f}, {42f, 39f, 0f}},
        {{41f, 29f, 0f}, {42f, 28f, 0f}},
        {{6f, 29f, 0f}, {5f, 28f, 0f}},
        {{6f, 35f, 0f}, {5f, 36f, 0f}},
        {{10f, 35f, 2f}, {10f, 36f, 2f}},
        {{12.5f, 35f, 0f}, {12.5f, 36f, 0f}},
        {{17.5f, 35f, 1f}, {17.5f, 36f, 1f}},
        {{23.5f, 35f, 0f}, {23.5f, 36f, 0f}},
        {{28.5f, 35f, 1f}, {28.5f, 36f, 1f}},
        {{31f, 35f, 0f}, {31f, 36f, 0f}},
        {{32f, 35f, 0f}, {31f, 36f, 0f}},
        {{32f, 38f, 3f}, {31f, 38f, 3f}},
        {{32f, 43f, 0f}, {31f, 43f, 0f}},
        {{32f, 46f, 0f}, {31f, 47f, 0f}},
        {{41f, 46f, 0f}, {42f, 47f, 0f}},
        {{41f, 39f, 0f}, {42f, 39f, 0f}}
    };

    private DesertFestivalRaceService() {
    }

    public enum RaceState {
        PRE_RACE,
        STARTING_LINE,
        READY,
        SET,
        GO,
        ANNOUNCE_WINNER,
        ANNOUNCE_WINNER2,
        ANNOUNCE_WINNER3,
        ANNOUNCE_WINNER4,
        RACE_END,
        RACES_OVER
    }

    public static void openRaceScreen(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RaceData data = get(player.server);
        data.ensureCurrentDay();
        data.viewers.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalRacePayload("hub", data.snapshot(player)));
    }

    public static void openShadyGuyScreen(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RaceData data = get(player.server);
        data.ensureCurrentDay();
        data.viewers.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalRacePayload("shady", data.snapshot(player)));
    }

    public static void handleAction(ServerPlayer player, DesertFestivalRaceActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        RaceData data = get(player.server);
        data.ensureCurrentDay();
        switch (payload.action()) {
            case "guess" -> data.guess(player, payload.racerIndex());
            case "sabotage" -> data.sabotage(player, payload.racerIndex());
            case "claim" -> data.claimRewards(player);
            case "create_room" -> data.createRoom(player);
            case "bet" -> data.placeRoomBet(player, payload.roomId(), payload.racerIndex(), payload.amount());
            case "lock_room" -> data.lockRoom(player, payload.roomId());
            case "start_room" -> data.startRoomRace(player, payload.roomId());
            default -> {
            }
        }
        data.setDirty();
        PacketDistributor.sendToPlayer(player, new DesertFestivalRaceStatePayload(data.snapshot(player)));
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RaceData data = get(server);
        data.ensureCurrentDay();
        if (!DesertFestivalService.isFestivalDay()) {
            data.resetForClosedFestival();
            return;
        }
        data.tickRace();
        data.syncViewers(server);
    }

    public static void closeForFestivalCleanup(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RaceData data = get(server);
        data.ensureCurrentDay();
        data.resetForClosedFestival();
        data.syncViewers(server);
    }

    public static RaceData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(RaceData::new, RaceData::load),
            DATA_NAME
        );
    }

    public static final class RaceData extends SavedData {
        private int dateKey = Integer.MIN_VALUE;
        private int lastStartMinute = Integer.MIN_VALUE;
        private int syncTick;
        private int nextRoomId = 1;
        private RaceState currentRaceState = RaceState.PRE_RACE;
        private int raceStateTimerTicks;
        private int lastRaceWinner = -1;
        private String announceKey = "";
        private final List<RacerState> racers = new ArrayList<>();
        private final List<Integer> finishedRacers = new ArrayList<>();
        private final Map<UUID, Integer> sabotages = new HashMap<>();
        private final Map<UUID, Integer> raceGuesses = new HashMap<>();
        private final Map<UUID, Integer> nextRaceGuesses = new HashMap<>();
        private final Map<UUID, Integer> rewardsToCollect = new HashMap<>();
        private final Set<UUID> specialRewardPending = new HashSet<>();
        private final Set<UUID> specialRewardCollected = new HashSet<>();
        private final Map<String, BettingRoom> rooms = new LinkedHashMap<>();
        private final Set<UUID> viewers = new HashSet<>();
        private final Random runtimeRandom = new Random();

        public void ensureCurrentDay() {
            int currentDate = StardewTimeManager.get().getAbsoluteDay();
            if (dateKey != currentDate) {
                resetForNewDay(currentDate);
            }
        }

        private void resetForNewDay(int currentDate) {
            dateKey = currentDate;
            lastStartMinute = Integer.MIN_VALUE;
            currentRaceState = RaceState.PRE_RACE;
            raceStateTimerTicks = 0;
            lastRaceWinner = -1;
            announceKey = "";
            racers.clear();
            finishedRacers.clear();
            sabotages.clear();
            raceGuesses.clear();
            nextRaceGuesses.clear();
            rewardsToCollect.clear();
            specialRewardPending.clear();
            rooms.clear();
            chooseDailyRacers();
            setDirty();
        }

        private void resetForClosedFestival() {
            if (currentRaceState != RaceState.PRE_RACE || !rooms.isEmpty() || !nextRaceGuesses.isEmpty() || !sabotages.isEmpty()) {
                currentRaceState = RaceState.PRE_RACE;
                rooms.clear();
                nextRaceGuesses.clear();
                sabotages.clear();
                setDirty();
            }
        }

        private void chooseDailyRacers() {
            racers.clear();
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < TOTAL_RACERS; i++) {
                pool.add(i);
            }
            long raceSeed = lastStartMinute == Integer.MIN_VALUE ? 0L : lastStartMinute;
            Random random = new Random((long) dateKey * 341873128712L
                + (long) StardewTimeManager.get().getCurrentYear() * 132897987541L
                + raceSeed * 42317861L
                + 7301L);
            for (int i = 0; i < RACER_COUNT; i++) {
                int poolIndex = random.nextInt(pool.size());
                int racerIndex = pool.remove(poolIndex);
                RacerState racer = new RacerState(racerIndex);
                racer.x = 44.5f * 64f;
                racer.y = (37.5f - i) * 64f;
                racer.segmentStartX = racer.x;
                racer.segmentStartY = racer.y;
                racer.segmentEndX = racer.x;
                racer.segmentEndY = racer.y;
                racer.resetMoveSpeed(random);
                racers.add(racer);
            }
        }

        private void tickRace() {
            int currentTime = StardewTimeManager.get().getCurrentTime();
            if (currentRaceState == RaceState.PRE_RACE && DesertFestivalService.isFestivalOpen()
                    && currentTime >= START_TIME && currentTime <= LAST_RACE_START_TIME
                    && (currentTime - START_TIME) % RACE_INTERVAL_MINUTES == 0
                    && lastStartMinute != currentTime) {
                beginRaceCountdown();
                lastStartMinute = currentTime;
            }
            if (raceStateTimerTicks > 0) {
                raceStateTimerTicks--;
                if (raceStateTimerTicks <= 0) {
                    advanceTimedState();
                }
            }
            if (currentRaceState == RaceState.GO) {
                if (finishedRacers.size() >= RACER_COUNT) {
                    currentRaceState = RaceState.ANNOUNCE_WINNER;
                    raceStateTimerTicks = seconds(2);
                    setDirty();
                } else {
                    for (RacerState racer : racers) {
                        racer.updateRaceProgress();
                    }
                }
            }
            if (currentRaceState.ordinal() >= RaceState.STARTING_LINE.ordinal() && currentRaceState != RaceState.RACES_OVER) {
                for (int i = 0; i < FRAMES_PER_SERVER_TICK; i++) {
                    for (RacerState racer : racers) {
                        racer.update(this, runtimeRandom, 1f / 60f);
                    }
                }
            }
        }

        private void beginRaceCountdown() {
            if (racers.size() != RACER_COUNT) {
                chooseDailyRacers();
            }
            resetRacersAtFinishLine();
            finishedRacers.clear();
            currentRaceState = RaceState.STARTING_LINE;
            raceStateTimerTicks = seconds(5);
            announce("Race_Begin");
            for (BettingRoom room : rooms.values()) {
                if (room.open && !room.settled) {
                    room.open = false;
                }
            }
            setDirty();
        }

        private void resetRacersAtFinishLine() {
            for (int i = 0; i < racers.size(); i++) {
                RacerState racer = racers.get(i);
                racer.x = 44.5f * 64f;
                racer.y = (37.5f - i) * 64f;
                racer.segmentStartX = racer.x;
                racer.segmentStartY = racer.y;
                racer.segmentEndX = racer.x;
                racer.segmentEndY = racer.y;
                racer.currentTrackIndex = -1;
                racer.horizontalPosition = -1f;
                racer.jumping = false;
                racer.tripping = false;
                racer.drawAboveMap = false;
                racer.height = 0f;
                racer.tripTimer = 0f;
                racer.gravity = 0f;
                racer.sabotages = 0;
                racer.direction = 3;
                racer.moving = false;
                racer.lastX = Float.NaN;
                racer.lastY = Float.NaN;
            }
        }

        private void advanceTimedState() {
            switch (currentRaceState) {
                case STARTING_LINE -> {
                    announce("Race_Ready");
                    currentRaceState = RaceState.READY;
                    raceStateTimerTicks = seconds(3);
                }
                case READY -> {
                    announce("Race_Set");
                    currentRaceState = RaceState.SET;
                    raceStateTimerTicks = seconds(3);
                }
                case SET -> startRace();
                case ANNOUNCE_WINNER -> {
                    announce("Race_Comment_" + (runtimeRandom.nextInt(4) + 1));
                    currentRaceState = RaceState.ANNOUNCE_WINNER2;
                    raceStateTimerTicks = seconds(4);
                }
                case ANNOUNCE_WINNER2 -> {
                    announce("Race_Winner");
                    currentRaceState = RaceState.ANNOUNCE_WINNER3;
                    raceStateTimerTicks = seconds(2);
                }
                case ANNOUNCE_WINNER3 -> {
                    announce("Racer_" + lastRaceWinner);
                    currentRaceState = RaceState.ANNOUNCE_WINNER4;
                    raceStateTimerTicks = seconds(4);
                }
                case ANNOUNCE_WINNER4 -> {
                    announce("RESULT");
                    currentRaceState = RaceState.RACE_END;
                    raceStateTimerTicks = seconds(2);
                    finishedRacers.clear();
                }
                case RACE_END -> {
                    if (!canMakeAnotherRaceGuess()) {
                        announce(StardewTimeManager.get().getCurrentDay() >= 17 ? "Race_Close_LastDay" : "Race_Close");
                        currentRaceState = RaceState.RACES_OVER;
                    } else {
                        chooseDailyRacers();
                        currentRaceState = RaceState.PRE_RACE;
                    }
                    raceStateTimerTicks = 0;
                }
                default -> {
                }
            }
            setDirty();
        }

        private void startRace() {
            currentRaceState = RaceState.GO;
            announce("Race_Go");
            raceGuesses.clear();
            raceGuesses.putAll(nextRaceGuesses);
            nextRaceGuesses.clear();
            for (RacerState racer : racers) {
                racer.sabotages = 0;
                for (int value : sabotages.values()) {
                    if (value == racer.racerIndex) {
                        racer.sabotages++;
                    }
                }
                racer.resetMoveSpeed(runtimeRandom);
            }
            sabotages.clear();
            raceStateTimerTicks = seconds(3);
            for (BettingRoom room : rooms.values()) {
                if (!room.settled) {
                    room.open = false;
                }
            }
        }

        private void onRaceWon(int winner) {
            lastRaceWinner = winner;
            if (!raceGuesses.isEmpty()) {
                for (Map.Entry<UUID, Integer> entry : raceGuesses.entrySet()) {
                    if (entry.getValue() != winner) {
                        continue;
                    }
                    if (winner == 3 && !specialRewardCollected.contains(entry.getKey())) {
                        specialRewardPending.add(entry.getKey());
                    } else {
                        rewardsToCollect.merge(entry.getKey(), 1, Integer::sum);
                    }
                }
            }
            settleRooms(winner);
            setDirty();
        }

        private void settleRooms(int winner) {
            for (BettingRoom room : rooms.values()) {
                if (room.settled || room.bets.isEmpty()) {
                    continue;
                }
                room.open = false;
                room.settled = true;
                room.winningRacer = winner;
                int pool = room.totalPool();
                int winningPool = room.poolFor(winner);
                if (winningPool <= 0) {
                    continue;
                }
                for (RoomBet bet : room.bets.values()) {
                    if (bet.racerIndex == winner) {
                        bet.payout = Math.max(1, bet.amount * pool / winningPool);
                    }
                }
            }
        }

        private boolean canMakeAnotherRaceGuess() {
            int currentTime = StardewTimeManager.get().getCurrentTime();
            return currentTime < LAST_RACE_START_TIME || currentRaceState.ordinal() < RaceState.GO.ordinal();
        }

        private boolean canActOnNextRace() {
            return DesertFestivalService.isFestivalOpen()
                && canMakeAnotherRaceGuess()
                && !(currentRaceState.ordinal() >= RaceState.GO.ordinal() && currentRaceState.ordinal() < RaceState.ANNOUNCE_WINNER4.ordinal());
        }

        private void guess(ServerPlayer player, int racerIndex) {
            if (!canActOnNextRace() || !isRacerInRace(racerIndex)) {
                toast(player, "stardewcraft.desert_festival.race.late");
                return;
            }
            if (nextRaceGuesses.containsKey(player.getUUID())) {
                toast(player, "stardewcraft.desert_festival.race.already_guessed");
                return;
            }
            nextRaceGuesses.put(player.getUUID(), racerIndex);
            toast(player, "stardewcraft.desert_festival.race.guess_made");
        }

        private void sabotage(ServerPlayer player, int racerIndex) {
            if (!canActOnNextRace() || !isRacerInRace(racerIndex)) {
                toast(player, "stardewcraft.desert_festival.race.shady_late");
                return;
            }
            if (sabotages.containsKey(player.getUUID())) {
                toast(player, "stardewcraft.desert_festival.race.shady_already");
                return;
            }
            if (!DesertFestivalService.consumeEggs(player, 1)) {
                toast(player, "stardewcraft.desert_festival.race.no_egg");
                return;
            }
            sabotages.put(player.getUUID(), racerIndex);
            toast(player, "stardewcraft.desert_festival.race.shady_done");
        }

        private void claimRewards(ServerPlayer player) {
            UUID playerId = player.getUUID();
            int totalEggs = 0;
            if (specialRewardPending.remove(playerId)) {
                specialRewardCollected.add(playerId);
                totalEggs += 100;
            }
            int claims = rewardsToCollect.getOrDefault(playerId, 0);
            if (claims > 0) {
                rewardsToCollect.remove(playerId);
                totalEggs += claims * 20;
            }
            for (BettingRoom room : rooms.values()) {
                RoomBet bet = room.bets.get(playerId);
                if (bet != null && bet.payout > 0 && !bet.claimed) {
                    totalEggs += bet.payout;
                    bet.claimed = true;
                }
            }
            if (totalEggs > 0) {
                DesertFestivalService.giveEggs(player, totalEggs);
                toast(player, "stardewcraft.desert_festival.race.claimed");
            } else {
                toast(player, "stardewcraft.desert_festival.race.no_rewards");
            }
        }

        private void createRoom(ServerPlayer player) {
            String id = "R" + nextRoomId++;
            rooms.put(id, new BettingRoom(id, player.getUUID(),
                    com.stardew.craft.player.PlayerDisplayName.get(player)));
            toast(player, "stardewcraft.desert_festival.race.room_created");
        }

        private void placeRoomBet(ServerPlayer player, String roomId, int racerIndex, int amount) {
            BettingRoom room = rooms.get(roomId);
            int betAmount = Math.max(1, Math.min(999, amount));
            if (room == null || !room.open || !canActOnNextRace() || !isRacerInRace(racerIndex)) {
                toast(player, "stardewcraft.desert_festival.race.bet_closed");
                return;
            }
            if (room.bets.containsKey(player.getUUID())) {
                toast(player, "stardewcraft.desert_festival.race.bet_already");
                return;
            }
            if (!DesertFestivalService.consumeEggs(player, betAmount)) {
                toast(player, "stardewcraft.desert_festival.race.no_egg");
                return;
            }
            room.bets.put(player.getUUID(), new RoomBet(racerIndex, betAmount));
            toast(player, "stardewcraft.desert_festival.race.bet_made");
        }

        private void lockRoom(ServerPlayer player, String roomId) {
            BettingRoom room = rooms.get(roomId);
            if (room != null && room.host.equals(player.getUUID()) && room.open) {
                room.open = false;
                toast(player, "stardewcraft.desert_festival.race.room_locked_toast");
            }
        }

        private void startRoomRace(ServerPlayer player, String roomId) {
            BettingRoom room = rooms.get(roomId);
            if (room == null || !room.host.equals(player.getUUID()) || currentRaceState != RaceState.PRE_RACE || !DesertFestivalService.isFestivalOpen()) {
                toast(player, "stardewcraft.desert_festival.race.cannot_start");
                return;
            }
            room.open = false;
            beginRaceCountdown();
        }

        private boolean isRacerInRace(int racerIndex) {
            for (RacerState racer : racers) {
                if (racer.racerIndex == racerIndex) {
                    return true;
                }
            }
            return false;
        }

        private void syncViewers(MinecraftServer server) {
            syncTick++;
            if (syncTick % 4 != 0) {
                return;
            }
            viewers.removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
            for (UUID playerId : viewers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new DesertFestivalRaceStatePayload(snapshot(player)));
                }
            }
        }

        private DesertFestivalRaceSnapshot snapshot(ServerPlayer player) {
            UUID playerId = player.getUUID();
            List<DesertFestivalRaceSnapshot.RacerEntry> racerEntries = racers.stream()
                .sorted(Comparator.comparingDouble(racer -> racer.y))
                .map(racer -> new DesertFestivalRaceSnapshot.RacerEntry(racer.racerIndex, racer.x, racer.y, racer.direction,
                    racer.frame, racer.jumping, racer.tripping, racer.drawAboveMap, racer.height, racer.progress, racer.sabotages))
                .toList();
            List<DesertFestivalRaceSnapshot.RoomEntry> roomEntries = rooms.values().stream()
                .map(room -> room.snapshot(playerId, racers))
                .toList();
            return new DesertFestivalRaceSnapshot(currentRaceState.name(), raceStateTimerTicks,
                StardewTimeManager.get().getCurrentTime(), DesertFestivalService.countEggs(player), lastRaceWinner,
                raceGuesses.getOrDefault(playerId, -1), nextRaceGuesses.getOrDefault(playerId, -1),
                sabotages.getOrDefault(playerId, -1), rewardsToCollect.getOrDefault(playerId, 0),
                specialRewardPending.contains(playerId), canActOnNextRace(), announceKey, racerEntries, roomEntries);
        }

        private void announce(String key) {
            announceKey = key;
        }

        private void toast(ServerPlayer player, String key) {
            player.displayClientMessage(Component.translatable(key), true);
        }

        private int seconds(int seconds) {
            return seconds * 20;
        }

        @Override
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putInt("DateKey", dateKey);
            tag.putInt("NextRoomId", nextRoomId);
            tag.put("Rewards", intMap(rewardsToCollect));
            tag.put("SpecialPending", uuidSet(specialRewardPending));
            tag.put("SpecialCollected", uuidSet(specialRewardCollected));
            tag.put("Rooms", roomsTag());
            return tag;
        }

        public static RaceData load(CompoundTag tag, HolderLookup.Provider registries) {
            RaceData data = new RaceData();
            data.dateKey = tag.getInt("DateKey");
            data.nextRoomId = Math.max(1, tag.getInt("NextRoomId"));
            readIntMap(tag.getList("Rewards", Tag.TAG_COMPOUND), data.rewardsToCollect);
            readUuidSet(tag.getList("SpecialPending", Tag.TAG_COMPOUND), data.specialRewardPending);
            readUuidSet(tag.getList("SpecialCollected", Tag.TAG_COMPOUND), data.specialRewardCollected);
            readRooms(tag.getList("Rooms", Tag.TAG_COMPOUND), data.rooms);
            for (String id : data.rooms.keySet()) {
                if (id.startsWith("R")) {
                    try {
                        data.nextRoomId = Math.max(data.nextRoomId, Integer.parseInt(id.substring(1)) + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            data.chooseDailyRacers();
            return data;
        }

        private ListTag roomsTag() {
            ListTag list = new ListTag();
            for (BettingRoom room : rooms.values()) {
                CompoundTag roomTag = new CompoundTag();
                roomTag.putString("Id", room.id);
                roomTag.putUUID("Host", room.host);
                roomTag.putString("HostName", room.hostName);
                roomTag.putBoolean("Open", room.open);
                roomTag.putBoolean("Settled", room.settled);
                roomTag.putInt("WinningRacer", room.winningRacer);
                ListTag betList = new ListTag();
                for (Map.Entry<UUID, RoomBet> entry : room.bets.entrySet()) {
                    CompoundTag betTag = new CompoundTag();
                    betTag.putUUID("Player", entry.getKey());
                    betTag.putInt("Racer", entry.getValue().racerIndex);
                    betTag.putInt("Amount", entry.getValue().amount);
                    betTag.putInt("Payout", entry.getValue().payout);
                    betTag.putBoolean("Claimed", entry.getValue().claimed);
                    betList.add(betTag);
                }
                roomTag.put("Bets", betList);
                list.add(roomTag);
            }
            return list;
        }

        private static void readRooms(ListTag list, Map<String, BettingRoom> target) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag roomTag = list.getCompound(i);
                BettingRoom room = new BettingRoom(roomTag.getString("Id"), roomTag.getUUID("Host"), roomTag.getString("HostName"));
                room.open = roomTag.getBoolean("Open");
                room.settled = roomTag.getBoolean("Settled");
                room.winningRacer = roomTag.getInt("WinningRacer");
                ListTag betList = roomTag.getList("Bets", Tag.TAG_COMPOUND);
                for (int j = 0; j < betList.size(); j++) {
                    CompoundTag betTag = betList.getCompound(j);
                    RoomBet bet = new RoomBet(betTag.getInt("Racer"), betTag.getInt("Amount"));
                    bet.payout = betTag.getInt("Payout");
                    bet.claimed = betTag.getBoolean("Claimed");
                    room.bets.put(betTag.getUUID("Player"), bet);
                }
                target.put(room.id, room);
            }
        }

        private static ListTag intMap(Map<UUID, Integer> values) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Integer> entry : values.entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Player", entry.getKey());
                tag.putInt("Value", entry.getValue());
                list.add(tag);
            }
            return list;
        }

        private static void readIntMap(ListTag list, Map<UUID, Integer> target) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                target.put(tag.getUUID("Player"), tag.getInt("Value"));
            }
        }

        private static ListTag uuidSet(Set<UUID> values) {
            ListTag list = new ListTag();
            for (UUID value : values) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Player", value);
                list.add(tag);
            }
            return list;
        }

        private static void readUuidSet(ListTag list, Set<UUID> target) {
            for (int i = 0; i < list.size(); i++) {
                target.add(list.getCompound(i).getUUID("Player"));
            }
        }
    }

    private static final class BettingRoom {
        final String id;
        final UUID host;
        final String hostName;
        final Map<UUID, RoomBet> bets = new HashMap<>();
        boolean open = true;
        boolean settled;
        int winningRacer = -1;

        BettingRoom(String id, UUID host, String hostName) {
            this.id = id;
            this.host = host;
            this.hostName = hostName;
        }

        int totalPool() {
            int total = 0;
            for (RoomBet bet : bets.values()) {
                total += bet.amount;
            }
            return total;
        }

        int poolFor(int racerIndex) {
            int total = 0;
            for (RoomBet bet : bets.values()) {
                if (bet.racerIndex == racerIndex) {
                    total += bet.amount;
                }
            }
            return total;
        }

        DesertFestivalRaceSnapshot.RoomEntry snapshot(UUID playerId, List<RacerState> racers) {
            int totalPool = totalPool();
            RoomBet playerBet = bets.get(playerId);
            List<DesertFestivalRaceSnapshot.OddsEntry> odds = new ArrayList<>();
            for (RacerState racer : racers) {
                int pool = poolFor(racer.racerIndex);
                int projected = pool <= 0 ? 0 : Math.max(1, totalPool / pool);
                odds.add(new DesertFestivalRaceSnapshot.OddsEntry(racer.racerIndex, pool, projected));
            }
            return new DesertFestivalRaceSnapshot.RoomEntry(id, hostName, host.equals(playerId), open, settled, winningRacer, totalPool,
                bets.size(), playerBet == null ? -1 : playerBet.racerIndex,
                playerBet == null ? 0 : playerBet.amount,
                playerBet == null ? 0 : playerBet.payout,
                playerBet != null && playerBet.claimed,
                odds);
        }
    }

    private static final class RoomBet {
        final int racerIndex;
        final int amount;
        int payout;
        boolean claimed;

        RoomBet(int racerIndex, int amount) {
            this.racerIndex = racerIndex;
            this.amount = amount;
        }
    }

    private static final class RacerState {
        final int racerIndex;
        float x;
        float y;
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        int direction = 3;
        float horizontalPosition = -1f;
        int currentTrackIndex = -1;
        float segmentStartX;
        float segmentStartY;
        float segmentEndX;
        float segmentEndY;
        float jumpSegmentStartX;
        float jumpSegmentStartY;
        float jumpSegmentEndX;
        float jumpSegmentEndY;
        boolean jumping;
        boolean tripping;
        boolean drawAboveMap;
        boolean moving;
        float moveSpeed = 3f;
        float minMoveSpeed = 3f;
        float maxMoveSpeed = 6f;
        float height;
        float tripTimer;
        boolean frame;
        float nextFrameSwap;
        float burstDuration;
        float nextBurst;
        float extraLuck;
        float gravity;
        int tripLeaps;
        float progress;
        int sabotages;

        RacerState(int racerIndex) {
            this.racerIndex = racerIndex;
        }

        void resetMoveSpeed(Random random) {
            minMoveSpeed = 1.5f;
            maxMoveSpeed = 4f;
            extraLuck = randomFloat(random, -0.25f, 0.25f);
            if (racerIndex == 3) {
                minMoveSpeed = 0.5f;
                maxMoveSpeed = 3.5f;
            }
            speedBurst(random);
        }

        void updateRaceProgress() {
            if (currentTrackIndex < 0) {
                progress = RACE_TRACK.length;
                return;
            }
            float dx = segmentEndX - segmentStartX;
            float dy = segmentEndY - segmentStartY;
            float segmentLength = length(dx, dy);
            if (segmentLength <= 0f) {
                progress = currentTrackIndex;
                return;
            }
            dx /= segmentLength;
            dy /= segmentLength;
            float positionInSegment = (x - segmentStartX) * dx + (y - segmentStartY) * dy;
            progress = currentTrackIndex + positionInSegment / segmentLength;
        }

        void update(RaceData data, Random random, float deltaSeconds) {
            boolean hasMoved = false;
            if (data.currentRaceState == RaceState.STARTING_LINE && currentTrackIndex < 0) {
                if (horizontalPosition < 0f) {
                    int index = data.racers.indexOf(this);
                    horizontalPosition = (float) index / (float) (RACER_COUNT - 1);
                }
                currentTrackIndex = 0;
                float[] trackPosition = trackPosition(currentTrackIndex, horizontalPosition);
                segmentStartX = x;
                segmentStartY = y;
                segmentEndX = trackPosition[0];
                segmentEndY = trackPosition[1];
            }
            float frameTravel = maxMoveSpeed;
            if (data.currentRaceState == RaceState.GO) {
                if (data.finishedRacers.isEmpty()) {
                    if (burstDuration > 0f) {
                        moveSpeed = maxMoveSpeed;
                        burstDuration -= deltaSeconds;
                        if (burstDuration <= 0f) {
                            burstDuration = 0f;
                            nextBurst = randomFloat(random, 0.75f, 1.5f);
                            if (random.nextDouble() + extraLuck < 0.25) {
                                nextBurst *= 0.5f;
                            }
                            if (racerIndex == 3) {
                                nextBurst *= 0.25f;
                            }
                            float lastPlace = RACE_TRACK.length;
                            for (RacerState racer : data.racers) {
                                lastPlace = Math.min(lastPlace, racer.progress);
                            }
                            if (progress > lastPlace && random.nextDouble() < Math.min(0.05f + sabotages * 0.2f, 0.5f)) {
                                tripping = true;
                                tripTimer = randomFloat(random, 1.5f, 2f);
                            }
                        }
                    } else if (nextBurst > 0f) {
                        moveSpeed = moveTowards(moveSpeed, minMoveSpeed, 0.5f);
                        nextBurst -= deltaSeconds;
                        if (nextBurst <= 0f) {
                            speedBurst(random);
                            nextBurst = 0f;
                        }
                    }
                    frameTravel = moveSpeed;
                }
                if (tripTimer > 0f) {
                    tripTimer -= deltaSeconds;
                    if (tripTimer < 0f) {
                        tripTimer = 0f;
                        tripping = false;
                    }
                }
            }
            if (jumping) {
                frameTravel = length(segmentEndX - segmentStartX, segmentEndY - segmentStartY) / 64f > 3f ? 6f : 3f;
            } else if (tripping) {
                frameTravel = 0.25f;
            }
            if (segmentStartX == segmentEndX && segmentStartY == segmentEndY && x == segmentEndX && y == segmentEndY && currentTrackIndex < 0) {
                frameTravel = 0f;
            }
            while (frameTravel > 0f) {
                float remaining = length(segmentEndX - x, segmentEndY - y);
                float movedAmount = Math.min(remaining, frameTravel);
                frameTravel -= movedAmount;
                float dx = segmentEndX - x;
                float dy = segmentEndY - y;
                if (dx != 0f || dy != 0f) {
                    float len = length(dx, dy);
                    dx /= len;
                    dy /= len;
                    x += dx * movedAmount;
                    y += dy * movedAmount;
                    hasMoved = true;
                    if (Math.abs(dy) > Math.abs(dx)) {
                        direction = dy < 0f ? 0 : 2;
                    } else {
                        direction = dx < 0f ? 3 : 1;
                    }
                }
                if (length(x - segmentEndX, y - segmentEndY) >= 0.01f) {
                    continue;
                }
                x = segmentEndX;
                y = segmentEndY;
                if (data.currentRaceState == RaceState.GO && currentTrackIndex >= 0) {
                    float[] oldTrackPosition = trackPosition(currentTrackIndex, horizontalPosition);
                    if (oldTrackPosition[2] > 0f) {
                        tripping = false;
                        tripTimer = 0f;
                        jumping = true;
                    } else {
                        jumping = false;
                    }
                    if (oldTrackPosition[2] == 2f) {
                        drawAboveMap = true;
                    } else if (oldTrackPosition[2] == 3f) {
                        drawAboveMap = false;
                    }
                    currentTrackIndex++;
                    if (currentTrackIndex >= RACE_TRACK.length) {
                        currentTrackIndex = -2;
                        segmentStartX = segmentEndX;
                        segmentStartY = segmentEndY;
                        segmentEndX = 44.5f * 64f;
                        segmentEndY = (37.5f - data.finishedRacers.size()) * 64f;
                        horizontalPosition = (float) (RACER_COUNT - 1 - data.finishedRacers.size()) / (float) (RACER_COUNT - 1);
                        data.finishedRacers.add(racerIndex);
                        if (data.finishedRacers.size() == 1) {
                            data.announce("Race_Finish");
                            data.onRaceWon(racerIndex);
                        }
                    } else {
                        float[] trackPosition = trackPosition(currentTrackIndex, horizontalPosition);
                        segmentStartX = segmentEndX;
                        segmentStartY = segmentEndY;
                        segmentEndX = trackPosition[0];
                        segmentEndY = trackPosition[1];
                    }
                    if (jumping) {
                        jumpSegmentStartX = segmentStartX;
                        jumpSegmentStartY = segmentStartY;
                        jumpSegmentEndX = segmentEndX;
                        jumpSegmentEndY = segmentEndY;
                    }
                } else {
                    frameTravel = 0f;
                    segmentStartX = segmentEndX;
                    segmentStartY = segmentEndY;
                    direction = data.currentRaceState.ordinal() >= RaceState.STARTING_LINE.ordinal()
                        && data.currentRaceState.ordinal() < RaceState.GO.ordinal() ? 0 : 3;
                }
            }
            moving = hasMoved;
            animate(random, deltaSeconds);
        }

        private void animate(Random random, float deltaSeconds) {
            if (Float.isNaN(lastX)) {
                lastX = x;
                lastY = y;
            }
            float distanceTraveled = length(lastX - x, lastY - y);
            nextFrameSwap -= distanceTraveled;
            while (nextFrameSwap <= 0f) {
                frame = !frame;
                nextFrameSwap += 8f;
            }
            lastX = x;
            lastY = y;
            if (!jumping) {
                if (moving) {
                    if (tripping && height == 0f) {
                        gravity = tripLeaps == 0 ? 1f : randomFloat(random, 0.5f, 0.75f);
                        tripLeaps++;
                    } else if (racerIndex == 2 && height == 0f) {
                        gravity = randomFloat(random, 0.25f, 0.5f);
                    }
                }
                if (height != 0f || gravity != 0f) {
                    height += gravity;
                    gravity -= deltaSeconds * 2f;
                    if (gravity == 0f) {
                        gravity = -0.0001f;
                    }
                    if (height <= 0f) {
                        gravity = 0f;
                        height = 0f;
                    }
                }
            }
            if (!tripping) {
                tripLeaps = 0;
            }
            if (jumping) {
                float dx = jumpSegmentEndX - jumpSegmentStartX;
                float dy = jumpSegmentEndY - jumpSegmentStartY;
                float segmentLength = length(dx, dy);
                if (segmentLength > 0f) {
                    dx /= segmentLength;
                    dy /= segmentLength;
                    float positionInSegment = (x - jumpSegmentStartX) * dx + (y - jumpSegmentStartY) * dy;
                    height = (float) Math.sin(clamp(positionInSegment / segmentLength, 0f, 1f) * Math.PI) * 48f;
                }
            } else if (gravity == 0f) {
                height = 0f;
            }
        }

        private void speedBurst(Random random) {
            burstDuration = randomFloat(random, 0.25f, 1f);
            if (random.nextDouble() + extraLuck < 0.25) {
                burstDuration *= 2f;
            }
            if (racerIndex == 3) {
                burstDuration *= 0.25f;
            }
            moveSpeed = maxMoveSpeed;
        }
    }

    private static float[] trackPosition(int trackIndex, float horizontalPosition) {
        float innerX = RACE_TRACK[trackIndex][0][0] + 0.5f;
        float innerY = RACE_TRACK[trackIndex][0][1] + 0.5f;
        float outerX = RACE_TRACK[trackIndex][1][0] + 0.5f;
        float outerY = RACE_TRACK[trackIndex][1][1] + 0.5f;
        float deltaX = outerX - innerX;
        float deltaY = outerY - innerY;
        float deltaLength = length(deltaX, deltaY);
        if (deltaLength > 0f) {
            deltaX /= deltaLength;
            deltaY /= deltaLength;
        }
        innerX = innerX * 64f - deltaX * 16f;
        innerY = innerY * 64f - deltaY * 16f;
        outerX = outerX * 64f + deltaX * 16f;
        outerY = outerY * 64f + deltaY * 16f;
        return new float[] {lerp(innerX, outerX, horizontalPosition), lerp(innerY, outerY, horizontalPosition), RACE_TRACK[trackIndex][0][2]};
    }

    private static float randomFloat(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
