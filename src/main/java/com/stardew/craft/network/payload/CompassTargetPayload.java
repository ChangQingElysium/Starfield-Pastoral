package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.WizardTowerCompassItem;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 -> 客户端：同步法师塔指南针目标坐标。
 * found=false 时表示未找到结构（指针随机旋转）。
 */
public record CompassTargetPayload(boolean found, int x, int z) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CompassTargetPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "compass_target"));

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, CompassTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CompassTargetPayload::found,
            ByteBufCodecs.INT, CompassTargetPayload::x,
            ByteBufCodecs.INT, CompassTargetPayload::z,
            CompassTargetPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompassTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.found) {
                WizardTowerCompassItem.setClientTarget(payload.x, payload.z);
            } else {
                WizardTowerCompassItem.clearClientTarget();
            }
        });
    }
}
