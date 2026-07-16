package com.stardew.craft.communitycenter.cutscene;

import com.stardew.craft.communitycenter.junimo.JunimoSpawner;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * SDV parity: T3.2 — Goodbye dance cutscene when all 6 areas are complete.
 * <p>
 * After the last area's restore cutscene finishes, trigger:
 * Phase 0: 6 colored Junimos fade in around the main hall center
 * Phase 1: Junimos dance/jump (with particles and sparkles)
 * Phase 2: Flash white, Junimos fade out, play goodbye jingle
 * <p>
 * SDV: junimoGoodbyeDance → camera freeze → 6 Junimos appear in a circle
 *      → "junimoStarSong" plays → sparkle particles → fade to white → end
 */
@SuppressWarnings("null")
public final class GoodbyeDanceCutscene {

    private GoodbyeDanceCutscene() {}

    private static boolean running = false;
    private static int phase = -1;
    private static int phaseTicksRemaining = 0;
    private static ServerLevel activeLevel;
    private static BlockPos activeCcOrigin;
    private static final List<JunimoEntity> dancingJunimos = new ArrayList<>();
    private static final ArrayDeque<PendingDance> pendingDances = new ArrayDeque<>();

    // Phase durations
    private static final int PHASE_0_TICKS = 40;   // 2s — Junimos appear
    private static final int PHASE_1_TICKS = 120;  // 6s — Dance
    private static final int PHASE_2_TICKS = 60;   // 3s — Flash + farewell

    private record PendingDance(ServerLevel level, BlockPos ccOrigin) {}

    /** Center of the main hall (relative to CC origin) */
    private static final BlockPos DANCE_CENTER_OFFSET = new BlockPos(11, 3, 34);

    /**
     * Start the goodbye dance cutscene.
     * Should be called after the LAST area's restore cutscene completes.
     */
    public static void start(ServerLevel level) {
        start(level, null);
    }

    /**
     * Per-player 版本，指定 CC 原点。
     */
    public static void start(ServerLevel level, BlockPos ccOrigin) {
        if (running) {
            BlockPos resolvedOrigin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
            boolean duplicate = pendingDances.stream().anyMatch(queued ->
                    queued.level() == level && queued.ccOrigin().equals(resolvedOrigin));
            if (!duplicate) {
                pendingDances.addLast(new PendingDance(level, resolvedOrigin));
            }
            return;
        }

        running = true;
        activeLevel = level;
        activeCcOrigin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        phase = -1;
        dancingJunimos.clear();

        advancePhase();
    }

    public static void tick() {
        if (!running) return;
        if (activeLevel == null) {
            stop();
            return;
        }
        if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(activeLevel)) {
            return;
        }

        phaseTicksRemaining--;

        // During dance phase, spawn particles periodically
        if (phase == 1 && phaseTicksRemaining % 10 == 0) {
            BlockPos center = activeCcOrigin.offset(DANCE_CENTER_OFFSET);
            activeLevel.sendParticles(ParticleTypes.END_ROD,
                    center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5,
                    5, 2.0, 1.0, 2.0, 0.02);
        }

        if (phaseTicksRemaining <= 0) {
            advancePhase();
        }
    }

    private static void advancePhase() {
        phase++;
        BlockPos center = activeCcOrigin.offset(DANCE_CENTER_OFFSET);

        switch (phase) {
            case 0 -> {
                // Phase 0: Spawn 6 colored Junimos in a circle
                phaseTicksRemaining = PHASE_0_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_GOODBYE_DANCE,
                        CutscenePayload.PHASE_FREEZE,
                        -1, center));
                spawnDancingJunimos(center);
                // Chime sound
                activeLevel.playSound(null, center, ModSounds.SMALL_SELECT.get(),
                        SoundSource.NEUTRAL, 1.2f, 1.0f);
            }
            case 1 -> {
                // Phase 1: Dance (Junimos jump around, sparkle particles)
                phaseTicksRemaining = PHASE_1_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_GOODBYE_DANCE,
                        CutscenePayload.PHASE_APPEAR,
                        -1, center));
                // Play a celebratory sound
                activeLevel.playSound(null, center, ModSounds.LEAFRUSTLE.get(),
                        SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            case 2 -> {
                // Phase 2: Flash white + Junimos fade out
                phaseTicksRemaining = PHASE_2_TICKS;
                broadcastToCC(new CutscenePayload(
                        CutscenePayload.TYPE_GOODBYE_DANCE,
                        CutscenePayload.PHASE_RESTORE,
                        -1, center));
                // Wand sound (magic farewell)
                activeLevel.playSound(null, center, ModSounds.WAND.get(),
                        SoundSource.NEUTRAL, 1.0f, 1.2f);
                // Fade out all Junimos
                for (JunimoEntity junimo : dancingJunimos) {
                    if (junimo.isAlive()) {
                        junimo.startFadeOut();
                    }
                }
                // Burst particles
                activeLevel.sendParticles(ParticleTypes.FLASH,
                        center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                        1, 0, 0, 0, 0);
                activeLevel.sendParticles(ParticleTypes.END_ROD,
                        center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                        30, 3.0, 2.0, 3.0, 0.05);
            }
            default -> {
                stop();
            }
        }
    }

    /**
     * Spawn 6 Junimos in a circle (one per area color).
     */
    private static void spawnDancingJunimos(BlockPos center) {
        double radius = 2.5;
        for (int areaId = 0; areaId <= 5; areaId++) {
            double angle = (2.0 * Math.PI / 6.0) * areaId;
            double x = center.getX() + 0.5 + radius * Math.cos(angle);
            double z = center.getZ() + 0.5 + radius * Math.sin(angle);

            JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), activeLevel);
            junimo.setJunimoColor(JunimoSpawner.getColorForArea(areaId));
            junimo.setHoldingType(JunimoEntity.HOLDING_NONE);
            junimo.setPos(x, center.getY(), z);
            junimo.setGlowingTag(true);
            junimo.setNoTimeout(true);
            activeLevel.addFreshEntity(junimo);
            dancingJunimos.add(junimo);
        }
    }

    private static void broadcastToCC(CutscenePayload payload) {
        if (activeLevel == null) return;
        for (ServerPlayer player : activeLevel.players()) {
            // 只广播给位于本 CC 实例内的玩家
            java.util.UUID posOwner = com.stardew.craft.interior.PlayerInteriorAllocator.get(activeLevel).findCCOwner(player.blockPosition());
            if (posOwner != null) {
                // 检查是否属于 activeCcOrigin 对应的 CC
                BlockPos playerCCOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(activeLevel).getCCOrigin(posOwner);
                if (playerCCOrigin.equals(activeCcOrigin)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    private static void stop() {
        running = false;
        phase = -1;
        activeLevel = null;
        activeCcOrigin = null;
        for (JunimoEntity junimo : dancingJunimos) {
            if (junimo.isAlive() && !junimo.isRemoved()) {
                junimo.startFadeOut();
            }
        }
        dancingJunimos.clear();

        PendingDance next = pendingDances.pollFirst();
        if (next != null) {
            start(next.level(), next.ccOrigin());
        }
    }

    public static boolean isRunning() {
        return running;
    }
}
