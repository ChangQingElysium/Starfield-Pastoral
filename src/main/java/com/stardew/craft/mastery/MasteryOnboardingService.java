package com.stardew.craft.mastery;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.MasteryHintPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 精通系统玩家上线/睡眠后的引导逻辑：
 *  - {@link #checkOnMorning(ServerPlayer)}：被 StardewTimeManager 的 morning 流程调用，
 *    首次达到 5×Lv10 且未访问过山洞 → 推送 MasteryHint toast + 设 gotMasteryHint。
 *  - {@link #onPlayerTick(PlayerTickEvent.Post)}：玩家踏过山洞门后任意一格 → setVisitedMasteryCave(true)。
 *
 * 对应 SDV {@code Game1.cs:9135-9141}。
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class MasteryOnboardingService {

    private MasteryOnboardingService() {}

    /** 早上推送：在 StardewTimeManager.advanceDayWithSleepTime 的 per-player 循环里调用。 */
    public static void checkOnMorning(ServerPlayer player) {
        if (player == null) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) return;
        if (data.isGotMasteryHint()) return;
        if (data.hasVisitedMasteryCave()) return;
        if (!data.hasAllSkillsMaxed()) return;
        data.setGotMasteryHint(true);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new MasteryHintPayload());
    }

    /** 山洞内部包围盒（门后那一侧，包括门的下半块在内向南延伸到 z=101）。 */
    private static boolean isInsideCave(Vec3 pos) {
        return pos.x >= -95.0 && pos.x <= -85.0
            && pos.y >= 64.0  && pos.y <= 68.0
            && pos.z >= 99.0  && pos.z <= 108.5; // 不含门外侧 z=109+
    }

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 每 10 tick 检测一次即可
        if ((++tickCounter % 10) != 0) return;
        if (!player.level().dimension().equals(ModDimensions.STARDEW_VALLEY)) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null || data.hasVisitedMasteryCave()) return;
        if (!isInsideCave(player.position())) return;

        data.setVisitedMasteryCave(true);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
}
