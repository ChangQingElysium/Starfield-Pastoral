package com.stardew.craft.weather;

import com.stardew.craft.blockentity.LightningRodBlockEntity;
import com.stardew.craft.blockentity.registry.LightningRodRegistry;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.manager.FruitTreeGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * SDV parity: {@code Utility.performLightningUpdate(int time_of_day)}.
 * <p>Called once per 10 in-game minute tick on the server thread.
 * Iterates Stardew-weather dimensions; if thundering, rolls a 12.5%
 * chance and picks up to 2 lightning rods from the level's registry.
 * Empty rods are charged immediately if their chunk is loaded, or
 * queued in the registry's pending set so they charge the next time
 * their block entity ticks.</p>
 *
 * <p>Chunk-unload safe: charges are authoritative because they live
 * in persistent {@link LightningRodRegistry}; block entities only
 * drain the pending queue when they happen to be loaded, but the
 * battery itself is guaranteed to materialise once the chunk loads.</p>
 */
public final class LightningStrikeScheduler {

    /** SDV: 0.125 + team average daily luck + average luck level / 100. */
    private static final double STRIKE_CHANCE = 0.125;
    /** SDV fallback terrain-feature hit chance: 0.25 - luck. */
    private static final double TERRAIN_STRIKE_CHANCE = 0.25;

    private LightningStrikeScheduler() {}

    public static void performTenMinuteUpdate(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (!hasStardewWeather(level)) continue;
            if (!WeatherManager.isThundering(level)) continue;
            tick(level);
        }
    }

    private static boolean hasStardewWeather(ServerLevel level) {
        return level.dimension() == ModDimensions.STARDEW_VALLEY;
    }

    private static void tick(ServerLevel level) {
        // Utility.performLightningUpdate: 0.125 + team AverageDailyLuck + AverageLuckLevel / 100.
        double luck = averageDailyLuck(level) + averageLuckLevel(level) / 100.0;
        if (level.random.nextDouble() >= STRIKE_CHANCE + luck) {
            // else-branch: 10% chance of a small flash only.
            if (level.random.nextDouble() < 0.1) {
                flash(level, null);
            }
            return;
        }

        LightningRodRegistry registry = LightningRodRegistry.get(level);
        // SDV picks up to 2 rods per successful roll; the FIRST empty rod
        // gets the battery and the loop returns.
        if (registry.size() > 0) {
            List<BlockPos> snapshot = new ArrayList<>(registry.positions());
            int attempts = Math.min(2, snapshot.size());
            for (int i = 0; i < attempts; i++) {
                // Fisher-Yates partial pick using RandomSource.
                int swap = i + level.random.nextInt(snapshot.size() - i);
                BlockPos pos = snapshot.get(swap);
                snapshot.set(swap, snapshot.get(i));
                snapshot.set(i, pos);
                if (tryStrike(level, registry, pos)) {
                    flash(level, pos);
                    return;
                }
            }
        }

        BlockPos bolt = null;
        if (level.random.nextDouble() < TERRAIN_STRIKE_CHANCE - luck) {
            bolt = FruitTreeGrowthManager.get(level).strikeRandomMatureTree(level, level.random);
        }
        flash(level, bolt);
    }

    private static double averageDailyLuck(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().stream()
                .mapToDouble(com.stardew.craft.player.PlayerStardewDataAPI::getDailyLuck).average().orElse(0.0);
    }

    private static double averageLuckLevel(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().stream()
                .mapToInt(player -> com.stardew.craft.player.PlayerDataManager.getPlayerData(player).getLuckLevel())
                .average().orElse(0.0);
    }

    /**
     * Farm.LightningStrikeEvent: a flash (and, when something is hit, a bolt) shown to players on the farm.
     * Rendered with a visual-only vanilla bolt, which flashes the sky and plays thunder.
     */
    private static void flash(ServerLevel level, BlockPos boltPos) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (!com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(level, player.blockPosition())
                    || !level.canSeeSky(player.blockPosition())) {
                continue;
            }
            BlockPos at = boltPos != null && boltPos.closerThan(player.blockPosition(), 128)
                    ? boltPos
                    : player.blockPosition().offset(level.random.nextInt(81) - 40, 0, level.random.nextInt(81) - 40);
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(
                    new BlockPos(at.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()), at.getZ())));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
            return;
        }
    }

    /** @return true if this rod consumed the strike (busy rods do NOT consume it in SDV). */
    private static boolean tryStrike(ServerLevel level, LightningRodRegistry registry, BlockPos pos) {
        if (isChunkLoaded(level, pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LightningRodBlockEntity rod) {
                if (rod.isBusy()) return false;
                rod.startChargingFromStrike();
                return true;
            }
            // Block gone — drop stale registry entry.
            registry.remove(pos);
            return false;
        }
        // Chunk not loaded: we cannot inspect busy-state. Queue the charge; the
        // BE will pick it up when the chunk next ticks. If it's already busy we
        // silently ignore on drain. Count it as a consumed strike (SDV would
        // also only try two rods total per roll).
        registry.addPending(pos);
        return true;
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
