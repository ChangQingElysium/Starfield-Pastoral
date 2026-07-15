package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 深夜时间警告包（服务端 -> 客户端）
 * 对标 SDV DayTimeMoneyBox.timeShakeTimer 效果
 * 
 * type=0: 午夜 (0:00) — 时钟抖动 + 全局消息 "It's getting late..."
 * type=1: 凌晨1点 (1:00 AM) — 时钟抖动（无消息）
 * type=2: 凌晨2点 (2:00 AM) — 时钟抖动（仅视觉，晕倒由服务端处理）
 */
public record TimeWarningPayload(int warningType) implements CustomPacketPayload {

    public static final int MIDNIGHT = 0;
    public static final int ONE_AM = 1;
    public static final int TWO_AM = 2;

    @SuppressWarnings("null")
    public static final Type<TimeWarningPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "time_warning")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, TimeWarningPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, TimeWarningPayload::warningType,
        TimeWarningPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TimeWarningPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 触发时钟抖动（2秒 = 40 ticks）
            com.stardew.craft.client.hud.StardewTimeHud.triggerTimeShake();

            if (payload.warningType() == MIDNIGHT) {
                // 午夜：显示全局消息 "It's getting late..."
                com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.time.getting_late")
                );
            }
        });
    }
}
