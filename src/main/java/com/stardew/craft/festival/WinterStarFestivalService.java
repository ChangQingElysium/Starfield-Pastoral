package com.stardew.craft.festival;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.network.payload.OpenWinterStarGiftPromptPayload;
import com.stardew.craft.network.payload.OpenWinterStarOverflowGiftPayload;
import com.stardew.craft.network.payload.OpenWinterStarRecipientThanksPayload;
import com.stardew.craft.network.payload.OpenWinterStarReturnGiftPayload;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WinterStarFestivalService {
    public static final String FESTIVAL_ID = "winter25";

    private static final String OVERLAY_ID = "Town-Christmas";
    private static final String SHOP_ID = "Festival_FeastOfTheWinterStar_Pierre";
    private static final String TAG_PARTICIPATING = "stardewcraft_winter_star_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_winter_star_music_synced";
    private static final String FLAG_GIFT_GIVEN_PREFIX = "winterStarGiftGiven";
    private static final String FLAG_GIFT_RECEIVED_PREFIX = "winterStarGiftReceived";
    private static final int FESTIVAL_END_MINUTE = 22 * 60;
    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(
        new BlockPos(-57, 79, -57),
        new BlockPos(96, 63, 65)
    );
    private static final AABB PIERRE_SHOP_ZONE = inclusiveBox(
        new BlockPos(-7, 67, 3),
        new BlockPos(-10, 63, 7)
    );
    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();
    private static final Set<UUID> SECRET_GIFTS_COMPLETED = new java.util.LinkedHashSet<>();
    private static final Set<UUID> RETURN_GIFTS_RECEIVED = new java.util.LinkedHashSet<>();
    private static final Set<UUID> RETURN_GIFT_CUTSCENES = new java.util.LinkedHashSet<>();
    private static final Map<UUID, ItemStack> PENDING_RETURN_GIFTS = new LinkedHashMap<>();
    private static final String RETURN_GIFT_CUTSCENE_ID = "winter_star_secret_santa";
    /**
     * Vanilla Data/Characters source order after WinterStarParticipant/HomeRegion,
     * socializable, Year 1 presence, and this festival's actor filters are applied.
     * Marlon isn't socializable and Kent isn't present in Year 1.
     */
    private static final List<String> YEAR_ONE_SECRET_FRIEND_CANDIDATES = List.of(
        "abigail", "caroline", "clint", "demetrius", "willy", "elliott", "emily",
        "evelyn", "george", "gus", "haley", "harvey", "jas", "jodi", "alex", "leah",
        "lewis", "linus", "marnie", "maru", "pam", "penny", "pierre", "robin", "sam",
        "sebastian", "shane", "vincent"
    );

    private WinterStarFestivalService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveWinterStarDay();
        boolean debugActive = FestivalService.isDebugActiveFestival(FESTIVAL_ID);
        if (!activeDay && !debugActive && !WinterStarNpcService.isDebugRequested()) {
            clearRuntimeState(level);
            return;
        }
        syncParticipantMusic(level);
        if (activeDay || debugActive) {
            for (ServerPlayer player : level.players()) {
                tickPlayer(level, player);
            }
        }
    }

    public static void startDebugFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalService.setDebugActiveFestival(FESTIVAL_ID);
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        clearSecretGiftProgress();
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        }
        requestDebugNpcs(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                player.getPersistentData().remove(TAG_PARTICIPATING);
                clearClientStateIfNeeded(player);
            }
        }
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        clearSecretGiftProgress();
        restoreNpcs(level);
    }

    public static void onMapOverlayApplied(ServerLevel level) {
        if (FestivalService.isDebugActiveFestival(FESTIVAL_ID) || WinterStarNpcService.isDebugRequested()) {
            WinterStarNpcService.tick(level, true);
        }
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean overlayApplied = FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        boolean debugActive = WinterStarNpcService.isDebugRequested() && overlayApplied;
        boolean venueActive = isActiveWinterStarDay()
            && overlayApplied
            && (FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID) || hasCurrentSessionParticipant(level));
        WinterStarNpcService.tick(level, debugActive || venueActive);
    }

    public static void requestDebugNpcs(ServerLevel level) {
        WinterStarNpcService.requestDebugStart(level);
    }

    public static void restoreNpcs(ServerLevel level) {
        WinterStarNpcService.restore(level);
    }

    public static boolean controlsNpc(String npcId) {
        return WinterStarNpcService.controlsNpc(npcId);
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
        if (player == null) {
            return;
        }
        boolean sessionParticipant = currentSession(player.serverLevel())
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
        if (FestivalService.isDebugActiveFestival(FESTIVAL_ID) || sessionParticipant) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        } else {
            player.getPersistentData().remove(TAG_PARTICIPATING);
            clearClientStateIfNeeded(player);
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CONFIRM_STATE.clearPlayerDialogs(player.getUUID());
        LAST_OUTSIDE_ENTRY.remove(player.getUUID());
        LAST_INSIDE_ENTRY.remove(player.getUUID());
        RETURN_GIFT_CUTSCENES.remove(player.getUUID());
        if (!EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            checkExitVote(player.serverLevel());
        }
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.EXIT),
            WinterStarFestivalService::isParticipant,
            WinterStarFestivalService::isActiveWinterStarDay
        );
    }

    public static void onPlayerConfirmed(ServerPlayer player, OpenFestivalConfirmPayload.Action action, boolean confirmed) {
        if (player == null || action == null) {
            return;
        }
        CONFIRM_STATE.closeDialog(player, action);
        if (action == OpenFestivalConfirmPayload.Action.ENTER) {
            if (confirmed) {
                enterFestival(player);
            }
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.EXIT && isParticipant(player)) {
            if (confirmed) {
                castExitVote(player);
            } else {
                EXIT_VOTES.remove(player.getUUID());
            }
        }
    }

    public static String resolveDialogueKey(ServerPlayer player, String npcId) {
        if (player == null || !isParticipant(player) || !WinterStarNpcService.containsActor(npcId)) {
            return null;
        }
        return FestivalDialogueService.resolveDialogueKey(FESTIVAL_ID, npcId, 1);
    }

    public static boolean tryPromptSecretGift(ServerPlayer player, StardewNpcEntity npc, String npcId) {
        if (player == null || npc == null || npcId == null || !isInteractionEnabled(player)) {
            return false;
        }
        if (tryDeliverPendingReturnGift(player)) {
            return true;
        }
        if (hasSecretGiftCompleted(player)
            || !npcId.equalsIgnoreCase(getSecretFriendId(player))) {
            return false;
        }
        com.stardew.craft.npc.data.NpcCapabilityProfile profile =
            com.stardew.craft.npc.data.NpcDataRegistry.capabilities().get(npcId.toLowerCase(java.util.Locale.ROOT));
        boolean female = profile != null && profile.gender() == com.stardew.craft.npc.data.NpcCapabilityProfile.GENDER_FEMALE;
        npc.facePlayerTemporarily(player, 60, () -> PacketDistributor.sendToPlayer(player,
            new OpenWinterStarGiftPromptPayload(npcId, npc.getDisplayName().getString(), female)));
        return true;
    }

    /** Read-only form of the secret-recipient prompt check. */
    public static boolean canPromptSecretGift(
            ServerPlayer player,
            String npcId
    ) {
        return player != null
                && npcId != null
                && isInteractionEnabled(player)
                && !hasSecretGiftCompleted(player)
                && npcId.equalsIgnoreCase(getSecretFriendId(player));
    }

    public static void handleSelectedSecretGift(ServerPlayer player, String npcId, int slot) {
        if (player == null || npcId == null || slot < 0 || slot >= player.getInventory().getContainerSize()
            || !isInteractionEnabled(player) || hasSecretGiftCompleted(player)
            || !npcId.equalsIgnoreCase(getSecretFriendId(player))) {
            return;
        }
        StardewNpcEntity recipient = NpcSpawnManager.getTrackedNpc(player.serverLevel(), npcId);
        ItemStack selected = player.getInventory().getItem(slot);
        String itemName = selected.isEmpty() ? "" : selected.getHoverName().getString();
        boolean validGift = recipient != null && NpcInteractionService.canBeGivenAsGift(selected);
        if (!validGift || (isActiveWinterStarDay()
            && !NpcInteractionService.applyWinterStarSecretGift(player, recipient, npcId, selected))) {
            PacketDistributor.sendToPlayer(player,
                new OpenObjectDialoguePayload(Component.translatable("stardewcraft.festival.winter_star.invalid_gift")));
            return;
        }
        if (!isActiveWinterStarDay()) {
            player.serverLevel().playSound(null, player.blockPosition(),
                com.stardew.craft.sound.ModSounds.GIVE_GIFT.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        SECRET_GIFTS_COMPLETED.add(player.getUUID());
        if (isActiveWinterStarDay()) {
            addPersistentFlag(player, giftGivenFlag());
        }
        PacketDistributor.sendToPlayer(player, new OpenWinterStarRecipientThanksPayload(npcId, itemName));
    }

    public static void advanceReturnGift(ServerPlayer player) {
        if (player == null || !isInteractionEnabled(player)
            || !hasSecretGiftCompleted(player)
            || hasReturnGiftReceived(player)) {
            return;
        }
        PENDING_RETURN_GIFTS.computeIfAbsent(player.getUUID(), ignored -> createReturnGift(player));
        tryDeliverPendingReturnGift(player);
    }

    /** Completes this per-player runtime scene without recording a permanent eventSeen id. */
    public static boolean onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (player == null || !RETURN_GIFT_CUTSCENE_ID.equals(eventId)) {
            return false;
        }
        RETURN_GIFT_CUTSCENES.remove(player.getUUID());
        ItemStack pending = PENDING_RETURN_GIFTS.get(player.getUUID());
        if (pending == null || pending.isEmpty() || hasReturnGiftReceived(player)) {
            return true;
        }
        if (!canFitCompletely(player, pending)) {
            PacketDistributor.sendToPlayer(player, new OpenWinterStarOverflowGiftPayload(
                BuiltInRegistries.ITEM.getKey(pending.getItem()).toString(), pending.getCount()));
            return true;
        }
        player.getInventory().add(pending.copy());
        PENDING_RETURN_GIFTS.remove(player.getUUID());
        markReturnGiftReceived(player);
        return true;
    }

    public static void claimReturnGiftDuringCutscene(ServerPlayer player) {
        if (player == null || !RETURN_GIFT_CUTSCENES.contains(player.getUUID())
            || hasReturnGiftReceived(player)) {
            return;
        }
        ItemStack pending = PENDING_RETURN_GIFTS.get(player.getUUID());
        if (pending == null || pending.isEmpty() || !canFitCompletely(player, pending)) {
            return;
        }
        player.getInventory().add(pending.copy());
        PENDING_RETURN_GIFTS.remove(player.getUUID());
        markReturnGiftReceived(player);
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
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            SHOP_ID,
            PlayerStardewDataAPI.getMoney(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static boolean isMainEventActive() {
        return false;
    }

    /** Vanilla Utility.GetRandomWinterStarParticipant parity for the local player. */
    public static String getSecretFriendId(ServerPlayer player) {
        if (player == null || YEAR_ONE_SECRET_FRIEND_CANDIDATES.isEmpty()) {
            return "";
        }
        List<String> candidates = availableYearOneCandidates();
        StardewDeterministicRandom random = participantRandom(player);
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Vanilla calls GetRandomWinterStarParticipant a second time with a fresh RNG,
     * excluding the recipient (and divorced NPCs, once that system exists).
     */
    public static String getGiftGiverId(ServerPlayer player) {
        String recipient = getSecretFriendId(player);
        List<String> candidates = new ArrayList<>(availableYearOneCandidates());
        candidates.remove(recipient);
        if (candidates.isEmpty()) {
            return recipient;
        }
        StardewDeterministicRandom random = participantRandom(player);
        return candidates.get(random.nextInt(candidates.size()));
    }

    public static Component getSecretFriendDisplayName(ServerPlayer player) {
        String npcId = getSecretFriendId(player);
        return npcId.isBlank()
            ? Component.literal("???")
            : Component.translatable("entity.stardewcraft.npc." + npcId);
    }

    public static long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
        return timeManager.getVirtualDayTime(level);
    }

    public static String debugStatus(ServerLevel level) {
        return "Feast of the Winter Star: overlay=" + OVERLAY_ID
            + ", overlayApplied=" + (level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID))
            + ", debug=" + FestivalService.isDebugActiveFestival(FESTIVAL_ID)
            + ", actors=" + WinterStarNpcService.debugStatus(level)
            + ", entryExit=" + ENTRY_EXIT_BOUNDS
            + ", shopZone=" + PIERRE_SHOP_ZONE;
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (player == null || player.isSpectator() || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean inside = ENTRY_EXIT_BOUNDS.contains(player.position());
        if (inside) {
            LAST_INSIDE_ENTRY.put(player.getUUID(), player.position());
        } else {
            LAST_OUTSIDE_ENTRY.put(player.getUUID(), player.position());
        }

        if (isParticipant(player)) {
            if (!inside) {
                CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.EXIT);
                moveToLastInsideEntry(level, player);
            }
            return;
        }
        if (!inside) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID)) {
            if (FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)) {
                player.displayClientMessage(Component.translatable("message.stardewcraft.festival.ended"), true);
            }
            moveToLastOutsideEntry(level, player);
            return;
        }
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.ENTER);
        moveToLastOutsideEntry(level, player);
    }

    private static void enterFestival(ServerPlayer player) {
        if (player == null || player.level().dimension() != ModDimensions.STARDEW_VALLEY
            || FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        moveToLastInsideEntry(player.serverLevel(), player);
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
        List<ServerPlayer> voters = onlineVoteParticipants(level);
        int voteCount = (int) voters.stream().filter(player -> EXIT_VOTES.contains(player.getUUID())).count();
        if (voters.isEmpty() || voteCount >= voters.size()) {
            finishFestival(level);
        }
    }

    private static void finishFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        List<ServerPlayer> participants = onlineParticipants(level);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().remove(TAG_PARTICIPATING);
            clearClientStateIfNeeded(participant);
            returnToFarm(participant);
        }
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        clearSecretGiftProgress();
        restoreNpcs(level);
        FestivalService.endFestival(level, FESTIVAL_ID);
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

    private static void jumpToFestivalEndTime(ServerLevel level, List<ServerPlayer> participants) {
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

    private static void syncParticipantMusic(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (isParticipant(player)) {
                if (!player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
                    syncFestivalMusic(player, FestivalMusicStatePayload.CHRISTMAS_THEME);
                    player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
                }
            } else {
                clearClientStateIfNeeded(player);
            }
        }
    }

    private static void clearClientStateIfNeeded(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        player.getPersistentData().remove(TAG_MUSIC_SYNCED);
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
    }

    private static void clearRuntimeState(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().remove(TAG_PARTICIPATING);
            clearClientStateIfNeeded(player);
        }
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        clearSecretGiftProgress();
        if (WinterStarNpcService.isActorsActive() || WinterStarNpcService.isDebugRequested()) {
            WinterStarNpcService.restore(level);
        }
    }

    private static void clearSecretGiftProgress() {
        SECRET_GIFTS_COMPLETED.clear();
        RETURN_GIFTS_RECEIVED.clear();
        RETURN_GIFT_CUTSCENES.clear();
        PENDING_RETURN_GIFTS.clear();
    }

    private static boolean tryDeliverPendingReturnGift(ServerPlayer player) {
        if (hasReturnGiftReceived(player)) {
            return false;
        }
        ItemStack pending = PENDING_RETURN_GIFTS.get(player.getUUID());
        if ((pending == null || pending.isEmpty()) && hasSecretGiftCompleted(player)) {
            pending = createReturnGift(player);
            if (!pending.isEmpty()) {
                PENDING_RETURN_GIFTS.put(player.getUUID(), pending);
            }
        }
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        if (RETURN_GIFT_CUTSCENES.contains(player.getUUID())) {
            return true;
        }
        if (!canFitCompletely(player, pending)) {
            PacketDistributor.sendToPlayer(player,
                new OpenWinterStarOverflowGiftPayload(
                    BuiltInRegistries.ITEM.getKey(pending.getItem()).toString(), pending.getCount()));
            return true;
        }
        String giver = getActualGiftGiverId(player);
        PacketDistributor.sendToPlayer(player, new OpenWinterStarReturnGiftPayload(
            giver,
            returnGiftDialogueKey(giver, false),
            returnGiftDialogueKey(giver, true),
            BuiltInRegistries.ITEM.getKey(pending.getItem()).toString(),
            pending.getCount()
        ));
        RETURN_GIFT_CUTSCENES.add(player.getUUID());
        com.stardew.craft.cutscene.server.ServerCutsceneTracker.startEvent(player, RETURN_GIFT_CUTSCENE_ID);
        return true;
    }

    private static boolean canFitCompletely(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack inventoryStack : player.getInventory().items) {
            if (inventoryStack.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(inventoryStack, stack)) {
                remaining -= Math.max(0, inventoryStack.getMaxStackSize() - inventoryStack.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static ItemStack createReturnGift(ServerPlayer player) {
        String giver = getActualGiftGiverId(player);
        StardewNpcEntity giverNpc = NpcSpawnManager.getTrackedNpc(player.serverLevel(), giver);
        int giverX = giverNpc != null ? giverNpc.blockPosition().getX() : 0;
        long saveId = player.server.overworld().getSeed();
        StardewTimeManager time = StardewTimeManager.get();
        StardewDeterministicRandom random = StardewDeterministicRandom.create(
            saveId / 2L, time.getCurrentYear(), time.getCurrentDay(), time.getCurrentSeason(), giverX);

        List<GiftSpec> gifts;
        switch (giver) {
            case "clint" -> gifts = List.of(
                new GiftSpec("iridium_bar", 1),
                new GiftSpec("gold_bar", 5),
                new GiftSpec(List.of("geode", "frozen_geode", "magma_geode").get(random.nextInt(3)), 5));
            case "willy" -> gifts = List.of(
                new GiftSpec("warp_totem_beach", 25),
                new GiftSpec("dressed_spinner", 1),
                new GiftSpec("magnet", 1));
            case "evelyn" -> gifts = List.of(new GiftSpec("cookie", 1));
            case "marnie" -> gifts = List.of(new GiftSpec("egg_white", 12));
            case "robin" -> gifts = List.of(
                new GiftSpec("wood_normal", 99),
                new GiftSpec("stone", 50),
                new GiftSpec("wood_hard", 25));
            case "jas", "vincent" -> gifts = List.of(
                new GiftSpec("clay", 1),
                new GiftSpec("ancient_doll", 1),
                new GiftSpec("rainbow_shell", 1),
                new GiftSpec(List.of("geode", "frozen_geode", "magma_geode").get(random.nextInt(3)), 1));
            default -> gifts = List.of(
                new GiftSpec("pumpkin_pie", 1), new GiftSpec("poppyseed_muffin", 1),
                new GiftSpec("blackberry_cobbler", 1), new GiftSpec("glow_ring", 1),
                new GiftSpec("deluxe_speed_gro", 10), new GiftSpec("purple_mushroom", 1),
                new GiftSpec("nautilus_shell", 1), new GiftSpec("wine", 1),
                new GiftSpec("beer", 1), new GiftSpec("tea_set", 1),
                new GiftSpec("pink_cake", 1), new GiftSpec("ruby", 1),
                new GiftSpec("emerald", 1), new GiftSpec("jade", 1));
        }
        GiftSpec selected = gifts.get(random.nextInt(gifts.size()));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("stardewcraft", selected.itemId()));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, selected.count());
    }

    private static String getActualGiftGiverId(ServerPlayer player) {
        String assigned = getGiftGiverId(player);
        return NpcSpawnManager.getTrackedNpc(player.serverLevel(), assigned) != null ? assigned : "lewis";
    }

    private static String returnGiftDialogueKey(String giver, boolean after) {
        com.google.gson.JsonObject dialogue = com.stardew.craft.npc.data.NpcDataRegistry.dialogues().get(giver);
        String customField = after ? "WinterStar_GiveGift_After" : "WinterStar_GiveGift_Before";
        if (dialogue != null && dialogue.has(customField) && dialogue.get(customField).isJsonPrimitive()) {
            return dialogue.get(customField).getAsString();
        }
        com.stardew.craft.npc.data.NpcCapabilityProfile profile =
            com.stardew.craft.npc.data.NpcDataRegistry.capabilities().get(giver);
        boolean child = profile != null && profile.age() == com.stardew.craft.npc.data.NpcCapabilityProfile.AGE_CHILD;
        if (child) return after ? "stardewcraft.festival.winter_star.giver.child_after" : "stardewcraft.festival.winter_star.giver.child_before";
        boolean rude = profile != null && profile.manners() == com.stardew.craft.npc.data.NpcCapabilityProfile.MANNERS_RUDE;
        if (rude) return after ? "stardewcraft.festival.winter_star.giver.rude_after" : "stardewcraft.festival.winter_star.giver.rude_before";
        return after ? "stardewcraft.festival.winter_star.giver.default_after" : "stardewcraft.festival.winter_star.giver.default_before";
    }

    private record GiftSpec(String itemId, int count) {
    }

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        return level == null ? List.of() : level.players().stream().filter(WinterStarFestivalService::isParticipant).toList();
    }

    private static List<ServerPlayer> onlineVoteParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(player -> EXIT_VOTE_PARTICIPANTS.contains(player.getUUID())).toList();
    }

    private static boolean isActiveWinterStarDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalService.getActiveFestivalForDate(time.getCurrentDay(), time.getCurrentSeason())
            .filter(definition -> FESTIVAL_ID.equalsIgnoreCase(definition.id()))
            .isPresent();
    }

    public static boolean isFormalFestivalDay() {
        return isActiveWinterStarDay();
    }

    private static boolean isInteractionEnabled(ServerPlayer player) {
        return isParticipant(player)
            && (isActiveWinterStarDay() || FestivalService.isDebugActiveFestival(FESTIVAL_ID));
    }

    private static boolean hasSecretGiftCompleted(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return SECRET_GIFTS_COMPLETED.contains(player.getUUID())
            || (isActiveWinterStarDay()
                && com.stardew.craft.player.PlayerDataManager.getPlayerData(player).hasMailFlag(giftGivenFlag()));
    }

    private static boolean hasReturnGiftReceived(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return RETURN_GIFTS_RECEIVED.contains(player.getUUID())
            || (isActiveWinterStarDay()
                && com.stardew.craft.player.PlayerDataManager.getPlayerData(player).hasMailFlag(giftReceivedFlag()));
    }

    private static void markReturnGiftReceived(ServerPlayer player) {
        RETURN_GIFTS_RECEIVED.add(player.getUUID());
        if (isActiveWinterStarDay()) {
            addPersistentFlag(player, giftReceivedFlag());
        }
    }

    private static void addPersistentFlag(ServerPlayer player, String flag) {
        com.stardew.craft.player.PlayerStardewData data =
            com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(flag)) {
            return;
        }
        data.addMailFlag(flag);
        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private static String giftGivenFlag() {
        return FLAG_GIFT_GIVEN_PREFIX + StardewTimeManager.get().getCurrentYear();
    }

    private static String giftReceivedFlag() {
        return FLAG_GIFT_RECEIVED_PREFIX + StardewTimeManager.get().getCurrentYear();
    }

    private static List<String> availableYearOneCandidates() {
        return YEAR_ONE_SECRET_FRIEND_CANDIDATES.stream()
            .filter(WinterStarNpcService::containsActor)
            .toList();
    }

    private static StardewDeterministicRandom participantRandom(ServerPlayer player) {
        long saveId = player.server.overworld().getSeed();
        UUID uuid = player.getUUID();
        long multiplayerId = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return StardewDeterministicRandom.create(
            saveId / 2L,
            StardewTimeManager.get().getCurrentYear(),
            multiplayerId
        );
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
