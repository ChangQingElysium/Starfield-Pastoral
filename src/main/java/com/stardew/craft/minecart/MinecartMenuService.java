package com.stardew.craft.minecart;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 矿车网络 — 定义 4 个站点的坐标、传送落点、解锁条件，
 * 并处理「打开菜单」和「玩家选定后传送」两件事。
 */
public final class MinecartMenuService {

    public record Destination(
            String id,                                  // 内部 ID，也是 stationId
            String labelKey,                            // 翻译键（菜单显示）
            ResourceKey<Level> dimension,               // 目的地所在维度
            double x, double y, double z,               // 玩家落点
            float yaw,                                  // 玩家朝向
            String gatingFlag                           // 解锁条件（null = 始终可选）
    ) {}

    public static final Destination TOWN = new Destination(
            "town", "stardewcraft.minecart.dest.town",
            ModDimensions.STARDEW_VALLEY,
            123.0 + 0.5, 64.0, 26.0 + 0.5, 180.0F, null);

    public static final Destination MINES = new Destination(
            "mines", "stardewcraft.minecart.dest.mines",
            ModMiningDimensions.STARDEW_MINING,
            -4.5, 66.125, -0.5, -90.0F, null);

    public static final Destination BUS = new Destination(
            "bus", "stardewcraft.minecart.dest.bus",
            ModDimensions.STARDEW_VALLEY,
            -76.0 + 0.5, 64.0, -70.0 + 0.5, 180.0F, null);

    public static final Destination QUARRY = new Destination(
            "quarry", "stardewcraft.minecart.dest.quarry",
            ModDimensions.STARDEW_VALLEY,
            187.0 + 0.5, 81.0, -141.0 + 0.5, 180.0F, CCStoryFlags.CC_CRAFTS_ROOM);

    private static final List<Destination> ALL = List.of(TOWN, MINES, BUS, QUARRY);

    private MinecartMenuService() {}

    public static Destination byId(String id) {
        for (Destination d : ALL) {
            if (d.id.equalsIgnoreCase(id)) return d;
        }
        return null;
    }

    /**
     * Data/Minecarts.json: the Mines destination needs mail landslideDone, which Mountain.DayUpdate
     * grants once DaysPlayed >= 5 (the project has no landslide event, so the day count is the source).
     */
    private static boolean destinationUnlocked(Destination d, PlayerStardewData data) {
        if (d.gatingFlag != null && !data.hasMailFlag(d.gatingFlag)) return false;
        if (d == MINES) {
            var clock = com.stardew.craft.time.StardewTimeManager.get();
            int daysPlayed = (clock.getCurrentYear() - 1) * 112 + clock.getCurrentSeason() * 28 + clock.getCurrentDay();
            return daysPlayed >= 5;
        }
        return true;
    }

    /** 服务端：打开菜单（已在 MinecartStationEntity.interact 里先做了 ccBoilerRoom 校验）。 */
    public static void openFor(ServerPlayer player, String currentStationId) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        // 双重校验（防止客户端作弊直接发 select 包）
        if (!data.hasMailFlag(CCStoryFlags.CC_BOILER_ROOM)) return;

        List<String> available = new ArrayList<>();
        for (Destination d : ALL) {
            if (Objects.equals(d.id, currentStationId)) continue; // 排除当前站
            if (!destinationUnlocked(d, data)) continue;
            available.add(d.id);
        }

        PacketDistributor.sendToPlayer(player,
                new com.stardew.craft.network.payload.OpenMinecartMenuPayload(
                        currentStationId == null ? "" : currentStationId,
                        available));
    }

    /** 服务端：玩家在菜单里选定后的处理。 */
    public static void handleSelection(ServerPlayer player, String currentStationId, String chosenId) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(CCStoryFlags.CC_BOILER_ROOM)) return;

        Destination dest = byId(chosenId);
        if (dest == null) return;
        if (Objects.equals(dest.id, currentStationId)) return; // 不能选当前站
        if (!destinationUnlocked(dest, data)) return;

        ServerLevel target = player.server.getLevel(dest.dimension);
        if (target == null) return;

        // 过场动作：停下正在做的事，冻结 0.7 秒，再传送（模仿 SDV MinecartWarp 的 freezePause=700ms）
        player.closeContainer();
        player.stopUsingItem();

        if(dest==MINES) {
            com.stardew.craft.mining.MiningCoordinates.teleportPlayerToFloor(player,target,0);
            var p=com.stardew.craft.mining.OrdinaryMineLayout.load(target,0).position(0,13,11);
            ModTeleport.to(player,target,p.getX()+.5,p.getY()+.125,p.getZ()+.5,-90,0);
        } else ModTeleport.to(player, target, dest.x, dest.y, dest.z, dest.yaw, 0.0F);
    }
}
