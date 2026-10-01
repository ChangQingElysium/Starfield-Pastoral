package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：广播睡眠投票进度。
 * <p>
 * 客户端收到后更新 SleepWaitingOverlayScreen 的进度文字。
 */
public record SleepVoteUpdatePayload(int votedCount, int requiredCount) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SleepVoteUpdatePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "sleep_vote_update"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, SleepVoteUpdatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.votedCount());
            buf.writeVarInt(payload.requiredCount());
        },
        buf -> new SleepVoteUpdatePayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(SleepVoteUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 显示投票进度到 action bar（原版 InBedChatScreen 下依然可见）
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.sleep.waiting.progress",
                        payload.votedCount(), payload.requiredCount()),
                    true);
            }
        });
    }
}
