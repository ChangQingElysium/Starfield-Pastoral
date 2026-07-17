package com.stardew.craft.communitycenter.junimo;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.communitycenter.block.StarPlaqueBlockEntity;
import com.stardew.craft.communitycenter.network.StarPlacedPayload;
import com.stardew.craft.communitycenter.restore.CCAreaRegistry;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * SDV parity: T3.1 — Star placement animation.
 * <p>
 * After an area completes, a Junimo spawns at the Junimo hut, carries a star,
 * pathfinds to the Star Plaque, and places it (incrementing the star count).
 * The Junimo then fades out.
 */
@SuppressWarnings("null")
public final class StarPlacementAnimator {

    private StarPlacementAnimator() {}

    /** Junimo 寻路目标: 星盘下方地面位置 (18820, 72, 18851) */
    private static final BlockPos STAR_PLAQUE_OFFSET = new BlockPos(4, 3, 35);

    /**
     * Spawn a star-carrying Junimo at the hut that walks to the star plaque.
     * Called from AreaRestoreCutscene after the area restore is complete.
     *
     * @param level  server world
     * @param areaId the completed area (determines Junimo color)
     */
    /** Per-player 版本，指定 CC 原点和该社区中心的所有者。 */
    public static void spawnStarCarrier(ServerLevel level, int areaId, BlockPos ccOrigin,
                                        java.util.UUID ownerUUID) {
        BlockPos baseOrigin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        BlockPos hutPos = baseOrigin.offset(CCAreaRegistry.JUNIMO_HUT_ENTRANCE_OFFSET);
        BlockPos plaquePos = baseOrigin.offset(STAR_PLAQUE_OFFSET);

        JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), level);
        junimo.setJunimoColor(JunimoSpawner.getColorForArea(areaId));
        junimo.setHoldingType(JunimoEntity.HOLDING_STAR); // carrying a star
        junimo.setPos(hutPos.getX() + 0.5, hutPos.getY(), hutPos.getZ() + 0.5);
        junimo.setGlowingTag(true);
        level.addFreshEntity(junimo);

        // Play spawn sound
        level.playSound(null, hutPos, ModSounds.SMALL_SELECT.get(), SoundSource.NEUTRAL, 1.0f, 1.2f);

        // 抑制 serverTick 自动同步, 给 Junimo 最多 30 秒走到星盘
        StarPlaqueBlockEntity.suppressSyncUntil(level.getGameTime() + 600);

        // Set target: walk to star plaque
        junimo.setTarget(plaquePos, () -> {
            // Arrived at plaque — only this Community Center's owner may see the star.
            ServerPlayer owner = ownerUUID == null
                    ? null
                    : level.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                StarPlacedPayload.sendToPlayer(owner, areaId);
            }

            // 同时更新 BlockEntity 用于持久化
            placeStar(level, plaquePos, areaId);

            junimo.setHoldingType(JunimoEntity.HOLDING_NONE);
            junimo.startFadeOut();

            // Play star placement sound
            level.playSound(null, plaquePos, ModSounds.NEW_ARTIFACT.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        });
    }

    /**
     * Find the Star Plaque BlockEntity near the given position and increment its star count.
     */
    private static void placeStar(ServerLevel level, BlockPos nearPos, int areaId) {
        // Search in a small area around the expected position
        BlockPos[] candidates = {
            nearPos, nearPos.above(), nearPos.below(),
            nearPos.north(), nearPos.south(), nearPos.east(), nearPos.west()
        };

        for (BlockPos pos : candidates) {
            if (level.getBlockState(pos).is(ModBlocks.STAR_PLAQUE.get())) {
                if (level.getBlockEntity(pos) instanceof StarPlaqueBlockEntity plaque) {
                    plaque.setNumberOfStars(plaque.getNumberOfStars() + 1);
                    StarPlaqueBlockEntity.clearSyncSuppression();

                    return;
                }
            }
        }

        StardewCraft.LOGGER.warn("[CC-STAR] No Star Plaque found near {} for area {}", nearPos, areaId);
    }
}
