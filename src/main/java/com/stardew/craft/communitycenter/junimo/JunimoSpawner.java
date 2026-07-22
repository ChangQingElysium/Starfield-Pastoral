package com.stardew.craft.communitycenter.junimo;

import com.stardew.craft.communitycenter.restore.CCAreaRegistry;
import com.stardew.craft.communitycenter.state.CommunityCenterProgress;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 在 Bundle 完成后生成一只搬运 Junimo：fadeIn → 持物寻路到小屋 → fadeOut 消失。
 */
@SuppressWarnings("null")
public final class JunimoSpawner {

    private JunimoSpawner() {}

    /** 每个区域对应的 Junimo 颜色 (SDV Junimo.cs) */
    private static final int[] AREA_COLORS = {
        0x32CD32, // 0 Pantry      – LimeGreen
        0xFFA500, // 1 Crafts Room – Orange
        0x40E0D0, // 2 Fish Tank   – Turquoise
        0xD2B48C, // 3 Boiler Room – Tan
        0xFFD700, // 4 Vault       – Gold
        0xFFEBCD, // 5 Bulletin    – BlanchedAlmond
        0xA014DC, // 6 Joja        – Purple
    };

    /**
     * 根据 areaId 获取对应颜色。
     */
    public static int getColorForArea(int areaId) {
        if (areaId < 0 || areaId >= AREA_COLORS.length) return 0x32CD32;
        return AREA_COLORS[areaId];
    }

    /**
     * SDV parity: bringBundleBackToHut()
     * 在 JunimoNote 方块位置附近生成一只搬运 Junimo。
     * Junimo fadeIn → 持有彩色包裹 → 寻路到 Junimo 小屋入口 → 到达后播放 Ship 音效 + fadeOut。
     *
     * @param level        服务端世界
     * @param noteBlockPos JunimoNote 方块的世界坐标
     * @param areaId       区域 ID (决定颜色)
     * @return 生成的 Junimo 实体，或 null 如果生成失败
     */
    @Nullable
    public static JunimoEntity spawnBundleCarrier(ServerLevel level, BlockPos noteBlockPos, int areaId, int bundleColorIndex) {
        return spawnBundleCarrier(level, noteBlockPos, areaId, bundleColorIndex, null);
    }

    @Nullable
    public static JunimoEntity spawnBundleCarrier(ServerLevel level, BlockPos noteBlockPos, int areaId, int bundleColorIndex, BlockPos ccOrigin) {
        JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), level);
        junimo.setJunimoColor(getColorForArea(areaId));
        junimo.setHoldingType(JunimoEntity.HOLDING_BUNDLE);
        junimo.setBundleColor(JunimoEntity.getColorFromBundleColorIndex(bundleColorIndex));
        BlockPos spawnPos = findOpenAdjacentPos(level, noteBlockPos);
        junimo.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        level.addFreshEntity(junimo);
        StardewCraft.LOGGER.info("[CC-Junimo] Spawned carrier Junimo at {} for area {} (notePos={})",
                spawnPos, areaId, noteBlockPos);

        // SDV: playSound("junimoMeep1") — 用 TINY_WHIP 替代
        level.playSound(null, spawnPos, ModSounds.TINY_WHIP.get(), SoundSource.NEUTRAL, 1.0f, 1.2f);

        // SDV: pathfind to Point(25, 10) = Junimo Hut entrance
        BlockPos baseOrigin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        BlockPos hutPos = baseOrigin.offset(CCAreaRegistry.JUNIMO_HUT_ENTRANCE_OFFSET);
        junimo.setTarget(hutPos, () -> {
            // SDV: junimoReachedHutToReturnBundle → playSound("Ship"), holdingBundle = false
            level.playSound(null, hutPos, ModSounds.SHIP.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
            junimo.setHoldingType(JunimoEntity.HOLDING_NONE);
            junimo.startFadeOut();
        });

        return junimo;
    }

    /**
     * 寻找一个开放的相邻位置来生成 Junimo。
     */
    private static BlockPos findOpenAdjacentPos(ServerLevel level, BlockPos center) {
        // 优先检查上面一格然后再检查四个方向
        BlockPos[] candidates = {
            center.above(),
            center.north(), center.south(), center.east(), center.west(),
            center.north().above(), center.south().above(),
            center.east().above(), center.west().above()
        };
        for (BlockPos pos : candidates) {
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                return pos;
            }
        }
        // 回退到原位上方
        return center.above();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  IDLE JUNIMOS — SDV CommunityCenter.resetSharedState() parity
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SDV parity: CommunityCenter.resetSharedState() spawns an idle Junimo NPC
     * at each JunimoNote position (notePos + 2 tiles south) for every area where
     * shouldNoteAppearInArea() is true and the area is not yet complete.
     * <p>
     * Call this when a player enters the CC interior. First removes any existing
     * idle Junimos to avoid duplicates.
     *
     * @param friendly if true, Junimos follow player (canReadJunimoText); if false, they flee
     */
    public static void spawnIdleJunimos(ServerLevel level, boolean friendly) {
        spawnIdleJunimos(level, friendly, null, null);
    }

    /**
     * Per-player 版本，指定玩家 UUID 和 CC 原点。
     */
    public static void spawnIdleJunimos(ServerLevel level, boolean friendly, java.util.UUID playerUUID, BlockPos ccOrigin) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uid = playerUUID != null ? playerUUID : new java.util.UUID(0L, 0L);
        if (data.areAllAreasComplete(uid)) return;

        // Remove any existing idle Junimos in this player's CC to avoid duplicates
        removeIdleJunimos(level, ccOrigin);

        for (var entry : CCAreaRegistry.ALL_AREAS.entrySet()) {
            int areaId = entry.getKey();
            if (!CommunityCenterProgress.shouldNoteAppearInArea(areaId, uid)) continue;
            if (data.isAreaComplete(uid, areaId)) continue;

            CCAreaRegistry.AreaBounds bounds = entry.getValue();
            BlockPos notePos = ccOrigin != null ? bounds.noteWorldPos(ccOrigin) : bounds.noteWorldPos();
            // SDV: new Junimo(new Vector2(notePos.X, notePos.Y + 2) * 64f, area)
            // In 3D: 2 blocks south (+Z) from note
            BlockPos idlePos = notePos.south(2);
            BlockPos spawnPos = findOpenAdjacentPos(level, idlePos);

            JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), level);
                junimo.setJunimoColor(getColorForArea(areaId));
            junimo.setHoldingType(JunimoEntity.HOLDING_NONE);
            junimo.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            // Idle Junimos are permanent (no timeout) and friendly
            junimo.setNoTimeout(true);
            // Tag for identification and cleanup
            junimo.addTag("cc_idle_junimo");
            // SDV parity: Junimo AI based on canReadJunimoText
            // Idle Junimos should have friendly behavior (follow gently)
            // The flee behavior is handled separately when player enters without canReadJunimoText
            if (friendly) {
                junimo.goalSelector.addGoal(1, new FollowPlayerGoal(junimo, 8.0, 0.4));
            } else {
                junimo.goalSelector.addGoal(1, new FleeFromPlayerGoal(junimo, 6.0, 0.6));
            }
            level.addFreshEntity(junimo);
        }
    }

    /**
     * Remove all idle Junimos from a specific CC interior.
     */
    public static void removeIdleJunimos(ServerLevel level, BlockPos ccOrigin) {
        BlockPos origin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        AABB ccBox = new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 23, origin.getY() + 8, origin.getZ() + 69
        );
        List<JunimoEntity> existing = level.getEntitiesOfClass(JunimoEntity.class, ccBox,
                e -> e.getTags().contains("cc_idle_junimo"));
        for (JunimoEntity j : existing) {
            j.discard();
        }
    }

    /**
     * Remove all idle Junimos from the default CC interior.
     */
    public static void removeIdleJunimos(ServerLevel level) {
        removeIdleJunimos(level, null);
    }
}
