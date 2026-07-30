package com.stardew.craft.cutscene.server;

import com.mojang.logging.LogUtils;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.network.CombatRescuePreparePayload;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Teleport/load/start orchestration for vanilla-style combat rescue scenes.
 *
 * <p>A rescue is deliberately not started in the same tick as its teleport.
 * The coordinator waits for a client ACK proving that the destination
 * dimension and chunk are loaded, then delegates playback and protection to
 * {@link ServerCutsceneTracker}.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CombatRescueCutsceneCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MINE_EVENT_ID = "combat_rescue_mine";
    public static final String HOSPITAL_EVENT_ID = "combat_rescue_hospital";
    public static final String ISLAND_EVENT_ID = "combat_rescue_island";

    /** Compatibility aliases; the canonical authoring data lives in {@link CombatRescuePoints}. */
    public static final Destination MINE_PLAYER = destination(CombatRescuePoints.M01);
    public static final Destination HOSPITAL_PLAYER = destination(CombatRescuePoints.H01);
    public static final Destination ISLAND_PLAYER = destination(CombatRescuePoints.I01);
    public static final Destination DESERT_FESTIVAL_RECOVERY = destination(CombatRescuePoints.D01);

    private static final int PROBE_INTERVAL_TICKS = 10;
    private static final int CLIENT_LOAD_TIMEOUT_TICKS = 20 * 30;
    private static final double MAX_READY_DISTANCE_SQUARED = 4.0D * 4.0D;
    private static final Map<UUID, PendingRescue> PENDING = new ConcurrentHashMap<>();

    private CombatRescueCutsceneCoordinator() {
    }

    public enum MineDialogue {
        ROBIN("event.combat_rescue.mine.robin"),
        CLINT("event.combat_rescue.mine.clint"),
        MARU_SPOUSE("event.combat_rescue.mine.maru_spouse"),
        MARU_NOT_SPOUSE("event.combat_rescue.mine.maru_not_spouse"),
        LINUS("event.combat_rescue.mine.linus"),
        SPOUSE_PLAYER_MALE("event.combat_rescue.mine.spouse_player_male"),
        SPOUSE_PLAYER_FEMALE("event.combat_rescue.mine.spouse_player_female");

        private final String translationKey;

        MineDialogue(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum Result {
        COMPLETED,
        DESTINATION_UNAVAILABLE,
        AUTHORING_POINTS_PENDING,
        CLIENT_LOAD_TIMEOUT,
        CUTSCENE_START_FAILED,
        REPLACED
    }

    @FunctionalInterface
    public interface Completion {
        Completion NOOP = (player, result) -> {
        };

        void accept(ServerPlayer player, Result result);
    }

    public record MineRescueChoice(String npcId, MineDialogue dialogue) {
        public MineRescueChoice {
            npcId = normalizeNpcId(npcId);
            dialogue = dialogue == null ? MineDialogue.LINUS : dialogue;
        }
    }

    public record IslandRescueChoice(String npcId, String dialogueKey) {
        public IslandRescueChoice {
            npcId = normalizeNpcId(npcId);
            dialogueKey = dialogueKey == null || dialogueKey.isBlank()
                    ? "event.combat_rescue.island.willy"
                    : dialogueKey.trim();
        }
    }

    public record Destination(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        public Destination {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension cannot be null");
            }
        }

        public BlockPos blockPos() {
            return BlockPos.containing(x, y, z);
        }

        public Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    /**
     * Reproduces Stardew Valley's mine rescuer roll:
     * Robin/Clint/Maru each occupy one of seven slots, Linus occupies four,
     * then an eligible spouse independently overrides the result 10% of the time.
     */
    public static MineRescueChoice selectVanillaMineRescuer(
            RandomGenerator random,
            String spouseNpcId,
            boolean engaged,
            boolean playerMale
    ) {
        RandomGenerator source = random == null ? ThreadLocalRandom.current() : random;
        String normalizedSpouse = normalizeOptionalNpcId(spouseNpcId);

        MineRescueChoice choice = switch (source.nextInt(7)) {
            case 0 -> new MineRescueChoice("robin", MineDialogue.ROBIN);
            case 1 -> new MineRescueChoice("clint", MineDialogue.CLINT);
            case 2 -> new MineRescueChoice(
                    "maru",
                    "maru".equals(normalizedSpouse)
                            ? MineDialogue.MARU_SPOUSE
                            : MineDialogue.MARU_NOT_SPOUSE);
            default -> new MineRescueChoice("linus", MineDialogue.LINUS);
        };

        if (!engaged && normalizedSpouse != null && source.nextDouble() < 0.1D) {
            return new MineRescueChoice(
                    normalizedSpouse,
                    playerMale
                            ? MineDialogue.SPOUSE_PLAYER_MALE
                            : MineDialogue.SPOUSE_PLAYER_FEMALE);
        }
        return choice;
    }

    /**
     * Original IslandSouth roll: Willy by default; once Leo is known, choose
     * Leo on an independent 50% roll.
     */
    public static IslandRescueChoice selectVanillaIslandRescuer(
            RandomGenerator random,
            boolean knowsLeo
    ) {
        RandomGenerator source = random == null ? ThreadLocalRandom.current() : random;
        if (knowsLeo && source.nextBoolean()) {
            return new IslandRescueChoice("leo", "event.combat_rescue.island.leo");
        }
        return new IslandRescueChoice("willy", "event.combat_rescue.island.willy");
    }

    /** A friendship record is the Stardew equivalent of friendshipData.ContainsKey("Leo"). */
    public static IslandRescueChoice selectVanillaIslandRescuer(
            ServerPlayer player,
            RandomGenerator random
    ) {
        boolean knowsLeo = player != null
                && NpcFriendshipDataManager.get(player.serverLevel())
                        .get(player.getUUID(), "leo") != null;
        return selectVanillaIslandRescuer(random, knowsLeo);
    }

    public static boolean beginMineRescue(
            ServerPlayer player,
            MineRescueChoice rescuer,
            Completion completion
    ) {
        MineRescueChoice choice = rescuer == null
                ? new MineRescueChoice("linus", MineDialogue.LINUS)
                : rescuer;
        return begin(
                player,
                MINE_PLAYER,
                MINE_EVENT_ID,
                choice.npcId(),
                choice.dialogue().translationKey(),
                completion);
    }

    public static boolean beginMineRescue(
            ServerPlayer player,
            String rescuerNpcId,
            MineDialogue dialogue,
            Completion completion
    ) {
        return beginMineRescue(player, new MineRescueChoice(rescuerNpcId, dialogue), completion);
    }

    public static boolean beginHospitalRescue(ServerPlayer player, Completion completion) {
        return begin(
                player,
                HOSPITAL_PLAYER,
                HOSPITAL_EVENT_ID,
                "harvey",
                "event.combat_rescue.hospital.harvey",
                completion);
    }

    /**
     * Starts the IslandSouth rescue only after I01-I03 are captured in
     * Minecraft. Until then this fails closed and reports the pending IDs.
     */
    public static boolean beginIslandRescue(
            ServerPlayer player,
            IslandRescueChoice rescuer,
            Completion completion
    ) {
        if (!CombatRescuePoints.allAuthorConfirmed(CombatRescuePoints.ISLAND)) {
            LOGGER.warn("Island combat rescue is pending authored Minecraft points {}; "
                            + "refusing to use provisional fallbacks for {}",
                    CombatRescuePoints.pendingPointIds(CombatRescuePoints.ISLAND),
                    player == null ? "<null>" : player.getName().getString());
            if (player != null) {
                safeComplete(player, completion, Result.AUTHORING_POINTS_PENDING);
            }
            return false;
        }
        return beginIslandRescueInternal(player, rescuer, completion);
    }

    /**
     * Explicit authoring-only entry point for checking provisional I01-I03.
     * Production pass-out logic must call {@link #beginIslandRescue}.
     */
    public static boolean beginIslandRescueWithPendingFallback(
            ServerPlayer player,
            IslandRescueChoice rescuer,
            Completion completion
    ) {
        LOGGER.warn("Using provisional Island rescue points {} for authoring preview only",
                CombatRescuePoints.pendingPointIds(CombatRescuePoints.ISLAND));
        return beginIslandRescueInternal(player, rescuer, completion);
    }

    private static boolean beginIslandRescueInternal(
            ServerPlayer player,
            IslandRescueChoice rescuer,
            Completion completion
    ) {
        IslandRescueChoice choice = rescuer == null
                ? new IslandRescueChoice("willy", "event.combat_rescue.island.willy")
                : rescuer;
        return begin(
                player,
                ISLAND_PLAYER,
                ISLAND_EVENT_ID,
                choice.npcId(),
                choice.dialogueKey(),
                completion);
    }

    /**
     * Moves a desert-festival knockout to D01 and completes after client load
     * confirmation. No NPC cutscene is started, matching the original event.
     */
    public static boolean beginDesertFestivalRecovery(ServerPlayer player, Completion completion) {
        return begin(
                player,
                DESERT_FESTIVAL_RECOVERY,
                null,
                "",
                "",
                completion);
    }

    public static boolean isPending(UUID playerId) {
        return playerId != null && PENDING.containsKey(playerId);
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PendingRescue removed = PENDING.remove(player.getUUID());
        if (removed != null) {
            finish(player, removed, Result.REPLACED);
        }
    }

    private static boolean begin(
            ServerPlayer player,
            Destination destination,
            String eventId,
            String rescuerNpcId,
            String dialogueKey,
            Completion completion
    ) {
        if (player == null || destination == null) {
            return false;
        }
        ServerLevel targetLevel = player.server.getLevel(destination.dimension());
        if (targetLevel == null) {
            safeComplete(player, completion, Result.DESTINATION_UNAVAILABLE);
            return false;
        }

        long token = nextToken();
        PendingRescue pending = new PendingRescue(
                token,
                destination,
                eventId,
                normalizeOptionalNpcId(rescuerNpcId) == null ? "" : normalizeNpcId(rescuerNpcId),
                dialogueKey == null ? "" : dialogueKey.trim(),
                completion == null ? Completion.NOOP : completion,
                player.server.getTickCount());
        PendingRescue replaced = PENDING.put(player.getUUID(), pending);
        if (replaced != null) {
            finish(player, replaced, Result.REPLACED);
        }

        // Load the server-side target first, then teleport. The client probe is
        // sent only after teleportTo has queued the dimension/position packets.
        targetLevel.getChunkAt(destination.blockPos());
        ModTeleport.to(
                player,
                targetLevel,
                destination.x(),
                destination.y(),
                destination.z(),
                destination.yaw(),
                destination.pitch());
        holdAtDestination(player, pending);
        sendProbe(player, pending);
        return true;
    }

    /** Called only by the validated C2S ready payload on the server thread. */
    public static void onClientReady(ServerPlayer player, long token) {
        PendingRescue pending = PENDING.get(player.getUUID());
        if (pending == null || pending.stage != Stage.WAITING_FOR_CLIENT || pending.token != token) {
            return;
        }

        Destination destination = pending.destination;
        if (!player.level().dimension().equals(destination.dimension())
                || !player.serverLevel().hasChunkAt(destination.blockPos())
                || player.position().distanceToSqr(destination.position()) > MAX_READY_DISTANCE_SQUARED) {
            return;
        }

        if (pending.eventId == null) {
            if (PENDING.remove(player.getUUID(), pending)) {
                finish(player, pending, Result.COMPLETED);
            }
            return;
        }

        if (!ServerCutsceneTracker.startEvent(player, pending.eventId)) {
            if (PENDING.remove(player.getUUID(), pending)) {
                finish(player, pending, Result.CUTSCENE_START_FAILED);
            }
            return;
        }
        pending.stage = Stage.PLAYING;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PendingRescue pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }

        if (pending.stage == Stage.PLAYING) {
            if (!ServerCutsceneTracker.isActive(player.getUUID())
                    && PENDING.remove(player.getUUID(), pending)) {
                finish(player, pending, Result.COMPLETED);
            }
            return;
        }

        holdAtDestination(player, pending);
        long elapsed = player.server.getTickCount() - pending.waitStartedAtTick;
        if (elapsed >= CLIENT_LOAD_TIMEOUT_TICKS) {
            if (PENDING.remove(player.getUUID(), pending)) {
                LOGGER.warn("Combat rescue destination did not load for {} within {} ticks",
                        player.getName().getString(), CLIENT_LOAD_TIMEOUT_TICKS);
                finish(player, pending, Result.CLIENT_LOAD_TIMEOUT);
            }
            return;
        }
        if (elapsed == 0L || elapsed - pending.lastProbeAtTick >= PROBE_INTERVAL_TICKS) {
            sendProbe(player, pending);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PendingRescue pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        // Logging out clears ServerCutsceneTracker's in-memory session. A
        // rescue which disconnected during playback must therefore repeat the
        // destination handshake and restart the repeatable manual event.
        pending.stage = Stage.WAITING_FOR_CLIENT;
        ServerLevel level = player.server.getLevel(pending.destination.dimension());
        if (level == null) {
            if (PENDING.remove(player.getUUID(), pending)) {
                finish(player, pending, Result.DESTINATION_UNAVAILABLE);
            }
            return;
        }
        pending.waitStartedAtTick = player.server.getTickCount();
        pending.lastProbeAtTick = pending.waitStartedAtTick;
        level.getChunkAt(pending.destination.blockPos());
        ModTeleport.to(
                player,
                level,
                pending.destination.x(),
                pending.destination.y(),
                pending.destination.z(),
                pending.destination.yaw(),
                pending.destination.pitch());
        holdAtDestination(player, pending);
        sendProbe(player, pending);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static void holdAtDestination(ServerPlayer player, PendingRescue pending) {
        Destination destination = pending.destination;
        ServerLevel level = player.server.getLevel(destination.dimension());
        if (level == null) {
            return;
        }
        if (!player.level().dimension().equals(destination.dimension())
                || player.position().distanceToSqr(destination.position()) > 0.25D) {
            ModTeleport.to(
                    player,
                    level,
                    destination.x(),
                    destination.y(),
                    destination.z(),
                    destination.yaw(),
                    destination.pitch());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);
    }

    private static void sendProbe(ServerPlayer player, PendingRescue pending) {
        pending.lastProbeAtTick = player.server.getTickCount();
        PacketDistributor.sendToPlayer(player, new CombatRescuePreparePayload(
                pending.token,
                pending.destination.dimension().location(),
                pending.destination.blockPos(),
                pending.rescuerNpcId,
                pending.dialogueKey));
    }

    private static void finish(ServerPlayer player, PendingRescue pending, Result result) {
        safeComplete(player, pending.completion, result);
    }

    private static void safeComplete(ServerPlayer player, Completion completion, Result result) {
        Completion callback = completion == null ? Completion.NOOP : completion;
        try {
            callback.accept(player, result);
        } catch (RuntimeException exception) {
            LOGGER.error("Combat rescue completion callback failed for {} ({})",
                    player.getName().getString(), result, exception);
        }
    }

    private static long nextToken() {
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    private static Destination destination(CombatRescuePoints.Point point) {
        return new Destination(
                point.dimension(),
                point.x(),
                point.y(),
                point.z(),
                point.yaw(),
                point.pitch());
    }

    private static String normalizeNpcId(String npcId) {
        String normalized = normalizeOptionalNpcId(npcId);
        return normalized == null ? "linus" : normalized;
    }

    private static String normalizeOptionalNpcId(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String normalized = npcId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.:-]+")) {
            return null;
        }
        return normalized;
    }

    private enum Stage {
        WAITING_FOR_CLIENT,
        PLAYING
    }

    private static final class PendingRescue {
        private final long token;
        private final Destination destination;
        private final String eventId;
        private final String rescuerNpcId;
        private final String dialogueKey;
        private final Completion completion;
        private volatile long waitStartedAtTick;
        private volatile long lastProbeAtTick;
        private volatile Stage stage = Stage.WAITING_FOR_CLIENT;

        private PendingRescue(
                long token,
                Destination destination,
                String eventId,
                String rescuerNpcId,
                String dialogueKey,
                Completion completion,
                long startedAtTick
        ) {
            this.token = token;
            this.destination = destination;
            this.eventId = eventId;
            this.rescuerNpcId = rescuerNpcId;
            this.dialogueKey = dialogueKey;
            this.completion = completion;
            this.waitStartedAtTick = startedAtTick;
            this.lastProbeAtTick = startedAtTick;
        }
    }
}
