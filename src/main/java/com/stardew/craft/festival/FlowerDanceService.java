package com.stardew.craft.festival;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.festival.client.FlowerDanceCutsceneClientState;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FlowerDanceCutsceneStatePayload;
import com.stardew.craft.network.payload.FestivalHudStatePayload;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenFlowerDancePlayerAskPayload;
import com.stardew.craft.network.payload.OpenFlowerDancePlayerInvitePayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenFlowerDanceInvitePayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FlowerDanceService {
    public static final String FESTIVAL_ID = "spring24";
    private static final String OVERLAY_ID = "Forest-FlowerFestival";
    private static final String MAIN_EVENT_CUTSCENE_ID = "flower_dance_main_event";
    private static final int FESTIVAL_START_MINUTE = 9 * 60;
    private static final int FESTIVAL_END_MINUTE = 22 * 60;
    private static final long PLAYER_INVITE_TIMEOUT_TICKS = 1200L;
    private static final AABB ENTRY_TRIGGER_BOUNDS = inclusiveBox(new BlockPos(-56, 80, 3), new BlockPos(-283, 58, 157));
    private static final BlockPos ENTRY_POS = new BlockPos(-176, 64, 106);
    private static final float WEST_YAW = 90.0F;
    private static final AABB PIERRE_SHOP_ZONE = inclusiveBox(new BlockPos(-224, 63, 135), new BlockPos(-219, 59, 137));
    private static final String SHOP_ID = "Festival_FlowerDance_Pierre";
    private static final Set<String> DANCEABLE_NPC_IDS = Set.of(
        "abigail", "alex", "elliott", "emily", "haley", "harvey",
        "leah", "maru", "penny", "sam", "sebastian", "shane"
    );
    private static final Set<String> FEMALE_DANCEABLE_NPC_IDS = Set.of(
        "abigail", "emily", "haley", "leah", "maru", "penny"
    );
    private static final List<DefaultDancePair> DEFAULT_DANCE_PAIRS = List.of(
        new DefaultDancePair("emily", "shane"),
        new DefaultDancePair("haley", "alex"),
        new DefaultDancePair("maru", "harvey"),
        new DefaultDancePair("leah", "elliott"),
        new DefaultDancePair("penny", "sam"),
        new DefaultDancePair("abigail", "sebastian")
    );
    private static final int MAX_DANCE_PAIRS = 18;
    private static final int MAX_SPECTATORS = 10;

    private static final String TAG_PARTICIPATING = "stardewcraft_flower_dance_participating";
    private static final String TAG_HUD_HIDDEN = "stardewcraft_flower_dance_hud_hidden";

    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, PartnerSelection> DANCE_PARTNERS = new LinkedHashMap<>();
    private static final Map<UUID, Set<String>> FESTIVAL_DIALOGUE_SEEN = new LinkedHashMap<>();
    private static final Map<UUID, PendingPlayerInvite> PENDING_PLAYER_INVITES = new LinkedHashMap<>();
    private static final Set<UUID> START_DANCE_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.START_DANCE);
    private static final Set<UUID> START_DANCE_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.START_DANCE);
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static Integer frozenMinute;
    private static MainEventPhase mainEventPhase = MainEventPhase.FREE;
    private static boolean mainEventStagePrepared;
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_PARTICIPANTS = new LinkedHashSet<>();
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_DONE = new LinkedHashSet<>();

    private FlowerDanceService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveFlowerDanceDay();
        if (!activeDay) {
            clearRuntimeState(level);
            return;
        }
        if (!hasCurrentSessionParticipant(level)) {
            stopTimeFreeze();
        }
        expirePlayerInvites(level);
        tickMainEvent(level);
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player);
        }
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveFlowerDanceDay();
        if (!activeDay) {
            FlowerDanceNpcService.tick(level, false);
            return;
        }
        boolean overlayApplied = FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        boolean venueActive = overlayApplied
            && (FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID) || hasOnlineParticipant(level))
            && (mainEventPhase != MainEventPhase.MAIN_EVENT || !mainEventStagePrepared);
        FlowerDanceNpcService.tick(level, venueActive);
    }

    public static long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
        long currentVirtual = timeManager.getVirtualDayTime();
        if (frozenMinute == null || !hasCurrentSessionParticipant(level)) {
            return currentVirtual;
        }
        long dayBase = Math.floorDiv(currentVirtual, 24000L) * 24000L;
        long target = dayBase + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(frozenMinute);
        if (currentVirtual != target) {
            timeManager.setVirtualDayTime(target);
        }
        return target;
    }

    public static boolean isTimeFreezeActive() {
        if (frozenMinute == null) {
            return false;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(ModDimensions.STARDEW_VALLEY);
        return stardewLevel != null && hasOnlineParticipant(stardewLevel);
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.START_DANCE, OpenFestivalConfirmPayload.Action.EXIT),
            FlowerDanceService::isParticipant,
            FlowerDanceService::isActiveFlowerDanceDay
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
        if (action == OpenFestivalConfirmPayload.Action.START_DANCE) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.START_DANCE);
            if (!isParticipant(player)) {
                return;
            }
            if (confirmed) {
                castStartDanceVote(player);
            } else {
                START_DANCE_VOTES.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.start_cancelled"), true);
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
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.exit_vote_cancelled"), true);
        }
    }

    public static boolean isParticipant(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(TAG_PARTICIPATING);
    }

    public static boolean canAskNpcToDance(String npcId) {
        return DANCEABLE_NPC_IDS.contains(canonical(npcId));
    }

    public static boolean hasDancePartner(ServerPlayer player) {
        return player != null && DANCE_PARTNERS.containsKey(player.getUUID());
    }

    public static boolean isMainEventCutsceneActive() {
        return mainEventPhase == MainEventPhase.MAIN_EVENT;
    }

    public static boolean tryStartMainEvent(ServerPlayer player) {
        if (player == null || !isParticipant(player)) {
            return false;
        }
        if (mainEventPhase == MainEventPhase.MAIN_EVENT || mainEventPhase == MainEventPhase.ENDING) {
            return true;
        }
        promptStartDance(player);
        return true;
    }

    public static boolean tryOpenPlayerDanceAsk(ServerPlayer sender, ServerPlayer target) {
        if (sender == null || target == null || sender.getUUID().equals(target.getUUID())) {
            return false;
        }
        if (!isParticipant(sender) || !isParticipant(target)) {
            return false;
        }
        if (hasDancePartner(sender)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.already_have_partner"), true);
            return true;
        }
        if (hasDancePartner(target)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.target_has_partner",
                    com.stardew.craft.player.PlayerDisplayName.get(target)), true);
            return true;
        }
        PacketDistributor.sendToPlayer(sender, new OpenFlowerDancePlayerAskPayload(
                target.getUUID(), com.stardew.craft.player.PlayerDisplayName.get(target)));
        return true;
    }

    public static void handlePlayerDanceAskResponse(ServerPlayer sender, UUID targetPlayerId, boolean confirmed) {
        if (sender == null || targetPlayerId == null || !confirmed) {
            return;
        }
        ServerPlayer target = sender.server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null || !isParticipant(sender) || !isParticipant(target)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_unavailable"), true);
            return;
        }
        if (hasDancePartner(sender)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.already_have_partner"), true);
            return;
        }
        if (hasDancePartner(target)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.target_has_partner",
                    com.stardew.craft.player.PlayerDisplayName.get(target)), true);
            return;
        }

        cancelPendingPlayerInvitesFor(sender.getUUID());
        cancelPendingPlayerInvitesFor(target.getUUID());
        UUID inviteId = UUID.randomUUID();
        PendingPlayerInvite invite = new PendingPlayerInvite(inviteId, sender.getUUID(), target.getUUID(), sender.serverLevel().getGameTime());
        PENDING_PLAYER_INVITES.put(inviteId, invite);
        PacketDistributor.sendToPlayer(target, new OpenFlowerDancePlayerInvitePayload(
                inviteId, com.stardew.craft.player.PlayerDisplayName.get(sender)));
        sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_sent",
                com.stardew.craft.player.PlayerDisplayName.get(target)), true);
    }

    public static void handlePlayerDanceInviteResponse(ServerPlayer target, UUID inviteId, boolean accepted) {
        if (target == null || inviteId == null) {
            return;
        }
        PendingPlayerInvite invite = PENDING_PLAYER_INVITES.remove(inviteId);
        if (invite == null || !target.getUUID().equals(invite.targetId())) {
            target.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_expired"), true);
            return;
        }
        ServerPlayer sender = target.server.getPlayerList().getPlayer(invite.senderId());
        if (sender == null || !isParticipant(sender) || !isParticipant(target)) {
            target.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_unavailable"), true);
            return;
        }
        if (!accepted) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_rejected",
                    com.stardew.craft.player.PlayerDisplayName.get(target)), true);
            return;
        }
        if (hasDancePartner(sender) || hasDancePartner(target)) {
            sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_unavailable"), true);
            target.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_unavailable"), true);
            return;
        }
        DANCE_PARTNERS.put(sender.getUUID(), PartnerSelection.player(target.getUUID()));
        DANCE_PARTNERS.put(target.getUUID(), PartnerSelection.player(sender.getUUID()));
        cancelPendingPlayerInvitesFor(sender.getUUID());
        cancelPendingPlayerInvitesFor(target.getUUID());
        sender.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_invite_accepted",
                com.stardew.craft.player.PlayerDisplayName.get(target)), true);
        target.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.player_partner_set",
                com.stardew.craft.player.PlayerDisplayName.get(sender)), true);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        cancelPendingPlayerInvitesFor(playerId);
        CONFIRM_STATE.clearPlayerDialogs(playerId);
        LAST_OUTSIDE_ENTRY.remove(playerId);
        LAST_INSIDE_ENTRY.remove(playerId);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActiveFlowerDanceDay()) {
            clearFlowerDanceClientStateIfNeeded(player);
            return;
        }
        boolean sessionParticipant = FestivalWorldData.get(level).getSession(FESTIVAL_ID)
            .filter(session -> session.year() == StardewTimeManager.get().getCurrentYear()
                && session.season() == StardewTimeManager.get().getCurrentSeason()
                && session.day() == StardewTimeManager.get().getCurrentDay())
            .filter(session -> session.phase() != FestivalSessionPhase.ENDING
                && session.phase() != FestivalSessionPhase.RESTORING_MAP
                && session.phase() != FestivalSessionPhase.CLOSED)
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
        if (sessionParticipant) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        }
        if (!isParticipant(player)) {
            clearFlowerDanceClientStateIfNeeded(player);
            return;
        }
        startTimeFreeze(level);
        forceSyncHud(player, true);
        if (mainEventPhase == MainEventPhase.MAIN_EVENT) {
            MAIN_EVENT_CUTSCENE_PARTICIPANTS.add(player.getUUID());
            MAIN_EVENT_CUTSCENE_DONE.remove(player.getUUID());
            sendCutsceneState(List.of(player));
            ServerCutsceneTracker.startEvent(player, MAIN_EVENT_CUTSCENE_ID);
        }
    }

    private static void clearFlowerDanceClientStateIfNeeded(ServerPlayer player) {
        boolean hadFlowerDanceState = player.getPersistentData().getBoolean(TAG_PARTICIPATING)
            || player.getPersistentData().getBoolean(TAG_HUD_HIDDEN);
        if (!hadFlowerDanceState) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
        forceSyncHud(player, false);
    }

    public static boolean hasFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player == null) {
            return false;
        }
        Set<String> seen = FESTIVAL_DIALOGUE_SEEN.get(player.getUUID());
        return seen != null && seen.contains(canonical(npcId));
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return;
        }
        FESTIVAL_DIALOGUE_SEEN.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>()).add(canonical(npcId));
    }

    public static boolean tryOpenNpcDanceInvite(ServerPlayer player, String npcId) {
        if (player == null || !isParticipant(player) || !canAskNpcToDance(npcId)) {
            return false;
        }
        if (!hasFestivalDialogueSeen(player, npcId)) {
            return false;
        }
        if (hasDancePartner(player)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.already_have_partner"), true);
            return true;
        }
        PacketDistributor.sendToPlayer(player, new OpenFlowerDanceInvitePayload(canonical(npcId)));
        return true;
    }

    public static boolean isNpcDancePartnerTaken(String npcId) {
        String canonicalNpcId = canonical(npcId);
        if (canonicalNpcId.isBlank()) {
            return false;
        }
        for (PartnerSelection selection : DANCE_PARTNERS.values()) {
            if (selection.kind() == PartnerKind.NPC && canonicalNpcId.equals(selection.npcId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean setNpcDancePartner(ServerPlayer player, String npcId) {
        if (player == null || !isParticipant(player) || hasDancePartner(player) || !canAskNpcToDance(npcId) || isNpcDancePartnerTaken(npcId)) {
            return false;
        }
        DANCE_PARTNERS.put(player.getUUID(), PartnerSelection.npc(canonical(npcId)));
        cancelPendingPlayerInvitesFor(player.getUUID());
        return true;
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        if (player == null || !isParticipant(player) || !PIERRE_SHOP_ZONE.contains(player.position())) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(SHOP_ID);
        if (shop == null) {
            player.displayClientMessage(Component.literal("Unknown shopId: " + SHOP_ID), true);
            return true;
        }
        int money = PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            SHOP_ID,
            money,
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static void startDebugFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalService.setDebugActiveFestival(FESTIVAL_ID);
        startTimeFreeze(level);
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        DANCE_PARTNERS.clear();
        FESTIVAL_DIALOGUE_SEEN.clear();
        PENDING_PLAYER_INVITES.clear();
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncHud(player, true);
        }
        FlowerDanceNpcService.requestDebugStart(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        clearRuntimeState(level);
        FlowerDanceNpcService.restore(level);
    }

    public static String debugStatus(ServerLevel level) {
        int participants = level == null ? 0 : onlineParticipants(level).size();
        return "Flower Dance: participants=" + participants
            + " partners=" + DANCE_PARTNERS.size()
            + " pendingPlayerInvites=" + PENDING_PLAYER_INVITES.size()
            + " mainEventPhase=" + mainEventPhase.name()
            + " cutsceneDone=" + MAIN_EVENT_CUTSCENE_DONE.size() + "/" + MAIN_EVENT_CUTSCENE_PARTICIPANTS.size()
            + " overlayApplied=" + (level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID))
            + " freeze=" + (frozenMinute == null ? "none" : frozenMinute)
            + "\n" + FlowerDanceNpcService.debugStatus(level);
    }

    private static void enterFestival(ServerPlayer player) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(player.serverLevel(), FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(player.serverLevel()), true);
            return;
        }
        if (FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.flower_dance.unavailable"), true);
            return;
        }
        startTimeFreeze(player.serverLevel());
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        syncHud(player, true);
        Vec3 target = safeInsideEntryTarget(player, Vec3.atBottomCenterOf(ENTRY_POS));
        ModTeleport.to(player, player.serverLevel(), target.x, target.y, target.z, WEST_YAW, 0.0F);
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        boolean insideEntry = ENTRY_TRIGGER_BOUNDS.contains(player.position());
        if (isParticipant(player)) {
            syncHud(player, true);
            if (insideEntry) {
                LAST_INSIDE_ENTRY.put(player.getUUID(), player.position());
            } else if (mainEventPhase == MainEventPhase.FREE) {
                LAST_OUTSIDE_ENTRY.put(player.getUUID(), player.position());
                promptExit(player);
                moveToLastInsideEntry(level, player);
            }
            return;
        }

        if (!insideEntry) {
            LAST_OUTSIDE_ENTRY.put(player.getUUID(), player.position());
            syncHud(player, false);
            return;
        }

        LAST_INSIDE_ENTRY.put(player.getUUID(), player.position());
        syncHud(player, false);
        if (!FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(level), true);
            moveToLastOutsideEntry(level, player);
            return;
        }
        promptEnter(player);
        moveToLastOutsideEntry(level, player);
    }

    private static void promptEnter(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.ENTER);
    }

    private static void promptExit(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.EXIT);
    }

    private static void promptStartDance(ServerPlayer player) {
        if (START_DANCE_VOTES.contains(player.getUUID())) {
            List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), START_DANCE_VOTE_PARTICIPANTS);
            int voteCount = voteCount(voters, START_DANCE_VOTES);
            player.displayClientMessage(Component.translatable(
                "message.stardewcraft.festival.flower_dance.start_vote_waiting", voteCount, voters.size()), true);
            return;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.START_DANCE);
    }

    private static void castStartDanceVote(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventPhase != MainEventPhase.FREE) {
            return;
        }
        if (START_DANCE_VOTE_PARTICIPANTS.isEmpty()) {
            START_DANCE_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        START_DANCE_VOTES.retainAll(START_DANCE_VOTE_PARTICIPANTS);
        START_DANCE_VOTES.add(player.getUUID());
        List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), START_DANCE_VOTE_PARTICIPANTS);
        int voteCount = voteCount(voters, START_DANCE_VOTES);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            startMainEventCutscene(player.serverLevel());
            return;
        }
        for (ServerPlayer participant : voters) {
            if (!START_DANCE_VOTES.contains(participant.getUUID())) {
                CONFIRM_STATE.prompt(participant, OpenFestivalConfirmPayload.Action.START_DANCE);
            }
            participant.displayClientMessage(Component.translatable(
                "message.stardewcraft.festival.flower_dance.start_vote_waiting", voteCount, voters.size()), true);
        }
    }

    private static void startMainEventCutscene(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        if (participants.isEmpty()) {
            return;
        }
        mainEventPhase = MainEventPhase.MAIN_EVENT;
        mainEventStagePrepared = false;
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.addAll(participants.stream().map(ServerPlayer::getUUID).toList());
        MAIN_EVENT_CUTSCENE_DONE.clear();
        CONFIRM_STATE.clearDialog(OpenFestivalConfirmPayload.Action.START_DANCE);
        CONFIRM_STATE.clearVote(OpenFestivalConfirmPayload.Action.START_DANCE);
        PENDING_PLAYER_INVITES.clear();
        setSessionPhase(level, FestivalSessionPhase.MAIN_EVENT);
        broadcastFestivalMusic(level, currentFestivalMusicTrack());
        sendCutsceneState(participants);
        for (ServerPlayer participant : participants) {
            ServerCutsceneTracker.startEvent(participant, MAIN_EVENT_CUTSCENE_ID);
        }
    }

    public static void onCutsceneStage(ServerPlayer player, String stage) {
        if (player == null || stage == null || !"main".equals(stage)) {
            return;
        }
        if (mainEventPhase != MainEventPhase.MAIN_EVENT || mainEventStagePrepared) {
            return;
        }
        mainEventStagePrepared = true;
        FlowerDanceNpcService.restore(player.serverLevel());
    }

    public static void onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (player == null || !MAIN_EVENT_CUTSCENE_ID.equals(eventId)) {
            return;
        }
        MAIN_EVENT_CUTSCENE_DONE.add(player.getUUID());
        List<ServerPlayer> participants = onlineSnapshotParticipants(player.serverLevel(), MAIN_EVENT_CUTSCENE_PARTICIPANTS);
        if (mainEventPhase == MainEventPhase.MAIN_EVENT
            && (participants.isEmpty() || containsAllOnlineParticipants(MAIN_EVENT_CUTSCENE_DONE, participants))) {
            mainEventPhase = MainEventPhase.ENDING;
            finishFestival(player.serverLevel());
        }
    }

    private static void moveToLastOutsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_OUTSIDE_ENTRY.get(player.getUUID());
        if (target == null || ENTRY_TRIGGER_BOUNDS.contains(target)) {
            target = pushOutsideEntry(player.position());
        }
        Vec3 safeTarget = FestivalBoundaryReturn.findSafeOutside(player, ENTRY_TRIGGER_BOUNDS, target);
        if (safeTarget != null) {
            target = safeTarget;
        }
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static void moveToLastInsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_INSIDE_ENTRY.get(player.getUUID());
        if (target == null || !ENTRY_TRIGGER_BOUNDS.contains(target)) {
            target = Vec3.atBottomCenterOf(ENTRY_POS);
        }
        target = safeInsideEntryTarget(player, target);
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static Vec3 pushOutsideEntry(Vec3 current) {
        return FestivalBoundaryReturn.pushOutside(ENTRY_TRIGGER_BOUNDS, current);
    }

    private static Vec3 safeInsideEntryTarget(ServerPlayer player, Vec3 preferred) {
        Vec3 target = FestivalBoundaryReturn.findSafeInside(player, ENTRY_TRIGGER_BOUNDS, preferred, Vec3.atBottomCenterOf(ENTRY_POS));
        if (target != null) {
            return target;
        }
        return FestivalBoundaryReturn.pushInside(ENTRY_TRIGGER_BOUNDS, Vec3.atBottomCenterOf(ENTRY_POS));
    }

    private static Component blockedEntryMessage(ServerLevel level) {
        String key = FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)
            ? "message.stardewcraft.festival.ended"
            : "message.stardewcraft.festival.flower_dance.setup";
        return Component.translatable(key);
    }

    private static void castExitVote(ServerPlayer player) {
        if (EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            EXIT_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        EXIT_VOTES.retainAll(EXIT_VOTE_PARTICIPANTS);
        EXIT_VOTES.add(player.getUUID());
        List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), EXIT_VOTE_PARTICIPANTS);
        int voteCount = voteCount(voters, EXIT_VOTES);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            finishFestival(player.serverLevel());
            return;
        }
        for (ServerPlayer participant : voters) {
            if (!EXIT_VOTES.contains(participant.getUUID())) {
                CONFIRM_STATE.prompt(participant, OpenFestivalConfirmPayload.Action.EXIT);
            }
            participant.displayClientMessage(Component.translatable(
                "message.stardewcraft.festival.exit_vote_waiting", voteCount, voters.size()), true);
        }
    }

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        setSessionPhase(level, FestivalSessionPhase.ENDING);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            syncHud(participant, false);
            returnToFarm(participant);
        }
        CONFIRM_STATE.clearAll();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        mainEventStagePrepared = false;
        mainEventPhase = MainEventPhase.FREE;
        stopTimeFreeze();
        FlowerDanceNpcService.restore(level);
        FestivalService.endFestival(level, FESTIVAL_ID);
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

    private static boolean hasOnlineParticipant(ServerLevel level) {
        return level != null && level.players().stream().anyMatch(FlowerDanceService::isParticipant);
    }

    private static boolean hasCurrentSessionParticipant(ServerLevel level) {
        return currentFlowerDanceSession(level)
            .map(session -> !session.participants().isEmpty())
            .orElse(false);
    }

    private static Optional<FestivalSessionState> currentFlowerDanceSession(ServerLevel level) {
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

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream()
            .filter(FlowerDanceService::isParticipant)
            .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static List<ServerPlayer> onlineVoteParticipants(ServerLevel level, Set<UUID> participantIds) {
        if (level == null || participantIds.isEmpty()) {
            return List.of();
        }
        return level.players().stream()
            .filter(player -> participantIds.contains(player.getUUID()))
            .filter(FlowerDanceService::isParticipant)
            .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static List<ServerPlayer> onlineSnapshotParticipants(ServerLevel level, Set<UUID> participantIds) {
        if (level == null || participantIds.isEmpty()) {
            return List.of();
        }
        return level.players().stream()
            .filter(player -> participantIds.contains(player.getUUID()))
            .filter(FlowerDanceService::isParticipant)
            .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static boolean containsAllOnlineParticipants(Set<UUID> done, List<ServerPlayer> participants) {
        if (participants.isEmpty()) {
            return false;
        }
        for (ServerPlayer participant : participants) {
            if (!done.contains(participant.getUUID())) {
                return false;
            }
        }
        return true;
    }

    private static void tickMainEvent(ServerLevel level) {
        if (mainEventPhase != MainEventPhase.MAIN_EVENT) {
            return;
        }
        if (onlineParticipants(level).isEmpty()) {
            if (!hasCurrentSessionParticipant(level)) {
                finishFestival(level);
            }
            return;
        }
        if (onlineSnapshotParticipants(level, MAIN_EVENT_CUTSCENE_PARTICIPANTS).isEmpty()) {
            if (!hasCurrentSessionParticipant(level)) {
                finishFestival(level);
            }
        }
    }

    private static void sendCutsceneState(List<ServerPlayer> participants) {
        DanceFormation formation = buildDanceFormation(participants);
        FlowerDanceCutsceneStatePayload payload = new FlowerDanceCutsceneStatePayload(formation.pairs(), formation.spectators());
        for (ServerPlayer participant : participants) {
            PacketDistributor.sendToPlayer(participant, payload);
        }
    }

    private static DanceFormation buildDanceFormation(List<ServerPlayer> participants) {
        Map<UUID, ServerPlayer> byId = new LinkedHashMap<>();
        for (ServerPlayer participant : participants) {
            byId.put(participant.getUUID(), participant);
        }
        List<ServerPlayer> sourcePlayers = participants.stream()
            .sorted(Comparator.comparing(player -> player.getUUID().toString()))
            .toList();
        List<FlowerDanceCutsceneClientState.DancePair> sourcePairs = new ArrayList<>();
        Set<UUID> pairedPlayers = new LinkedHashSet<>();
        Set<String> occupiedNpcs = new LinkedHashSet<>();

        for (ServerPlayer player : sourcePlayers) {
            UUID playerId = player.getUUID();
            if (pairedPlayers.contains(playerId)) {
                continue;
            }
            PartnerSelection selection = DANCE_PARTNERS.get(playerId);
            if (selection == null) {
                continue;
            }
            if (selection.kind() == PartnerKind.NPC) {
                String npcId = canonical(selection.npcId());
                if (!canAskNpcToDance(npcId) || occupiedNpcs.contains(npcId)) {
                    continue;
                }
                occupiedNpcs.add(npcId);
                pairedPlayers.add(playerId);
                sourcePairs.add(pairForPlayerAndNpc(playerId, npcId));
                continue;
            }
            UUID partnerId = selection.playerId();
            if (partnerId == null || pairedPlayers.contains(partnerId) || !byId.containsKey(partnerId)) {
                continue;
            }
            pairedPlayers.add(playerId);
            pairedPlayers.add(partnerId);
            sourcePairs.add(pairForPlayers(playerId, partnerId));
        }

        List<DefaultDancePair> availableDefaultPairs = DEFAULT_DANCE_PAIRS.stream()
            .filter(defaultPair -> !occupiedNpcs.contains(defaultPair.femaleNpcId()) && !occupiedNpcs.contains(defaultPair.maleNpcId()))
            .toList();
        for (DefaultDancePair defaultPair : availableDefaultPairs) {
            if (sourcePairs.size() >= MAX_DANCE_PAIRS) {
                break;
            }
            occupiedNpcs.add(defaultPair.femaleNpcId());
            occupiedNpcs.add(defaultPair.maleNpcId());
            sourcePairs.add(new FlowerDanceCutsceneClientState.DancePair(
                FlowerDanceCutsceneClientState.Partner.npc(defaultPair.femaleNpcId()),
                FlowerDanceCutsceneClientState.Partner.npc(defaultPair.maleNpcId())
            ));
        }

        List<FlowerDanceCutsceneClientState.DancePair> displayPairs = sourceOrderedDisplayPairs(sourcePairs);
        if (displayPairs.size() > MAX_DANCE_PAIRS) {
            displayPairs = List.copyOf(displayPairs.subList(0, MAX_DANCE_PAIRS));
        }
        List<FlowerDanceCutsceneClientState.Partner> spectators = new ArrayList<>();
        for (ServerPlayer participant : participants) {
            if (spectators.size() >= MAX_SPECTATORS) {
                break;
            }
            if (!pairedPlayers.contains(participant.getUUID())) {
                spectators.add(FlowerDanceCutsceneClientState.Partner.player(participant.getUUID()));
            }
        }
        for (DefaultDancePair defaultPair : DEFAULT_DANCE_PAIRS) {
            if (spectators.size() >= MAX_SPECTATORS) {
                break;
            }
            if (!occupiedNpcs.contains(defaultPair.femaleNpcId())) {
                spectators.add(FlowerDanceCutsceneClientState.Partner.npc(defaultPair.femaleNpcId()));
            }
            if (spectators.size() >= MAX_SPECTATORS) {
                break;
            }
            if (!occupiedNpcs.contains(defaultPair.maleNpcId())) {
                spectators.add(FlowerDanceCutsceneClientState.Partner.npc(defaultPair.maleNpcId()));
            }
        }
        return new DanceFormation(displayPairs, spectators);
    }

    private static List<FlowerDanceCutsceneClientState.DancePair> sourceOrderedDisplayPairs(List<FlowerDanceCutsceneClientState.DancePair> sourcePairs) {
        List<FlowerDanceCutsceneClientState.DancePair> display = new ArrayList<>();
        boolean addLeft = true;
        for (FlowerDanceCutsceneClientState.DancePair pair : sourcePairs) {
            if (addLeft) {
                display.add(0, pair);
            } else {
                display.add(pair);
            }
            addLeft = !addLeft;
        }
        return display;
    }

    private static FlowerDanceCutsceneClientState.DancePair pairForPlayerAndNpc(UUID playerId, String npcId) {
        FlowerDanceCutsceneClientState.Partner player = FlowerDanceCutsceneClientState.Partner.player(playerId);
        FlowerDanceCutsceneClientState.Partner npc = FlowerDanceCutsceneClientState.Partner.npc(npcId);
        if (FEMALE_DANCEABLE_NPC_IDS.contains(npcId)) {
            return new FlowerDanceCutsceneClientState.DancePair(npc, player);
        }
        return new FlowerDanceCutsceneClientState.DancePair(player, npc);
    }

    private static FlowerDanceCutsceneClientState.DancePair pairForPlayers(UUID firstPlayerId, UUID secondPlayerId) {
        if (firstPlayerId.toString().compareTo(secondPlayerId.toString()) <= 0) {
            return new FlowerDanceCutsceneClientState.DancePair(
                FlowerDanceCutsceneClientState.Partner.player(firstPlayerId),
                FlowerDanceCutsceneClientState.Partner.player(secondPlayerId)
            );
        }
        return new FlowerDanceCutsceneClientState.DancePair(
            FlowerDanceCutsceneClientState.Partner.player(secondPlayerId),
            FlowerDanceCutsceneClientState.Partner.player(firstPlayerId)
        );
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

    private static void syncHud(ServerPlayer player, boolean hidden) {
        syncHud(player, hidden, false);
    }

    private static void forceSyncHud(ServerPlayer player, boolean hidden) {
        syncHud(player, hidden, true);
    }

    private static void syncHud(ServerPlayer player, boolean hidden, boolean force) {
        boolean current = player.getPersistentData().getBoolean(TAG_HUD_HIDDEN);
        if (!force && current == hidden) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_HUD_HIDDEN, hidden);
        PacketDistributor.sendToPlayer(player, new FestivalHudStatePayload(hidden));
        syncFestivalMusic(player, hidden ? currentFestivalMusicTrack() : FestivalMusicStatePayload.RELEASE);
    }

    private static void broadcastFestivalMusic(ServerLevel level, String track) {
        for (ServerPlayer participant : onlineParticipants(level)) {
            syncFestivalMusic(participant, track);
        }
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
    }

    private static String currentFestivalMusicTrack() {
        return mainEventPhase == MainEventPhase.MAIN_EVENT
            ? FestivalMusicStatePayload.FLOWER_DANCE
            : FestivalMusicStatePayload.EVENT1;
    }

    private static void clearRuntimeState(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (isParticipant(player)) {
                player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            }
            syncHud(player, false);
        }
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        DANCE_PARTNERS.clear();
        FESTIVAL_DIALOGUE_SEEN.clear();
        PENDING_PLAYER_INVITES.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        mainEventStagePrepared = false;
        mainEventPhase = MainEventPhase.FREE;
        stopTimeFreeze();
        FlowerDanceNpcService.restore(level);
    }

    private static void startTimeFreeze(ServerLevel level) {
        if (frozenMinute == null) {
            frozenMinute = FESTIVAL_START_MINUTE;
        }
    }

    private static void stopTimeFreeze() {
        frozenMinute = null;
    }

    private static boolean isActiveFlowerDanceDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equals(definition.id()))
            .isPresent();
    }

    private static String canonical(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void expirePlayerInvites(ServerLevel level) {
        if (level == null || PENDING_PLAYER_INVITES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        PENDING_PLAYER_INVITES.entrySet().removeIf(entry -> now - entry.getValue().createdTick() > PLAYER_INVITE_TIMEOUT_TICKS);
    }

    private static void cancelPendingPlayerInvitesFor(UUID playerId) {
        if (playerId == null || PENDING_PLAYER_INVITES.isEmpty()) {
            return;
        }
        PENDING_PLAYER_INVITES.entrySet().removeIf(entry -> playerId.equals(entry.getValue().senderId()) || playerId.equals(entry.getValue().targetId()));
    }

    private enum PartnerKind {
        NPC,
        PLAYER
    }

    private record PartnerSelection(PartnerKind kind, String npcId, UUID playerId) {
        private static PartnerSelection npc(String npcId) {
            return new PartnerSelection(PartnerKind.NPC, npcId, null);
        }

        private static PartnerSelection player(UUID playerId) {
            return new PartnerSelection(PartnerKind.PLAYER, "", playerId);
        }
    }

    private record PendingPlayerInvite(UUID inviteId, UUID senderId, UUID targetId, long createdTick) {
    }

    private record DefaultDancePair(String femaleNpcId, String maleNpcId) {
    }

    private record DanceFormation(List<FlowerDanceCutsceneClientState.DancePair> pairs, List<FlowerDanceCutsceneClientState.Partner> spectators) {
    }

    private enum MainEventPhase {
        FREE,
        MAIN_EVENT,
        ENDING
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
}
