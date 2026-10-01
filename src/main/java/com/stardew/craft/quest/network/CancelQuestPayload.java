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
 * C→S: 玩家取消任务（从任务日志）
 */
@SuppressWarnings("null")
public record CancelQuestPayload(String questId) implements CustomPacketPayload {

    public static final Type<CancelQuestPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "cancel_quest")
    );

    public static final StreamCodec<ByteBuf, CancelQuestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        CancelQuestPayload::questId,
        CancelQuestPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelQuestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            if (data == null) return;
            QuestManager mgr = data.getQuestManager();
            StardewQuest quest = mgr.getQuest(payload.questId());
            if (quest != null && quest.isCanBeCancelled()) {
                mgr.removeQuest(payload.questId(), serverPlayer);
            }
        });
    }
}
