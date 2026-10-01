package com.stardew.craft.quest.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S→C: 同步任务完成通知（播放音效 + 更新客户端状态）
 */
@SuppressWarnings("null")
public record QuestCompletePayload(String questId, int moneyReward) implements CustomPacketPayload {

    public static final Type<QuestCompletePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "quest_complete")
    );

    public static final StreamCodec<ByteBuf, QuestCompletePayload> STREAM_CODEC = StreamCodec.composite(
        com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
        QuestCompletePayload::questId,
        com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
        QuestCompletePayload::moneyReward,
        QuestCompletePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuestCompletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(QuestCompletePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // 更新客户端任务缓存
        ClientQuestData.markCompleted(payload.questId());

        // 触发 HUD 任务图标脉冲
        com.stardew.craft.client.hud.QuestIconHud.pingQuestComplete();

        // 播放任务完成音效
        mc.player.playSound(
            com.stardew.craft.sound.ModSounds.QUEST_COMPLETE.get(),
            1.0f, 1.0f
        );
    }
}
