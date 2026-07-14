package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.mining.MineEntranceBootstrap;
import com.stardew.craft.mining.MineFloorGenerator;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.network.MiningFloorSyncPacket;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 星露谷主命令 - 整合传送、树、时间、天气等子系统
 */
public class StardewTeleportCommand {
    
    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                // 注意：不要在 "stardew" 根节点加 .requires()，
                // 否则 FarmJoinCommand 的 accept/reject 也会被阻断。
                // 各子命令自行限制权限。
                // 传送相关
                .then(CommandTargets.executesWithTarget(
                    Commands.literal("tp")
                        .requires(source -> source.hasPermission(2))
                        // 兼容旧用法：/stardew tp 依然等价于 main
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("main"),
                            StardewTeleportCommand::teleportToStardew))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("mine"),
                            StardewTeleportCommand::teleportToMine))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("desert_mine"),
                            StardewTeleportCommand::teleportToDesertMine)),
                    StardewTeleportCommand::teleportToStardew))
                .then(CommandTargets.executesWithTarget(
                    Commands.literal("return")
                        .requires(source -> source.hasPermission(2)),
                    StardewTeleportCommand::returnToOverworld))
                .then(Commands.literal("mine")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("set_floor")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("floor", IntegerArgumentType.integer(0)),
                            StardewTeleportCommand::setMineFloor))
                    )
                    .then(Commands.literal("set_skull_floor")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("floor", IntegerArgumentType.integer(1)),
                            StardewTeleportCommand::setDesertMineFloor))
                    )
                )
                .then(Commands.literal("interior")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("ensure")
                        .executes(StardewTeleportCommand::ensureInteriorLoaded)
                    )
                    .then(Commands.literal("reload")
                        .executes(StardewTeleportCommand::forceReloadInterior)
                    )
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("tp_origin"),
                        StardewTeleportCommand::teleportToInteriorOrigin))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("tp_spawn"),
                        StardewTeleportCommand::teleportToInteriorSpawn))
                )
                // 时间管理
                .then(Commands.literal("time")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("get")
                        .executes(StardewTeleportCommand::getTime))
                    .then(Commands.literal("set")
                        .then(Commands.literal("time")
                            .then(Commands.argument("time", IntegerArgumentType.integer(0, 1560))
                                .executes(StardewTeleportCommand::setTime)))
                        .then(Commands.literal("date")
                            .then(Commands.argument("day", IntegerArgumentType.integer(1, 28))
                                .executes(StardewTeleportCommand::setDate)))
                        .then(Commands.literal("season")
                            .then(Commands.literal("spring").executes(ctx -> setSeason(ctx, 0)))
                            .then(Commands.literal("summer").executes(ctx -> setSeason(ctx, 1)))
                            .then(Commands.literal("fall").executes(ctx -> setSeason(ctx, 2)))
                            .then(Commands.literal("winter").executes(ctx -> setSeason(ctx, 3))))
                        .then(Commands.literal("year")
                            .then(Commands.argument("year_val", IntegerArgumentType.integer(1))
                                .executes(StardewTeleportCommand::setYear))))
                    .then(Commands.literal("add")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                            .executes(StardewTeleportCommand::addTime)))
                    .then(Commands.literal("newday")
                        .executes(StardewTeleportCommand::newDay))
                    .then(Commands.literal("reset")
                        .executes(StardewTeleportCommand::resetTime)))
                // 天气管理
                .then(Commands.literal("weather")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("get")
                        .executes(StardewTeleportCommand::getWeather))
                    .then(Commands.literal("set")
                        .then(Commands.literal("sun").executes(ctx -> setWeather(ctx, "Sun")))
                        .then(Commands.literal("rain").executes(ctx -> setWeather(ctx, "Rain")))
                        .then(Commands.literal("storm").executes(ctx -> setWeather(ctx, "Storm")))
                        .then(Commands.literal("snow").executes(ctx -> setWeather(ctx, "Snow")))
                        .then(Commands.literal("windspring").executes(ctx -> setWeather(ctx, "WindSpring")))
                        .then(Commands.literal("windfall").executes(ctx -> setWeather(ctx, "WindFall")))
                        .then(Commands.literal("festival").executes(ctx -> setWeather(ctx, "Festival"))))
                    .then(Commands.literal("tomorrow")
                        .executes(StardewTeleportCommand::getTomorrowWeather))
                    .then(Commands.literal("test")
                        .executes(StardewTeleportCommand::testWeatherProbability))
                    .then(Commands.literal("diagnose")
                        .executes(StardewTeleportCommand::diagnoseRain))
                    .then(Commands.literal("fixvanilla")
                        .executes(StardewTeleportCommand::fixVanillaWeather)))
        );
    }

    /**
     * 传送到星露谷维度
     */
    private static int teleportToStardew(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel stardewLevel = context.getSource().getServer()
                .getLevel(ModDimensions.STARDEW_VALLEY);
            
            if (stardewLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.stardew_unavailable");
                return 0;
            }

            InteriorSubspaceManager.ensureLoaded(stardewLevel, "tp_stardew");
            
            // 传送到星露谷维度的出生点（可以后续改为农场位置）
            BlockPos targetPos = new BlockPos(0, 70, 0);
            
            ModTeleport.to(player, stardewLevel, targetPos, player.getYRot(), player.getXRot());
            
            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.stardew_success"),
                false
            );
            
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.failed", e.getMessage());
            return 0;
        }
    }

    private static int ensureInteriorLoaded(CommandContext<CommandSourceStack> context) {
        try {
            @SuppressWarnings("null")
            ServerLevel stardewLevel = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.stardew_unavailable");
                return 0;
            }

            InteriorSubspaceManager.forceReload(stardewLevel, "manual_command_force_reload");
            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.interior_interactions_reloaded"),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.interior_load_failed", e.getMessage());
            return 0;
        }
    }

    private static int forceReloadInterior(CommandContext<CommandSourceStack> context) {
        try {
            @SuppressWarnings("null")
            ServerLevel stardewLevel = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.stardew_unavailable");
                return 0;
            }

            InteriorSubspaceManager.forceReload(stardewLevel, "manual_force_reload");
            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.interior_reloaded"),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.interior_reload_failed", e.getMessage());
            return 0;
        }
    }

    private static int teleportToInteriorOrigin(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel stardewLevel = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.stardew_unavailable");
                return 0;
            }

            InteriorSubspaceManager.ensureLoaded(stardewLevel, "manual_tp_origin");
            ModTeleport.to(player, stardewLevel, 21.5D, 36.0D, -12.5D, 180.0F, 0.0F);
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.teleport.interior_origin_success", "21 36 -12"), false);
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.interior_origin_failed", e.getMessage());
            return 0;
        }
    }

    private static int teleportToInteriorSpawn(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel stardewLevel = context.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.stardew_unavailable");
                return 0;
            }

            InteriorSubspaceManager.ensureLoaded(stardewLevel, "manual_tp_spawn");
            ModTeleport.to(player, stardewLevel, 12038.5D, 71.0D, 12038.5D, -90.0F, 0.0F);
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.teleport.interior_spawn_success", "12038 71 12038"), false);
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.interior_spawn_failed", e.getMessage());
            return 0;
        }
    }

    /**
     * 传送到矿井维度（调试用）
     * - 会确保入口大厅结构只生成一次
     * - 落点统一走 MiningCoordinates.teleportPlayerToFloor(0)，即 (0.5, 66, -7.5) 面朝北
     */
    private static int teleportToMine(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel mineLevel = context.getSource().getServer().getLevel(ModMiningDimensions.STARDEW_MINING);

            if (mineLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.mine_unavailable_detailed");
                return 0;
            }

            MineEntranceBootstrap.ensureGenerated(mineLevel);
            MiningCoordinates.teleportPlayerToFloor(player, mineLevel, 0);

            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.mine_entrance_success"),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.mine_failed", e.getMessage());
            return 0;
        }
    }

    /**
     * 传送到骷髅矿洞入口（floor 121 的安全区）
     */
    private static int teleportToDesertMine(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel mineLevel = context.getSource().getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
            if (mineLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.mine_unavailable");
                return 0;
            }

            // 骷髅矿洞入口 = floor 121 safe zone
            int floor = 121;
            int floorZ = floor * com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING + 14;
            double safeY = 66.0;

            // 确保 floor 121 已生成
            com.stardew.craft.mining.MineFloorGenerator.generateFloor(mineLevel, floor);

            ModTeleport.to(player, mineLevel, 0.5, safeY, floorZ + 0.5, player.getYRot(), player.getXRot());

            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.skull_cavern_entrance_success", 121),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.skull_cavern_failed", e.getMessage());
            return 0;
        }
    }

    /**
     * 直接设置矿井楼层并传送（调试/管理）
     * 用法：/stardew mine set_floor <floor>
     */
    @SuppressWarnings("null")
    private static int setMineFloor(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            int targetFloor = IntegerArgumentType.getInteger(context, "floor");

            @SuppressWarnings("null")
            ServerLevel mineLevel = context.getSource().getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
            if (mineLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.mine_unavailable_detailed");
                return 0;
            }

            MineEntranceBootstrap.ensureGenerated(mineLevel);
            if (targetFloor > 0) {
                MineFloorGenerator.generateFloor(mineLevel, targetFloor);
            }

            MiningPlayerData playerData = MiningDataManager.getPlayerData(player);
            playerData.setCurrentFloor(targetFloor);
            MiningDataManager.savePlayerData(player, playerData);

            MiningCoordinates.teleportPlayerToFloor(player, mineLevel, targetFloor);
            PacketDistributor.sendToPlayer(player, new MiningFloorSyncPacket(targetFloor));
            com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(player, targetFloor);

            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.mine_floor_success", targetFloor),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.mine_floor_failed", e.getMessage());
            return 0;
        }
    }

    /**
     * 设置骷髅矿井楼层（SDV 相对层，1 = 内部 floor 121）。
     * 用法：/stardew desert_mine set_floor <n>
     */
    @SuppressWarnings("null")
    private static int setDesertMineFloor(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            int relative = IntegerArgumentType.getInteger(context, "floor");
            int targetFloor = 120 + relative; // 骷髅矿井 1 -> 内部 121

            ServerLevel mineLevel = context.getSource().getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
            if (mineLevel == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.mine_unavailable");
                return 0;
            }

            MineEntranceBootstrap.ensureGenerated(mineLevel);
            MineFloorGenerator.generateFloor(mineLevel, targetFloor);

            MiningPlayerData playerData = MiningDataManager.getPlayerData(player);
            playerData.setCurrentFloor(targetFloor);
            MiningDataManager.savePlayerData(player, playerData);

            MiningCoordinates.teleportPlayerToFloor(player, mineLevel, targetFloor);
            PacketDistributor.sendToPlayer(player, new MiningFloorSyncPacket(targetFloor));
            com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(player, targetFloor);

            final int displayFloor = relative;
            final int internal = targetFloor;
            context.getSource().sendSuccess(
                () -> Component.translatable(
                    "stardewcraft.command.teleport.skull_cavern_floor_success", displayFloor, internal),
                false
            );
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.skull_cavern_floor_failed", e.getMessage());
            return 0;
        }
    }
    private static int returnToOverworld(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = CommandTargets.resolve(context);
            if (player == null) {
                sendFailureMsg(context, "stardewcraft.command.target_required");
                return 0;
            }
            @SuppressWarnings("null")
            ServerLevel overworld = context.getSource().getServer()
                .getLevel(Level.OVERWORLD);
            
            if (overworld == null) {
                sendFailureMsg(context, "stardewcraft.command.teleport.overworld_unavailable");
                return 0;
            }
            
            // 返回到主世界出生点
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            
            ModTeleport.to(player, overworld, spawnPos, player.getYRot(), player.getXRot());
            
            context.getSource().sendSuccess(
                () -> Component.translatable("stardewcraft.command.teleport.overworld_success"),
                false
            );
            
            return 1;
        } catch (Exception e) {
            sendFailureMsg(context, "stardewcraft.command.teleport.failed", e.getMessage());
            return 0;
        }
    }
    
    @SuppressWarnings("null")
    private static void sendFailureMsg(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args));
    }
    
    // ================== 时间管理方法 ==================
    
    @SuppressWarnings("null")
    private static int getTime(CommandContext<CommandSourceStack> context) {
        StardewTimeManager time = StardewTimeManager.get();
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.info",
            time.getFormattedTime12Hour(), seasonComponent(time), time.getCurrentDay(),
            time.getCurrentYear(), time.getCurrentTime()), false);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int setDate(CommandContext<CommandSourceStack> context) {
        int day = IntegerArgumentType.getInteger(context, "day");
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentDay(day);
        syncTime(time);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.date_set", day), true);
        return 1;
    }

    @SuppressWarnings("null")
    private static int setSeason(CommandContext<CommandSourceStack> context, int season) {
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentSeason(season);
        syncTime(time);

        // 改季节后立即让非当季作物枯萎（已加载区块内）
        var server = context.getSource().getServer();
        if (server != null) {
            ServerLevel stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel != null) {
                CropGrowthManager.get(stardewLevel).killOutOfSeasonLoaded(stardewLevel);
            }
        }
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.season_set", seasonComponent(time)), true);
        return 1;
    }

    @SuppressWarnings("null")
    private static int setYear(CommandContext<CommandSourceStack> context) {
        int year = IntegerArgumentType.getInteger(context, "year_val");
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentYear(year);
        syncTime(time);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.year_set", year), true);
        return 1;
    }

    @SuppressWarnings("null")
    private static void syncTime(StardewTimeManager time) {
        // 立即更新MC时间
        com.stardew.craft.event.DimensionEventHandler.updateMCTime(time);
        // 同步到客户端
        PacketDistributor.sendToAllPlayers(TimeSyncPacket.fromTimeManager(time));
    }

    @SuppressWarnings("null")
    private static int setTime(CommandContext<CommandSourceStack> context) {
        int newTime = IntegerArgumentType.getInteger(context, "time");
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentTime(newTime);
        syncTime(time);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.time_set", time.getFormattedTime12Hour()), true);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int addTime(CommandContext<CommandSourceStack> context) {
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentTime(time.getCurrentTime() + minutes);
        
        syncTime(time);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.added", minutes, time.getFormattedTime12Hour()), true);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int newDay(CommandContext<CommandSourceStack> context) {
        StardewTimeManager time = StardewTimeManager.get();
        time.advanceDay();
        
        PacketDistributor.sendToAllPlayers(TimeSyncPacket.fromTimeManager(time));
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.time.new_day", seasonComponent(time), time.getCurrentDay()), true);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int resetTime(CommandContext<CommandSourceStack> context) {
        StardewTimeManager time = StardewTimeManager.get();
        time.setCurrentTime(600); // 重置到6:00 AM
        time.setCurrentDay(1);
        time.setCurrentSeason(0); // Spring
        time.setCurrentYear(1);
        
        com.stardew.craft.event.DimensionEventHandler.updateMCTime(time);
        PacketDistributor.sendToAllPlayers(TimeSyncPacket.fromTimeManager(time));
        
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.time.reset"), true);
        
        return 1;
    }
    
    // ================== 天气管理方法 ==================
    
    @SuppressWarnings("null")
    private static int getWeather(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.stardew_dimension_only"));
            return 0;
        }
        
        String currentWeather = WeatherManager.getCurrentWeather(level);
        String tomorrowWeather = WeatherManager.getTomorrowWeather(level);
        
        StardewTimeManager timeManager = StardewTimeManager.get();
        int day = timeManager.getCurrentDay();
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.weather.info",
            seasonComponent(timeManager), day,
            currentWeather, weatherComponent(currentWeather),
            tomorrowWeather, weatherComponent(tomorrowWeather),
            Component.translatable(level.isRaining()
                ? "stardewcraft.command.weather.mc.raining"
                : "stardewcraft.command.weather.mc.clear"),
            Component.translatable(level.isThundering()
                ? "stardewcraft.common.yes"
                : "stardewcraft.common.no")), false);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int setWeather(CommandContext<CommandSourceStack> context, String weatherType) {
        ServerLevel level = context.getSource().getLevel();
        
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.stardew_dimension_only"));
            return 0;
        }
        
        WeatherManager.setWeather(level, weatherType);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.weather.set",
            weatherType, weatherComponent(weatherType),
            Component.translatable(level.isRaining()
                ? "stardewcraft.command.weather.mc.raining"
                : "stardewcraft.command.weather.mc.clear"),
            Component.translatable(level.isThundering()
                ? "stardewcraft.common.yes"
                : "stardewcraft.common.no")), true);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int getTomorrowWeather(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.stardew_dimension_only"));
            return 0;
        }
        
        String tomorrowWeather = WeatherManager.getTomorrowWeather(level);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.weather.tomorrow", tomorrowWeather,
            weatherComponent(tomorrowWeather)), false);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int testWeatherProbability(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.stardew_dimension_only"));
            return 0;
        }
        
        StardewTimeManager timeManager = StardewTimeManager.get();
        String currentSeason = timeManager.getSeasonName();
        
        // 模拟100天的天气
        int[] weatherCount = new int[7];
        String[] weatherNames = {"Sun", "Rain", "Storm", "Snow", "WindSpring", "WindFall", "Festival"};
        
        java.util.Random random = new java.util.Random(level.getSeed());
        for (int i = 0; i < 100; i++) {
            String weather = WeatherManager.predictTomorrowWeather(level, currentSeason, i % 28 + 1, random);
            for (int j = 0; j < weatherNames.length; j++) {
                if (weatherNames[j].equals(weather)) {
                    weatherCount[j]++;
                    break;
                }
            }
        }
        
        var result = Component.translatable(
            "stardewcraft.command.weather.test_header", 100,
            Component.translatable("stardewcraft.season." + currentSeason.toLowerCase(java.util.Locale.ROOT)));
        
        for (int i = 0; i < weatherNames.length; i++) {
            if (weatherCount[i] > 0) {
                result.append(Component.translatable(
                    "stardewcraft.command.weather.test_entry",
                    weatherNames[i], weatherCount[i], weatherComponent(weatherNames[i])));
            }
        }

        context.getSource().sendSuccess(() -> result, false);
        
        return 1;
    }
    
    @SuppressWarnings("null")
    private static int diagnoseRain(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.player_only"));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        int hmMotionBlocking = level.getHeight(Heightmap.Types.MOTION_BLOCKING, playerPos.getX(), playerPos.getZ());
        int hmWorldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX(), playerPos.getZ());
        boolean canSeeSky = level.canSeeSky(playerPos);
        boolean isRaining = level.isRaining();
        boolean isRainingAtPlayer = level.isRainingAt(playerPos);
        boolean isRainingAtAbove = level.isRainingAt(playerPos.above());
        float rainLevel = level.getRainLevel(0f);
        float thunderLevel = level.getThunderLevel(0f);

        var biomeHolder = level.getBiome(playerPos);
        String biomeName = biomeHolder.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("unknown");
        boolean hasPrecip = biomeHolder.value().hasPrecipitation();
        Biome.Precipitation precipType = biomeHolder.value().getPrecipitationAt(playerPos);

        String stardewWeather = WeatherManager.getCurrentWeather(level);
        boolean weatherCycleEnabled = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.weather.diagnose",
            stardewWeather, isRaining, level.isThundering(),
            String.format(java.util.Locale.ROOT, "%.3f", rainLevel),
            String.format(java.util.Locale.ROOT, "%.3f", thunderLevel),
            weatherCycleEnabled,
            playerPos.getX(), playerPos.getY(), playerPos.getZ(),
            hmMotionBlocking, hmWorldSurface, canSeeSky,
            isRainingAtPlayer, isRainingAtAbove,
            biomeName, hasPrecip, precipType), false);
        return 1;
    }

    private static int fixVanillaWeather(CommandContext<CommandSourceStack> context) {
        var src = context.getSource();
        var server = src.getServer();
        ServerLevel overworld = server.overworld();
        boolean wasRaining = overworld.isRaining();
        boolean wasThundering = overworld.isThundering();
        // Clear shared PrimaryLevelData weather (also resets the timers so the
        // game won't immediately re-roll a thunderstorm).
        overworld.setWeatherParameters(0, 0, false, false);

        // Re-broadcast the Stardew dim's authoritative custom weather so any
        // client that already cached the bogus storm state gets corrected.
        for (ServerLevel sl : server.getAllLevels()) {
            if (sl.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) {
                var state = com.stardew.craft.weather.WeatherSavedData.get(sl)
                    .getWeatherState(sl.dimension());
                com.stardew.craft.weather.WeatherManager.setWeather(sl, state.getWeatherType());
            }
        }

        src.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.weather.vanilla_cleared", wasRaining, wasThundering), true);
        return 1;
    }

    private static Component weatherComponent(String weatherType) {
        return Component.translatable("stardewcraft.weather." + weatherType.toLowerCase(java.util.Locale.ROOT));
    }

    private static Component seasonComponent(StardewTimeManager time) {
        return Component.translatable("stardewcraft.season."
            + time.getSeasonName().toLowerCase(java.util.Locale.ROOT));
    }
}
