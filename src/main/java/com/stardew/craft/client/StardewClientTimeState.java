package com.stardew.craft.client;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.stardew.craft.StardewCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端侧星露谷天空时间控制器。
 *
 * 问题背景：
 * 非主世界维度的 ServerLevel 使用 DerivedLevelData，其 getDayTime() 直接代理到
 * 主世界，setDayTime() 是空操作。因此原版 synchronizeTime / sendLevelInfo 发给客户端
 * 的 dayTime 始终是主世界的值，和我们的虚拟时间冲突，导致天空一白天一黑夜来回闪。
 *
 * 修复方式：
 * 通过自定义 TimeSyncPacket 传递正确的 virtualDayTime，每客户端 tick 强制覆盖
 * ClientLevel 的 dayTime，压制任何原版泄漏的错误值。
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewClientTimeState {

    /** 上次服务端同步的虚拟 dayTime。 */
    private static long baseDayTime = -1;
    /** 接收 baseDayTime 时的客户端 gameTime。 */
    private static long baseGameTime = -1;
    private static boolean timeFrozen;

    private StardewClientTimeState() {}

    /**
     * 由 TimeSyncPacket 的客户端处理器调用，记录基准时间。
     */
    public static void onServerTimeSync(long virtualDayTime, boolean frozen) {
        baseDayTime = virtualDayTime;
        timeFrozen = frozen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            baseGameTime = mc.level.getGameTime();
        }
    }

    /**
     * 玩家离开星露谷维度时重置状态。
     */
    public static void reset() {
        baseDayTime = -1;
        baseGameTime = -1;
        timeFrozen = false;
    }

    /** True when the server says the visible Stardew clock is not advancing for any reason. */
    public static boolean isTimeFrozenCurrentLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return timeFrozen
            && minecraft.level != null
            && (ModDimensions.STARDEW_VALLEY.equals(minecraft.level.dimension())
                || ModMiningDimensions.STARDEW_MINING.equals(minecraft.level.dimension()));
    }

    /**
     * 每客户端 tick 强制覆盖 ClientLevel 的 dayTime，
     * 确保天空渲染始终使用虚拟时间，不受原版包干扰。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        boolean inStardew = ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || ModMiningDimensions.STARDEW_MINING.equals(level.dimension());
        if (!inStardew) {
            if (baseDayTime != -1) {
                reset();
            }
            return;
        }

        if (baseDayTime < 0) {
            return; // 尚未收到服务端时间同步
        }

        // 基于上次同步的 baseDayTime + 经过的 tick 数线性插值；节日中则完全冻结。
        long elapsed = level.getGameTime() - baseGameTime;
        long targetDayTime = timeFrozen ? baseDayTime : baseDayTime + elapsed;

        // 直接设置正值。tickTime() 已被 ClientLevelTickTimeMixin 取消，
        // handleSetTime 已被 ClientSetTimeMixin 拦截，不会被覆盖。
        level.setDayTime(targetDayTime);
    }
}
