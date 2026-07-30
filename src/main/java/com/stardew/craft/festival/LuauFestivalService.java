package com.stardew.craft.festival;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcSocialRules;
import com.stardew.craft.npc.runtime.NpcCentralMovementService;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcFriendshipRewardService;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.stardew.craft.festival.FestivalNpcActorRuntime.actor;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.actorMap;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.canonical;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.point;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.route;

public final class LuauFestivalService {
    public static final String FESTIVAL_ID = "summer11";
    private static final String OVERLAY_ID = "Beach-Luau";
    private static final String SHOP_ID = "Festival_Luau_Pierre";
    private static final String MAIN_EVENT_CUTSCENE_ID = "luau_main_event";
    private static final String MOVEMENT_OWNER = "luau";
    private static final String ACTOR_TAG = "stardewcraft_luau_actor";
    private static final String TAG_PARTICIPATING = "stardewcraft_luau_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_luau_music_synced";
    private static final int STATIC_VERIFY_TICKS = 100;
    private static final int SPAWN_RETRY_TICKS = 100;
    private static final int FESTIVAL_START_MINUTE = 9 * 60;
    private static final int FESTIVAL_END_MINUTE = 22 * 60;

    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(new BlockPos(-3, 73, 83), new BlockPos(141, 59, 175));
    private static final AABB VENUE_BOUNDS = inclusiveBox(new BlockPos(27, 59, 88), new BlockPos(90, 63, 160));
    private static final AABB PIERRE_SHOP_ZONE = inclusiveBox(new BlockPos(72, 60, 95), new BlockPos(64, 62, 91));
    private static final AABB SOUP_CAULDRON_ZONE = inclusiveBox(new BlockPos(59, 60, 108), new BlockPos(63, 61, 110));
    private static final Vec3 SAFE_ENTRY_RETURN = new Vec3(68.5D, 60.0D, 94.5D);
    private static final Map<String, FestivalNpcActorRuntime.ActorDefinition> ACTORS = createActors();
    private static final FestivalNpcActorRuntime NPC_ACTORS = new FestivalNpcActorRuntime(new FestivalNpcActorRuntime.Config(
        "Luau",
        MOVEMENT_OWNER,
        ACTOR_TAG,
        "luau_",
        VENUE_BOUNDS,
        STATIC_VERIFY_TICKS,
        SPAWN_RETRY_TICKS,
        FestivalNpcActorRuntime.DEFAULT_ROTATE_TICKS,
        false,
        ACTORS
    ), new FestivalNpcActorRuntime.Hooks() {
        @Override
        public boolean shouldResumeNpcSpawn(String npcId) {
            return !"governor".equals(npcId);
        }

        @Override
        public boolean shouldForceSpawnNpc(String npcId) {
            return !"governor".equals(npcId);
        }

        @Override
        public StardewNpcEntity createMissingActor(ServerLevel level,
                                                   FestivalNpcActorRuntime.ActorDefinition definition,
                                                   FestivalNpcActorRuntime.ActorRuntime actorRuntime) {
            if (!"governor".equals(definition.npcId())) {
                return null;
            }
            return spawnGovernor(level, definition.points().get(0));
        }

        @Override
        public void onRestoreNpc(ServerLevel level, String npcId, StardewNpcEntity npc) {
            if ("governor".equals(npcId)) {
                npc.discard();
            } else {
                FestivalNpcActorRuntime.Hooks.super.onRestoreNpc(level, npcId, npc);
            }
        }
    });
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, ItemStack> LUAU_INGREDIENTS = new LinkedHashMap<>();
    private static final Map<UUID, InteractionHand> PENDING_SOUP_HAND = new HashMap<>();
    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> LEWIS_DIALOGUE_SEEN = new HashSet<>();
    private static final Set<UUID> START_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.LUAU_START);
    private static final Set<UUID> START_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.LUAU_START);
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_PARTICIPANTS = new HashSet<>();
    private static final Set<UUID> MAIN_EVENT_CUTSCENE_DONE = new HashSet<>();
    private static boolean mainEventActive;
    private static int mainEventReaction = 2;
    private static Integer frozenMinute;

    private LuauFestivalService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!isActiveLuauDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            clearRuntimeState(level);
            return;
        }
        if (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            stopTimeFreeze();
        }
        syncParticipantMusic(level);
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player);
        }
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveLuauDay();
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
        }
        requestDebugNpcs(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            }
        }
        clearFestivalState();
        restoreNpcs(level);
    }

    public static void requestDebugNpcs(ServerLevel level) {
        NPC_ACTORS.requestDebugStart(level);
        if (level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)) {
            tickActors(level, true);
        }
    }

    public static void restoreNpcs(ServerLevel level) {
        NPC_ACTORS.restore(level);
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
        if (currentSession(player.serverLevel())
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false)) {
            return true;
        }
        return false;
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActiveLuauDay()) {
            clearLuauClientStateIfNeeded(player);
            return;
        }
        if (currentSession(level).map(session -> session.participants().contains(player.getUUID())).orElse(false)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            startTimeFreeze(level);
            syncFestivalMusic(player, FestivalMusicStatePayload.EVENT2);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            if (mainEventActive) {
                MAIN_EVENT_CUTSCENE_PARTICIPANTS.add(player.getUUID());
                MAIN_EVENT_CUTSCENE_DONE.remove(player.getUUID());
                ServerCutsceneTracker.startEvent(player, MAIN_EVENT_CUTSCENE_ID + "_" + mainEventReaction);
            }
            return;
        }
        clearLuauClientStateIfNeeded(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PENDING_SOUP_HAND.remove(player.getUUID());
        CONFIRM_STATE.clearPlayerDialogs(player.getUUID());
        LAST_OUTSIDE_ENTRY.remove(player.getUUID());
        LAST_INSIDE_ENTRY.remove(player.getUUID());
        if (!EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            checkExitVote(player.serverLevel());
        }
        if (!START_VOTE_PARTICIPANTS.isEmpty()) {
            checkStartVote(player.serverLevel());
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
        return stardewLevel != null && stardewLevel.players().stream().anyMatch(LuauFestivalService::isParticipant);
    }

    public static boolean isMainEventActive() {
        return mainEventActive;
    }

    public static void onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (player == null || !isLuauMainEvent(eventId)) {
            return;
        }
        MAIN_EVENT_CUTSCENE_DONE.add(player.getUUID());
        List<ServerPlayer> participants = onlineSnapshotParticipants(player.serverLevel(), MAIN_EVENT_CUTSCENE_PARTICIPANTS);
        if (mainEventActive && (participants.isEmpty() || containsAllOnlineParticipants(MAIN_EVENT_CUTSCENE_DONE, participants))) {
            finishFestival(player.serverLevel());
        }
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        if (player == null || action == null) {
            return false;
        }
        if (action == OpenFestivalConfirmPayload.Action.LUAU_ADD_SOUP) {
            return isParticipant(player) || PENDING_SOUP_HAND.containsKey(player.getUUID());
        }
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.EXIT, OpenFestivalConfirmPayload.Action.LUAU_START),
            LuauFestivalService::isParticipant,
            LuauFestivalService::isActiveLuauDay
        );
    }

    public static void onPlayerConfirmed(ServerPlayer player, OpenFestivalConfirmPayload.Action action, boolean confirmed) {
        if (player == null) {
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.ENTER) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.ENTER);
            if (confirmed) {
                enterFestival(player);
            }
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.EXIT) {
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
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.LUAU_ADD_SOUP) {
            if (confirmed) {
                addHeldItemToSoup(player);
            }
            PENDING_SOUP_HAND.remove(player.getUUID());
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.LUAU_START) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.LUAU_START);
            if (confirmed) {
                castStartVote(player);
            }
        }
    }

    public static boolean tryOpenSoupContribution(ServerPlayer player, BlockPos clickedPos, InteractionHand hand) {
        if (hand == null || !canInteractWithSoup(player, clickedPos)) {
            return false;
        }
        if (LUAU_INGREDIENTS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.already_added"), true);
            return true;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.need_item"), true);
            return true;
        }
        if (!canAddToSoup(held)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.invalid_item"), true);
            return true;
        }
        PENDING_SOUP_HAND.put(player.getUUID(), hand);
        PacketDistributor.sendToPlayer(player, new OpenFestivalConfirmPayload(OpenFestivalConfirmPayload.Action.LUAU_ADD_SOUP));
        return true;
    }

    public static boolean canInteractWithSoup(ServerPlayer player, BlockPos clickedPos) {
        return player != null
            && clickedPos != null
            && isParticipant(player)
            && SOUP_CAULDRON_ZONE.contains(Vec3.atCenterOf(clickedPos));
    }

    public static boolean hasPendingSoupContribution(ServerPlayer player, InteractionHand hand) {
        return player != null && hand != null && PENDING_SOUP_HAND.get(player.getUUID()) == hand;
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player != null && "lewis".equals(canonical(npcId))) {
            LEWIS_DIALOGUE_SEEN.add(player.getUUID());
        }
    }

    public static boolean tryStartMainEvent(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventActive || !LEWIS_DIALOGUE_SEEN.contains(player.getUUID())) {
            return false;
        }
        if (START_VOTES.contains(player.getUUID())) {
            List<ServerPlayer> voters = onlineVoteParticipants(player.serverLevel());
            int voteCount = voteCount(voters);
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.start_vote_waiting", voteCount, voters.size()), true);
            return true;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.LUAU_START);
        return true;
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        if (!canOpenPierreFestivalShop(player)) {
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

    public static boolean canOpenPierreFestivalShop(ServerPlayer player) {
        return player != null
            && isParticipant(player)
            && PIERRE_SHOP_ZONE.contains(player.position());
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

    private static void enterFestival(ServerPlayer player) {
        if (player == null || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(player.serverLevel(), FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(player.serverLevel()), true);
            return;
        }
        if (FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.unavailable"), true);
            return;
        }
        startTimeFreeze(player.serverLevel());
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        syncFestivalMusic(player, FestivalMusicStatePayload.EVENT2);
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
            if (!insideEntry && !mainEventActive) {
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
            : "message.stardewcraft.festival.luau.setup";
        return Component.translatable(key);
    }

    public static String debugStatus(ServerLevel level) {
        StringBuilder message = new StringBuilder("Luau NPC actors: ")
            .append(NPC_ACTORS.isActorsActive() ? "ACTIVE" : "INACTIVE")
            .append(", debugRequested=").append(NPC_ACTORS.isDebugRequested())
            .append(", tracked=").append(NPC_ACTORS.runtimes().size()).append('/').append(ACTORS.size());
        if (level == null) {
            return message.toString();
        }
        for (FestivalNpcActorRuntime.ActorDefinition definition : ACTORS.values()) {
            StardewNpcEntity npc = NPC_ACTORS.findActorEntity(level, definition.npcId());
            message.append('\n').append(definition.npcId())
                .append(" pos=").append(npc == null ? "<missing>" : fmt(npc.position()))
                .append(" route=").append(definition.routeTargets().size());
        }
        return message.toString();
    }

    private static void addHeldItemToSoup(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventActive) {
            return;
        }
        if (LUAU_INGREDIENTS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.already_added"), true);
            return;
        }
        InteractionHand hand = PENDING_SOUP_HAND.getOrDefault(player.getUUID(), InteractionHand.MAIN_HAND);
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !canAddToSoup(held)) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.invalid_item"), true);
            return;
        }
        ItemStack ingredient = held.copy();
        ingredient.setCount(1);
        LUAU_INGREDIENTS.put(player.getUUID(), ingredient);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 1.0F);
        Component itemName = ingredient.getHoverName();
        player.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.added_self", itemName), false);
        for (ServerPlayer participant : onlineParticipants(player.serverLevel())) {
            if (!participant.getUUID().equals(player.getUUID())) {
                participant.displayClientMessage(Component.translatable(
                        "message.stardewcraft.festival.luau.added_broadcast",
                        com.stardew.craft.player.PlayerDisplayName.get(player), itemName), false);
            }
        }
    }

    private static boolean canAddToSoup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isMayorShorts(stack) || isSap(stack)) {
            return true;
        }
        return StardewItemDataApi.resolve(stack).map(data -> data.edibility() > -300).orElse(false);
    }

    private static boolean isMayorShorts(ItemStack stack) {
        return stack.is(ModItems.LUCKY_PURPLE_SHORTS.get());
    }

    private static boolean isSap(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return "stardewcraft:sap".equals(itemId);
    }

    private static void castStartVote(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventActive) {
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
        List<ServerPlayer> voters = onlineVoteParticipants(level);
        int voteCount = voteCount(voters);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            startMainEvent(level);
            return;
        }
        for (ServerPlayer participant : voters) {
            if (!START_VOTES.contains(participant.getUUID())) {
                CONFIRM_STATE.prompt(participant, OpenFestivalConfirmPayload.Action.LUAU_START);
            }
            participant.displayClientMessage(Component.translatable("message.stardewcraft.festival.luau.start_vote_waiting", voteCount, voters.size()), true);
        }
    }

    private static void castExitVote(ServerPlayer player) {
        if (player == null || !isParticipant(player) || mainEventActive) {
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

    private static void startMainEvent(ServerLevel level) {
        if (level == null || mainEventActive) {
            return;
        }
        List<ServerPlayer> participants = onlineParticipants(level);
        if (participants.isEmpty()) {
            return;
        }
        mainEventActive = true;
        CONFIRM_STATE.clearDialog(OpenFestivalConfirmPayload.Action.LUAU_START);
        CONFIRM_STATE.clearVote(OpenFestivalConfirmPayload.Action.LUAU_START);
        setSessionPhase(level, FestivalSessionPhase.MAIN_EVENT);
        int reaction = calculateGovernorReaction(level);
        mainEventReaction = reaction;
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.addAll(participants.stream().map(ServerPlayer::getUUID).toList());
        MAIN_EVENT_CUTSCENE_DONE.clear();
        String eventId = MAIN_EVENT_CUTSCENE_ID + "_" + reaction;
        for (ServerPlayer participant : participants) {
            ServerCutsceneTracker.startEvent(participant, eventId);
        }
    }

    private static boolean isLuauMainEvent(String eventId) {
        return eventId != null && (eventId.equals(MAIN_EVENT_CUTSCENE_ID) || eventId.startsWith(MAIN_EVENT_CUTSCENE_ID + "_"));
    }

    private static int calculateGovernorReaction(ServerLevel level) {
        int likeLevel = 5;
        for (ItemStack ingredient : LUAU_INGREDIENTS.values()) {
            int itemLevel = 5;
            if (isMayorShorts(ingredient)) {
                return 6;
            }
            var metadata = StardewItemDataApi.resolve(ingredient).orElse(null);
            if (metadata != null) {
                int quality = com.stardew.craft.item.quality.QualityHelper.getQuality(ingredient);
                int price = metadata.baseSellPrice();
                int edibility = metadata.edibility();
                if ((quality >= 2 && price >= 160) || (quality == 1 && price >= 300 && edibility > 10)) {
                    itemLevel = 4;
                } else if (edibility >= 20 || price >= 100 || (price >= 70 && quality >= 1)) {
                    itemLevel = 3;
                } else if ((price > 20 && edibility >= 10) || (price >= 40 && edibility >= 5)) {
                    itemLevel = 2;
                } else if (edibility >= 0) {
                    itemLevel = 1;
                }
                if (edibility > -300 && edibility < 0) {
                    itemLevel = 0;
                }
            }
            if (itemLevel < likeLevel) {
                likeLevel = itemLevel;
            }
        }
        if (likeLevel != 6 && LUAU_INGREDIENTS.size() < onlineParticipants(level).size()) {
            likeLevel = 5;
        }
        return likeLevel;
    }

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(LuauFestivalService::isParticipant).toList();
    }

    private static List<ServerPlayer> onlineVoteParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        if (START_VOTE_PARTICIPANTS.isEmpty()) {
            return onlineParticipants(level);
        }
        return level.players().stream()
            .filter(player -> START_VOTE_PARTICIPANTS.contains(player.getUUID()))
            .filter(LuauFestivalService::isParticipant)
            .toList();
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
            .filter(LuauFestivalService::isParticipant)
            .toList();
    }

    private static int voteCount(List<ServerPlayer> voters) {
        int count = 0;
        for (ServerPlayer voter : voters) {
            if (START_VOTES.contains(voter.getUUID())) {
                count++;
            }
        }
        return count;
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

    private static List<ServerPlayer> onlineSnapshotParticipants(ServerLevel level, Set<UUID> snapshot) {
        if (level == null || snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        return level.players().stream()
            .filter(player -> snapshot.contains(player.getUUID()))
            .filter(LuauFestivalService::isParticipant)
            .toList();
    }

    private static boolean containsAllOnlineParticipants(Set<UUID> completed, List<ServerPlayer> participants) {
        for (ServerPlayer participant : participants) {
            if (!completed.contains(participant.getUUID())) {
                return false;
            }
        }
        return true;
    }

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        setSessionPhase(level, FestivalSessionPhase.ENDING);
        applyFriendshipEffects(level, participants, mainEventReaction);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            participant.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
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

    private static void applyFriendshipEffects(ServerLevel level, List<ServerPlayer> participants, int reaction) {
        int delta = switch (reaction) {
            case 4 -> 120;
            case 3 -> 60;
            case 1 -> -50;
            case 0 -> -100;
            default -> 0;
        };
        if (level == null || participants.isEmpty() || delta == 0) {
            return;
        }
        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(level);
        List<String> npcIds = NpcDataRegistry.tastes().keySet().stream()
            .filter(NpcSocialRules::canSocialize)
            .sorted()
            .toList();
        for (ServerPlayer participant : participants) {
            for (String npcId : npcIds) {
                NpcFriendshipDataManager.FriendshipState state = friendshipManager.getOrCreate(participant.getUUID(), npcId);
                state.addPoints(delta, NpcInteractionService.getMaxFriendshipPointsFor(npcId));
                NpcFriendshipRewardService.applyEligibleRewards(participant, npcId, state.points());
            }
        }
        friendshipManager.setDirty();
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
        LUAU_INGREDIENTS.clear();
        PENDING_SOUP_HAND.clear();
        CONFIRM_STATE.clearAll();
        LEWIS_DIALOGUE_SEEN.clear();
        MAIN_EVENT_CUTSCENE_PARTICIPANTS.clear();
        MAIN_EVENT_CUTSCENE_DONE.clear();
        mainEventActive = false;
        mainEventReaction = 2;
        stopTimeFreeze();
    }

    private static void clearRuntimeState(ServerLevel level) {
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                clearLuauClientStateIfNeeded(player);
            }
        }
        clearFestivalState();
    }

    private static void clearLuauClientStateIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean hadLuauState = player.getPersistentData().getBoolean(TAG_PARTICIPATING)
            || player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
        if (!hadLuauState) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
    }

    private static void syncParticipantMusic(ServerLevel level) {
        if (level == null || mainEventActive) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean shouldPlay = isParticipant(player);
            boolean alreadySynced = player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
            if (shouldPlay && !alreadySynced) {
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
                syncFestivalMusic(player, FestivalMusicStatePayload.EVENT2);
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

    private static void tickActors(ServerLevel level, boolean activeRequested) {
        NPC_ACTORS.tick(level, activeRequested);
    }

    private static StardewNpcEntity spawnGovernor(ServerLevel level, FestivalNpcActorRuntime.Waypoint point) {
        StardewNpcEntity npc = ModEntities.STARDEW_NPC.get().create(level);
        if (npc == null) {
            return null;
        }
        npc.setNpcId("governor");
        npc.moveTo(point.position().x, point.position().y, point.position().z, point.yaw(), 0.0F);
        npc.setCustomNameVisible(false);
        NpcCentralMovementService.snapToSurface(level, npc);
        if (!level.addFreshEntity(npc)) {
            return null;
        }
        return npc;
    }

    private static boolean isActiveLuauDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equalsIgnoreCase(definition.id()))
            .isPresent();
    }

    private static boolean hasCurrentSessionParticipant(ServerLevel level) {
        return currentSession(level)
            .map(session -> !session.participants().isEmpty())
            .orElse(false);
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

    private static AABB inclusiveBox(BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
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
        definitions.add(actor("pierre", point(69, 60, 90, 'S')));
        definitions.add(actor("governor", point(48, 60, 94, 'S')));
        definitions.add(actor("lewis", point(49, 60, 94, 'S')));
        definitions.add(actor("george", point(27, 60, 96, 'S')));
        definitions.add(actor("evelyn", point(29, 60, 96, 'S')));
        definitions.add(actor("emily", point(38, 60, 104, 'N')));
        definitions.add(actor("robin", point(43, 60, 106, 'E')));
        definitions.add(actor("demetrius", point(44, 60, 106, 'W')));
        definitions.add(actor("jas", point(39, 60, 110, 'E')));
        definitions.add(actor("vincent", point(41, 60, 110, 'E')));
        definitions.add(actor("haley", point(44, 60, 114, 'S')));
        definitions.add(actor("alex", point(45, 60, 116, 'W')));
        definitions.add(actor("shane", point(52, 60, 107, 'E')));
        definitions.add(route("gus", point(55, 60, 112, 'W'), point(55, 60, 107, 'W')));
        definitions.add(actor("sebastian", point(52, 60, 117, 'W')));
        definitions.add(actor("abigail", point(44, 60, 134, 'S')));
        definitions.add(actor("pam", point(58, 60, 104, 'N')));
        definitions.add(actor("marnie", point(62, 60, 107, 'S')));
        definitions.add(actor("caroline", point(66, 60, 108, 'E')));
        definitions.add(actor("elliott", point(75, 60, 103, 'S')));
        definitions.add(actor("leah", point(76, 60, 104, 'W')));
        definitions.add(actor("marlon", point(89, 60, 101, 'S')));
        definitions.add(actor("sam", point(90, 60, 117, 'E')));
        definitions.add(actor("penny", point(90, 60, 119, 'E')));
        definitions.add(actor("clint", point(65, 60, 118, 'N')));
        definitions.add(actor("harvey", point(71, 60, 118, 'E')));
        definitions.add(actor("maru", point(72, 60, 119, 'N')));
        definitions.add(actor("linus", point(82, 60, 123, 'W')));
        definitions.add(actor("jodi", point(60, 60, 114, 'S')));
        definitions.add(actor("willy", point(75, 60, 155, 'S')));
        definitions.add(actor("wizard", point(36, 60, 160, 'S')));

        return actorMap(definitions);
    }
}
