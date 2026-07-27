package com.stardew.craft.integration.create;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.block.crop.StardewCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 与 Create 机械收割机（Mechanical Harvester）等基于 DeployerFakePlayer 的伪玩家收割器兼容。
 *
 * <p>不依赖 Create 类，仅通过类名字符串判断破坏者，未安装 Create 时此监听器空跑。
 *
 * <p>Create 的收割机会调用 {@code level.destroyBlock(...)}，并由 contraption 触发
 * {@link BlockEvent.BreakEvent}，事件玩家为 {@code DeployerFakePlayer}（或派生）。
 * 我们在此拦截：成熟作物走星露谷自己的 {@code harvest} 路径（保证多年生作物 regrow），
 * 未成熟作物直接取消（避免被铲掉）。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CreateHarvesterCompat {

    private CreateHarvesterCompat() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getPlayer();
        if (player == null || !isCreateFakePlayer(player)) return;

        if (!(event.getLevel() instanceof Level lvl) || !(lvl instanceof ServerLevel server)) return;

        BlockPos pos = event.getPos();
        if (StardewCropRuntime.inspect(server, pos) == null) return;

        // 不论成熟与否，都要阻止 Create 的破坏（多年生作物绝不能被无脑挖掉）
        event.setCanceled(true);

        // 作为无经验自动化输出处理；核心与附属作物使用同一个事务入口。
        StardewCropRuntime.harvestForAutomation(
                server,
                pos,
                0,
                stack -> net.minecraft.world.level.block.Block.popResource(
                        server, pos, stack)
        );
    }

    /** 通过类名字符串识别 Create 的 DeployerFakePlayer，避免硬编译依赖。 */
    private static boolean isCreateFakePlayer(Player player) {
        String name = player.getClass().getName();
        return name.startsWith("com.simibubi.create.")
                && name.endsWith("DeployerFakePlayer");
    }
}
