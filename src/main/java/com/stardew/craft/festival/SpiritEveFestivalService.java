package com.stardew.craft.festival;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.WoodenChestBlock;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.WoodenChestMenu;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
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
import static com.stardew.craft.festival.FestivalNpcActorRuntime.yaw;

public final class SpiritEveFestivalService {
    public static final String FESTIVAL_ID = "fall27";
    private static final String SHOP_ID = "Festival_SpiritsEve_Pierre";
    private static final String OVERLAY_ID = "Town-Halloween";
    private static final String MOVEMENT_OWNER = "spirit_eve";
    private static final String ACTOR_TAG = "stardewcraft_spirit_eve_actor";
    private static final String MONSTER_TAG = "stardewcraft_spirit_eve_monster";
    private static final String MONSTER_PERSISTENT_FLAG = "stardewcraft_spirit_eve_monster";
    private static final String TAG_PARTICIPATING = "stardewcraft_spirit_eve_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_spirit_eve_music_synced";
    private static final String TAG_REWARD_CLAIM_YEAR = "stardewcraft_spirit_eve_reward_claim_year";
    private static final String QUESTION_CONTEXT_SHORTCUT = "spirit_eve_shortcut";
    private static final String SHORTCUT_MINECART_ID = "spirit_eve_shortcut";
    private static final String RETURN_MINECART_ID = "spirit_eve_return_display";
    private static final int FESTIVAL_START_MINUTE = 22 * 60;
    private static final int SPAWN_RETRY_TICKS = 40;
    private static final int STATIC_VERIFY_TICKS = 20;
    private static final int ROUTE_WAIT_TICKS = 60;
    private static final int REWARD_SLOT = 13;
    private static final int CHEST_BROWN_COLOR = 8;
    private static final AABB VENUE_BOUNDS = inclusiveBox(new BlockPos(-36, 63, -77), new BlockPos(74, 71, 22));
    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(new BlockPos(-39, 84, -70), new BlockPos(78, 64, 29));
    private static final AABB PIERRE_SHOP_BOUNDS = inclusiveBox(new BlockPos(2, 63, -16), new BlockPos(-1, 67, -19));
    private static final BlockPos GOLDEN_PUMPKIN_CHEST_POS = new BlockPos(67, 66, -58);
    private static final BlockPos SHORTCUT_MINECART_POS = new BlockPos(68, 66, -60);
    private static final BlockPos RETURN_MINECART_POS = new BlockPos(3, 64, -5);
    private static final Vec3 SHORTCUT_RETURN_TARGET = new Vec3(3.5D, 64.0D, -2.5D);
    private static final Map<String, FestivalNpcActorRuntime.ActorDefinition> ACTORS = createActors();
    private static final FestivalNpcActorRuntime NPC_ACTORS = new FestivalNpcActorRuntime(new FestivalNpcActorRuntime.Config(
        "Spirit's Eve",
        MOVEMENT_OWNER,
        ACTOR_TAG,
        "spirit_eve_",
        VENUE_BOUNDS,
        STATIC_VERIFY_TICKS,
        SPAWN_RETRY_TICKS,
        FestivalNpcActorRuntime.DEFAULT_ROTATE_TICKS,
        false,
        ACTORS
    ));
    private static final List<MonsterSpawn> MONSTERS = List.of(
        new MonsterSpawn(EntityType.ENDERMAN, point(-14, 64, -1, 'S')),
        new MonsterSpawn(EntityType.SKELETON, point(-22, 64, -1, 'S')),
        new MonsterSpawn(EntityType.SKELETON, point(-22, 64, 1, 'S'))
    );
    private static final Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new LinkedHashMap<>();
    private static final Map<UUID, Vec3> LAST_INSIDE_ENTRY = new LinkedHashMap<>();
    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);

    private static Integer frozenMinute;

    private SpiritEveFestivalService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!isActiveSpiritEveDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            clearRuntimeState(level);
            return;
        }
        if (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            stopTimeFreeze();
        }
        syncParticipantMusic(level);
        ensureFestivalMonsters(level);
        ensureRewardProps(level);
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
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncFestivalMusic(player, FestivalMusicStatePayload.SPIRITS_EVE);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        }
        ensureFestivalMonsters(level);
        ensureRewardProps(level);
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
                clearClientStateIfNeeded(player);
            }
            restoreNpcs(level);
            removeFestivalMonsters(level);
            removeShortcutMinecarts(level);
        }
        clearFestivalState();
        stopTimeFreeze();
    }

    public static void onMapOverlayApplied(ServerLevel level) {
        ensureFestivalMonsters(level);
        ensureRewardProps(level);
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean venueActive = (isActiveSpiritEveDay() && hasCurrentSessionParticipant(level))
            || FestivalService.isDebugActiveFestival(FESTIVAL_ID);
        tickActors(level, venueActive);
        if (!venueActive) {
            removeFestivalMonsters(level);
        }
    }

    public static void requestDebugNpcs(ServerLevel level) {
        if (level == null) {
            return;
        }
        NPC_ACTORS.requestDebugStart(level);
        tickActors(level, true);
        ensureFestivalMonsters(level);
    }

    public static void restoreNpcs(ServerLevel level) {
        NPC_ACTORS.restore(level);
        removeFestivalMonsters(level);
    }

    public static String debugNpcStatus(ServerLevel level) {
        StringBuilder message = new StringBuilder("Spirit's Eve NPC actors: ")
            .append(NPC_ACTORS.isActorsActive() ? "ACTIVE" : "INACTIVE")
            .append(", definitions=").append(ACTORS.size());
        if (level == null) {
            return message.append(", level=null").toString();
        }
        int monsterCount = level.getEntitiesOfClass(LivingEntity.class, VENUE_BOUNDS.inflate(8.0D), SpiritEveFestivalService::isFestivalMonster).size();
        message.append(", monsters=").append(monsterCount);
        for (FestivalNpcActorRuntime.ActorDefinition definition : ACTORS.values()) {
            StardewNpcEntity npc = NPC_ACTORS.findActorEntity(level, definition.npcId());
            message.append("\n - ").append(definition.npcId())
                .append(": ").append(npc == null ? "missing" : fmt(npc.position()));
        }
        return message.toString();
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
        if (!isActiveSpiritEveDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            clearClientStateIfNeeded(player);
            return;
        }
        boolean sessionParticipant = currentSession(level)
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
        if (sessionParticipant || player.getPersistentData().getBoolean(TAG_PARTICIPATING)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            startTimeFreeze(level);
            syncFestivalMusic(player, FestivalMusicStatePayload.SPIRITS_EVE);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            return;
        }
        clearClientStateIfNeeded(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CONFIRM_STATE.clearPlayer(player.getUUID());
        LAST_OUTSIDE_ENTRY.remove(player.getUUID());
        LAST_INSIDE_ENTRY.remove(player.getUUID());
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
            SpiritEveFestivalService::isParticipant,
            SpiritEveFestivalService::isActiveSpiritEveDay
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
            } else {
                moveToLastOutsideEntry(player.serverLevel(), player);
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
            moveToLastInsideEntry(player.serverLevel(), player);
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
        return FestivalDialogueService.resolveDialogueKey(FESTIVAL_ID, canonicalId, 2);
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        if (player == null || !isParticipant(player) || !PIERRE_SHOP_BOUNDS.contains(player.position())) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(SHOP_ID);
        if (shop == null) {
            return false;
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

    public static boolean tryOpenGoldenPumpkinChest(ServerPlayer player, BlockPos pos) {
        if (!canOpenGoldenPumpkinChest(player, pos)) {
            return false;
        }
        ensureRewardProps(player.serverLevel());
        openGoldenPumpkinChest(player);
        return true;
    }

    public static boolean canOpenGoldenPumpkinChest(ServerPlayer player, BlockPos pos) {
        return player != null
            && GOLDEN_PUMPKIN_CHEST_POS.equals(pos)
            && isParticipant(player);
    }

    public static boolean tryUseShortcutMinecart(ServerPlayer player, MinecartStationEntity minecart) {
        if (!canUseShortcutMinecart(player, minecart)) {
            return false;
        }
        String stationId = minecart.getStationId();
        if (RETURN_MINECART_ID.equals(stationId) || !isParticipant(player)) {
            return true;
        }
        sendShortcutQuestion(player);
        return true;
    }

    public static boolean canUseShortcutMinecart(ServerPlayer player, MinecartStationEntity minecart) {
        if (player == null || minecart == null) {
            return false;
        }
        String stationId = minecart.getStationId();
        return RETURN_MINECART_ID.equals(stationId)
            || SHORTCUT_MINECART_ID.equals(stationId);
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, String choiceId) {
        if (!QUESTION_CONTEXT_SHORTCUT.equals(context) || player == null || !isParticipant(player)) {
            return;
        }
        if (!"yes".equals(choiceId)) {
            return;
        }
        player.closeContainer();
        player.stopUsingItem();
        player.playNotifySound(ModSounds.STAIRS_DOWN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ModTeleport.to(
            player,
            player.serverLevel(),
            SHORTCUT_RETURN_TARGET.x,
            SHORTCUT_RETURN_TARGET.y,
            SHORTCUT_RETURN_TARGET.z,
            yaw('S'),
            0.0F
        );
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    public static boolean isMainEventActive() {
        return false;
    }

    public static boolean isTimeFreezeActive() {
        if (frozenMinute == null) {
            return false;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(ModDimensions.STARDEW_VALLEY);
        return stardewLevel != null
            && (hasCurrentSessionParticipant(stardewLevel) || FestivalService.isDebugActiveFestival(FESTIVAL_ID));
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
        int participants = level == null ? 0 : (int) level.players().stream().filter(SpiritEveFestivalService::isParticipant).count();
        int musicSynced = level == null ? 0 : (int) level.players().stream()
            .filter(player -> player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED))
            .count();
        boolean overlayApplied = level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        return "Spirit's Eve: participants=" + participants
            + ", musicSynced=" + musicSynced
            + ", npcActors=" + (NPC_ACTORS.isActorsActive() ? "active" : "inactive")
            + ", overlayApplied=" + overlayApplied
            + ", timeFreeze=" + isTimeFreezeActive()
            + ", overlayBounds=(-36,63,-77)..(74,71,22)"
            + ", pierreShopBounds=(-1,63,-19)..(2,67,-16)"
            + ", entryExitBounds=(-39,64,-70)..(78,84,29)"
            + ", yearVariant=year2";
    }

    private static boolean isActiveSpiritEveDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equals(definition.id()))
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

    private static void startTimeFreeze(ServerLevel level) {
        if (frozenMinute == null) {
            frozenMinute = FESTIVAL_START_MINUTE;
        }
    }

    private static void stopTimeFreeze() {
        frozenMinute = null;
    }

    private static void clearRuntimeState(ServerLevel level) {
        stopTimeFreeze();
        for (ServerPlayer player : level.players()) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            clearClientStateIfNeeded(player);
        }
        restoreNpcs(level);
        removeFestivalMonsters(level);
        removeShortcutMinecarts(level);
        clearFestivalState();
    }

    private static void syncParticipantMusic(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean shouldPlay = isParticipant(player);
            boolean synced = player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
            if (shouldPlay && !synced) {
                syncFestivalMusic(player, FestivalMusicStatePayload.SPIRITS_EVE);
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            } else if (!shouldPlay && synced) {
                clearClientStateIfNeeded(player);
            }
        }
    }

    private static void clearClientStateIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
    }

    private static void tickActors(ServerLevel level, boolean activeRequested) {
        NPC_ACTORS.tick(level, activeRequested);
    }

    private static void ensureFestivalMonsters(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean activeRequested = (isActiveSpiritEveDay() && hasCurrentSessionParticipant(level))
            || FestivalService.isDebugActiveFestival(FESTIVAL_ID)
            || NPC_ACTORS.isDebugRequested();
        if (!activeRequested) {
            removeFestivalMonsters(level);
            return;
        }
        for (int i = 0; i < MONSTERS.size(); i++) {
            MonsterSpawn spawn = MONSTERS.get(i);
            if (hasFestivalMonster(level, spawn, i)) {
                continue;
            }
            level.getChunkAt(BlockPos.containing(spawn.point().position()));
            Mob mob = spawn.type().create(level);
            if (mob == null) {
                continue;
            }
            configureFestivalMonster(mob, spawn, i);
            if (level.addFreshEntity(mob)) {
                continue;
            }
            mob.discard();
        }
    }

    private static boolean hasFestivalMonster(ServerLevel level, MonsterSpawn spawn, int index) {
        String spawnTag = monsterSpawnTag(index);
        AABB box = new AABB(BlockPos.containing(spawn.point().position())).inflate(1.5D, 2.0D, 1.5D);
        return !level.getEntitiesOfClass(Mob.class, box, mob ->
            mob.isAlive() && mob.getType() == spawn.type() && mob.getTags().contains(MONSTER_TAG) && mob.getTags().contains(spawnTag)
        ).isEmpty();
    }

    private static void configureFestivalMonster(Mob mob, MonsterSpawn spawn, int index) {
        FestivalNpcActorRuntime.Waypoint point = spawn.point();
        mob.addTag(MONSTER_TAG);
        mob.addTag(ACTOR_TAG);
        mob.addTag(monsterSpawnTag(index));
        mob.getPersistentData().putBoolean(MONSTER_PERSISTENT_FLAG, true);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setPersistenceRequired();
        mob.setSilent(true);
        mob.setCanPickUpLoot(false);
        if (mob instanceof Skeleton) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (mob instanceof EnderMan enderMan) {
            enderMan.setCarriedBlock(null);
        }
        mob.moveTo(point.position().x, point.position().y, point.position().z, point.yaw(), 0.0F);
        mob.setYHeadRot(point.yaw());
        mob.setYBodyRot(point.yaw());
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hasImpulse = false;
    }

    private static void removeFestivalMonsters(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        level.getEntitiesOfClass(LivingEntity.class, VENUE_BOUNDS.inflate(8.0D), SpiritEveFestivalService::isFestivalMonster)
            .forEach(LivingEntity::discard);
    }

    private static boolean isFestivalMonster(LivingEntity entity) {
        return entity != null && (entity.getTags().contains(MONSTER_TAG)
            || entity.getPersistentData().getBoolean(MONSTER_PERSISTENT_FLAG));
    }

    private static String monsterSpawnTag(int index) {
        return MONSTER_TAG + "_" + index;
    }

    private static void ensureRewardProps(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean activeRequested = (isActiveSpiritEveDay() && hasCurrentSessionParticipant(level))
            || FestivalService.isDebugActiveFestival(FESTIVAL_ID)
            || NPC_ACTORS.isDebugRequested();
        if (!activeRequested) {
            removeShortcutMinecarts(level);
            return;
        }
        ensureRewardChest(level);
        ensureShortcutMinecart(level, SHORTCUT_MINECART_POS, SHORTCUT_MINECART_ID);
        ensureShortcutMinecart(level, RETURN_MINECART_POS, RETURN_MINECART_ID);
    }

    private static void ensureRewardChest(ServerLevel level) {
        level.getChunkAt(GOLDEN_PUMPKIN_CHEST_POS);
        BlockState state = level.getBlockState(GOLDEN_PUMPKIN_CHEST_POS);
        if (!state.is(ModBlocks.WOODEN_CHEST.get())) {
            state = ModBlocks.WOODEN_CHEST.get().defaultBlockState()
                .setValue(WoodenChestBlock.FACING, Direction.NORTH)
                .setValue(WoodenChestBlock.OPEN, false);
            level.setBlock(GOLDEN_PUMPKIN_CHEST_POS, state, 3);
        } else if (state.hasProperty(WoodenChestBlock.OPEN) && state.getValue(WoodenChestBlock.OPEN)) {
            level.setBlock(GOLDEN_PUMPKIN_CHEST_POS, state.setValue(WoodenChestBlock.OPEN, false), 3);
        }
        if (level.getBlockEntity(GOLDEN_PUMPKIN_CHEST_POS) instanceof WoodenChestBlockEntity chest) {
            chest.setColorSelection(CHEST_BROWN_COLOR);
        }
    }

    private static void ensureShortcutMinecart(ServerLevel level, BlockPos pos, String stationId) {
        if (findShortcutMinecart(level, pos, stationId) != null) {
            return;
        }
        level.getChunkAt(pos);
        MinecartStationEntity minecart = new MinecartStationEntity(level, pos, stationId);
        minecart.addTag(ACTOR_TAG);
        minecart.addTag(stationId);
        if (!level.addFreshEntity(minecart)) {
            minecart.discard();
        }
    }

    private static MinecartStationEntity findShortcutMinecart(ServerLevel level, BlockPos pos, String stationId) {
        AABB box = new AABB(pos).inflate(0.75D, 1.5D, 0.75D);
        return level.getEntitiesOfClass(MinecartStationEntity.class, box, minecart ->
                minecart.isAlive() && stationId.equals(minecart.getStationId()))
            .stream()
            .min(Comparator.comparingInt(Entity::getId))
            .orElse(null);
    }

    private static void removeShortcutMinecarts(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        level.getEntitiesOfClass(MinecartStationEntity.class, VENUE_BOUNDS.inflate(8.0D), minecart ->
                SHORTCUT_MINECART_ID.equals(minecart.getStationId()) || RETURN_MINECART_ID.equals(minecart.getStationId()))
            .forEach(MinecartStationEntity::discard);
    }

    private static void openGoldenPumpkinChest(ServerPlayer player) {
        RewardChestContainer container = new RewardChestContainer(claimedSpiritEveRewardThisYear(player), createSpiritEveRewardStack());
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.stardew_craft.wooden_chest");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player openingPlayer) {
                return new WoodenChestMenu(containerId, playerInventory, container, ignored -> {
                }, CHEST_BROWN_COLOR);
            }
        });
    }

    private static boolean claimedSpiritEveRewardThisYear(ServerPlayer player) {
        return claimedSpiritEveRewardForYear(player, currentYear());
    }

    private static boolean claimedSpiritEveRewardForYear(ServerPlayer player, int year) {
        return player != null && player.getPersistentData().getInt(TAG_REWARD_CLAIM_YEAR) == year;
    }

    private static void markSpiritEveRewardClaimed(ServerPlayer player, int year) {
        player.getPersistentData().putInt(TAG_REWARD_CLAIM_YEAR, year);
    }

    private static ItemStack createSpiritEveRewardStack() {
        if (currentYear() % 2 == 0) {
            return new ItemStack(ModItems.PRIZE_TICKET.get());
        }
        return new ItemStack(ModItems.GOLDEN_PUMPKIN.get());
    }

    private static int currentYear() {
        StardewTimeManager time = StardewTimeManager.get();
        return time == null ? 1 : time.getCurrentYear();
    }

    private static void sendShortcutQuestion(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            QUESTION_CONTEXT_SHORTCUT,
            0,
            "",
            Component.Serializer.toJson(Component.translatable("stardewcraft.festival.spirit_eve.shortcut.question"), player.registryAccess()),
            List.of(
                response("yes", Component.translatable("stardewcraft.dialog.yes"), player),
                response("no", Component.translatable("stardewcraft.dialog.no"), player)
            )
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
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
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.spirit_eve.unavailable"), true);
            return;
        }
        startTimeFreeze(player.serverLevel());
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        syncFestivalMusic(player, FestivalMusicStatePayload.SPIRITS_EVE);
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
        tickNpcActors(player.serverLevel());
        ensureFestivalMonsters(player.serverLevel());
        ensureRewardProps(player.serverLevel());
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
        target = FestivalBoundaryReturn.findSafeOutside(player, ENTRY_EXIT_BOUNDS, target);
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
        Vec3 fallback = pushInsideEntry(player == null ? preferred : player.position());
        Vec3 target = FestivalBoundaryReturn.findSafeInside(player, ENTRY_EXIT_BOUNDS, preferred, fallback);
        return target != null ? target : fallback;
    }

    private static boolean isInsideEntryBounds(Vec3 position) {
        return position != null && ENTRY_EXIT_BOUNDS.contains(position);
    }

    private static Component blockedEntryMessage(ServerLevel level) {
        String key = FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)
            ? "message.stardewcraft.festival.ended"
            : "message.stardewcraft.festival.spirit_eve.setup";
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

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            participant.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
            syncFestivalMusic(participant, FestivalMusicStatePayload.RELEASE);
            returnToFarm(participant);
        }
        clearFestivalState();
        if (level != null) {
            restoreNpcs(level);
            removeFestivalMonsters(level);
            removeShortcutMinecarts(level);
            FestivalService.endFestival(level, FESTIVAL_ID);
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

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(SpiritEveFestivalService::isParticipant).toList();
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
            .filter(SpiritEveFestivalService::isParticipant)
            .toList();
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

    private static void clearFestivalState() {
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        CONFIRM_STATE.clearAll();
        stopTimeFreeze();
        NPC_ACTORS.setDebugRequested(false);
    }

    private static String canonical(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private static String fmt(Vec3 position) {
        if (position == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", position.x, position.y, position.z);
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

    private static FestivalNpcActorRuntime.ActorDefinition route(String npcId, FestivalNpcActorRuntime.Waypoint... points) {
        return FestivalNpcActorRuntime.route(npcId, true, ROUTE_WAIT_TICKS, points);
    }

    private static Map<String, FestivalNpcActorRuntime.ActorDefinition> createActors() {
        List<FestivalNpcActorRuntime.ActorDefinition> definitions = new ArrayList<>();
        definitions.add(actor("pierre", point(1, 64, -21, 'S')));
        definitions.add(actor("lewis", point(-3, 64, -15, 'S')));
        definitions.add(actor("marnie", point(-2, 64, -13, 'W')));
        definitions.add(actor("clint", point(10, 64, -4, 'S')));
        definitions.add(actor("willy", point(12, 64, -2, 'N')));
        definitions.add(actor("evelyn", point(23, 64, -4, 'S')));
        definitions.add(actor("george", point(24, 64, -2, 'N')));
        definitions.add(actor("caroline", point(9, 64, 2, 'W')));
        definitions.add(actor("jodi", point(7, 64, 5, 'N')));
        definitions.add(actor("marlon", point(-4, 64, 1, 'S')));
        definitions.add(actor("elliott", point(-18, 64, -1, 'E')));
        definitions.add(route("abigail", point(-10, 64, 1, 'W'), point(-13, 64, 4, 'W')));
        definitions.add(actor("demetrius", point(-3, 64, 14, 'E')));
        definitions.add(actor("robin", point(-1, 64, 16, 'N')));
        definitions.add(actor("maru", point(2, 64, 13, 'W')));
        definitions.add(actor("pam", point(6, 64, 14, 'N')));
        definitions.add(actor("gus", point(9, 64, 12, 'S')));
        definitions.add(actor("shane", point(-13, 66, -38, 'E')));
        definitions.add(actor("jas", point(-12, 66, -38, 'W')));
        definitions.add(actor("emily", point(-14, 66, -52, 'S')));
        definitions.add(actor("sebastian", point(-10, 66, -64, 'S')));
        definitions.add(actor("alex", point(12, 66, -58, 'S')));
        definitions.add(actor("haley", point(14, 66, -53, 'S')));
        definitions.add(actor("harvey", point(13, 66, -39, 'W')));
        definitions.add(actor("vincent", point(22, 66, -35, 'S')));
        definitions.add(actor("sam", point(26, 66, -35, 'N')));

        return actorMap(definitions);
    }

    private record MonsterSpawn(EntityType<? extends Mob> type, FestivalNpcActorRuntime.Waypoint point) {
    }

    private static final class RewardChestContainer extends SimpleContainer {
        private final int year;
        private final boolean initiallyClaimed;
        private final ItemStack rewardStack;
        private boolean stopped;

        private RewardChestContainer(boolean claimed, ItemStack rewardStack) {
            super(27);
            this.year = currentYear();
            this.initiallyClaimed = claimed;
            this.rewardStack = rewardStack.copy();
            if (!claimed) {
                setItem(REWARD_SLOT, this.rewardStack.copy());
            }
        }

        @Override
        public void stopOpen(Player player) {
            super.stopOpen(player);
            if (stopped || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            stopped = true;
            boolean rewardLeft = containsRewardItem();
            if (!initiallyClaimed && !rewardLeft && !claimedSpiritEveRewardForYear(serverPlayer, year)) {
                markSpiritEveRewardClaimed(serverPlayer, year);
                serverPlayer.playNotifySound(ModSounds.REWARD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                ItemPickupHudPacket.sendTo(serverPlayer, rewardStack, rewardStack.getCount(), rewardStack.is(ModItems.GOLDEN_PUMPKIN.get()));
                ObjectDialogueService.show(serverPlayer, Component.translatable(
                    "stardewcraft.festival.spirit_eve.reward_received",
                    rewardStack.getHoverName(),
                    rewardStack.getCount()
                ));
            }
            returnNonRewardItems(serverPlayer, !initiallyClaimed);
            clearContent();
        }

        private boolean containsRewardItem() {
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack stack = getItem(i);
                if (!stack.isEmpty() && stack.is(rewardStack.getItem())) {
                    return true;
                }
            }
            return false;
        }

        private void returnNonRewardItems(ServerPlayer player, boolean skipRewardItem) {
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack stack = getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (skipRewardItem && stack.is(rewardStack.getItem())) {
                    continue;
                }
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy)) {
                    player.drop(copy, false);
                }
            }
        }
    }

}
