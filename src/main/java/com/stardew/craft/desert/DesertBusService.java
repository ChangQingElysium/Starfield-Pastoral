package com.stardew.craft.desert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.network.payload.OpenDesertBusConfirmPayload;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcRuntimeDataManager;
import com.stardew.craft.npc.runtime.NpcRuntimeState;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 沙漠公交服务——统一两条入口（portal trigger + block 右键）。
 *
 * <p>参考原版 {@code StardewValley.Locations.BusStop}（问询 → 扣 500g → 8s 寻路 → 门动画 +
 * {@code trashcanlid} → {@code stoneStep} → {@code globalFadeToBlack} → {@code batFlap} +
 * {@code busDriveOff} → warp → {@code globalFadeToClear}）。
 *
 * <p>本模组因为没有公交精灵与寻路资产，用如下精简节奏（tick 数，20 tps）：
 * <ul>
 *   <li>t=0   扣钱 + {@code TRASHCANLID}（关车门）+ 开始 fade to black（20 tick）</li>
 *   <li>t=15  {@code STONE_STEP}（踏入车厢）</li>
 *   <li>t=35  {@code DOOR_CREAK}（车辆起步的替代音，我们没有 batFlap/busDriveOff）</li>
 *   <li>t=55  warp 到沙漠到达点</li>
 *   <li>t=60  开始 fade from black（20 tick）+ 发送到达消息</li>
 * </ul>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DesertBusService {

    private DesertBusService() {}

    private static final String TAG_RIDE_START = "stardewcraft_desert_bus_ride_start_tick";
    /** 0 = 去沙漠（收费+CC Vault 检查），1 = 返回鹈鹕镇（免费、无前置）。 */
    private static final String TAG_RIDE_DIR = "stardewcraft_desert_bus_ride_dir";

    public static final int DIR_TO_DESERT = 0;
    public static final int DIR_TO_TOWN = 1;

    // 时序（单位 tick）
    private static final int FADE_OUT_TICKS = 20;
    private static final int STEP_SOUND_AT = 15;
    private static final int BUS_SOUND_AT = 35;
    private static final int WARP_AT = 55;
    private static final int FADE_IN_AT = 60;
    private static final int FADE_IN_TICKS = 20;
    private static final int RIDE_TOTAL = 90; // 清理 tag
    private static final String PAM_DRIVER_POINT = "pam_busstop_bench";
    private static final double PAM_READY_DISTANCE_SQR = 4.0D;

    /**
     * 去沙漠：做前置检查 + 弹出买票确认。
     */
    public static void beginBusRide(ServerPlayer player) {
        if (isRiding(player)) return;

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);

        if (!data.hasMailFlag(CCStoryFlags.CC_VAULT)) {
            ObjectDialogueService.show(player, "message.stardewcraft.desert_bus.locked");
            return;
        }

        if (PlayerStardewDataAPI.getMoney(player) < DesertConstants.BUS_TICKET_PRICE) {
            ObjectDialogueService.show(player, "message.stardewcraft.desert_bus.no_money",
                    DesertConstants.BUS_TICKET_PRICE);
            return;
        }

        PacketDistributor.sendToPlayer(player,
            new OpenDesertBusConfirmPayload(DesertConstants.BUS_TICKET_PRICE));
    }

    /**
     * 从沙漠返回鹈鹕镇。免费、无前置条件，直接弹出确认。
     */
    public static void beginReturnRide(ServerPlayer player) {
        if (isRiding(player)) return;
        // price=0 表示返程免费；客户端用这个值驱动问题文案
        PacketDistributor.sendToPlayer(player, new OpenDesertBusConfirmPayload(0));
    }

    /**
    * 客户端确认后服务端回调——再次校验，启动乘车状态。
    * 玩家现在所在维度是 stardew_valley，根据玩家是否位于沙漠区域判断方向：
    * 位于沙漠区域 → 返程；否则 → 去沙漠。
     */
    public static void onPlayerConfirmed(ServerPlayer player) {
        if (isRiding(player)) return;

        int direction = detectDirection(player);
        if (direction == DIR_TO_DESERT) {
            PlayerStardewData data = PlayerStardewDataAPI.getData(player);
            if (!data.hasMailFlag(CCStoryFlags.CC_VAULT)) {
                ObjectDialogueService.show(player, "message.stardewcraft.desert_bus.locked");
                return;
            }
            if (!isPamReadyToDrive(player.serverLevel())
                    && !canDriveYourselfToday(player.serverLevel())) {
                ObjectDialogueService.show(player, "message.stardewcraft.desert_bus.no_driver");
                return;
            }
            if (PlayerStardewDataAPI.getMoney(player) < DesertConstants.BUS_TICKET_PRICE) {
                ObjectDialogueService.show(player, "message.stardewcraft.desert_bus.no_money",
                        DesertConstants.BUS_TICKET_PRICE);
                return;
            }
            PlayerStardewDataAPI.removeMoney(player, DesertConstants.BUS_TICKET_PRICE);
        }

        long now = player.serverLevel().getGameTime();
        player.getPersistentData().putLong(TAG_RIDE_START, now);
        player.getPersistentData().putInt(TAG_RIDE_DIR, direction);

        // t=0：门关音效 + 开始淡入黑屏
        playPlayerSound(player, ModSounds.TRASHCANLID.get(), 1.0F, 1.0F);
        PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 0, FADE_OUT_TICKS));
    }

    /** 根据玩家当前位置是否落在沙漠包围盒内判断方向。 */
    private static int detectDirection(ServerPlayer player) {
        return DesertConstants.isInDesertRegion(player.blockPosition()) ? DIR_TO_TOWN : DIR_TO_DESERT;
    }

    private static boolean isPamReadyToDrive(ServerLevel level) {
        NpcRuntimeState schedule = NpcRuntimeDataManager.get(level).states().get("pam");
        if (schedule == null || !PAM_DRIVER_POINT.equals(schedule.namedPointId())) {
            return false;
        }
        NpcScheduleRuntimeService.TargetPoint driverPoint =
                NpcScheduleRuntimeService.resolveWorldTarget(level, schedule, null);
        if (driverPoint == null) {
            return false;
        }
        StardewNpcEntity pam = NpcSpawnManager.getTrackedNpc(level, "pam");
        return pam != null && pam.isAlive() && !pam.isRemoved()
                && isAtDriverPoint(pam.position(), driverPoint.position());
    }

    static boolean isAtDriverPoint(Vec3 position, Vec3 driverPoint) {
        if (position == null || driverPoint == null) {
            return false;
        }
        double dx = position.x - driverPoint.x;
        double dz = position.z - driverPoint.z;
        return dx * dx + dz * dz <= PAM_READY_DISTANCE_SQR;
    }

    /**
     * Vanilla's {@code canDriveYourselfToday}: once the bus is globally repaired,
     * players may drive on days when Pam's selected schedule never visits BusStop.
     * Green Rain is the sole no-driver exception.
     */
    private static boolean canDriveYourselfToday(ServerLevel level) {
        if (!CCStoryFlags.anyPlayerHasFlag(CCStoryFlags.CC_VAULT)) {
            return false;
        }
        NpcRuntimeState pam = NpcRuntimeDataManager.get(level).states().get("pam");
        if (pam == null || "greenrain".equalsIgnoreCase(pam.activeScheduleKey())) {
            return false;
        }

        JsonObject root = NpcDataRegistry.schedules().get("pam");
        JsonObject schedule = findSchedule(root, pam.activeScheduleKey());
        return schedule != null && !scheduleVisitsBusStop(root, schedule, new java.util.HashSet<>());
    }

    private static boolean scheduleVisitsBusStop(JsonObject root,
                                                 JsonObject schedule,
                                                 Set<String> visited) {
        if (schedule.has("_goto") && schedule.get("_goto").isJsonPrimitive()) {
            String target = schedule.get("_goto").getAsString();
            if (!visited.add(target.toLowerCase(Locale.ROOT))) {
                return true;
            }
            JsonObject next = findSchedule(root, target);
            return next == null || scheduleVisitsBusStop(root, next, visited);
        }
        for (Map.Entry<String, JsonElement> entry : schedule.entrySet()) {
            if (entry.getKey().startsWith("_") || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            String node = entry.getValue().getAsString().trim().toLowerCase(Locale.ROOT);
            if (node.startsWith("busstop ") || node.contains("@pam_busstop_bench")) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject findSchedule(JsonObject root, String key) {
        if (root == null || key == null || key.isBlank()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue().isJsonObject()) {
                return entry.getValue().getAsJsonObject();
            }
        }
        return null;
    }

    public static boolean isRiding(ServerPlayer player) {
        return player.getPersistentData().getLong(TAG_RIDE_START) != 0L;
    }

    /** 由 {@link #onPlayerTick} 驱动的时序机。 */
    private static void tickRide(ServerPlayer player) {
        long start = player.getPersistentData().getLong(TAG_RIDE_START);
        if (start == 0L) return;

        long elapsed = player.serverLevel().getGameTime() - start;

        if (elapsed == STEP_SOUND_AT) {
            playPlayerSound(player, ModSounds.STONE_STEP.get(), 1.0F, 1.0F);
        } else if (elapsed == BUS_SOUND_AT) {
            playPlayerSound(player, ModSounds.DOOR_CREAK.get(), 0.9F, 0.7F);
        } else if (elapsed == WARP_AT) {
            ServerLevel stardew = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardew == null) {
                clearRide(player);
                return;
            }
            int dir = player.getPersistentData().getInt(TAG_RIDE_DIR);
            if (dir == DIR_TO_TOWN) {
                BlockPos a = DesertConstants.TOWN_RETURN_ARRIVAL;
                player.teleportTo(stardew,
                    a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D,
                    Set.of(), DesertConstants.TOWN_RETURN_YAW, 0.0F);
            } else {
                BlockPos arrival = DesertConstants.worldPos(DesertConstants.ARRIVAL_OFFSET);
                player.teleportTo(stardew,
                    arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D,
                    Set.of(), 180.0F, 0.0F);
            }
        } else if (elapsed == FADE_IN_AT) {
            PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 1, FADE_IN_TICKS));
            int dir = player.getPersistentData().getInt(TAG_RIDE_DIR);
            String msg = dir == DIR_TO_TOWN
                ? "message.stardewcraft.desert_bus.returned"
                : "message.stardewcraft.desert_bus.arrived";
            player.displayClientMessage(Component.translatable(msg), true);
        } else if (elapsed >= RIDE_TOTAL) {
            clearRide(player);
        }
    }

    private static void clearRide(ServerPlayer player) {
        player.getPersistentData().remove(TAG_RIDE_START);
        player.getPersistentData().remove(TAG_RIDE_DIR);
    }

    private static void playPlayerSound(ServerPlayer player,
                                        net.minecraft.sounds.SoundEvent sound,
                                        float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, SoundSource.PLAYERS, volume, pitch);
    }
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.getPersistentData().getLong(TAG_RIDE_START) == 0L) return;
        tickRide(sp);
    }
}
