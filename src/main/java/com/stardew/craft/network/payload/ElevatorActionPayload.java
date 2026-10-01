package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.menu.ElevatorMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * 电梯传送操作数据包
 * 客户端 -> 服务端
 */
public record ElevatorActionPayload(int targetFloor) implements CustomPacketPayload {

    private static final ResourceLocation TYPE_ID =
        new ResourceLocation(StardewCraft.MODID, "elevator_action");

    public static final Type<ElevatorActionPayload> TYPE = new Type<>(Objects.requireNonNull(TYPE_ID, "typeId"));

    public static final StreamCodec<FriendlyByteBuf, ElevatorActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.targetFloor),
        buf -> new ElevatorActionPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ElevatorActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof ElevatorMenu elevatorMenu) {
                elevatorMenu.teleportToFloor(payload.targetFloor());
            }
        });
    }
}
