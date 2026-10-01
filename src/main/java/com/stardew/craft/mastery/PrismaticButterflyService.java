package com.stardew.craft.mastery;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.mastery.PrismaticButterflyEntity;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class PrismaticButterflyService {
    private static final List<SpawnArea> AREAS = List.of(
        new SpawnArea("Forest", -176, -58, 63, 65, 12, 142),
        new SpawnArea("Town", -35, 141, 65, 66, -64, 63),
        new SpawnArea("Beach", 2, 140, 58, 60, 91, 175),
        new SpawnArea("Mountain", 11, 121, 81, 93, -151, -80),
        new SpawnArea("BusStop", -105, -46, 63, 69, -71, -47)
    );

    private PrismaticButterflyService() {}

    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_6.get())) {
            removeOwnedButterflies(level, player, false);
            return;
        }
        if (StardewTimeManager.get().getCurrentTime() >= 1020) {
            return;
        }
        if (hasOwnedButterfly(level, player)) {
            return;
        }

        SpawnArea dailyArea = chooseDailyArea(player);
        if (!dailyArea.contains(player.blockPosition())) {
            return;
        }
        spawnInArea(level, player, dailyArea, false);
    }

    public static boolean spawnAt(ServerPlayer player, Vec3 pos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        removeOwnedButterflies(level, player, true);
        PrismaticButterflyEntity entity = new PrismaticButterflyEntity(level, player, pos.x, pos.y, pos.z);
        entity.setDebugSpawn(true);
        return level.addFreshEntity(entity);
    }

    public static void clearFor(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            removeOwnedButterflies(level, player, true);
        }
    }

    public static Optional<String> spawnForDebug(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Optional.of("not on server level");
        }
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return Optional.of("not in Stardew Valley dimension");
        }
        SpawnArea area = areaAt(player.blockPosition()).orElse(chooseDailyArea(player));
        if (spawnInArea(level, player, area, true)) {
            return Optional.empty();
        }
        return Optional.of("no exposed solid spawn tile found in " + area.name);
    }

    private static boolean spawnInArea(ServerLevel level, ServerPlayer player, SpawnArea area, boolean replaceExisting) {
        if (replaceExisting) {
            removeOwnedButterflies(level, player, true);
        }
        RandomSource random = RandomSource.create(spawnSeed(player, area));
        for (int i = 0; i < 32; i++) {
            int x = random.nextIntBetweenInclusive(area.minX, area.maxX);
            int z = random.nextIntBetweenInclusive(area.minZ, area.maxZ);
            Optional<Vec3> pos = findOpenSkySpawn(level, x, z, area.minY, area.maxY, random);
            if (pos.isPresent()) {
                PrismaticButterflyEntity entity = new PrismaticButterflyEntity(level, player, pos.get().x, pos.get().y, pos.get().z);
                entity.setDebugSpawn(replaceExisting);
                return level.addFreshEntity(entity);
            }
        }
        return false;
    }

    private static Optional<Vec3> findOpenSkySpawn(ServerLevel level, int x, int z, int minY, int maxY, RandomSource random) {
        for (int y = maxY + 4; y >= minY - 4; y--) {
            BlockPos solidPos = new BlockPos(x, y, z);
            if (!level.isLoaded(solidPos)) {
                continue;
            }
            BlockPos airPos = solidPos.above();
            BlockPos headPos = airPos.above();
            BlockState state = level.getBlockState(solidPos);
            if (!state.isFaceSturdy(level, solidPos, Direction.UP)) {
                continue;
            }
            if (!level.getBlockState(airPos).isAir() || !level.getBlockState(headPos).isAir()) {
                continue;
            }
            if (!level.canSeeSky(airPos)) {
                continue;
            }
            double yOffset = random.nextBoolean() ? 1.05D : 1.85D;
            return Optional.of(new Vec3(x + 0.5D, y + yOffset, z + 0.5D));
        }
        return Optional.empty();
    }

    private static SpawnArea chooseDailyArea(ServerPlayer player) {
        RandomSource random = RandomSource.create(player.getUUID().getMostSignificantBits()
            ^ player.getUUID().getLeastSignificantBits()
            ^ ((long) StardewTimeManager.get().getAbsoluteDay() * 0x9E3779B97F4A7C15L));
        return AREAS.get(random.nextInt(AREAS.size()));
    }

    private static long spawnSeed(ServerPlayer player, SpawnArea area) {
        return player.getUUID().getMostSignificantBits()
            ^ player.getUUID().getLeastSignificantBits()
            ^ ((long) StardewTimeManager.get().getAbsoluteDay() * 0xBF58476D1CE4E5B9L)
            ^ area.name.hashCode();
    }

    private static Optional<SpawnArea> areaAt(BlockPos pos) {
        return AREAS.stream().filter(area -> area.contains(pos)).findFirst();
    }

    private static boolean hasOwnedButterfly(ServerLevel level, ServerPlayer player) {
        return !level.getEntitiesOfClass(PrismaticButterflyEntity.class,
            new AABB(player.blockPosition()).inflate(256.0D),
            entity -> entity.isOwnedBy(player.getUUID()) && entity.isAlive()).isEmpty();
    }

    private static void removeOwnedButterflies(ServerLevel level, ServerPlayer player, boolean includeDebug) {
        for (PrismaticButterflyEntity entity : level.getEntitiesOfClass(PrismaticButterflyEntity.class,
            new AABB(player.blockPosition()).inflate(512.0D),
            entity -> entity.isOwnedBy(player.getUUID()) && (includeDebug || !entity.isDebugSpawn()))) {
            entity.discard();
        }
    }

    private record SpawnArea(String name, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY - 8 && pos.getY() <= maxY + 16
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}