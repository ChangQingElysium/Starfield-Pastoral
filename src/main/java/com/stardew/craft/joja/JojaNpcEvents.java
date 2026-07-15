package com.stardew.craft.joja;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Joja Mart NPC 固定生成：Morris + 女收银员。
 *
 * <p>参考 {@link com.stardew.craft.shop.CamelMerchantEvents} 的硬性生成模式 ——
 * 每 40 tick 巡检，缺则即时补位，NoAI + Persistent + 固定 pose。
 * 绕过 {@link com.stardew.craft.npc.runtime.NpcSpawnManager} 的 UUID 追踪 / 区块预加载依赖，
 * <b>老存档兼容</b>。
 *
 * <p>NPC 的对话和超市交互仍走 {@link com.stardew.craft.npc.runtime.NpcInteractionService}
 * 里的 joja_cashier / morris 分支。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class JojaNpcEvents {

    /** 实体持久化标签 —— 清理旧残留实体时使用。 */
    private static final String MARKER_TAG = "stardewcraft_joja_npc";

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double SCAN_RADIUS = 4.0;
    /**
     * 每个 NPC 的固定生成描述：
     * id: NPC 标识（StardewNpcEntity.setNpcId）
     * x/y/z/yaw: 世界坐标与朝向
     */
    private record Spawn(String id, double x, double y, double z, float yaw) {
        BlockPos blockPos() {
            return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
    }

    /** 坐标与 default_spawns.json 一致 —— 保留那份只是为了兼容未来的 NpcSpawnManager 使用。 */
    private static final Spawn MORRIS       = new Spawn("morris",       114.5, 45.0, -22.5, 0.0f);
    private static final Spawn JOJA_CASHIER = new Spawn("joja_cashier", 104.5, 45.0, -21.5, -90.0f);

    private static final Spawn[] SPAWNS = { MORRIS, JOJA_CASHIER };

    private static int tickCounter = 0;

    private JojaNpcEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return;
        if (level.players().isEmpty()) return;

        for (Spawn s : SPAWNS) {
            loadSpawnChunk(level, s);
            ensureSingleEntity(level, s);
        }
    }

    private static void loadSpawnChunk(ServerLevel level, Spawn s) {
        level.getChunk(s.blockPos().getX() >> 4, s.blockPos().getZ() >> 4);
    }

    /** 位置偏离目标超过此距离（方块）时，不信 teleportTo，直接 discard + 新建。 */
    private static final double MAX_DRIFT_SQ = 16.0 * 16.0;

    private static void ensureSingleEntity(ServerLevel level, Spawn s) {
        // 1) 扫 Joja Mart 周围是否已经有正确位置的实体
        AABB scanBox = new AABB(s.blockPos()).inflate(SCAN_RADIUS);
        List<StardewNpcEntity> nearby = level.getEntitiesOfClass(
            StardewNpcEntity.class, scanBox,
            e -> s.id.equalsIgnoreCase(e.getNpcId()) && e.isAlive());

        StardewNpcEntity managed = nearby.isEmpty() ? null : nearby.get(0);
        // 清掉 AABB 内多余的重复
        for (int i = 1; i < nearby.size(); i++) {
            nearby.get(i).discard();
        }

        // 2) 如果 Joja Mart 里没有，检查全局是否存在一个漂到别处的同 ID 实体
        //    —— 无条件 discard 它，不尝试 teleport（实测 teleportTo 对跨大距离实体有时悄悄失败）。
        if (managed == null) {
            StardewNpcEntity tracked = com.stardew.craft.npc.runtime.NpcSpawnManager.getTrackedNpc(level, s.id);
            if (tracked != null && tracked.isAlive()) {
                double dx = tracked.getX() - s.x;
                double dy = tracked.getY() - s.y;
                double dz = tracked.getZ() - s.z;
                if (dx * dx + dy * dy + dz * dz > MAX_DRIFT_SQ) {
                    com.stardew.craft.StardewCraft.LOGGER.info(
                        "[JOJA NPC] Discarding stale {} at ({},{},{}), will respawn at target.",
                        s.id, tracked.getX(), tracked.getY(), tracked.getZ());
                    tracked.discard();
                } else {
                    managed = tracked;
                }
            }
        }

        // 3) 还是没有就新建
        if (managed == null) {
            managed = spawnNewEntity(level, s);
            if (managed == null) return;
        }

        forceHoldPose(managed, s);
    }

    private static StardewNpcEntity spawnNewEntity(ServerLevel level, Spawn s) {
        StardewNpcEntity e = ModEntities.STARDEW_NPC.get().create(level);
        if (e == null) return null;

        e.setNpcId(s.id);
        e.moveTo(s.x, s.y, s.z, s.yaw, 0.0f);
        e.setYHeadRot(s.yaw);
        e.setYBodyRot(s.yaw);

        e.setNoAi(true);
        e.setInvulnerable(true);
        e.setPersistenceRequired();
        e.setSilent(true);
        e.setCustomNameVisible(false);
        e.addTag(MARKER_TAG);

        if (!level.addFreshEntity(e)) return null;
        return e;
    }

    /** 每次巡检锁回坐标 + 朝向 + 状态位，防外力扰动。 */
    private static void forceHoldPose(StardewNpcEntity e, Spawn s) {
        if (!e.isNoAi()) e.setNoAi(true);
        if (!e.isInvulnerable()) e.setInvulnerable(true);
        if (!e.isPersistenceRequired()) e.setPersistenceRequired();
        if (!e.isSilent()) e.setSilent(true);
        if (!e.getTags().contains(MARKER_TAG)) e.addTag(MARKER_TAG);

        double dx = e.getX() - s.x;
        double dy = e.getY() - s.y;
        double dz = e.getZ() - s.z;
        if (dx * dx + dy * dy + dz * dz > 1.0e-4) {
            e.teleportTo(s.x, s.y, s.z);
        }
        if (Math.abs(e.getYRot() - s.yaw) > 0.01f
            || Math.abs(e.getYHeadRot() - s.yaw) > 0.01f) {
            e.setYRot(s.yaw);
            e.setYHeadRot(s.yaw);
            e.setYBodyRot(s.yaw);
        }
        e.setDeltaMovement(0, 0, 0);
        e.hasImpulse = false;
    }

    /** 对外检查 —— NpcSpawnManager / NpcCentralMovementService 跳过这两个 ID。 */
    public static boolean isJojaMartNpc(String npcId) {
        if (npcId == null) return false;
        String id = npcId.toLowerCase();
        return "morris".equals(id) || "joja_cashier".equals(id);
    }

    /**
     * EntityJoinLevelEvent 级别的即时 snap —— 从 NpcSpawnManager.onNpcJoin 调来。
     * 我们自己的 fresh spawn 已经带 MARKER_TAG（{@link #spawnNewEntity} 在 addFreshEntity 前加），
     * 直接放行。其他情况（chunk 加载出陈旧 NBT 实体）如果位置偏离就 discard，让巡检重建。
     */
    public static void onJojaNpcJoin(StardewNpcEntity npc, String npcId) {
        Spawn target = null;
        for (Spawn s : SPAWNS) {
            if (s.id.equalsIgnoreCase(npcId)) { target = s; break; }
        }
        if (target == null) return;

        double dx = npc.getX() - target.x;
        double dy = npc.getY() - target.y;
        double dz = npc.getZ() - target.z;
        if (dx * dx + dy * dy + dz * dz > 16.0 * 16.0) {
            com.stardew.craft.StardewCraft.LOGGER.info(
                "[JOJA NPC] Chunk-loaded {} was at ({},{},{}) — discarding stale entity.",
                npcId, npc.getX(), npc.getY(), npc.getZ());
            npc.discard();
            return;
        }
        npc.addTag(MARKER_TAG);
        forceHoldPose(npc, target);
    }

    /** 调试用 —— 立刻巡检（不等 40 tick）。 */
    public static void forceCheckNow(ServerLevel level) {
        if (level == null) return;
        for (Spawn s : SPAWNS) {
            ensureSingleEntity(level, s);
        }
    }
}
