package com.stardew.craft.festival;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.stardew.craft.festival.FestivalNpcActorRuntime.actorMap;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.canonical;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.point;

public final class MoonlightJelliesFestivalService {
    public static final String FESTIVAL_ID = "summer28";
    private static final String OVERLAY_ID = "Beach-Jellies";
    private static final String SHOP_ID = "Festival_DanceOfTheMoonlightJellies_Pierre";
    private static final String MAIN_EVENT_CUTSCENE_ID = "moonlight_jellies_main_event";
    private static final String MOVEMENT_OWNER = "moonlight_jellies";
    private static final String ACTOR_TAG = "stardewcraft_moonlight_jellies_actor";
    private static final String EVENT_ENTITY_TAG = "stardewcraft_moonlight_jellies_event";
    private static final String TAG_PARTICIPATING = "stardewcraft_moonlight_jellies_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_moonlight_jellies_music_synced";
    private static final String TAG_LEWIS_DIALOGUE_SEEN = "stardewcraft_moonlight_jellies_lewis_dialogue_seen";
    private static final int STATIC_VERIFY_TICKS = 100;
    private static final int SPAWN_RETRY_TICKS = 100;
    private static final int FESTIVAL_START_MINUTE = 22 * 60;
    private static final int FESTIVAL_END_MINUTE = 24 * 60;
    private static final int BOAT_SAIL_TICKS = 220;
    private static final int JELLY_FADE_TICKS = 130;
    private static final Method DISPLAY_BRIGHTNESS_METHOD = resolveDisplayBrightnessMethod();

    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(new BlockPos(20, 68, 95), new BlockPos(157, 59, 172));
    private static final AABB PIERRE_SHOP_ZONE = inclusiveBox(new BlockPos(27, 63, 98), new BlockPos(33, 60, 102));
    private static final AABB JELLY_WATER_BOUNDS = inclusiveBox(new BlockPos(9, 59, 135), new BlockPos(151, 59, 217));
    private static final Vec3 SAFE_ENTRY_RETURN = new Vec3(30.5D, 60.0D, 100.5D);
    private static final BlockPos BOAT_LANTERN_START = new BlockPos(49, 60, 156);
    private static final Vec3 BOAT_START = new Vec3(BOAT_LANTERN_START.getX() + 0.5D, BOAT_LANTERN_START.getY(), BOAT_LANTERN_START.getZ() + 0.5D);
    private static final Vec3 BOAT_END = new Vec3(BOAT_START.x, BOAT_START.y, BOAT_START.z + 8.0D);
    private static final List<JellySpec> JELLY_SPECS = createJellySpecs();
    private static final Map<String, FestivalNpcActorRuntime.ActorDefinition> ACTORS = createActors();
    private static final FestivalNpcActorRuntime NPC_ACTORS = new FestivalNpcActorRuntime(new FestivalNpcActorRuntime.Config(
        "Moonlight Jellies",
        MOVEMENT_OWNER,
        ACTOR_TAG,
        "moonlight_jellies_",
        ENTRY_EXIT_BOUNDS,
        STATIC_VERIFY_TICKS,
        SPAWN_RETRY_TICKS,
        FestivalNpcActorRuntime.DEFAULT_ROTATE_TICKS,
        false,
        ACTORS
    ));
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();
    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> START_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
    private static final Set<UUID> START_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_PARTICIPANTS = new LinkedHashSet<>();
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_DONE = new LinkedHashSet<>();
    private static final Map<Integer, UUID> JELLY_ENTITIES = new LinkedHashMap<>();
    private static final Set<BlockPos> EVENT_LIGHT_POSITIONS = new LinkedHashSet<>();
    private static Integer frozenMinute;
    private static MainEventPhase mainEventPhase = MainEventPhase.FREE;
    private static int mainEventTick;
    private static UUID boatEntityId;
    private static UUID lanternDisplayEntityId;

    private MoonlightJelliesFestivalService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!isActiveMoonlightJelliesDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            clearRuntimeState(level);
            return;
        }
        if (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            stopTimeFreeze();
        }
        syncParticipantMusic(level);
        tickMainEvent(level);
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player);
        }
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveMoonlightJelliesDay();
        boolean debugActive = NPC_ACTORS.isDebugRequested() && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        boolean venueActive = activeDay
            && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)
            && (FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID) || hasCurrentSessionParticipant(level));
        tickActors(level, venueActive || debugActive);
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
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            player.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, false);
        }
        requestDebugNpcs(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
                player.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, false);
                clearClientStateIfNeeded(player);
            }
        }
        clearFestivalState();
        cleanupMainEventEntities(level);
        restoreNpcs(level);
    }

    public static void requestDebugNpcs(ServerLevel level) {
        NPC_ACTORS.requestDebugStart(level);
        if (level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)) {
            tickActors(level, true);
        }
    }

    public static void restoreNpcs(ServerLevel level) {
        boolean cleanupMainEvent = level != null && mainEventPhase == MainEventPhase.FREE;
        NPC_ACTORS.restore(level);
        if (cleanupMainEvent) {
            cleanupMainEventEntities(level);
        }
    }

    public static boolean controlsNpc(String npcId) {
        return NPC_ACTORS.controlsNpc(npcId);
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
        if (!isActiveMoonlightJelliesDay()) {
            clearClientStateIfNeeded(player);
            return;
        }
        if (currentSession(level).map(session -> session.participants().contains(player.getUUID())).orElse(false)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            startTimeFreeze(level);
                syncFestivalMusic(player, FestivalMusicStatePayload.OCEAN_AMBIENCE);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            return;
        }
        clearClientStateIfNeeded(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CONFIRM_STATE.clearPlayerDialogs(player.getUUID());
        LAST_OUTSIDE_ENTRY.remove(player.getUUID());
        LAST_INSIDE_ENTRY.remove(player.getUUID());
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.EXIT, OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START),
            MoonlightJelliesFestivalService::isParticipant,
            MoonlightJelliesFestivalService::isActiveMoonlightJelliesDay
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
        if (action == OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
            if (confirmed) {
                castStartVote(player);
            } else {
                START_VOTES.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.stardewcraft.festival.moonlight_jellies.start_cancelled"), true);
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

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        if (player == null || !PIERRE_SHOP_ZONE.contains(player.position()) || !isParticipant(player)) {
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

    public static boolean isMainEventActive() {
        return mainEventPhase != MainEventPhase.FREE;
    }

    public static boolean tryStartMainEvent(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventPhase != MainEventPhase.FREE || !player.getPersistentData().getBoolean(TAG_LEWIS_DIALOGUE_SEEN)) {
            return false;
        }
        if (START_VOTES.contains(player.getUUID())) {
            List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), START_VOTE_PARTICIPANTS);
            int voteCount = voteCount(voters, START_VOTES);
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.moonlight_jellies.start_vote_waiting", voteCount, voters.size()), true);
            return true;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
        return true;
    }

    public static void onCutsceneStage(ServerPlayer player, String stage) {
        if (player == null || stage == null || !"release".equals(stage)) {
            return;
        }
        if (mainEventPhase != MainEventPhase.READY) {
            return;
        }
        mainEventPhase = MainEventPhase.SAILING;
        mainEventTick = 0;
    }

    public static void onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (player == null || !MAIN_EVENT_CUTSCENE_ID.equals(eventId)) {
            return;
        }
        MAIN_EVENT_CUTSCENE_DONE.add(player.getUUID());
        List<ServerPlayer> participants = onlineSnapshotParticipants(player.serverLevel(), MAIN_EVENT_CUTSCENE_PARTICIPANTS);
        if (mainEventPhase != MainEventPhase.FREE
            && (participants.isEmpty() || containsAllOnlineParticipants(MAIN_EVENT_CUTSCENE_DONE, participants))) {
            mainEventPhase = MainEventPhase.ENDING;
            finishFestival(player.serverLevel());
        }
    }

    public static String resolveDialogueKey(ServerPlayer player, String npcId) {
        if (player == null || npcId == null || npcId.isBlank() || !isParticipant(player)) {
            return null;
        }
        String canonicalId = canonical(npcId);
        if (!NPC_ACTORS.actorIds().contains(canonicalId)) {
            return null;
        }
        return FestivalDialogueService.resolveDialogueKey(FESTIVAL_ID, canonicalId);
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player != null && "lewis".equals(canonical(npcId))) {
            player.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, true);
        }
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

    public static String debugStatus(ServerLevel level) {
        StringBuilder message = new StringBuilder("Moonlight Jellies actors: ")
            .append(NPC_ACTORS.isActorsActive() ? "ACTIVE" : "INACTIVE")
            .append(", debugRequested=").append(NPC_ACTORS.isDebugRequested())
            .append(", tracked=").append(NPC_ACTORS.runtimes().size()).append('/').append(ACTORS.size())
            .append(", boatLanternStart=").append(BOAT_LANTERN_START.toShortString())
            .append(", boatEnd=").append(fmt(BOAT_END))
            .append(", mainEvent=").append(mainEventPhase).append('@').append(mainEventTick)
            .append(", jellies=").append(JELLY_ENTITIES.size()).append('/').append(JELLY_SPECS.size())
            .append(", jellyWater=").append(fmt(new Vec3(JELLY_WATER_BOUNDS.minX, JELLY_WATER_BOUNDS.minY, JELLY_WATER_BOUNDS.minZ)))
            .append("..").append(fmt(new Vec3(JELLY_WATER_BOUNDS.maxX, JELLY_WATER_BOUNDS.maxY, JELLY_WATER_BOUNDS.maxZ)));
        if (level == null) {
            return message.toString();
        }
        for (FestivalNpcActorRuntime.ActorDefinition definition : ACTORS.values()) {
            var npc = NPC_ACTORS.findActorEntity(level, definition.npcId());
            message.append('\n').append(definition.npcId())
                .append(" pos=").append(npc == null ? "<missing>" : fmt(npc.position()))
                .append(" target=").append(fmt(definition.point().position()));
        }
        return message.toString();
    }

    private static void enterFestival(ServerPlayer player) {
        if (player == null || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(player.serverLevel(), FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(player.serverLevel()), true);
            return;
        }
        if (FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.moonlight_jellies.unavailable"), true);
            return;
        }
        startTimeFreeze(player.serverLevel());
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        player.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, false);
        syncFestivalMusic(player, FestivalMusicStatePayload.OCEAN_AMBIENCE);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        Vec3 target = LAST_INSIDE_ENTRY.get(player.getUUID());
        if (!isInsideEntryBounds(target)) {
            target = pushInsideEntry(player.position());
        }
        target = safeInsideEntryTarget(player, target);
        ModTeleport.to(player, player.serverLevel(), target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
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
                promptExit(player);
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
        promptEnter(player);
        moveToLastOutsideEntry(level, player);
    }

    private static void promptEnter(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.ENTER);
    }

    private static void promptExit(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.EXIT);
    }

    private static void moveToLastOutsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_OUTSIDE_ENTRY.get(player.getUUID());
        if (isInsideEntryBounds(target) || target == null) {
            target = pushOutsideEntry(player.position());
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
        if (!isInsideEntryBounds(target)) {
            target = pushInsideEntry(player.position());
        }
        target = safeInsideEntryTarget(player, target);
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static Vec3 pushOutsideEntry(Vec3 current) {
        return FestivalBoundaryReturn.pushOutside(ENTRY_EXIT_BOUNDS, current);
    }

    private static Vec3 pushInsideEntry(Vec3 current) {
        return FestivalBoundaryReturn.pushInside(ENTRY_EXIT_BOUNDS, current);
    }

    private static Vec3 safeInsideEntryTarget(ServerPlayer player, Vec3 preferred) {
        Vec3 target = FestivalBoundaryReturn.findSafeInside(player, ENTRY_EXIT_BOUNDS, preferred, SAFE_ENTRY_RETURN);
        if (target != null) {
            return target;
        }
        return pushInsideEntry(SAFE_ENTRY_RETURN);
    }

    private static boolean isInsideEntryBounds(Vec3 position) {
        return position != null && ENTRY_EXIT_BOUNDS.contains(position);
    }

    private static Component blockedEntryMessage(ServerLevel level) {
        String key = FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)
            ? "message.stardewcraft.festival.ended"
            : "message.stardewcraft.festival.moonlight_jellies.setup";
        return Component.translatable(key);
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
        List<ServerPlayer> voters = onlineExitVoteParticipants(level);
        int voteCount = exitVoteCount(voters);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            finishFestival(level);
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

    private static void castStartVote(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventPhase != MainEventPhase.FREE) {
            return;
        }
        if (START_VOTE_PARTICIPANTS.isEmpty()) {
            START_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        START_VOTES.retainAll(START_VOTE_PARTICIPANTS);
        START_VOTES.add(player.getUUID());
        List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel(), START_VOTE_PARTICIPANTS);
        int voteCount = voteCount(voters, START_VOTES);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            startMainEvent(player.serverLevel());
            return;
        }
        for (ServerPlayer participant : voters) {
            if (!START_VOTES.contains(participant.getUUID())) {
                CONFIRM_STATE.prompt(participant, OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
            }
            participant.displayClientMessage(Component.translatable(
                "message.stardewcraft.festival.moonlight_jellies.start_vote_waiting", voteCount, voters.size()), true);
        }
    }

    private static void startMainEvent(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        if (level == null || participants.isEmpty() || mainEventPhase != MainEventPhase.FREE) {
            return;
        }
        mainEventPhase = MainEventPhase.READY;
        mainEventTick = 0;
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.addAll(participants.stream().map(ServerPlayer::getUUID).toList());
        MAIN_EVENT_CUTSCENE_DONE.clear();
        JELLY_ENTITIES.clear();
        CONFIRM_STATE.clearDialog(OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
        CONFIRM_STATE.clearVote(OpenFestivalConfirmPayload.Action.MOONLIGHT_JELLIES_START);
        setSessionPhase(level, FestivalSessionPhase.MAIN_EVENT);
        cleanupMainEventEntities(level);
        ensureLanternBoatAt(level, BOAT_START);
        for (ServerPlayer participant : participants) {
            ServerCutsceneTracker.startEvent(participant, MAIN_EVENT_CUTSCENE_ID);
        }
    }

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        setSessionPhase(level, FestivalSessionPhase.ENDING);
        cleanupMainEventEntities(level);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            participant.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
            participant.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, false);
            syncFestivalMusic(participant, FestivalMusicStatePayload.RELEASE);
            returnToFarm(participant);
        }
        clearFestivalState();
        restoreNpcs(level);
        if (level != null) {
            FestivalService.endFestival(level, FESTIVAL_ID);
        }
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
        ServerLevel miningLevel = level.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
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

    private static void clearFestivalState() {
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        CONFIRM_STATE.clearAll();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        JELLY_ENTITIES.clear();
        mainEventPhase = MainEventPhase.FREE;
        mainEventTick = 0;
        boatEntityId = null;
        lanternDisplayEntityId = null;
        stopTimeFreeze();
    }

    private static void clearRuntimeState(ServerLevel level) {
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                clearClientStateIfNeeded(player);
            }
            cleanupMainEventEntities(level);
        }
        clearFestivalState();
        restoreNpcs(level);
    }

    private static void clearClientStateIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean hadState = player.getPersistentData().getBoolean(TAG_PARTICIPATING)
            || player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)
            || player.getPersistentData().getBoolean(TAG_LEWIS_DIALOGUE_SEEN);
        if (!hadState) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
        player.getPersistentData().putBoolean(TAG_LEWIS_DIALOGUE_SEEN, false);
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
    }

    private static void syncParticipantMusic(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean shouldPlay = isParticipant(player);
            boolean alreadySynced = player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
            if (shouldPlay && !alreadySynced) {
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
                syncFestivalMusic(player, FestivalMusicStatePayload.OCEAN_AMBIENCE);
            } else if (!shouldPlay && alreadySynced) {
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
                syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
            }
        }
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
        }
    }

    private static void tickMainEvent(ServerLevel level) {
        if (level == null || mainEventPhase == MainEventPhase.FREE) {
            return;
        }
        if (mainEventPhase == MainEventPhase.READY) {
            ensureLanternBoatAt(level, BOAT_START);
            Set<BlockPos> staleLights = beginEventLightRefresh();
            updateEventLight(level, BlockPos.containing(BOAT_START.x, BOAT_START.y + 1.0D, BOAT_START.z), 12, staleLights);
            removeStaleEventLights(level, staleLights);
            return;
        }
        if (mainEventPhase != MainEventPhase.SAILING) {
            return;
        }
        Set<BlockPos> staleLights = beginEventLightRefresh();
        mainEventTick++;
        double progress = smoothstep(Math.min(1.0D, mainEventTick / (double) BOAT_SAIL_TICKS));
        Vec3 boatPos = new Vec3(
            lerp(BOAT_START.x, BOAT_END.x, progress),
            BOAT_START.y + Math.sin(mainEventTick * 0.07D) * 0.04D,
            lerp(BOAT_START.z, BOAT_END.z, progress)
        );
        ensureLanternBoatAt(level, boatPos);
        updateEventLight(level, BlockPos.containing(boatPos.x, boatPos.y + 1.0D, boatPos.z), 12, staleLights);
        tickMoonlightJellies(level, staleLights);
        removeStaleEventLights(level, staleLights);
    }

    private static void ensureLanternBoatAt(ServerLevel level, Vec3 position) {
        Entity boat = boatEntityId == null ? null : level.getEntity(boatEntityId);
        Display.ItemDisplay lantern = null;
        Entity displayEntity = lanternDisplayEntityId == null ? null : level.getEntity(lanternDisplayEntityId);
        if (displayEntity instanceof Display.ItemDisplay itemDisplay) {
            lantern = itemDisplay;
        }
        if (boat == null || boat.isRemoved() || lantern == null || lantern.isRemoved()) {
            cleanupMainEventEntities(level);
            boat = EntityType.BOAT.create(level);
            if (boat == null) {
                return;
            }
            boat.addTag(EVENT_ENTITY_TAG);
            boat.setInvulnerable(true);
            boat.setNoGravity(true);
            boat.setGlowingTag(false);
            level.addFreshEntity(boat);
            boatEntityId = boat.getUUID();

            lantern = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            lantern.addTag(EVENT_ENTITY_TAG);
            lantern.setInvulnerable(true);
            lantern.setNoGravity(true);
            lantern.setGlowingTag(false);
            lantern.getSlot(0).set(new ItemStack(ModItems.WATER_LANTERN.get()));
            setDisplayFullBright(lantern);
            level.addFreshEntity(lantern);
            lanternDisplayEntityId = lantern.getUUID();
            lantern.startRiding(boat, true);
        }
        placeBoatAndLantern(boat, lantern, position);
    }

    private static void placeBoatAndLantern(Entity boat, Display.ItemDisplay lantern, Vec3 position) {
        boat.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        boat.setYRot(0.0F);
        boat.setYHeadRot(0.0F);
        boat.setDeltaMovement(Vec3.ZERO);
        boat.setGlowingTag(false);
        boat.hasImpulse = false;

        if (lantern.getVehicle() != boat) {
            lantern.startRiding(boat, true);
        }
        lantern.moveTo(position.x, position.y + 0.34D, position.z - 0.18D, 0.0F, 0.0F);
        lantern.setYRot(0.0F);
        lantern.setDeltaMovement(Vec3.ZERO);
        lantern.setGlowingTag(false);
        setDisplayFullBright(lantern);
        lantern.hasImpulse = false;
    }

    private static void tickMoonlightJellies(ServerLevel level, Set<BlockPos> staleLights) {
        for (JellySpec spec : JELLY_SPECS) {
            UUID entityId = JELLY_ENTITIES.get(spec.index());
            MoonlightJellyEntity jelly = null;
            Entity existing = entityId == null ? null : level.getEntity(entityId);
            if (existing instanceof MoonlightJellyEntity moonlightJelly && moonlightJelly.isAlive()) {
                jelly = moonlightJelly;
            }
            if (jelly == null) {
                jelly = new MoonlightJellyEntity(level, spec.x(), 59.45D, spec.stopZ());
                jelly.addTag(EVENT_ENTITY_TAG);
                jelly.setAlpha(0.0F);
                level.addFreshEntity(jelly);
                JELLY_ENTITIES.put(spec.index(), jelly.getUUID());
            }
            int activeTicks = Math.max(0, mainEventTick - spec.delayTicks());
            double alpha = smoothstep(Math.min(1.0D, activeTicks / (double) JELLY_FADE_TICKS));
            double cycle = (mainEventTick + spec.phaseTicks()) / spec.loopTicks() * Math.PI * 2.0D;
            double wave = Math.sin(cycle);
            double slowWave = Math.sin(cycle * 0.43D + spec.index() * 0.71D);
            double x = spec.x() + wave * spec.xRange() + slowWave * 0.08D;
            double y = 59.45D + Math.sin(cycle * 1.35D + spec.index() * 0.37D) * spec.yRange();
            double z = spec.stopZ() + Math.cos(cycle * 0.61D + spec.index() * 0.29D) * spec.zRange();
            jelly.moveTo(x, y, z, 0.0F, 0.0F);
            jelly.setAlpha((float) alpha);
            if (alpha > 0.12D) {
                int lightLevel = 6 + (int) Math.round(alpha * 5.0D);
                updateEventLight(level, BlockPos.containing(x, y + 1.6D, z), lightLevel, staleLights);
            }
            jelly.setDeltaMovement(Vec3.ZERO);
            jelly.hasImpulse = false;
        }
    }

    private static Set<BlockPos> beginEventLightRefresh() {
        Set<BlockPos> staleLights = new LinkedHashSet<>(EVENT_LIGHT_POSITIONS);
        EVENT_LIGHT_POSITIONS.clear();
        return staleLights;
    }

    private static void updateEventLight(ServerLevel level, BlockPos pos, int lightLevel, Set<BlockPos> ownedLightPositions) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return;
        }
        int clampedLight = Math.max(1, Math.min(15, lightLevel));
        BlockState existing = level.getBlockState(pos);
        boolean alreadyOwned = ownedLightPositions != null && ownedLightPositions.contains(pos);
        if (existing.isAir() || (existing.is(Blocks.LIGHT) && alreadyOwned)) {
            BlockState lightState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, clampedLight);
            if (!existing.is(Blocks.LIGHT) || existing.getValue(LightBlock.LEVEL) != clampedLight) {
                level.setBlock(pos, lightState, 3);
            }
            EVENT_LIGHT_POSITIONS.add(pos.immutable());
        }
    }

    private static void removeStaleEventLights(ServerLevel level, Set<BlockPos> staleLights) {
        if (level == null || staleLights == null || staleLights.isEmpty()) {
            return;
        }
        for (BlockPos pos : staleLights) {
            if (!EVENT_LIGHT_POSITIONS.contains(pos) && level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void cleanupEventLights(ServerLevel level) {
        if (level == null) {
            EVENT_LIGHT_POSITIONS.clear();
            return;
        }
        for (BlockPos pos : List.copyOf(EVENT_LIGHT_POSITIONS)) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        EVENT_LIGHT_POSITIONS.clear();
    }

    private static void cleanupMainEventEntities(ServerLevel level) {
        if (level != null) {
            AABB cleanupBounds = JELLY_WATER_BOUNDS.inflate(64.0D, 24.0D, 24.0D);
            level.getEntitiesOfClass(Entity.class, cleanupBounds, entity -> entity.getTags().contains(EVENT_ENTITY_TAG))
                .forEach(Entity::discard);
        }
        cleanupEventLights(level);
        boatEntityId = null;
        lanternDisplayEntityId = null;
        JELLY_ENTITIES.clear();
        EVENT_LIGHT_POSITIONS.clear();
    }

    private static Method resolveDisplayBrightnessMethod() {
        try {
            Method method = Display.class.getDeclaredMethod("setBrightnessOverride", net.minecraft.util.Brightness.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static void setDisplayFullBright(Display display) {
        if (display == null || DISPLAY_BRIGHTNESS_METHOD == null) {
            return;
        }
        try {
            DISPLAY_BRIGHTNESS_METHOD.invoke(display, net.minecraft.util.Brightness.FULL_BRIGHT);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void tickActors(ServerLevel level, boolean activeRequested) {
        if (activeRequested && (mainEventPhase == MainEventPhase.FREE || mainEventPhase == MainEventPhase.READY)) {
            ensureLanternBoatAt(level, BOAT_START);
        }
        NPC_ACTORS.tick(level, activeRequested);
    }

    private static boolean isActiveMoonlightJelliesDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equalsIgnoreCase(definition.id()))
            .isPresent();
    }

    private static boolean hasCurrentSessionParticipant(ServerLevel level) {
        return currentSession(level)
            .map(session -> !session.participants().isEmpty())
            .orElse(false);
    }

    private static boolean hasOnlineParticipant(ServerLevel level) {
        return level != null && level.players().stream().anyMatch(MoonlightJelliesFestivalService::isParticipant);
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

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(MoonlightJelliesFestivalService::isParticipant).toList();
    }

    private static List<ServerPlayer> onlineExitVoteParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        if (EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            return onlineParticipants(level);
        }
        return level.players().stream()
            .filter(player -> EXIT_VOTE_PARTICIPANTS.contains(player.getUUID()))
            .filter(MoonlightJelliesFestivalService::isParticipant)
            .toList();
    }

    private static List<ServerPlayer> onlineVoteParticipants(ServerLevel level, Set<UUID> voteParticipants) {
        if (level == null) {
            return List.of();
        }
        if (voteParticipants == null || voteParticipants.isEmpty()) {
            return onlineParticipants(level);
        }
        return level.players().stream()
            .filter(player -> voteParticipants.contains(player.getUUID()))
            .filter(MoonlightJelliesFestivalService::isParticipant)
            .toList();
    }

    private static List<ServerPlayer> onlineSnapshotParticipants(ServerLevel level, Set<UUID> snapshot) {
        if (level == null || snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        return level.players().stream()
            .filter(player -> snapshot.contains(player.getUUID()))
            .filter(MoonlightJelliesFestivalService::isParticipant)
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

    private static boolean containsAllOnlineParticipants(Set<UUID> completed, List<ServerPlayer> participants) {
        for (ServerPlayer participant : participants) {
            if (!completed.contains(participant.getUUID())) {
                return false;
            }
        }
        return true;
    }

    private static int exitVoteCount(List<ServerPlayer> voters) {
        int count = 0;
        for (ServerPlayer voter : voters) {
            if (EXIT_VOTES.contains(voter.getUUID())) {
                count++;
            }
        }
        return count;
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double smoothstep(double progress) {
        double clamped = clamp(progress, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static FestivalNpcActorRuntime.ActorDefinition actor(String npcId, FestivalNpcActorRuntime.Waypoint point) {
        return FestivalNpcActorRuntime.actor(npcId, point);
    }

    private static float yaw(char facing) {
        return switch (Character.toUpperCase(facing)) {
            case 'N' -> 180.0F;
            case 'E' -> -90.0F;
            case 'W' -> 90.0F;
            case 'S' -> 0.0F;
            default -> 0.0F;
        };
    }

    private static String canonical(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private static String fmt(Vec3 position) {
        if (position == null) {
            return "<none>";
        }
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", position.x, position.y, position.z);
    }

    private static Map<String, FestivalNpcActorRuntime.ActorDefinition> createActors() {
        List<FestivalNpcActorRuntime.ActorDefinition> definitions = new ArrayList<>();
        definitions.add(actor("pierre", point(31, 60, 96, 'S')));
        definitions.add(actor("pam", point(27, 60, 103, 'W')));
        definitions.add(actor("gus", point(28, 60, 107, 'W')));
        definitions.add(actor("penny", point(35, 60, 127, 'W')));
        definitions.add(actor("vincent", point(35, 60, 135, 'W')));
        definitions.add(actor("sebastian", point(33, 60, 159, 'W')));
        definitions.add(actor("abigail", point(36, 60, 160, 'S')));
        definitions.add(actor("sam", point(37, 60, 158, 'E')));
        definitions.add(actor("haley", point(47, 60, 134, 'S')));
        definitions.add(actor("alex", point(49, 60, 134, 'S')));
        definitions.add(actor("marnie", point(55, 60, 129, 'W')));
        definitions.add(actor("shane", point(54, 60, 142, 'W')));
        definitions.add(actor("jas", point(54, 60, 145, 'W')));
        definitions.add(actor("lewis", point(56, 60, 153, 'S')));
        definitions.add(actor("willy", point(59, 60, 153, 'S')));
        definitions.add(actor("jodi", point(67, 60, 156, 'S')));
        definitions.add(actor("emily", point(77, 60, 156, 'S')));
        definitions.add(actor("clint", point(83, 60, 156, 'S')));
        definitions.add(actor("elliott", point(86, 60, 154, 'E')));
        definitions.add(actor("evelyn", point(86, 60, 128, 'S')));
        definitions.add(actor("george", point(87, 60, 128, 'S')));
        definitions.add(actor("linus", point(88, 60, 101, 'S')));
        definitions.add(actor("harvey", point(84, 60, 154, 'E')));
        definitions.add(actor("maru", point(111, 60, 130, 'S')));
        definitions.add(actor("robin", point(113, 60, 130, 'S')));
        definitions.add(actor("demetrius", point(114, 60, 130, 'S')));
        definitions.add(actor("leah", point(129, 60, 131, 'S')));
        definitions.add(actor("wizard", point(118, 60, 96, 'S')));

        return actorMap(definitions);
    }

    private static List<JellySpec> createJellySpecs() {
        List<JellySpec> specs = new ArrayList<>();
        double[][] positions = {
            {26.5D, 176.0D, 0.8D, 70.0D},
            {34.5D, 172.0D, 0.8D, 70.0D},
            {42.5D, 182.0D, 0.8D, 70.0D},
            {52.5D, 168.0D, 0.9D, 64.0D},
            {63.5D, 184.0D, 0.9D, 64.0D},
            {75.5D, 173.0D, 0.8D, 70.0D},
            {88.5D, 189.0D, 0.8D, 70.0D},
            {101.5D, 170.0D, 0.9D, 64.0D},
            {112.5D, 196.0D, 0.8D, 70.0D},
            {124.5D, 180.0D, 0.8D, 70.0D},
            {137.5D, 203.0D, 0.9D, 64.0D},
            {18.5D, 197.0D, 0.7D, 76.0D},
            {48.5D, 201.0D, 0.7D, 76.0D},
            {94.5D, 205.0D, 0.7D, 76.0D},
            {145.5D, 199.0D, 0.7D, 76.0D},
            {58.5D, 158.0D, 1.2D, 54.0D},
            {70.5D, 160.0D, 1.2D, 54.0D},
            {82.5D, 164.0D, 1.2D, 54.0D},
            {96.5D, 162.0D, 1.2D, 54.0D},
            {110.5D, 165.0D, 1.2D, 54.0D}
        };
        for (int index = 0; index < positions.length; index++) {
            double[] position = positions[index];
            double depthProgress = clamp((position[1] - 156.0D) / 52.0D, 0.0D, 1.0D);
            int delayTicks = 20 + (int) Math.round(depthProgress * 280.0D) + (index % 4) * 14;
            double startZ = position[1];
            double xRange = position[2] * 0.22D;
            double yRange = 0.08D + depthProgress * 0.08D;
            double zRange = 0.05D + depthProgress * 0.08D;
            int loopTicks = (int) position[3] + (index % 5) * 9;
            int phaseTicks = index * 31 + (int) Math.round(depthProgress * 45.0D);
            specs.add(new JellySpec(index, position[0], startZ, position[1], delayTicks, xRange, yRange, zRange, loopTicks, phaseTicks));
        }
        return List.copyOf(specs);
    }

    private record JellySpec(int index, double x, double startZ, double stopZ, int delayTicks, double xRange, double yRange, double zRange, int loopTicks, int phaseTicks) {
    }

    private enum MainEventPhase {
        FREE,
        READY,
        SAILING,
        ENDING
    }

}
