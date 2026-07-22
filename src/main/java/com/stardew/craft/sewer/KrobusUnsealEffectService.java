package com.stardew.craft.sewer;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewSimulationTaskScheduler;
import com.stardew.craft.world.MutantBugLairService;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Recreates the two delayed debuffSpell projectiles used by vanilla Krobus when opening BugLand. */
public final class KrobusUnsealEffectService {
    private static final int PENDING_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final int FIRST_BOLT_DELAY = 4;
    private static final int SECOND_BOLT_DELAY = 14;
    private static final int BOLT_STEPS = 30;
    private static final int BOLT_STEP_TICKS = 2;
    private static final Vec3 FALLBACK_KROBUS_ORIGIN = new Vec3(30.5D, 52.1D, 54.5D);
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private KrobusUnsealEffectService() {
    }

    public static void armAfterDialogueEffect(ServerPlayer player) {
        if (player != null && isInSewer(player)) {
            PENDING.put(player.getUUID(), player.server.getTickCount() + PENDING_TIMEOUT_TICKS);
        }
    }

    public static void onDialogueClosed(ServerPlayer player, String npcId) {
        if (player == null || npcId == null || !"krobus".equalsIgnoreCase(npcId)) {
            return;
        }
        Integer expiresAt = PENDING.remove(player.getUUID());
        if (expiresAt == null || player.server.getTickCount() > expiresAt || !isInSewer(player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Vec3 start = findKrobusOrigin(level, player);
        Vec3 seal = Vec3.atCenterOf(MutantBugLairService.ENTRANCE_PORTAL_BASE.above());
        scheduleBolt(level, start, seal, FIRST_BOLT_DELAY);
        scheduleBolt(level, start, seal, SECOND_BOLT_DELAY);
    }

    private static void scheduleBolt(ServerLevel level, Vec3 start, Vec3 end, int delayTicks) {
        StardewSimulationTaskScheduler.schedule(level, delayTicks, () -> level.playSound(
                null, start.x, start.y, start.z, ModSounds.DEBUFF_SPELL.get(), SoundSource.PLAYERS, 1.0F, 1.0F));
        for (int step = 0; step <= BOLT_STEPS; step++) {
            int currentStep = step;
            StardewSimulationTaskScheduler.schedule(level, delayTicks + step * BOLT_STEP_TICKS,
                    () -> emitBoltStep(level, start, end, currentStep));
        }
    }

    private static void emitBoltStep(ServerLevel level, Vec3 start, Vec3 end, int step) {
        double t = step / (double) BOLT_STEPS;
        double arc = Math.sin(t * Math.PI) * 0.7D;
        double x = start.x + (end.x - start.x) * t;
        double y = start.y + (end.y - start.y) * t + arc;
        double z = start.z + (end.z - start.z) * t;
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.58F, 0.0F, 0.2F),
                x, y, z, 3, 0.08D, 0.08D, 0.08D, 0.01D);
        level.sendParticles(ParticleTypes.WITCH, x, y, z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        if (step == BOLT_STEPS) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.9F, 0.05F, 0.4F),
                    end.x, end.y, end.z, 32, 0.38D, 0.7D, 0.38D, 0.04D);
            level.sendParticles(ParticleTypes.WITCH,
                    end.x, end.y, end.z, 20, 0.32D, 0.65D, 0.32D, 0.035D);
            level.sendParticles(ParticleTypes.PORTAL,
                    end.x, end.y, end.z, 16, 0.3D, 0.6D, 0.3D, 0.06D);
        }
    }

    private static Vec3 findKrobusOrigin(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(StardewNpcEntity.class, new AABB(player.blockPosition()).inflate(48.0D),
                        npc -> "krobus".equalsIgnoreCase(npc.getNpcId()))
                .stream()
                .findFirst()
                .map(npc -> npc.position().add(0.0D, npc.getBbHeight() * 0.65D, 0.0D))
                .orElse(FALLBACK_KROBUS_ORIGIN);
    }

    private static boolean isInSewer(ServerPlayer player) {
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return false;
        }
        var pos = player.blockPosition();
        return pos.getX() >= -1 && pos.getX() <= 39
                && pos.getY() >= 49 && pos.getY() <= 58
                && pos.getZ() >= 36 && pos.getZ() <= 67;
    }
}
