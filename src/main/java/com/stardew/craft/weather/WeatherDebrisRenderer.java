package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 碎片天气渲染器（客户端）
 * 渲染春天的花瓣和秋天的落叶
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class WeatherDebrisRenderer {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        ClientLevel level = player.clientLevel;
        if (level == null) {
            return;
        }
        
        // 如果游戏暂停（打开菜单），不生成粒子
        if (mc.isPaused()) {
            return;
        }

        // 检查天气类型（使用客户端缓存）
        boolean isSpringWind = "WindSpring".equals(com.stardew.craft.weather.ClientWeatherCache.getCurrentWeather(level.dimension()));
        boolean isFallWind = "WindFall".equals(com.stardew.craft.weather.ClientWeatherCache.getCurrentWeather(level.dimension()));
        boolean isSnowing = "Snow".equals(com.stardew.craft.weather.ClientWeatherCache.getCurrentWeather(level.dimension()));
        
        if (!isSpringWind && !isFallWind && !isSnowing) {
            return;
        }

        // 沙漠区域不展现星露谷外部天气粒子（春风花瓣 / 秋风落叶 / 雪花）
        if (com.stardew.craft.desert.DesertConstants.isInDesertRegion(player.blockPosition())) {
            return;
        }

        tickCounter++;
        // 降低生成频率：风天每20 ticks生成一次（约1秒），雪天每10 ticks
        int tickInterval = isSnowing ? 10 : 20;
        if (tickCounter < tickInterval) {
            return;
        }
        tickCounter = 0;

        // 检查玩家是否在室外
        if (!isPlayerOutdoors(player)) {
            return;
        }

        // 根据天气类型生成不同的粒子
        if (isSpringWind) {
            spawnCherryLeaves(player, level);
        } else if (isFallWind) {
            spawnFallLeaves(player, level);
        } else if (isSnowing) {
            spawnSnowfall(player, level);
        }
    }

    /**
     * 生成春天的樱花花瓣
     */
    @SuppressWarnings("null")
    private static void spawnCherryLeaves(LocalPlayer player, ClientLevel level) {
        RandomSource random = level.random;
        BlockPos playerPos = player.blockPosition();

        // 在玩家当前区块及周围区块生成花瓣，营造满村效果
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        
        // 遍历周围3x3区块
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                // 每个区块生成1-2个花瓣（原来太多了）
                int count = 1 + random.nextInt(2);
                for (int i = 0; i < count; i++) {
                    // 在区块内随机位置
                    double x = ((chunkX + cx) * 16) + random.nextDouble() * 16;
                    double y = playerPos.getY() + random.nextDouble() * 20 + 10; // 从高处飘落
                    double z = ((chunkZ + cz) * 16) + random.nextDouble() * 16;

                    // 距离检查（不渲染太远的粒子）
                    double distSq = player.distanceToSqr(x, y, z);
                    if (distSq > 1600) { // 40格范围内
                        continue;
                    }

                    // 飘落速度 - 轻盈飘动
                    double vx = (random.nextDouble() - 0.5) * 0.1;
                    double vy = -0.03 - random.nextDouble() * 0.02;
                    double vz = (random.nextDouble() - 0.5) * 0.1;

                    level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, vx, vy, vz);
                }
            }
        }
    }

    /**
     * 生成秋天的落叶
     */
    @SuppressWarnings("null")
    private static void spawnFallLeaves(LocalPlayer player, ClientLevel level) {
        RandomSource random = level.random;
        BlockPos playerPos = player.blockPosition();

        // 在玩家当前区块及周围区块生成落叶，营造满村效果
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        
        // 遍历周围3x3区块
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                // 每个区块生成1-2个落叶（原来太多了）
                int count = 1 + random.nextInt(2);
                for (int i = 0; i < count; i++) {
                    // 在区块内随机位置
                    double x = ((chunkX + cx) * 16) + random.nextDouble() * 16;
                    double y = playerPos.getY() + random.nextDouble() * 20 + 10;
                    double z = ((chunkZ + cz) * 16) + random.nextDouble() * 16;

                    // 距离检查
                    double distSq = player.distanceToSqr(x, y, z);
                    if (distSq > 1600) {
                        continue;
                    }

                    // 飘落速度 - 比春天略快
                    double vx = (random.nextDouble() - 0.5) * 0.12;
                    double vy = -0.05 - random.nextDouble() * 0.03;
                    double vz = (random.nextDouble() - 0.5) * 0.12;

                    // 随机使用橙色或黄色秋叶（和樱花一样的飘落效果）
                    if (random.nextBoolean()) {
                        level.addParticle(ModParticles.AUTUMN_LEAF_ORANGE.get(), x, y, z, vx, vy, vz);
                    } else {
                        level.addParticle(ModParticles.AUTUMN_LEAF_YELLOW.get(), x, y, z, vx, vy, vz);
                    }
                }
            }
        }
    }

    /**
     * 生成雪花粒子（全局下雪效果，不依赖生物群系）
     */
    @SuppressWarnings("null")
    private static void spawnSnowfall(LocalPlayer player, ClientLevel level) {
        RandomSource random = level.random;
        BlockPos playerPos = player.blockPosition();

        // 在玩家当前区块及周围区块生成雪花
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        
        // 遍历周围3x3区块（和春秋一致）
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                // 每个区块生成1-2个雪花（和春秋一致）
                int count = 1 + random.nextInt(2);
                for (int i = 0; i < count; i++) {
                    // 在区块内随机位置
                    double x = ((chunkX + cx) * 16) + random.nextDouble() * 16;
                    double y = playerPos.getY() + random.nextDouble() * 20 + 10;
                    double z = ((chunkZ + cz) * 16) + random.nextDouble() * 16;

                    // 距离检查
                    double distSq = player.distanceToSqr(x, y, z);
                    if (distSq > 1600) { // 40格范围（和春秋一致）
                        continue;
                    }

                    // 雪花飘落速度（慢速垂直下落，轻微横向飘动）
                    double vx = (random.nextDouble() - 0.5) * 0.03;
                    double vy = -0.04 - random.nextDouble() * 0.02;
                    double vz = (random.nextDouble() - 0.5) * 0.03;

                    level.addParticle(ModParticles.CUSTOM_SNOWFLAKE.get(), x, y, z, vx, vy, vz);
                }
            }
        }
    }

    /**
     * 检查玩家是否在室外（头顶能看到天空）
     */
    @SuppressWarnings("null")
    private static boolean isPlayerOutdoors(LocalPlayer player) {
        BlockPos playerPos = player.blockPosition();
        return player.level().canSeeSky(playerPos.above());
    }
}
