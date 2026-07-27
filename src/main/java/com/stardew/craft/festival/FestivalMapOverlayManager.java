package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.portal.PortalTriggerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class FestivalMapOverlayManager {
    private static final int BLOCKS_PER_TICK = 1500;
    private static final int BULK_REPLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<String, FestivalMapPatch> PATCH_CACHE = new HashMap<>();
    private static final Set<String> MISSING_RECOVERY_WARNINGS =
            new HashSet<>();

    private FestivalMapOverlayManager() {
    }

    public static boolean beginApply(ServerLevel level, FestivalDefinition festival, int year, int season, int day) {
        if (level == null || festival == null || festival.mapOverlayId().isBlank()) {
            return false;
        }
        FestivalMapOverlayDefinition definition =
                FestivalMapOverlayRegistry.get(
                        level, festival.mapOverlayId()).orElse(null);
        if (definition == null) {
            StardewCraft.LOGGER.warn("[FESTIVAL_OVERLAY] Overlay {} for festival {} is not registered yet", festival.mapOverlayId(), festival.id());
            return false;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalMapOverlayState state = data.getOrCreateOverlayState(definition.overlayId());
        if (state.phase() == FestivalMapOverlayPhase.APPLIED) {
            ActiveFestivalHandlers.onMapOverlayApplied(level, state.overlayId());
            notifyPassiveOverlayApplied(level, state);
            return true;
        }
        if (state.phase() == FestivalMapOverlayPhase.APPLYING) {
            notifyPassiveOverlayApplyStarted(level, state);
            return true;
        }
        FestivalMapPatch patch = patch(level, definition, state);
        if (patch == null || patch.width() <= 0 || patch.height() <= 0 || patch.length() <= 0) {
            return false;
        }
        state.begin(festival.id(), year, season, day, FestivalMapOverlayPhase.APPLYING);
        // Persist the exact applied patch even for fixed-base overlays. This
        // is the recovery contract when a data pack is removed or changes
        // while its festival map is still active.
        state.setRuntimePatch(patch);
        notifyPassiveOverlayApplyStarted(level, state);
        forceChunks(level, patch, true);
        data.setDirty();
        return true;
    }

    public static boolean beginRestore(ServerLevel level, String overlayId) {
        if (level == null || overlayId == null || overlayId.isBlank()) {
            return false;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalMapOverlayState state = data.getOverlayState(overlayId)
                .orElse(null);
        if (state == null
                || state.phase() != FestivalMapOverlayPhase.APPLIED) {
            return false;
        }
        FestivalMapOverlayDefinition definition =
                FestivalMapOverlayRegistry.get(
                        level, overlayId).orElse(null);
        FestivalMapPatch patch = state.runtimePatch();
        if (patch == null && definition != null) {
            patch = patch(level, definition, state);
        }
        if (patch == null) {
            warnMissingRecovery(state);
            return false;
        }
        clearMissingRecoveryWarning(state);
        if (patch == null || patch.width() <= 0 || patch.height() <= 0 || patch.length() <= 0) {
            return false;
        }
        state.begin(state.festivalId(), state.year(), state.season(), state.day(), FestivalMapOverlayPhase.RESTORING);
        notifyPassiveOverlayRestoreStarted(level, state);
        forceChunks(level, patch, true);
        data.setDirty();
        return true;
    }

    public static boolean isApplied(ServerLevel level, String overlayId) {
        if (level == null || overlayId == null || overlayId.isBlank()) {
            return false;
        }
        return FestivalWorldData.get(level).getOverlayState(overlayId)
            .map(state -> state.phase() == FestivalMapOverlayPhase.APPLIED)
            .orElse(false);
    }

    public static boolean isRestored(ServerLevel level, String overlayId) {
        if (level == null || overlayId == null || overlayId.isBlank()) {
            return false;
        }
        return FestivalWorldData.get(level).getOverlayState(overlayId)
            .map(state -> state.phase() == FestivalMapOverlayPhase.RESTORED || state.phase() == FestivalMapOverlayPhase.NONE)
            .orElse(true);
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        for (FestivalMapOverlayState state : data.overlayStates()) {
            if (state.phase() != FestivalMapOverlayPhase.APPLYING && state.phase() != FestivalMapOverlayPhase.RESTORING) {
                continue;
            }
            FestivalMapOverlayDefinition definition =
                    FestivalMapOverlayRegistry.get(
                            level, state.overlayId()).orElse(null);
            FestivalMapPatch patch = state.runtimePatch();
            if (patch == null && definition != null) {
                patch = patch(level, definition, state);
            }
            if (patch == null) {
                warnMissingRecovery(state);
                continue;
            }
            clearMissingRecoveryWarning(state);
            applyBatch(level, state, patch);
            data.setDirty();
        }
    }

    private static void applyBatch(ServerLevel level, FestivalMapOverlayState state, FestivalMapPatch patch) {
        if (patch.isEmpty()) {
            state.setPhase(state.phase() == FestivalMapOverlayPhase.APPLYING ? FestivalMapOverlayPhase.APPLIED : FestivalMapOverlayPhase.RESTORED);
            if (state.phase() == FestivalMapOverlayPhase.RESTORED) {
                state.clearRuntimePatch();
            }
            forceChunks(level, patch, false);
            return;
        }
        int end = Math.min(state.cursor() + BLOCKS_PER_TICK, patch.entries().size());
        boolean applying = state.phase() == FestivalMapOverlayPhase.APPLYING;
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            for (int index = state.cursor(); index < end; index++) {
                FestivalMapPatchEntry entry = patch.entries().get(index);
                BlockPos worldPos = patch.origin().offset(entry.relativePos());
                if (level.getBlockState(worldPos).getBlock() instanceof PortalTriggerBlock) {
                    continue;
                }
                BlockState targetState = applying ? entry.festivalState() : entry.baseState();
                CompoundTag targetBlockEntity = applying ? entry.festivalBlockEntityTag() : entry.baseBlockEntityTag();
                level.removeBlockEntity(worldPos);
                level.setBlock(worldPos, targetState, BULK_REPLACE_FLAGS);
                applyBlockEntity(level, worldPos, targetBlockEntity);
            }
        });
        state.setCursor(end);
        if (end >= patch.entries().size()) {
            state.setPhase(applying ? FestivalMapOverlayPhase.APPLIED : FestivalMapOverlayPhase.RESTORED);
            if (!applying) {
                state.clearRuntimePatch();
            }
            forceChunks(level, patch, false);
            StardewCraft.LOGGER.info("[FESTIVAL_OVERLAY] {} overlay {} ({} blocks)", applying ? "Applied" : "Restored", state.overlayId(), patch.entries().size());
            if (applying) {
                ActiveFestivalHandlers.onMapOverlayApplied(level, state.overlayId());
                notifyPassiveOverlayApplied(level, state);
            } else {
                notifyPassiveOverlayRestored(level, state);
            }
        }
    }

    private static void notifyPassiveOverlayApplied(ServerLevel level, FestivalMapOverlayState state) {
        notifyPassiveOverlay(level, state, PassiveOverlayEvent.APPLIED);
    }

    private static void notifyPassiveOverlayApplyStarted(ServerLevel level, FestivalMapOverlayState state) {
        notifyPassiveOverlay(level, state, PassiveOverlayEvent.APPLY_STARTED);
    }

    private static void notifyPassiveOverlayRestoreStarted(ServerLevel level, FestivalMapOverlayState state) {
        notifyPassiveOverlay(level, state, PassiveOverlayEvent.RESTORE_STARTED);
    }

    private static void notifyPassiveOverlayRestored(ServerLevel level, FestivalMapOverlayState state) {
        notifyPassiveOverlay(level, state, PassiveOverlayEvent.RESTORED);
    }

    private static void notifyPassiveOverlay(ServerLevel level, FestivalMapOverlayState state, PassiveOverlayEvent event) {
        FestivalDefinition definition = FestivalRegistry.get(state.festivalId())
            .or(() -> FestivalRegistry.getByOverlayId(state.overlayId()))
            .filter(candidate -> candidate.type() == FestivalType.PASSIVE)
            .orElse(null);
        if (definition == null) {
            return;
        }
        FestivalSessionState session = FestivalWorldData.get(level).getOrCreateSession(definition, state.year(), state.season(), state.day());
        switch (event) {
            case APPLY_STARTED -> PassiveFestivalHandlers.onMapOverlayApplyStarted(level, definition, session);
            case APPLIED -> PassiveFestivalHandlers.onMapOverlayApplied(level, definition, session);
            case RESTORE_STARTED -> PassiveFestivalHandlers.onMapOverlayRestoreStarted(level, definition, session);
            case RESTORED -> PassiveFestivalHandlers.onMapOverlayRestored(level, definition, session);
        }
    }

    private enum PassiveOverlayEvent {
        APPLY_STARTED,
        APPLIED,
        RESTORE_STARTED,
        RESTORED
    }

    private static void applyBlockEntity(ServerLevel level, BlockPos pos, CompoundTag rawTag) {
        if (rawTag == null || rawTag.isEmpty()) {
            level.removeBlockEntity(pos);
            return;
        }
        CompoundTag normalized = rawTag.contains("Data", Tag.TAG_COMPOUND)
            ? rawTag.getCompound("Data").copy()
            : rawTag.copy();
        if (!normalized.contains("id", Tag.TAG_STRING) && normalized.contains("Id", Tag.TAG_STRING)) {
            normalized.putString("id", normalized.getString("Id"));
        }
        if (!normalized.contains("id", Tag.TAG_STRING) && rawTag.contains("Id", Tag.TAG_STRING)) {
            normalized.putString("id", rawTag.getString("Id"));
        }
        normalized.putInt("x", pos.getX());
        normalized.putInt("y", pos.getY());
        normalized.putInt("z", pos.getZ());
        if (!normalized.contains("id", Tag.TAG_STRING)) {
            return;
        }
        BlockEntity blockEntity = BlockEntity.loadStatic(pos, level.getBlockState(pos), normalized, level.registryAccess());
        if (blockEntity != null) {
            level.setBlockEntity(blockEntity);
            blockEntity.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            level.getChunkSource().blockChanged(pos);
        }
    }

    private static FestivalMapPatch patch(FestivalMapOverlayDefinition definition) {
        String cacheKey = definition.overlayId()
                + "|" + definition.origin()
                + "|" + definition.baseSchematicPath()
                + "|" + definition.festivalSchematicPath();
        return PATCH_CACHE.computeIfAbsent(
                cacheKey, ignored -> FestivalMapPatch.build(definition));
    }

    private static FestivalMapPatch patch(ServerLevel level, FestivalMapOverlayDefinition definition, FestivalMapOverlayState state) {
        FestivalMapPatch runtimePatch = state.runtimePatch();
        if (runtimePatch != null) {
            return runtimePatch;
        }
        if (!definition.usesRuntimeBase()) {
            return patch(definition);
        }
        if (state.phase() == FestivalMapOverlayPhase.APPLIED || state.phase() == FestivalMapOverlayPhase.RESTORING) {
            StardewCraft.LOGGER.warn("[FESTIVAL_OVERLAY] Runtime backup for overlay {} is missing", definition.overlayId());
            return null;
        }
        return FestivalMapPatch.buildFromRuntimeBase(level, definition);
    }

    private static void forceChunks(ServerLevel level, FestivalMapPatch patch, boolean forced) {
        if (patch.width() <= 0 || patch.length() <= 0) {
            return;
        }
        int minChunkX = patch.origin().getX() >> 4;
        int minChunkZ = patch.origin().getZ() >> 4;
        int maxChunkX = (patch.origin().getX() + patch.width() - 1) >> 4;
        int maxChunkZ = (patch.origin().getZ() + patch.length() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, forced);
            }
        }
    }

    private static void warnMissingRecovery(
            FestivalMapOverlayState state
    ) {
        String key = state.overlayId() + "|" + state.phase();
        if (MISSING_RECOVERY_WARNINGS.add(key)) {
            StardewCraft.LOGGER.warn(
                    "[FESTIVAL_OVERLAY] Paused overlay {} in phase {}: "
                            + "definition and persisted patch are unavailable",
                    state.overlayId(), state.phase());
        }
    }

    private static void clearMissingRecoveryWarning(
            FestivalMapOverlayState state
    ) {
        MISSING_RECOVERY_WARNINGS.remove(
                state.overlayId() + "|" + state.phase());
    }
}
