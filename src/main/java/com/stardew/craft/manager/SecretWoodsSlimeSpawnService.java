package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;

public final class SecretWoodsSlimeSpawnService {
    private static final String DATA_ID = "stardewcraft_secret_woods_slimes";
    private static final String SECRET_WOODS_SLIME_TAG = "sd_secret_woods_slime";
    private static final int SPAWN_ATTEMPTS = 50;
    private static final double SPAWN_CHANCE = 0.25D;
    private static final int SPAWN_MIN_X = -261;
    private static final int SPAWN_MAX_X = -199;
    private static final int SPAWN_Y = 68;
    private static final int SPAWN_MIN_Z = 2;
    private static final int SPAWN_MAX_Z = 37;
    private static final AABB CLEAR_BOUNDS = new AABB(
            SPAWN_MIN_X, CoalForestArea.MIN_Y, SPAWN_MIN_Z,
            SPAWN_MAX_X + 1, CoalForestArea.MAX_Y + 1, SPAWN_MAX_Z + 1);

    private SecretWoodsSlimeSpawnService() {
    }

    public static void ensureTodaySpawned(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        int absoluteDay = StardewTimeManager.get().getAbsoluteDay();
        SecretWoodsSlimeData data = level.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(SecretWoodsSlimeData.factory()), com.stardew.craft.port.PortSavedData.constructor(SecretWoodsSlimeData.factory()), DATA_ID);
        if (data.lastSpawnedDay() == absoluteDay) {
            return;
        }

        ensureRegionChunksLoaded(level);
        int cleared = clearExisting(level);
        int spawned = spawnDailySlimes(level, level.getRandom());
        data.setLastSpawnedDay(absoluteDay);
        StardewCraft.LOGGER.info("[SECRET_WOODS] Slime refresh for day {}: cleared={}, spawned={}",
                absoluteDay, cleared, spawned);
    }

    private static int spawnDailySlimes(ServerLevel level, RandomSource random) {
        int spawned = 0;
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            BlockPos pos = randomSpawnPos(random);
            if (random.nextDouble() >= SPAWN_CHANCE) {
                continue;
            }
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }

            // Woods.resetSharedState: spring/summer level 0, fall 0 or 40, winter 40.
            int mineFloor = switch (com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason()) {
                case 2 -> random.nextBoolean() ? 0 : 40;
                case 3 -> 40;
                default -> 0;
            };
            Vec3 spawnPos = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            Mob mob = MineMonsterSpawnHandler.spawnConfiguredMonster(
                    level,
                    "green_slime",
                    spawnPos,
                    random.nextFloat() * 360.0F,
                    mineFloor);
            if (mob == null) {
                continue;
            }

            mob.addTag(SECRET_WOODS_SLIME_TAG);
            mob.setPersistenceRequired();
            spawned++;
        }
        return spawned;
    }

    private static BlockPos randomSpawnPos(RandomSource random) {
        int x = SPAWN_MIN_X + random.nextInt(SPAWN_MAX_X - SPAWN_MIN_X + 1);
        int z = SPAWN_MIN_Z + random.nextInt(SPAWN_MAX_Z - SPAWN_MIN_Z + 1);
        return new BlockPos(x, SPAWN_Y, z);
    }

    private static int clearExisting(ServerLevel level) {
        var slimes = level.getEntitiesOfClass(Mob.class, CLEAR_BOUNDS,
                mob -> mob.getTags().contains(SECRET_WOODS_SLIME_TAG));
        for (Mob slime : slimes) {
            slime.discard();
        }
        return slimes.size();
    }

    private static void ensureRegionChunksLoaded(ServerLevel level) {
        int minChunkX = SPAWN_MIN_X >> 4;
        int maxChunkX = SPAWN_MAX_X >> 4;
        int minChunkZ = SPAWN_MIN_Z >> 4;
        int maxChunkZ = SPAWN_MAX_Z >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static final class SecretWoodsSlimeData extends SavedData {
        private int lastSpawnedDay;

        private SecretWoodsSlimeData(int lastSpawnedDay) {
            this.lastSpawnedDay = lastSpawnedDay;
        }

        static com.stardew.craft.port.PortSavedData.Factory<SecretWoodsSlimeData> factory() {
            return new com.stardew.craft.port.PortSavedData.Factory<>(
                    () -> new SecretWoodsSlimeData(0),
                    SecretWoodsSlimeData::load);
        }

        static SecretWoodsSlimeData load(CompoundTag tag, HolderLookup.Provider registries) {
            return new SecretWoodsSlimeData(tag.getInt("LastSpawnedDay"));
        }

        int lastSpawnedDay() {
            return lastSpawnedDay;
        }

        void setLastSpawnedDay(int value) {
            if (lastSpawnedDay != value) {
                lastSpawnedDay = value;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
            tag.putInt("LastSpawnedDay", lastSpawnedDay);
            return tag;
        }
    }
}