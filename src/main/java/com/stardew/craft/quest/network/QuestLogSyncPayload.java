package com.stardew.craft.quest.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.quest.StardewQuest;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S→C: 完整同步玩家任务日志（登录 / 任务变更时发送）
 */
@SuppressWarnings("null")
public record QuestLogSyncPayload(CompoundTag data) implements CustomPacketPayload {

    public static final Type<QuestLogSyncPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "quest_log_sync")
    );

    public static final StreamCodec<ByteBuf, QuestLogSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG,
        QuestLogSyncPayload::data,
        QuestLogSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端构建：将 QuestManager 的 questLog 序列化为 NBT */
    public static QuestLogSyncPayload fromQuests(List<StardewQuest> quests, int billboardQuestsDone) {
        return fromQuests(quests, billboardQuestsDone, Set.of());
    }

    public static QuestLogSyncPayload fromQuests(List<StardewQuest> quests, int billboardQuestsDone, Set<Integer> dailyQuestCompletedDays) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StardewQuest q : quests) {
            list.add(q.save());
        }
        tag.put("Quests", list);
        tag.putInt("BillboardDone", billboardQuestsDone);
        tag.putIntArray("CompletedDays", dailyQuestCompletedDays.stream().mapToInt(Integer::intValue).toArray());
        return new QuestLogSyncPayload(tag);
    }

    public static void handle(QuestLogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(QuestLogSyncPayload payload) {
        CompoundTag tag = payload.data();
        ListTag list = tag.getList("Quests", 10);
        List<StardewQuest> quests = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            quests.add(StardewQuest.load(list.getCompound(i)));
        }
        ClientQuestData.setQuestLog(quests);
        ClientQuestData.setBillboardQuestsDone(tag.getInt("BillboardDone"));
        Set<Integer> completedDays = new HashSet<>();
        if (tag.contains("CompletedDays")) {
            for (int d : tag.getIntArray("CompletedDays")) {
                completedDays.add(d);
            }
        }
        ClientQuestData.setDailyQuestCompletedDays(completedDays);
    }
}
