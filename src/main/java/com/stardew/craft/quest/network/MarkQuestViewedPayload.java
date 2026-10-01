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
 * C→S: 玩家在任务日志中查看了某任务，清除 showNew 标记
 */
@SuppressWarnings("null")
public record MarkQuestViewedPayload(String questId) implements CustomPacketPayload {

    public static final Type<MarkQuestViewedPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "mark_quest_viewed")
    );

    public static final StreamCodec<ByteBuf, MarkQuestViewedPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        MarkQuestViewedPayload::questId,
        MarkQuestViewedPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarkQuestViewedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            if (data == null) return;
            QuestManager mgr = data.getQuestManager();
            StardewQuest quest = mgr.getQuest(payload.questId());
            if (quest != null && quest.isShowNew()) {
                quest.setShowNew(false);
            }
        });
    }
}
