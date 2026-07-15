package com.stardew.craft.block.decor;

import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.network.DailyQuestSyncPayload;
import com.stardew.craft.quest.network.QuestLogSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

/**
 * 公告栏方块 — 右键打开 Billboard UI（日历 + 每日任务）
 * 生存模式不可破坏（硬度 = -1），仅可在创造模式中放置/移除。
 */
@SuppressWarnings("null")
public class BulletinBoardBlock extends MapDecorWallStaticBlock {

    public BulletinBoardBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    /**
     * 模型 X 范围 -13.52~13.52 跨越 2 格，但 VoxelShape 不支持负坐标，
     * 导致自动检测失败。手动指定 MAIN(0,0,0) + EXTENSION(-1,0,0)。
     */
    @Override
    protected java.util.Set<CellOffset> localOccupiedOffsets() {
        java.util.Set<CellOffset> offsets = new java.util.LinkedHashSet<>();
        offsets.add(CellOffset.ZERO);
        offsets.add(new CellOffset(-1, 0, 0));
        return offsets;
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        // Extension → delegate to MAIN block
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) return InteractionResult.PASS;

        if (level.isClientSide) {
            // The model's MAIN cell is the board's visual left half; its
            // EXTENSION cell is the visual right half for every facing.
            openBillboardScreen(state.getValue(PART) == Part.MAIN);
        } else if (player instanceof ServerPlayer serverPlayer) {
            // 服务端同步每日任务和任务日志到客户端
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            if (data != null) {
                // SDV parity: 首次查看公告栏 → 记下 checkedBulletinOnce 标记
                // （对应 SDV Billboard 构造函数 L149-153 的 mailReceived.Add("checkedBulletinOnce") + Town.checkedBoard()）
                data.addMailFlag("checkedBulletinOnce");
                QuestManager mgr = data.getQuestManager();
                PacketDistributor.sendToPlayer(serverPlayer,
                    DailyQuestSyncPayload.fromQuest(mgr.getDailyQuest()));
                PacketDistributor.sendToPlayer(serverPlayer,
                    QuestLogSyncPayload.fromQuests(mgr.getQuestLog(), mgr.getBillboardQuestsDone(), mgr.getDailyQuestCompletedDays()));
                // 同步玩家数据让 "checkedBulletinOnce" 到达客户端 → "?" 消失
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(serverPlayer, data);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private void openBillboardScreen(boolean calendar) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.stardew.craft.client.gui.quest.BillboardScreen(calendar
                    ? com.stardew.craft.client.gui.quest.BillboardScreen.Mode.CALENDAR
                    : com.stardew.craft.client.gui.quest.BillboardScreen.Mode.DAILY_QUEST));
    }
}
