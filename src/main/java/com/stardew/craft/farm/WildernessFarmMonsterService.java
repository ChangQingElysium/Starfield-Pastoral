package com.stardew.craft.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.entity.monster.MineBatEntity;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.monster.MonsterSpawnContext;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Wilderness Farm's original post-19:00 ten-minute monster encounter. */
public final class WildernessFarmMonsterService {
    public static final String MOB_TAG = "sd_wilderness_farm_monster";
    private static final int SPAWN_ATTEMPTS = 64;
    private static final int MIN_PLAYER_DISTANCE = 26;
    private static final int MAX_PLAYER_DISTANCE = 48;

    private WildernessFarmMonsterService() {}

    public static void performTenMinuteUpdate(MinecraftServer server) {
        if (StardewTimeManager.get().getCurrentTime() < 1140) return;
        ServerLevel level = server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (level == null) return;
        RandomSource random = level.getRandom();
        for (FarmInstance farm : FarmInstanceRegistry.get(server).getAllFarms()) {
            if (!farm.isInitialized() || !farm.getFarmLayoutId().equals(
                    StardewFarmLayoutRegistry.builtinId(FarmType.WILDERNESS))) continue;
            List<ServerPlayer> farmers = level.players().stream()
                    .filter(player -> farm.isFarmer(player.getUUID()) && farm.contains(player.blockPosition()))
                    .toList();
            if (farmers.isEmpty()) continue;
            double averageLuck = farmers.stream().mapToDouble(PlayerStardewDataAPI::getDailyLuck)
                    .average().orElse(0.0D);
            if (random.nextDouble() >= 0.25D - averageLuck / 2.0D) continue;
            int combat = farmers.stream().mapToInt(
                    player -> PlayerStardewDataAPI.getSkillLevel(player, SkillType.COMBAT)).max().orElse(0);
            boolean galaxySword = farmers.stream().flatMap(player -> player.getInventory().items.stream())
                    .anyMatch(stack -> stack.is(ModItems.GALAXY_SWORD.get()));
            if (random.nextDouble() < 0.25D) spawnFlying(level, farm, farmers, combat, galaxySword, random);
            else spawnGround(level, farm, farmers, combat, random);
        }
    }

    private static void spawnFlying(ServerLevel level, FarmInstance farm, List<ServerPlayer> farmers,
                                    int combat, boolean galaxySword, RandomSource random) {
        String kind;
        int floor;
        if (combat >= 10 && galaxySword && random.nextDouble() < 0.01D) {
            kind = "iridium_bat"; floor = 9999;
        } else if (combat >= 10 && random.nextDouble() < 0.25D) {
            kind = "iridium_bat"; floor = 172;
        } else if (combat >= 10 && random.nextDouble() < 0.25D) {
            kind = "serpent"; floor = 121;
        } else if (combat >= 8 && random.nextBoolean()) {
            kind = "lava_bat"; floor = 81;
        } else if (combat >= 5 && random.nextBoolean()) {
            kind = "frost_bat"; floor = 41;
        } else {
            kind = "bat"; floor = 1;
        }
        BlockPos spawn = findOffscreenSurface(level, farm, farmers, random);
        if (spawn == null) return;
        Mob mob = spawn(level, kind, spawn.above(2), floor, farmers);
        if (mob instanceof MineBatEntity bat) bat.startPursuit();
    }

    private static void spawnGround(ServerLevel level, FarmInstance farm, List<ServerPlayer> farmers,
                                    int combat, RandomSource random) {
        BlockPos spawn = findOffscreenSurface(level, farm, farmers, random);
        if (spawn == null) return;
        String kind;
        int floor;
        if (combat >= 8 && random.nextDouble() < 0.15D) {
            kind = "shadow_brute"; floor = 81;
        } else if (random.nextDouble() < 0.66D) {
            kind = com.stardew.craft.monster.FarmGolemRules.iridium(combat, true, random) ? "iridium_golem" : "wilderness_golem"; floor = 1;
        } else {
            // Farm.spawnGroundMonsterOffScreen builds GreenSlime(virtual mine level 1/41/100/140): the colour
            // comes from the level, which this project expresses with the slime entity type.
            if (combat >= 10) { kind = "sludge"; floor = 140; }
            else if (combat >= 8) { kind = "sludge"; floor = 100; }
            else if (combat >= 4) { kind = "frost_jelly"; floor = 41; }
            else { kind = "green_slime"; floor = 1; }
        }
        spawn(level, kind, spawn, floor, farmers, combat);
    }

    private static Mob spawn(ServerLevel level, String kind, BlockPos position, int floor,
                             List<ServerPlayer> farmers) {
        return spawn(level, kind, position, floor, farmers, 0);
    }

    private static Mob spawn(ServerLevel level, String kind, BlockPos position, int floor,
                             List<ServerPlayer> farmers, int combat) {
        Mob mob = MineMonsterSpawnHandler.spawnConfiguredMonster(
                level, kind, Vec3.atBottomCenterOf(position), 0.0F,
                MonsterSpawnContext.capture(level, MonsterSpawnContext.Source.WORLD, Math.max(1, floor)),
                value -> {
                    value.setPersistenceRequired();
                    value.addTag(MOB_TAG);
                    if (!(value instanceof com.stardew.craft.entity.monster.MineRockGolemEntity)) value.addTag("sd_focused_on_farmers");
                }, value -> {
                    if (value instanceof com.stardew.craft.entity.monster.MineRockGolemEntity golem && golem.isFarmGolem())
                        golem.setFarmCombatLevel(combat);
                });
        if (mob != null) farmers.stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr))
                .ifPresent(mob::setTarget);
        return mob;
    }

    private static BlockPos findOffscreenSurface(ServerLevel level, FarmInstance farm,
                                                 List<ServerPlayer> farmers, RandomSource random) {
        int minimumDistanceSquared = MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            ServerPlayer focus = farmers.get(random.nextInt(farmers.size()));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = MIN_PLAYER_DISTANCE
                    + random.nextInt(MAX_PLAYER_DISTANCE - MIN_PLAYER_DISTANCE + 1);
            int x = net.minecraft.util.Mth.floor(focus.getX() + Math.cos(angle) * distance);
            int z = net.minecraft.util.Mth.floor(focus.getZ() + Math.sin(angle) * distance);
            if (farmers.stream().anyMatch(player -> {
                double dx = player.getX() - (x + 0.5D);
                double dz = player.getZ() - (z + 0.5D);
                return dx * dx + dz * dz < minimumDistanceSquared;
            })) {
                continue;
            }
            BlockPos surface = FarmDebrisPlacementRules.findMonsterSurface(level, farm, x, z);
            if (surface != null) return surface;
        }
        return null;
    }
}
