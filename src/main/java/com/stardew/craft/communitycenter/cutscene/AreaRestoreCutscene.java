package com.stardew.craft.communitycenter.cutscene;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.junimo.JunimoSpawner;
import com.stardew.craft.communitycenter.junimo.StarPlacementAnimator;
import com.stardew.craft.communitycenter.restore.AreaRestoreHandler;
import com.stardew.craft.communitycenter.restore.CCAreaRegistry;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端区域修复过场状态机。SDV CommunityCenter.UpdateWhenCurrentLocation 区域修复过场还原。
 * <p>
 * 4 阶段 (匹配 SDV 时间线):
 * Phase 0 (FREEZE): 冻结玩家 (1s / 20 tick) — SDV restoreAreaTimer=1000
 * Phase 1 (APPEAR): 随机生成临时 Junimo + tinyWhip 音效 (3s / 60 tick) — SDV restoreAreaTimer=3000
 * Phase 2 (GLOW):   辉光渐强 + wind 音效 + dustMeep/junimoMeep1 随机叫声 (5.2s / 104 tick) — SDV restoreAreaTimer=999999→5200
 * Phase 3 (RESTORE): 移除临时 Junimo + 方块替换 + 闪白 + wand/woodyHit 音效 (2s / 40 tick) — SDV restoreAreaTimer=2000
 * <p>
 * 单实例，在区域完成时启动；同一时间只能有一个过场在运行。
 */
@SuppressWarnings("null")
public final class AreaRestoreCutscene {

    private AreaRestoreCutscene() {}

    private static boolean running = false;
    private static int currentAreaId = -1;
    private static int phase = -1;
    private static int phaseTicksRemaining = 0;
    private static ServerLevel activeLevel;
    private static java.util.UUID ownerUUID;
    private static final List<JunimoEntity> spawnedJunimos = new ArrayList<>();
    private static final ArrayDeque<PendingRestore> pendingRestores = new ArrayDeque<>();

    // Phase durations (ticks) — matches SDV millisecond timings
    private static final int PHASE_0_TICKS = 20;    // 1s  (SDV 1000ms)
    private static final int PHASE_1_TICKS = 60;    // 3s  (SDV 3000ms)
    private static final int PHASE_2_TICKS = 104;   // 5.2s (SDV 5200ms)
    private static final int PHASE_3_TICKS = 40;    // 2s  (SDV 2000ms)

    private record PendingRestore(ServerLevel level, int areaId, java.util.UUID ownerUUID) {}

    /**
     * 启动区域修复过场。
     * @param level  服务端世界
     * @param areaId 完成的区域 ID
     */
    public static void start(ServerLevel level, int areaId) {
        start(level, areaId, null);
    }

    /**
     * 启动区域修复过场，指定拥有者 UUID。
     */
    public static void start(ServerLevel level, int areaId, java.util.UUID owner) {
        if (running) {
            PendingRestore request = new PendingRestore(level, areaId, owner);
            boolean duplicate = pendingRestores.stream().anyMatch(queued ->
                    queued.level() == level
                            && queued.areaId() == areaId
                            && java.util.Objects.equals(queued.ownerUUID(), owner));
            if (!duplicate) {
                pendingRestores.addLast(request);
                StardewCraft.LOGGER.info("[CC-CUTSCENE] Queued area {} restore for owner {} while another cutscene is running",
                        areaId, owner);
            }
            return;
        }

        CCAreaRegistry.AreaBounds bounds = CCAreaRegistry.getArea(areaId);
        if (bounds == null) {
            StardewCraft.LOGGER.warn("[CC-CUTSCENE] Unknown area {}, running restore without cutscene", areaId);
            AreaRestoreHandler.restoreArea(level, areaId);
            return;
        }

        running = true;
        currentAreaId = areaId;
        activeLevel = level;
        ownerUUID = owner;
        phase = -1;
        spawnedJunimos.clear();

        advancePhase();
    }

    /**
     * 每服务端 tick 调用。
     */
    public static void tick() {
        if (!running) return;
        if (activeLevel == null) {
            stop();
            return;
        }
        if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(activeLevel)) {
            return;
        }

        BlockPos center = getCenterPos();

        // SDV parity: periodic sounds during phases
        if (phase == 1) {
            // Phase 1: randomly spawn more Junimos + "tinyWhip" sounds (SDV: 40% chance per tick)
            if (activeLevel.random.nextDouble() < 0.4) {
                spawnSingleCutsceneJunimo();
                activeLevel.playSound(null, center, ModSounds.TINY_WHIP.get(), SoundSource.NEUTRAL, 0.6f, 1.0f);
            }
        } else if (phase == 2) {
            // Phase 2: random "dustMeep"/"junimoMeep1" vocalizations (SDV: probability increases)
            float progress = 1.0f - (float) phaseTicksRemaining / PHASE_2_TICKS;
            if (activeLevel.random.nextDouble() < progress * 0.15) {
                // 用 DWOP 替代 dustMeep/junimoMeep1
                activeLevel.playSound(null, center, ModSounds.DWOP.get(), SoundSource.NEUTRAL, 0.4f,
                        0.8f + activeLevel.random.nextFloat() * 0.6f);
            }
        }

        phaseTicksRemaining--;
        if (phaseTicksRemaining <= 0) {
            advancePhase();
        }
    }

    private static void advancePhase() {
        phase++;

        BlockPos center = getCenterPos();

        switch (phase) {
            case 0 -> {
                // Phase 0: 冻结 + 辉光
                phaseTicksRemaining = PHASE_0_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_AREA_RESTORE,
                        CutscenePayload.PHASE_FREEZE,
                        currentAreaId, center));
            }
            case 1 -> {
                // Phase 1: 生成初始 3~5 只临时 Junimo (后续 tick 中会继续随机生成)
                phaseTicksRemaining = PHASE_1_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_AREA_RESTORE,
                        CutscenePayload.PHASE_APPEAR,
                        currentAreaId, center));
                spawnCutsceneJunimos();
                // SDV: "tinyWhip" 出现音效
                activeLevel.playSound(null, center, ModSounds.TINY_WHIP.get(), SoundSource.NEUTRAL, 1.0f, 1.2f);
            }
            case 2 -> {
                // Phase 2: 辉光渐强 + wind 音效
                phaseTicksRemaining = PHASE_2_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_AREA_RESTORE,
                        CutscenePayload.PHASE_GLOW,
                        currentAreaId, center));
                // SDV: Game1.playSound("wind") — 用 WAND 替代 wind 音效起始
                activeLevel.playSound(null, center, ModSounds.WAND.get(), SoundSource.NEUTRAL, 0.3f, 0.5f);
            }
            case 3 -> {
                // Phase 3: 移除临时 Junimo + 方块替换 + 闪白
                phaseTicksRemaining = PHASE_3_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_AREA_RESTORE,
                        CutscenePayload.PHASE_RESTORE,
                        currentAreaId, center));

                // SDV: 移除所有临时 Junimo
                for (JunimoEntity junimo : spawnedJunimos) {
                    if (junimo.isAlive()) {
                        junimo.discard();
                    }
                }
                spawnedJunimos.clear();

                // 执行方块替换
                int replaced = AreaRestoreHandler.restoreArea(activeLevel, currentAreaId, getCCOrigin());
                StardewCraft.LOGGER.info("[CC-CUTSCENE] Area {} restored, {} blocks replaced", currentAreaId, replaced);

                CommunityCenterSavedData data = CommunityCenterSavedData.get();
                java.util.UUID uid = ownerUUID != null ? ownerUUID : new java.util.UUID(0L, 0L);
                if (data.areAllAreasComplete(uid)) {
                    int remaining = AreaRestoreHandler.restoreAllRemaining(activeLevel, getCCOrigin());
                    StardewCraft.LOGGER.info("[CC-CUTSCENE] All areas complete; restored {} remaining blocks", remaining);
                }

                // SDV: localSound("wand") + localSound("woodyHit") + Game1.flashAlpha = 1f
                activeLevel.playSound(null, center, ModSounds.WAND.get(), SoundSource.NEUTRAL, 1.0f, 1.4f);
                activeLevel.playSound(null, center, ModSounds.SHIP.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
            }
            default -> {
                // 过场结束 — spawn star carrier Junimo (T3.1)
                if (activeLevel != null) {
                    BlockPos ccOrig = getCCOrigin();
                    StarPlacementAnimator.spawnStarCarrier(activeLevel, currentAreaId, ccOrig, ownerUUID);
                    // T3.2: If all areas now complete, trigger goodbye dance
                    CommunityCenterSavedData data = CommunityCenterSavedData.get();
                    java.util.UUID uid = ownerUUID != null ? ownerUUID : new java.util.UUID(0L, 0L);
                    if (data.areAllAreasComplete(uid)) {
                        // Delay on simulation time so opening a menu cannot start the dance while
                        // the star carrier and all other entities are frozen.
                        final ServerLevel level = activeLevel;
                        com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(
                                level, 80, () -> GoodbyeDanceCutscene.start(level, ccOrig));
                    }
                }
                stop();
            }
        }
    }

    /**
     * 在修复区域内随机生成 3~5 只装饰性 Junimo (初始批次)。
     */
    private static void spawnCutsceneJunimos() {
        if (activeLevel == null) return;
        int count = 3 + activeLevel.random.nextInt(3); // 3~5
        for (int i = 0; i < count; i++) {
            spawnSingleCutsceneJunimo();
        }
    }

    /**
     * SDV parity: 在区域内随机位置生成单个临时 Junimo。
     */
    private static void spawnSingleCutsceneJunimo() {
        if (activeLevel == null) return;
        CCAreaRegistry.AreaBounds bounds = CCAreaRegistry.getArea(currentAreaId);
        if (bounds == null || !bounds.hasRoomBounds()) return;

        BlockPos notePos = bounds.noteWorldPos(getCCOrigin());
        JunimoEntity junimo = new JunimoEntity(
                com.stardew.craft.entity.ModEntities.JUNIMO.get(), activeLevel);
        junimo.setJunimoColor(JunimoSpawner.getColorForArea(currentAreaId));
        junimo.setHoldingType(JunimoEntity.HOLDING_NONE);

        // 在 note 方块附近随机位置
        int dx = activeLevel.random.nextInt(5) - 2;
        int dz = activeLevel.random.nextInt(5) - 2;
        BlockPos spawnPos = notePos.offset(dx, 0, dz);
        // 确保可以站立
        if (!activeLevel.isEmptyBlock(spawnPos)) {
            spawnPos = spawnPos.above();
        }
        junimo.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        activeLevel.addFreshEntity(junimo);
        spawnedJunimos.add(junimo);
    }

    /**
     * 获取当前过场的 CC 原点（per-player 或默认）
     */
    private static BlockPos getCCOrigin() {
        if (ownerUUID != null && activeLevel != null) {
            return PlayerInteriorAllocator.get(activeLevel).getCCOrigin(ownerUUID);
        }
        return InteriorSubspaceManager.CC_ORIGIN;
    }

    /**
     * 获取当前区域的中心位置 (用于音效/效果)
     */
    private static BlockPos getCenterPos() {
        CCAreaRegistry.AreaBounds bounds = CCAreaRegistry.getArea(currentAreaId);
        if (bounds != null) {
            return bounds.noteWorldPos(getCCOrigin());
        }
        return InteriorSubspaceManager.CC_ORIGIN;
    }

    /**
     * 向 CC 室内区域的所有玩家广播 packet。
     */
    private static void broadcastToCC(CutscenePayload payload) {
        if (activeLevel == null) return;
        for (ServerPlayer player : activeLevel.players()) {
            // 只广播给位于当前 owner CC 实例内的玩家
            java.util.UUID posOwner = PlayerInteriorAllocator.get(activeLevel).findCCOwner(player.blockPosition());
            if (posOwner != null && posOwner.equals(ownerUUID != null ? ownerUUID : posOwner)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void stop() {
        running = false;
        currentAreaId = -1;
        phase = -1;
        activeLevel = null;
        ownerUUID = null;
        // 清理残留 Junimo
        for (JunimoEntity junimo : spawnedJunimos) {
            if (junimo.isAlive() && !junimo.isRemoved()) {
                junimo.startFadeOut();
            }
        }
        spawnedJunimos.clear();

        PendingRestore next = pendingRestores.pollFirst();
        if (next != null) {
            start(next.level(), next.areaId(), next.ownerUUID());
        }
    }

    public static boolean isRunning() {
        return running;
    }
}
