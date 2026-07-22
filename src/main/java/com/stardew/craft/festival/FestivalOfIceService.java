package com.stardew.craft.festival;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.IceFishingCutsceneStatePayload;
import com.stardew.craft.network.payload.IceFishingHudStatePayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.PlayerDisplayName;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.stardew.craft.festival.FestivalNpcActorRuntime.canonical;

public final class FestivalOfIceService {
    public static final String FESTIVAL_ID = "winter8";
    private static final String TEMPORARY_ROD_OWNER = "winter8_ice_fishing";
    public static final String TRAVELING_MERCHANT_TARGET_ID = "festival_of_ice_traveling_merchant";

    private static final String OVERLAY_ID = "Forest-IceFestival";
    private static final String SHOP_ID = "Festival_FestivalOfIce_TravelingMerchant";
    private static final String TRAVELING_MERCHANT_MARKER_TAG = "sdv_festival_marker:festival_of_ice_traveling_merchant";
    private static final String MAIN_EVENT_CUTSCENE_ID = "festival_of_ice_main_event";
    private static final String END_CUTSCENE_ID = "festival_of_ice_award";
    private static final String ICE_FISHING_SCOREBOARD_OBJECTIVE = "sdvIceFish";
    private static final String ICE_FESTIVAL_WIN_FLAG = "Ice Festival";

    private static final String TAG_PARTICIPATING = "stardewcraft_festival_of_ice_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_festival_of_ice_music_synced";

    private static final int FESTIVAL_START_MINUTE = 9 * 60;
    private static final int FESTIVAL_END_MINUTE = 22 * 60;
    private static final int ICE_FISHING_DURATION_TICKS = 120 * 20;
    private static final int FISH_TO_WIN = 5;
    private static final float SOUTH_YAW = 0.0F;

    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(new BlockPos(-187, 95, 13), new BlockPos(-61, 61, 82));
    private static final BlockPos TRAVELING_MERCHANT_INTERACTION_POS = new BlockPos(-128, 65, 54);
    private static final BlockPos PLAYER_CONTEST_POS = new BlockPos(-110, 64, 57);
    private static final AABB TEMP_ROD_CLEANUP_BOUNDS = AABB.ofSize(
        new Vec3(-110.0D, 64.0D, 57.0D), 48.0D, 10.0D, 48.0D);

    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> START_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START);
    private static final Set<UUID> START_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START);
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_PARTICIPANTS = new LinkedHashSet<>();
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_DONE = new LinkedHashSet<>();
    private static final Set<UUID> LEWIS_DIALOGUE_SEEN = new LinkedHashSet<>();
    private static final Set<UUID> WINNERS = new LinkedHashSet<>();
    private static final Map<UUID, IceFishingPlayerState> ICE_FISHING_PLAYERS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();

    private static Integer frozenMinute;
    private static boolean debugRequested;
    private static MainEventPhase mainEventPhase = MainEventPhase.FREE;
    private static int iceFishingTicksRemaining;
    private static int iceFishingHudSyncTicks;
    private static Component winnerDialogue = Component.empty();
    private static boolean playerCanWinPrize;

    private FestivalOfIceService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!isActiveFestivalOfIceDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID) && !debugRequested) {
            clearRuntimeState(level);
            return;
        }
        if (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            stopTimeFreeze();
        }
        syncFestivalInteractions(level);
        tickIceFishingContest(level);
        syncParticipantMusic(level);
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player);
        }
    }

    public static void startDebugFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalService.setDebugActiveFestival(FESTIVAL_ID);
        startTimeFreeze(level);
        debugRequested = true;
        CONFIRM_STATE.clearAll();
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        }
        syncFestivalInteractions(level);
        FestivalOfIceNpcService.requestDebugStart(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                removeTemporaryFishingRods(player);
                player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
                clearClientStateIfNeeded(player);
            }
            removeFestivalInteractions(level);
            clearIceFishingScoreboard(level);
            restoreNpcs(level);
        }
        clearFestivalState();
    }

    public static void onMapOverlayApplied(ServerLevel level) {
        syncFestivalInteractions(level);
    }

    public static void requestDebugNpcs(ServerLevel level) {
        debugRequested = true;
        FestivalOfIceNpcService.requestDebugStart(level);
    }

    public static void restoreNpcs(ServerLevel level) {
        debugRequested = false;
        FestivalOfIceNpcService.restore(level);
    }

    public static String debugNpcStatus(ServerLevel level) {
        return FestivalOfIceNpcService.debugStatus(level);
    }

    public static boolean controlsNpc(String npcId) {
        return FestivalOfIceNpcService.controlsNpc(npcId);
    }

    public static boolean isParticipant(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.getPersistentData().getBoolean(TAG_PARTICIPATING)) {
            return true;
        }
        return currentSession(player.serverLevel())
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActiveFestivalOfIceDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            clearClientStateIfNeeded(player);
            return;
        }
        boolean sessionParticipant = currentSession(level)
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
        if (sessionParticipant || player.getPersistentData().getBoolean(TAG_PARTICIPATING)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            startTimeFreeze(level);
            syncFestivalMusic(player, currentFestivalMusicTrack());
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            syncIceFishingHud(player, isFishingContestActive(player));
            return;
        }
        clearClientStateIfNeeded(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CONFIRM_STATE.clearPlayerDialogs(player.getUUID());
        if (!EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            checkExitVote(player.serverLevel());
        }
        if (!START_VOTE_PARTICIPANTS.isEmpty()) {
            checkStartVote(player.serverLevel());
        }
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player != null && "lewis".equals(canonical(npcId))) {
            LEWIS_DIALOGUE_SEEN.add(player.getUUID());
        }
    }

    public static String resolveDialogueKey(ServerPlayer player, String npcId) {
        return FestivalOfIceNpcService.resolveDialogueKey(player, npcId);
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.EXIT, OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START),
            FestivalOfIceService::isParticipant,
            FestivalOfIceService::isActiveFestivalOfIceDay
        );
    }

    public static void onPlayerConfirmed(ServerPlayer player, OpenFestivalConfirmPayload.Action action, boolean confirmed) {
        if (player == null || action == null) {
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.ENTER) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.ENTER);
            if (confirmed) {
                enterFestival(player);
            }
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START);
            if (confirmed) {
                castStartVote(player);
            } else {
                START_VOTES.remove(player.getUUID());
            }
            return;
        }
        if (action != OpenFestivalConfirmPayload.Action.EXIT) {
            return;
        }
        CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.EXIT);
        if (!isParticipant(player)) {
            return;
        }
        if (confirmed) {
            castExitVote(player);
        } else {
            EXIT_VOTES.remove(player.getUUID());
        }
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        return false;
    }

    public static boolean openTravelingMerchantShop(ServerPlayer player) {
        if (player == null || !isParticipant(player)) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(SHOP_ID);
        if (shop == null) {
            player.displayClientMessage(Component.literal("Unknown shopId: " + SHOP_ID), true);
            return false;
        }
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            SHOP_ID,
            PlayerStardewDataAPI.getMoney(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            List.copyOf(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static boolean isMainEventActive() {
        return mainEventPhase != MainEventPhase.FREE;
    }

    public static boolean tryStartMainEvent(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventPhase != MainEventPhase.FREE
            || !LEWIS_DIALOGUE_SEEN.contains(player.getUUID())) {
            return false;
        }
        if (START_VOTES.contains(player.getUUID())) {
            List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), START_VOTE_PARTICIPANTS);
            int voteCount = voteCount(voters, START_VOTES);
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.ice_fishing.start_vote_waiting", voteCount, voters.size()), true);
            return true;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.FESTIVAL_OF_ICE_START);
        return true;
    }

    public static void onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (player == null || eventId == null) {
            return;
        }
        if (MAIN_EVENT_CUTSCENE_ID.equals(eventId) && mainEventPhase == MainEventPhase.INTRO_CUTSCENE) {
            MAIN_EVENT_CUTSCENE_DONE.add(player.getUUID());
            if (MAIN_EVENT_CUTSCENE_DONE.containsAll(MAIN_EVENT_CUTSCENE_PARTICIPANTS)) {
                beginIceFishingContest(player.serverLevel());
            }
            return;
        }
        if (END_CUTSCENE_ID.equals(eventId) && mainEventPhase == MainEventPhase.AWARD_CUTSCENE) {
            MAIN_EVENT_CUTSCENE_DONE.add(player.getUUID());
            if (MAIN_EVENT_CUTSCENE_DONE.containsAll(MAIN_EVENT_CUTSCENE_PARTICIPANTS)) {
                finishFestival(player.serverLevel());
            }
        }
    }

    public static boolean canStartFishingCast(ServerPlayer player) {
        if (!isFishingContestActive(player)) {
            return true;
        }
        return mainEventPhase == MainEventPhase.FISHING && iceFishingTicksRemaining > 0;
    }

    public static boolean isFishingContestActive(ServerPlayer player) {
        return player != null && mainEventPhase == MainEventPhase.FISHING && ICE_FISHING_PLAYERS.containsKey(player.getUUID());
    }

    public static boolean isUsableFishingContestRod(ServerPlayer player, ItemStack rodStack) {
        if (!isFishingContestActive(player)) {
            return rodStack != null && !rodStack.isEmpty() && rodStack.getItem() instanceof FishingRodItem;
        }
        return FishingRodItem.isFairTemporaryRod(rodStack, TEMPORARY_ROD_OWNER);
    }

    public static void onFishingCatch(ServerPlayer player, boolean fish) {
        if (player == null || !isFishingContestActive(player)) {
            return;
        }
        if (!fish || iceFishingTicksRemaining <= 0) {
            return;
        }
        IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(player.getUUID());
        if (state == null) {
            return;
        }
        state.fishCaught++;
        player.playNotifySound(ModSounds.NEW_ARTIFACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        syncIceFishingHud(player, true);
        updateIceFishingScoreboard(player.serverLevel());
    }

    public static boolean isTimeFreezeActive() {
        if (frozenMinute == null) {
            return false;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(ModDimensions.STARDEW_VALLEY);
        return stardewLevel != null && (FestivalService.isDebugActiveFestival(FESTIVAL_ID)
            || stardewLevel.players().stream().anyMatch(FestivalOfIceService::isParticipant));
    }

    public static long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
        long currentVirtual = timeManager.getVirtualDayTime();
        if (frozenMinute == null || (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID))) {
            return currentVirtual;
        }
        long dayBase = Math.floorDiv(currentVirtual, 24000L) * 24000L;
        long target = dayBase + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(frozenMinute);
        if (currentVirtual != target) {
            timeManager.setVirtualDayTime(target);
        }
        return target;
    }

    public static String debugStatus(ServerLevel level) {
        boolean overlayApplied = level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        int participants = level == null ? 0 : onlineParticipants(level).size();
        return "Festival of Ice: overlay=" + OVERLAY_ID
            + ", overlayApplied=" + overlayApplied
            + ", debug=" + FestivalService.isDebugActiveFestival(FESTIVAL_ID)
            + ", debugRequested=" + debugRequested
            + ", participants=" + participants
            + ", timeFreeze=" + isTimeFreezeActive()
            + ", npcActors=confirmed"
            + ", shop=travelingMerchant@" + TRAVELING_MERCHANT_INTERACTION_POS.toShortString()
            + ", iceFishing=" + mainEventPhase
            + ", ticks=" + iceFishingTicksRemaining;
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean overlayApplied = FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        boolean activeDay = isActiveFestivalOfIceDay();
        boolean debugActive = debugRequested && overlayApplied;
        boolean venueActive = activeDay
            && overlayApplied
            && hasCurrentSessionParticipant(level);
        boolean fishingStage = mainEventPhase == MainEventPhase.INTRO_CUTSCENE
            || mainEventPhase == MainEventPhase.FISHING
            || mainEventPhase == MainEventPhase.AWARD_CUTSCENE;
        FestivalOfIceNpcService.tick(level, venueActive || debugActive, fishingStage);
    }

    private static void enterFestival(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(player.serverLevel(), FESTIVAL_ID)) {
            return;
        }
        if (FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        startTimeFreeze(player.serverLevel());
        syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        moveToLastInsideEntry(player.serverLevel(), player);
    }

    private static void castStartVote(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventPhase != MainEventPhase.FREE) {
            return;
        }
        if (START_VOTE_PARTICIPANTS.isEmpty()) {
            START_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        START_VOTES.retainAll(START_VOTE_PARTICIPANTS);
        START_VOTES.add(player.getUUID());
        checkStartVote(player.serverLevel());
    }

    private static void checkStartVote(ServerLevel level) {
        List<ServerPlayer> voters = onlineVoteParticipants(level, START_VOTE_PARTICIPANTS);
        int voteCount = voteCount(voters, START_VOTES);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            startOpeningCutscene(level);
        }
    }

    private static void startOpeningCutscene(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        if (level == null || participants.isEmpty()) {
            finishFestival(level);
            return;
        }
        setSessionPhase(level, FestivalSessionPhase.MAIN_EVENT);
        mainEventPhase = MainEventPhase.INTRO_CUTSCENE;
        START_VOTES.clear();
        START_VOTE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.addAll(participants.stream().map(ServerPlayer::getUUID).toList());
        prepareIceFishingPlayers(participants);
        broadcastFestivalMusic(level, FestivalMusicStatePayload.NONE);
        tickNpcActors(level);
        for (ServerPlayer participant : participants) {
            ModTeleport.to(participant, level, PLAYER_CONTEST_POS, SOUTH_YAW, 0.0F);
            participant.setDeltaMovement(Vec3.ZERO);
            ServerCutsceneTracker.startEvent(participant, MAIN_EVENT_CUTSCENE_ID);
        }
    }

    private static void beginIceFishingContest(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        if (level == null || participants.isEmpty()) {
            finishFestival(level);
            return;
        }
        mainEventPhase = MainEventPhase.FISHING;
        iceFishingTicksRemaining = ICE_FISHING_DURATION_TICKS;
        iceFishingHudSyncTicks = 0;
        prepareIceFishingPlayers(participants);
        startIceFishingScoreboard(level);
        broadcastFestivalMusic(level, FestivalMusicStatePayload.FALL_FEST);
        for (ServerPlayer participant : participants) {
            ServerCutsceneTracker.clear(participant);
            ModTeleport.to(participant, level, PLAYER_CONTEST_POS, SOUTH_YAW, 0.0F);
            removeTemporaryFishingRods(participant);
            IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(participant.getUUID());
            if (state != null) {
                giveTemporaryFishingRod(participant, state);
            }
            syncIceFishingHud(participant, true);
        }
    }

    private static void tickIceFishingContest(ServerLevel level) {
        if (mainEventPhase != MainEventPhase.FISHING) {
            return;
        }
        List<ServerPlayer> participants = onlineParticipants(level);
        if (participants.isEmpty()) {
            endIceFishingContest(level);
            return;
        }
        for (ServerPlayer participant : participants) {
            PlayerStardewDataAPI.restoreEnergy(participant, PlayerStardewDataAPI.getMaxEnergy(participant));
        }
        iceFishingHudSyncTicks--;
        if (iceFishingHudSyncTicks <= 0) {
            iceFishingHudSyncTicks = 20;
            for (ServerPlayer participant : participants) {
                syncIceFishingHud(participant, true);
            }
            updateIceFishingScoreboard(level);
        }
        iceFishingTicksRemaining--;
        if (iceFishingTicksRemaining > 0) {
            return;
        }
        iceFishingTicksRemaining = 0;
        var fishing = com.stardew.craft.fishing.server.FishingSessionManager.get(level.getServer());
        for (ServerPlayer participant : participants) {
            if (shouldWaitForActiveFishingAction(fishing.getState(participant))) {
                return;
            }
        }
        for (ServerPlayer participant : participants) {
            fishing.cancel(participant);
        }
        level.playSound(null, PLAYER_CONTEST_POS, ModSounds.WHISTLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        endIceFishingContest(level);
    }

    private static boolean shouldWaitForActiveFishingAction(com.stardew.craft.fishing.server.FishingSession.State state) {
        return state == com.stardew.craft.fishing.server.FishingSession.State.HOOKED_ANIM
            || state == com.stardew.craft.fishing.server.FishingSession.State.MINIGAME;
    }

    private static void endIceFishingContest(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        calculateWinners(participants);
        mainEventPhase = MainEventPhase.AWARD_CUTSCENE;
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.addAll(participants.stream().map(ServerPlayer::getUUID).toList());
        clearIceFishingScoreboard(level);
        broadcastFestivalMusic(level, FestivalMusicStatePayload.EVENT1);
        for (ServerPlayer participant : participants) {
            removeTemporaryFishingRods(participant);
            IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(participant.getUUID());
            if (state != null) {
                restoreDisplacedSelectedItem(participant, state);
            }
            syncIceFishingHud(participant, false);
            sendCutsceneState(participant);
            ModTeleport.to(participant, level, PLAYER_CONTEST_POS, SOUTH_YAW, 0.0F);
            participant.setDeltaMovement(Vec3.ZERO);
            ServerCutsceneTracker.startEvent(participant, END_CUTSCENE_ID);
        }
    }

    private static void calculateWinners(List<ServerPlayer> participants) {
        WINNERS.clear();
        int best = 0;
        for (ServerPlayer participant : participants) {
            IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(participant.getUUID());
            if (state != null) {
                best = Math.max(best, state.fishCaught);
            }
        }
        if (best >= FISH_TO_WIN) {
            for (ServerPlayer participant : participants) {
                IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(participant.getUUID());
                if (state != null && state.fishCaught == best) {
                    WINNERS.add(participant.getUUID());
                }
            }
        }
        playerCanWinPrize = !WINNERS.isEmpty();
        winnerDialogue = createWinnerDialogue(participants, best);
    }

    private static Component createWinnerDialogue(List<ServerPlayer> participants, int best) {
        if (WINNERS.isEmpty()) {
            return Component.translatable("event.festival_of_ice.award.winner.willy");
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayer participant : participants) {
            if (WINNERS.contains(participant.getUUID())) {
                names.add(PlayerDisplayName.get(participant));
            }
        }
        if (names.size() == 1) {
            return Component.translatable("event.festival_of_ice.award.winner.player",
                Component.literal(names.get(0)), best);
        }
        return Component.translatable("event.festival_of_ice.award.winner.players",
            Component.literal(String.join(", ", names)), best);
    }

    private static void sendCutsceneState(ServerPlayer participant) {
        PacketDistributor.sendToPlayer(participant, new IceFishingCutsceneStatePayload(
            WINNERS.contains(participant.getUUID()),
            Component.Serializer.toJson(winnerDialogue, participant.registryAccess())
        ));
    }

    private static void awardIceFishingPrize(ServerPlayer player) {
        if (!WINNERS.contains(player.getUUID())) {
            return;
        }
        var data = PlayerStardewDataAPI.getData(player);
        if (data.hasMailFlag(ICE_FESTIVAL_WIN_FLAG)) {
            giveOrDrop(player, new ItemStack(ModItems.PRIZE_TICKET.get()));
        } else {
            data.addMailFlag(ICE_FESTIVAL_WIN_FLAG);
            PlayerDataEventHandler.syncPlayerData(player, data);
            giveOrDrop(player, new ItemStack(ModItems.SAILORS_CAP.get()));
            giveOrDrop(player, new ItemStack(ModItems.DRESSED_SPINNER.get()));
            giveOrDrop(player, new ItemStack(ModItems.BARBED_HOOK.get()));
            giveOrDrop(player, new ItemStack(ModItems.MAGNET.get()));
        }
        player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        setSessionPhase(level, FestivalSessionPhase.ENDING);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            awardIceFishingPrize(participant);
            removeTemporaryFishingRods(participant);
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            clearClientStateIfNeeded(participant);
            returnToFarm(participant);
        }
        if (level != null) {
            removeFestivalInteractions(level);
            clearIceFishingScoreboard(level);
            restoreNpcs(level);
            FestivalService.endFestival(level, FESTIVAL_ID);
        }
        clearFestivalState();
    }

    private static void jumpToFestivalEndTime(ServerLevel level, List<ServerPlayer> participants) {
        if (level == null) {
            return;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        long currentVirtual = timeManager.getVirtualDayTime(level);
        long dayBase = Math.floorDiv(currentVirtual, 24000L) * 24000L;
        long targetVirtual = dayBase + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(FESTIVAL_END_MINUTE);
        timeManager.setVirtualDayTime(level, targetVirtual);
        timeManager.setCurrentTime(FESTIVAL_END_MINUTE);
        level.setDayTime(targetVirtual);
        ServerLevel miningLevel = level.getServer().getLevel(com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING);
        if (miningLevel != null) {
            miningLevel.setDayTime(targetVirtual);
        }
        TimeSyncPacket packet = TimeSyncPacket.fromTimeManager(timeManager);
        for (ServerPlayer participant : participants) {
            PacketDistributor.sendToPlayer(participant, packet);
        }
    }

    private static void returnToFarm(ServerPlayer player) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (farm == null || stardewLevel == null) {
            player.displayClientMessage(Component.translatable("stardewcraft.warp.farm.unavailable"), true);
            return;
        }
        ModTeleport.to(player, stardewLevel, farm.getSpawnPoint(), farm.getSpawnYaw(), 0.0F);
    }

    private static void castExitVote(ServerPlayer player) {
        if (player == null || !isParticipant(player)) {
            return;
        }
        if (EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            EXIT_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        EXIT_VOTES.retainAll(EXIT_VOTE_PARTICIPANTS);
        EXIT_VOTES.add(player.getUUID());
        checkExitVote(player.serverLevel());
    }

    private static void checkExitVote(ServerLevel level) {
        List<ServerPlayer> voters = onlineVoteParticipants(level, EXIT_VOTE_PARTICIPANTS);
        int voteCount = voteCount(voters, EXIT_VOTES);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            finishFestival(level);
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (player == null || player.isSpectator() || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean insideEntry = ENTRY_EXIT_BOUNDS.contains(player.position());
        if (insideEntry) {
            LAST_INSIDE_ENTRY.put(player.getUUID(), player.position());
        } else {
            LAST_OUTSIDE_ENTRY.put(player.getUUID(), player.position());
        }

        if (isParticipant(player)) {
            if (!insideEntry) {
                CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.EXIT);
                moveToLastInsideEntry(level, player);
            }
            return;
        }

        if (!insideEntry) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(level), true);
            moveToLastOutsideEntry(level, player);
            return;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.ENTER);
        moveToLastOutsideEntry(level, player);
    }

    private static Component blockedEntryMessage(ServerLevel level) {
        String key = FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)
            ? "message.stardewcraft.festival.ended"
            : "message.stardewcraft.festival.ice.setup";
        return Component.translatable(key);
    }

    private static void moveToLastOutsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_OUTSIDE_ENTRY.get(player.getUUID());
        if (target == null || ENTRY_EXIT_BOUNDS.contains(target)) {
            target = FestivalBoundaryReturn.pushOutside(ENTRY_EXIT_BOUNDS, player.position());
        }
        Vec3 safeTarget = FestivalBoundaryReturn.findSafeOutside(player, ENTRY_EXIT_BOUNDS, target);
        if (safeTarget != null) {
            target = safeTarget;
        }
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_OUTSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static void moveToLastInsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_INSIDE_ENTRY.get(player.getUUID());
        if (target == null || !ENTRY_EXIT_BOUNDS.contains(target)) {
            target = FestivalBoundaryReturn.pushInside(ENTRY_EXIT_BOUNDS, player.position());
        }
        Vec3 fallback = FestivalBoundaryReturn.pushInside(ENTRY_EXIT_BOUNDS, player.position());
        Vec3 safeTarget = FestivalBoundaryReturn.findSafeInside(player, ENTRY_EXIT_BOUNDS, target, fallback);
        if (safeTarget != null) {
            target = safeTarget;
        }
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static void prepareIceFishingPlayers(List<ServerPlayer> participants) {
        for (ServerPlayer participant : participants) {
            IceFishingPlayerState state = ICE_FISHING_PLAYERS.computeIfAbsent(participant.getUUID(), ignored -> new IceFishingPlayerState());
            state.fishCaught = 0;
        }
        ICE_FISHING_PLAYERS.keySet().retainAll(participants.stream().map(ServerPlayer::getUUID).toList());
    }

    private static void giveTemporaryFishingRod(ServerPlayer player, IceFishingPlayerState state) {
        ItemStack rod = new ItemStack(ModItems.FISHING_ROD.get());
        FishingRodItem.configureFairTemporaryRod(
            rod,
            TEMPORARY_ROD_OWNER,
            ItemStack.EMPTY,
            new ItemStack(ModItems.DRESSED_SPINNER.get())
        );
        if (player.getInventory().add(rod)) {
            selectTemporaryFishingRod(player);
        } else {
            int selected = player.getInventory().selected;
            ItemStack displaced = player.getInventory().items.get(selected);
            state.displacedSelectedSlot = selected;
            state.displacedSelectedStack = displaced.copy();
            player.getInventory().items.set(selected, rod);
            player.getInventory().setChanged();
        }
    }

    private static void restoreDisplacedSelectedItem(ServerPlayer player, IceFishingPlayerState state) {
        if (state.displacedSelectedStack.isEmpty()) {
            return;
        }
        int slot = state.displacedSelectedSlot;
        if (slot >= 0 && slot < player.getInventory().items.size()
            && player.getInventory().items.get(slot).isEmpty()) {
            player.getInventory().items.set(slot, state.displacedSelectedStack.copy());
        } else if (!player.getInventory().add(state.displacedSelectedStack.copy())) {
            player.drop(state.displacedSelectedStack.copy(), false);
        }
        state.displacedSelectedStack = ItemStack.EMPTY;
        state.displacedSelectedSlot = -1;
        player.getInventory().setChanged();
    }

    private static void selectTemporaryFishingRod(ServerPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (FishingRodItem.isFairTemporaryRod(player.getInventory().items.get(i), TEMPORARY_ROD_OWNER)) {
                player.getInventory().selected = i;
                player.getInventory().setChanged();
                return;
            }
        }
        for (int i = 9; i < player.getInventory().items.size(); i++) {
            if (FishingRodItem.isFairTemporaryRod(player.getInventory().items.get(i), TEMPORARY_ROD_OWNER)) {
                ItemStack rod = player.getInventory().items.get(i);
                int selected = player.getInventory().selected;
                player.getInventory().items.set(i, player.getInventory().items.get(selected));
                player.getInventory().items.set(selected, rod);
                player.getInventory().setChanged();
                return;
            }
        }
    }

    private static void removeTemporaryFishingRods(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (FishingRodItem.isFairTemporaryRod(stack, TEMPORARY_ROD_OWNER)) {
                player.getInventory().items.set(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (FishingRodItem.isFairTemporaryRod(stack, TEMPORARY_ROD_OWNER)) {
                player.getInventory().offhand.set(i, ItemStack.EMPTY);
            }
        }
        player.getInventory().setChanged();
        if (player.level() instanceof ServerLevel level) {
            removeDroppedTemporaryFishingRods(level);
        }
    }

    private static void removeDroppedTemporaryFishingRods(ServerLevel level) {
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, TEMP_ROD_CLEANUP_BOUNDS)) {
            if (FishingRodItem.isFairTemporaryRod(itemEntity.getItem(), TEMPORARY_ROD_OWNER)) {
                itemEntity.discard();
            }
        }
    }

    private static void startIceFishingScoreboard(ServerLevel level) {
        runServerCommand(level, "scoreboard objectives remove " + ICE_FISHING_SCOREBOARD_OBJECTIVE);
        runServerCommand(level, "scoreboard objectives add " + ICE_FISHING_SCOREBOARD_OBJECTIVE
                + " dummy {\"translate\":\"stardewcraft.festival.ice_fishing.scoreboard\"}");
        runServerCommand(level, "scoreboard objectives setdisplay sidebar " + ICE_FISHING_SCOREBOARD_OBJECTIVE);
        updateIceFishingScoreboard(level);
    }

    private static void updateIceFishingScoreboard(ServerLevel level) {
        if (level == null) {
            return;
        }
        var scoreboard = level.getScoreboard();
        var objective = scoreboard.getObjective(ICE_FISHING_SCOREBOARD_OBJECTIVE);
        if (objective == null) {
            return;
        }
        for (ServerPlayer participant : onlineParticipants(level)) {
            IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(participant.getUUID());
            int count = state == null ? 0 : state.fishCaught;
            var score = scoreboard.getOrCreatePlayerScore(
                    ScoreHolder.forNameOnly(participant.getStringUUID()), objective);
            score.set(count);
            score.display(Component.literal(PlayerDisplayName.get(participant)));
        }
    }

    private static void clearIceFishingScoreboard(ServerLevel level) {
        runServerCommand(level, "scoreboard objectives remove " + ICE_FISHING_SCOREBOARD_OBJECTIVE);
    }

    private static void runServerCommand(ServerLevel level, String command) {
        if (level == null || command == null || command.isBlank()) {
            return;
        }
        level.getServer().getCommands().performPrefixedCommand(
            level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(FestivalOfIceService::isParticipant).toList();
    }

    private static List<ServerPlayer> onlineVoteParticipants(ServerLevel level, Set<UUID> participantIds) {
        if (level == null) {
            return List.of();
        }
        if (participantIds.isEmpty()) {
            return onlineParticipants(level);
        }
        return level.players().stream()
            .filter(player -> participantIds.contains(player.getUUID()))
            .filter(FestivalOfIceService::isParticipant)
            .toList();
    }

    private static int voteCount(List<ServerPlayer> voters, Set<UUID> votes) {
        int count = 0;
        for (ServerPlayer voter : voters) {
            if (votes.contains(voter.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isActiveFestivalOfIceDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equalsIgnoreCase(definition.id()))
            .isPresent();
    }

    private static boolean hasCurrentSessionParticipant(ServerLevel level) {
        return currentSession(level)
            .map(session -> !session.participants().isEmpty())
            .orElse(false);
    }

    private static Optional<FestivalSessionState> currentSession(ServerLevel level) {
        if (level == null) {
            return Optional.empty();
        }
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalWorldData.get(level).getSession(FESTIVAL_ID)
            .filter(session -> session.year() == time.getCurrentYear()
                && session.season() == time.getCurrentSeason()
                && session.day() == time.getCurrentDay())
            .filter(session -> session.phase() != FestivalSessionPhase.ENDING
                && session.phase() != FestivalSessionPhase.RESTORING_MAP
                && session.phase() != FestivalSessionPhase.CLOSED);
    }

    private static void setSessionPhase(ServerLevel level, FestivalSessionPhase phase) {
        FestivalDefinition definition = FestivalRegistry.get(FESTIVAL_ID).orElse(null);
        if (level == null || definition == null) {
            return;
        }
        StardewTimeManager time = StardewTimeManager.get();
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalSessionState session = data.getOrCreateSession(
            definition,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        session.setPhase(phase);
        data.setDirty();
    }

    private static void startTimeFreeze(ServerLevel level) {
        if (frozenMinute == null) {
            frozenMinute = FESTIVAL_START_MINUTE;
        }
    }

    private static void stopTimeFreeze() {
        frozenMinute = null;
    }

    private static void clearRuntimeState(ServerLevel level) {
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                removeTemporaryFishingRods(player);
                clearClientStateIfNeeded(player);
            }
            removeFestivalInteractions(level);
            clearIceFishingScoreboard(level);
            restoreNpcs(level);
        }
        clearFestivalState();
    }

    private static void syncFestivalInteractions(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)) {
            return;
        }
        if (!FestivalService.isDebugActiveFestival(FESTIVAL_ID) && !hasCurrentSessionParticipant(level)) {
            return;
        }
        installFestivalInteraction(level, TRAVELING_MERCHANT_INTERACTION_POS,
            TRAVELING_MERCHANT_TARGET_ID, TRAVELING_MERCHANT_MARKER_TAG);
    }

    private static void installFestivalInteraction(ServerLevel level, BlockPos pos, String targetId, String markerTag) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
            && targetId.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(targetId, markerTag);
        }
    }

    private static void removeFestivalInteractions(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (level.getBlockState(TRAVELING_MERCHANT_INTERACTION_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(TRAVELING_MERCHANT_INTERACTION_POS) instanceof PortalTriggerBlockEntity blockEntity
            && TRAVELING_MERCHANT_TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(TRAVELING_MERCHANT_INTERACTION_POS, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void clearFestivalState() {
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        EXIT_VOTES.clear();
        EXIT_VOTE_PARTICIPANTS.clear();
        START_VOTES.clear();
        START_VOTE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        LEWIS_DIALOGUE_SEEN.clear();
        WINNERS.clear();
        ICE_FISHING_PLAYERS.clear();
        mainEventPhase = MainEventPhase.FREE;
        iceFishingTicksRemaining = 0;
        iceFishingHudSyncTicks = 0;
        winnerDialogue = Component.empty();
        playerCanWinPrize = false;
        stopTimeFreeze();
        debugRequested = false;
    }

    private static void syncParticipantMusic(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean shouldPlay = isParticipant(player);
            boolean synced = player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
            if (shouldPlay && !synced) {
                syncFestivalMusic(player, currentFestivalMusicTrack());
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            } else if (!shouldPlay && synced) {
                clearClientStateIfNeeded(player);
            }
        }
    }

    private static String currentFestivalMusicTrack() {
        return switch (mainEventPhase) {
            case INTRO_CUTSCENE -> FestivalMusicStatePayload.NONE;
            case FISHING -> FestivalMusicStatePayload.FALL_FEST;
            case AWARD_CUTSCENE -> FestivalMusicStatePayload.EVENT1;
            case FREE -> FestivalMusicStatePayload.CHRISTMAS_THEME;
        };
    }

    private static void broadcastFestivalMusic(ServerLevel level, String track) {
        for (ServerPlayer participant : onlineParticipants(level)) {
            syncFestivalMusic(participant, track);
            participant.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        }
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
        }
    }

    private static void syncIceFishingHud(ServerPlayer player, boolean active) {
        if (player == null) {
            return;
        }
        IceFishingPlayerState state = ICE_FISHING_PLAYERS.get(player.getUUID());
        int count = state == null ? 0 : state.fishCaught;
        PacketDistributor.sendToPlayer(player, new IceFishingHudStatePayload(
            active,
            Math.max(0, iceFishingTicksRemaining * 50),
            count
        ));
    }

    private static void clearClientStateIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean hadState = player.getPersistentData().getBoolean(TAG_PARTICIPATING);
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        syncIceFishingHud(player, false);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        if (hadState) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
        }
    }

    private static AABB inclusiveBox(BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private enum MainEventPhase {
        FREE,
        INTRO_CUTSCENE,
        FISHING,
        AWARD_CUTSCENE
    }

    private static final class IceFishingPlayerState {
        private int fishCaught;
        private int displacedSelectedSlot = -1;
        private ItemStack displacedSelectedStack = ItemStack.EMPTY;
    }
}
