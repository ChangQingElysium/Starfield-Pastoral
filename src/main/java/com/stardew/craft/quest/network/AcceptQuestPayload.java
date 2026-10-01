package com.stardew.craft.quest.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.StardewQuest;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C→S: 玩家接受任务（从公告栏）
 */
@SuppressWarnings("null")
public record AcceptQuestPayload(String questId) implements CustomPacketPayload {

    public static final Type<AcceptQuestPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "accept_quest")
    );

    public static final StreamCodec<ByteBuf, AcceptQuestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        AcceptQuestPayload::questId,
        AcceptQuestPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AcceptQuestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            if (data == null) return;
            QuestManager mgr = data.getQuestManager();

            // 每日任务 ID 格式为 "daily_X"，不在 quests.json 中，
            // 需要从 QuestManager 中获取已生成的实例
            String qid = payload.questId();
            if (qid.startsWith("daily_")) {
                StardewQuest daily = mgr.getDailyQuest();
                if (daily != null && daily.getId().equals(qid) && !mgr.hasQuest(qid)) {
                    mgr.acceptQuest(daily, serverPlayer);
                    // 让客户端的 dailyQuest 缓存 (accepted=true) 及时刷新 → 公告栏按钮变灰
                    com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        DailyQuestSyncPayload.fromQuest(daily));
                }
            } else {
                mgr.acceptQuest(qid, serverPlayer);
            }
        });
    }
}
