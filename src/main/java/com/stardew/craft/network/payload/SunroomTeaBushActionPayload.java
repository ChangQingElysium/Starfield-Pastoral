package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.interior.SunroomService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One physical primary-action press against Caroline's central tea bush. */
public record SunroomTeaBushActionPayload() implements CustomPacketPayload {
    public static final Type<SunroomTeaBushActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sunroom_tea_bush_action"));
    public static final StreamCodec<ByteBuf, SunroomTeaBushActionPayload> STREAM_CODEC =
            StreamCodec.unit(new SunroomTeaBushActionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SunroomTeaBushActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SunroomService.handlePrimaryAction(player);
            }
        });
    }
}
