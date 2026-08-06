package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.event.SleepVoteTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端 → 服务端：玩家取消睡眠投票（按 ESC 退出等待界面）。
 */
public record SleepCancelPayload() implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SleepCancelPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sleep_cancel"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, SleepCancelPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new SleepCancelPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(SleepCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 唤醒玩家（取消睡眠姿态 + 移动锁定）
            if (player.isSleeping()) {
                player.stopSleeping();
            }
            SleepVoteTracker.revokeVoteAndBroadcast(player);
            PacketDistributor.sendToPlayer(
                    player, new SleepFadeRestorePayload());
        });
    }
}
