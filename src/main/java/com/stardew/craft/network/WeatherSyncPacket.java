package com.stardew.craft.network;

import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 天气同步数据包 - 服务端发送给客户端。
 *
 * <p>既同步 WeatherManager 的天气字符串（供 HUD、音乐等使用），
 * 也直接携带 raining/thundering 布尔值用于控制客户端 {@code ClientLevel.rainLevel}，
 * 替代原版 {@code ClientboundGameEventPacket} 的天气包（后者在星露谷维度被 mixin 屏蔽）。
 */
public record WeatherSyncPacket(
        String dimension,
        String weatherType,
        String weatherForTomorrow,
        boolean raining,
        boolean thundering
) implements CustomPacketPayload {

    @SuppressWarnings("null")
public static final CustomPacketPayload.Type<WeatherSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation("stardewcraft", "weather_sync"));

    @SuppressWarnings("null")
public static final StreamCodec<ByteBuf, WeatherSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            WeatherSyncPacket::dimension,
            ByteBufCodecs.STRING_UTF8,
            WeatherSyncPacket::weatherType,
            ByteBufCodecs.STRING_UTF8,
            WeatherSyncPacket::weatherForTomorrow,
            ByteBufCodecs.BOOL,
            WeatherSyncPacket::raining,
            ByteBufCodecs.BOOL,
            WeatherSyncPacket::thundering,
            WeatherSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
