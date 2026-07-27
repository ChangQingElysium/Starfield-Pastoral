package com.stardew.craft.animal.service;

import com.stardew.craft.block.nature.PastureGrassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded, per-level grass targeting for managed farm animals.
 *
 * <p>SDV reserves grass targets and limits animal pathfinding work per tick. This service applies
 * the same two safeguards to the 3D search: only a bounded number of previously unseen chunk
 * sections may be indexed per server tick, and two animals can't reserve the same grass block.
 */
public final class AnimalGrassTargetService {
    private static final int MAX_NEW_SECTION_INDEXES_PER_TICK = 2;
    private static final int VERTICAL_SEARCH_RADIUS = 2;
    private static final int RESERVATION_TTL_TICKS = 200;
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private AnimalGrassTargetService() {
    }

    @Nullable
    public static BlockPos reserveNearest(
            ServerLevel level,
            long animalId,
            BlockPos origin,
            int radius
    ) {
        LevelState state = state(level);
        long gameTime = level.getGameTime();
        state.beginTick(gameTime);
        state.removeExpired(level, gameTime);

        Long existingTarget = state.targetByAnimal.get(animalId);
        if (existingTarget != null) {
            BlockPos existing = BlockPos.of(existingTarget);
            if (isGrass(level, existing)
                    && insideSearchBounds(
                    origin, existing, radius)) {
                return existing;
            }
            state.release(animalId);
        }

        indexRequiredSections(level, state, origin, radius);
        BlockPos best = state.index.findNearest(
                origin,
                radius,
                VERTICAL_SEARCH_RADIUS,
                packed -> {
                    Reservation reservation =
                            state.reservationsByTarget.get(packed);
                    return (reservation == null
                            || reservation.animalId == animalId)
                            && isGrass(level, BlockPos.of(packed));
                }
        );

        if (best != null) {
            state.reserve(animalId, best, gameTime + RESERVATION_TTL_TICKS);
        }
        return best;
    }

    public static void release(ServerLevel level, long animalId) {
        state(level).release(animalId);
    }

    public static void onGrassStateChanged(
            ServerLevel level,
            BlockPos pos,
            boolean present
    ) {
        LevelState state = state(level);
        state.index.update(pos, present);
        if (!present) {
            state.releaseTarget(pos.asLong());
        }
    }

    public static void invalidateChunk(
            ServerLevel level,
            int chunkX,
            int chunkZ
    ) {
        LevelState state = state(level);
        state.index.invalidateChunk(chunkX, chunkZ);
        Iterator<Map.Entry<Long, Reservation>> iterator =
                state.reservationsByTarget.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Reservation> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (Math.floorDiv(pos.getX(), 16) != chunkX
                    || Math.floorDiv(pos.getZ(), 16) != chunkZ) {
                continue;
            }
            state.targetByAnimal.remove(
                    entry.getValue().animalId, entry.getKey());
            iterator.remove();
        }
    }

    private static synchronized LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new LevelState());
    }

    private static void indexRequiredSections(
            ServerLevel level,
            LevelState state,
            BlockPos origin,
            int radius
    ) {
        int minSectionX = Math.floorDiv(origin.getX() - radius, 16);
        int maxSectionX = Math.floorDiv(origin.getX() + radius, 16);
        int minSectionY = Math.floorDiv(
                origin.getY() - VERTICAL_SEARCH_RADIUS, 16);
        int maxSectionY = Math.floorDiv(
                origin.getY() + VERTICAL_SEARCH_RADIUS, 16);
        int minSectionZ = Math.floorDiv(origin.getZ() - radius, 16);
        int maxSectionZ = Math.floorDiv(origin.getZ() + radius, 16);

        for (int sectionX = minSectionX;
             sectionX <= maxSectionX;
             sectionX++) {
            for (int sectionY = minSectionY;
                 sectionY <= maxSectionY;
                 sectionY++) {
                for (int sectionZ = minSectionZ;
                     sectionZ <= maxSectionZ;
                     sectionZ++) {
                    if (state.index.isIndexed(
                            sectionX, sectionY, sectionZ)
                            || state.newSectionIndexesThisTick
                            >= MAX_NEW_SECTION_INDEXES_PER_TICK
                            || !level.hasChunk(sectionX, sectionZ)) {
                        continue;
                    }
                    indexSection(
                            level, state,
                            sectionX, sectionY, sectionZ);
                    state.newSectionIndexesThisTick++;
                }
            }
        }
    }

    private static void indexSection(
            ServerLevel level,
            LevelState state,
            int sectionX,
            int sectionY,
            int sectionZ
    ) {
        LevelChunk chunk = level.getChunk(sectionX, sectionZ);
        int sectionIndex =
                level.getSectionIndexFromSectionY(sectionY);
        List<BlockPos> grass = new ArrayList<>();
        if (sectionIndex >= 0
                && sectionIndex < chunk.getSections().length) {
            LevelChunkSection section =
                    chunk.getSection(sectionIndex);
            if (!section.hasOnlyAir()) {
                int baseX = sectionX * 16;
                int baseY = sectionY * 16;
                int baseZ = sectionZ * 16;
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0;
                         localZ < 16;
                         localZ++) {
                        for (int localX = 0;
                             localX < 16;
                             localX++) {
                            if (section.getBlockState(
                                    localX, localY, localZ)
                                    .getBlock()
                                    instanceof PastureGrassBlock) {
                                grass.add(new BlockPos(
                                        baseX + localX,
                                        baseY + localY,
                                        baseZ + localZ));
                            }
                        }
                    }
                }
            }
        }
        state.index.replaceSection(
                sectionX, sectionY, sectionZ, grass);
    }

    private static boolean isGrass(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PastureGrassBlock;
    }

    private static boolean insideSearchBounds(
            BlockPos origin,
            BlockPos candidate,
            int radius
    ) {
        return Math.abs(candidate.getX() - origin.getX()) <= radius
                && Math.abs(candidate.getY() - origin.getY())
                <= VERTICAL_SEARCH_RADIUS
                && Math.abs(candidate.getZ() - origin.getZ())
                <= radius;
    }

    private static final class LevelState {
        private final AnimalGrassSpatialIndex index =
                new AnimalGrassSpatialIndex();
        private final Map<Long, Reservation> reservationsByTarget = new HashMap<>();
        private final Map<Long, Long> targetByAnimal = new HashMap<>();
        private long currentTick = Long.MIN_VALUE;
        private int newSectionIndexesThisTick;

        private void beginTick(long gameTime) {
            if (currentTick != gameTime) {
                currentTick = gameTime;
                newSectionIndexesThisTick = 0;
            }
        }

        private void reserve(long animalId, BlockPos target, long expiresAt) {
            release(animalId);
            long targetKey = target.asLong();
            reservationsByTarget.put(targetKey, new Reservation(animalId, expiresAt));
            targetByAnimal.put(animalId, targetKey);
        }

        private void release(long animalId) {
            Long target = targetByAnimal.remove(animalId);
            if (target == null) {
                return;
            }
            Reservation reservation = reservationsByTarget.get(target);
            if (reservation != null && reservation.animalId == animalId) {
                reservationsByTarget.remove(target);
            }
        }

        private void releaseTarget(long target) {
            Reservation reservation =
                    reservationsByTarget.remove(target);
            if (reservation != null) {
                targetByAnimal.remove(
                        reservation.animalId, target);
            }
        }

        private void removeExpired(ServerLevel level, long gameTime) {
            Iterator<Map.Entry<Long, Reservation>> iterator =
                    reservationsByTarget.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Reservation> entry = iterator.next();
                Reservation reservation = entry.getValue();
                if (reservation.expiresAt > gameTime
                        && isGrass(level, BlockPos.of(entry.getKey()))) {
                    continue;
                }
                targetByAnimal.remove(reservation.animalId, entry.getKey());
                iterator.remove();
            }
        }
    }

    private record Reservation(long animalId, long expiresAt) {
    }
}
