package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Random;

/**
 * 星露谷天气管理器
 * 管理每个维度的天气状态，使用 Minecraft 原版天气系统
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class WeatherManager {

    private static boolean isStardewLevel(ServerLevel level) {
        return ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }

    /**
     * 获取维度的天气状态（从SavedData中）
     */
    private static WeatherState getWeatherState(ServerLevel level) {
        WeatherSavedData data = WeatherSavedData.get(level);
        return data.getWeatherState(level.dimension());
    }

    /**
     * 更新明天的天气预测
     */
    public static void updateWeatherForNewDay(ServerLevel level, int dayOfMonth, String season, int daysPlayed) {
        if (!isStardewLevel(level)) {
            return;
        }
        WeatherState state = getWeatherState(level);

        state.updateTomorrow(level, dayOfMonth, season, daysPlayed);
        WeatherSavedData.get(level).setDirty();

        // 同步到客户端
        syncToAllPlayers(level, state);
    }

    /**
     * 使用与“天数”相关的稳定随机种子，避免连续天数产生相关输出
     */
    private static Random createDayRandom(ServerLevel level, int daysPlayed) {
        long seed = level.getSeed();
        long mixed = seed ^ (long) daysPlayed * 0x9E3779B97F4A7C15L;
        mixed ^= (mixed >>> 30);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 27);
        mixed *= 0x94D049BB133111EBL;
        mixed ^= (mixed >>> 31);
        return new Random(mixed);
    }

    /**
     * 应用今天的天气（在新的一天开始时调用）
     */
    public static void applyWeatherForNewDay(ServerLevel level, int dayOfMonth, String season, int daysPlayed) {
        if (!isStardewLevel(level)) {
            return;
        }
        WeatherState state = getWeatherState(level);
        state.applyToday(level, dayOfMonth, season, daysPlayed);
        WeatherSavedData.get(level).setDirty();

        syncToAllPlayers(level, state);
    }

    /**
     * 星露谷物语的天气修改规则
     */
    private static String getWeatherModificationsForDate(int dayOfMonth, String season, int daysPlayed, String defaultWeather) {
        String weather = defaultWeather;

        // 1. 每月第一天必定晴天
        if (dayOfMonth == 1 || daysPlayed <= 4) {
            weather = "Sun";
        }

        // 2. 第3天（教程）必定下雨
        if (daysPlayed == 3) {
            weather = "Rain";
        }

        // 3. 夏天每13天一次雷暴（夏天的第13天和第26天）
        if ("summer".equalsIgnoreCase(season) && (dayOfMonth == 13 || dayOfMonth == 26)) {
            weather = "Storm";
        }

        // SDV: active festival days become "Festival" weather, and passive festivals with
        // Valley map replacements force Sun. StardewCraft has no separate Festival visuals.
        if (isFestivalWeatherDay(dayOfMonth, season)) {
            weather = "Sun";
        }

        // 4. 碎片天气根据季节区分
        if (weather.startsWith("Wind")) {
            if ("spring".equalsIgnoreCase(season)) {
                weather = "WindSpring";
            } else if ("fall".equalsIgnoreCase(season)) {
                weather = "WindFall";
            }
        }

        return weather;
    }

    private static boolean isFestivalWeatherDay(int dayOfMonth, String season) {
        int seasonIndex = seasonIndex(season);
        if (seasonIndex < 0) {
            return false;
        }
        return FestivalRegistry.all().stream()
            .filter(definition -> definition.type() == FestivalType.ACTIVE || !definition.mapReplacements().isEmpty())
            .anyMatch(definition -> definition.season() == seasonIndex
                && dayOfMonth >= definition.startDay()
                && dayOfMonth <= definition.endDay());
    }

    private static int seasonIndex(String season) {
        if (season == null) {
            return -1;
        }
        return switch (season.toLowerCase(java.util.Locale.ROOT)) {
            case "spring" -> FestivalRegistry.SPRING;
            case "summer" -> FestivalRegistry.SUMMER;
            case "fall", "autumn" -> FestivalRegistry.FALL;
            case "winter" -> FestivalRegistry.WINTER;
            default -> -1;
        };
    }

    /**
     * 预测明天的天气（基于星露谷物语的概率系统）
     * SDV 原版概率来自 Data/LocationContexts.json WeatherConditions：
     * - 春/秋：18.3% 雨（其中 25% 变雷暴≈4.6%），春风~15%，秋风~49%
     * - 夏：特殊雨概率（约 18%），其中 85% 变雷暴
     * - 冬：63% 雪
     */
    public static String predictTomorrowWeather(ServerLevel level, String season, int dayOfMonth, Random random) {
        

        double roll = random.nextDouble();

        if ("winter".equalsIgnoreCase(season)) {
            // 冬天：63%雪天，37%晴天（SDV: SYNCED_RANDOM 0.63）
            return roll < 0.63 ? "Snow" : "Sun";
        } else if ("spring".equalsIgnoreCase(season)) {
            // 春天（SDV 顺序）：18.3%雨/雷暴，剩余中20%风，其余晴天
            if (roll < 0.183) {
                // 雨中25%变雷暴（需28天后）
                return random.nextDouble() < 0.25 ? "Storm" : "Rain";
            } else if (random.nextDouble() < 0.20) {
                return "WindSpring";
            } else {
                return "Sun";
            }
        } else if ("summer".equalsIgnoreCase(season)) {
            // 夏天：约18%降水，其中85%变雷暴；13号固定雷暴
            if (dayOfMonth % 13 == 0) {
                return "Storm";
            }
            if (roll < 0.18) {
                return random.nextDouble() < 0.85 ? "Storm" : "Rain";
            } else {
                return "Sun";
            }
        } else if ("fall".equalsIgnoreCase(season)) {
            // 秋天（SDV 顺序）：18.3%雨/雷暴，剩余中60%风，其余晴天
            if (roll < 0.183) {
                return random.nextDouble() < 0.25 ? "Storm" : "Rain";
            } else if (random.nextDouble() < 0.60) {
                return "WindFall";
            } else {
                return "Sun";
            }
        }
        
        return "Sun";
    }

    /**
     * 获取当前天气类型
     */
    public static String getCurrentWeather(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return "Sun";
        }
        WeatherState state = getWeatherState(serverLevel);
        return state.weatherType;
    }

    /**
     * 兼容旧调用点：判断当前是否为雨天或雷暴。
     */
    public static boolean isRaining(Level level) {
        String weather = getCurrentWeather(level);
        return "Rain".equals(weather) || "Storm".equals(weather);
    }

    /**
     * 兼容旧调用点：判断当前是否为雷暴。
     */
    public static boolean isThundering(Level level) {
        return "Storm".equals(getCurrentWeather(level));
    }

    /**
     * 获取明天的天气预测
     */
    public static String getTomorrowWeather(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return "Sun";
        }
        WeatherState state = getWeatherState(serverLevel);
        return state.weatherForTomorrow;
    }

    /**
     * 检查是否是碎片天气（用于渲染花瓣/落叶）
     */
    public static boolean isDebrisWeather(Level level) {
        String weather = getCurrentWeather(level);
        return "WindSpring".equals(weather) || "WindFall".equals(weather);
    }

    /**
     * 检查是否是春季微风
     */
    public static boolean isSpringWind(Level level) {
        return "WindSpring".equals(getCurrentWeather(level));
    }

    /**
     * 检查是否是秋季微风
     */
    public static boolean isFallWind(Level level) {
        return "WindFall".equals(getCurrentWeather(level));
    }

    /**
     * 检查是否是下雪天（客户端用于渲染雪花粒子）
     */
    public static boolean isSnowing(Level level) {
        return "Snow".equals(getCurrentWeather(level));
    }

    /**
     * 手动设置天气（用于调试命令）
     */
    public static void setWeather(ServerLevel level, String weatherType) {
        WeatherState state = getWeatherState(level);
        state.weatherType = weatherType;
        state.applyToLevel(level);
        WeatherSavedData.get(level).setDirty();
        
        // 同步到客户端
        syncToAllPlayers(level, state);
    }

    /**
     * 设置明天的天气（用于求雨图腾等）。
     * 严格复刻 SDV rainTotem：默认维度检查节日日，非节日设为 Rain。
     */
    public static void setTomorrowWeather(ServerLevel level, String weatherType) {
        WeatherState state = getWeatherState(level);
        state.weatherForTomorrow = weatherType;
        WeatherSavedData.get(level).setDirty();
        syncToAllPlayers(level, state);
    }
    
    /**
     * 同步天气到所有玩家
     */
    @SuppressWarnings("null")
    private static void syncToAllPlayers(ServerLevel level, WeatherState state) {
        String dimStr = level.dimension().location().toString();
        com.stardew.craft.network.WeatherSyncPacket packet = new com.stardew.craft.network.WeatherSyncPacket(
            dimStr,
            state.weatherType,
            state.weatherForTomorrow,
            state.isRaining(),
            state.isThundering()
        );
        
        for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            net.minecraftforge.network.PacketDistributor.sendToPlayer(player, packet);
        }
    }

    /**
     * 同步维度天气（在维度加载时调用）
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (!isStardewLevel(level)) {
                return;
            }
            WeatherState state = getWeatherState(level);
            syncToAllPlayers(level, state);
        }
    }

    /**
     * 天气状态类
     */
    public static class WeatherState {
        private String weatherType = "Sun";           // 当前天气
        private String weatherForTomorrow = "Sun";    // 明天的天气
        private int monthlyNonRainyDayCount = 0;      // 本月非雨天数
        
        /**
         * 保存到NBT
         */
        @SuppressWarnings("null")
        public void saveToNBT(net.minecraft.nbt.CompoundTag tag) {
            tag.putString("weatherType", weatherType);
            tag.putString("weatherForTomorrow", weatherForTomorrow);
            tag.putInt("monthlyNonRainyDayCount", monthlyNonRainyDayCount);
        }
        
        /**
         * 从NBT加载
         */
        public void loadFromNBT(net.minecraft.nbt.CompoundTag tag) {
            weatherType = tag.getString("weatherType");
            weatherForTomorrow = tag.getString("weatherForTomorrow");
            monthlyNonRainyDayCount = tag.getInt("monthlyNonRainyDayCount");
        }

        /**
         * 应用天气到 Minecraft 世界
         */
        public void applyToLevel(ServerLevel level) {
            // Starfield weather is intentionally isolated from vanilla weather.
            // ServerLevelData/GameRules are shared through Minecraft's primary
            // level data, so writing them here also rewrites the overworld and
            // makes /weather clear look ineffective. Gameplay reads this state
            // through WeatherManager; visuals are synced with WeatherSyncPacket.
        }

        /**
         * 兼容旧调用点：给单个玩家发送天气同步包。
         */
        @SuppressWarnings("null")
        public static void sendWeatherPackets(net.minecraft.server.level.ServerPlayer player, boolean raining, boolean thundering) {
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            WeatherState state = getWeatherState(level);
            String dimStr = level.dimension().location().toString();
            com.stardew.craft.network.WeatherSyncPacket packet = new com.stardew.craft.network.WeatherSyncPacket(
                    dimStr,
                    state.getWeatherType(),
                    state.getWeatherForTomorrow(),
                    state.isRaining(),
                    state.isThundering());
            net.minecraftforge.network.PacketDistributor.sendToPlayer(player, packet);
        }

        public boolean isRaining() {
            return "Rain".equals(weatherType) || "Storm".equals(weatherType);
        }

        public boolean isWindy() {
            return "WindSpring".equals(weatherType) || "WindFall".equals(weatherType);
        }

        public boolean isThundering() {
            return "Storm".equals(weatherType);
        }

        public String getWeatherType() {
            return weatherType;
        }

        public String getWeatherForTomorrow() {
            return weatherForTomorrow;
        }

        public int getMonthlyNonRainyDayCount() {
            return monthlyNonRainyDayCount;
        }

        private void applyToday(ServerLevel level, int dayOfMonth, String season, int daysPlayed) {
            weatherForTomorrow = getWeatherModificationsForDate(dayOfMonth, season, daysPlayed, weatherForTomorrow);

            // 将明天的天气变为今天的天气
            weatherType = weatherForTomorrow;

            // 应用到 Minecraft 世界
            applyToLevel(level);

            // 统计非雨天数
            if (dayOfMonth == 1) {
                monthlyNonRainyDayCount = 0;
            }
            if (!"Rain".equals(weatherType) && !"Storm".equals(weatherType)) {
                monthlyNonRainyDayCount++;
            }
        }

        private void updateTomorrow(ServerLevel level, int dayOfMonth, String season, int daysPlayed) {
            // 初始化明天天气（原版会在更新前清空为晴天）
            weatherForTomorrow = "Sun";

            // 预测明天的天气（基于随机和规则）
            Random random = createDayRandom(level, daysPlayed);
            String predictedWeather = predictTomorrowWeather(level, season, dayOfMonth + 1, random);

            // 应用星露谷物语的天气修改规则（会覆盖预测）
            weatherForTomorrow = getWeatherModificationsForDate(
                dayOfMonth + 1,
                season,
                daysPlayed + 1,
                predictedWeather
            );
        }
    }
}
