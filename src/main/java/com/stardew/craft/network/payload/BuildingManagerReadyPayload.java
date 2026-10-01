package com.stardew.craft.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Completion refresh for the manager, including an unchanged construction-time block. */
public record BuildingManagerReadyPayload(ResourceLocation dimension, BlockPos pos, BlockState state)
        implements CustomPacketPayload {
    public static final Type<BuildingManagerReadyPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_manager_ready"));
    public static final StreamCodec<FriendlyByteBuf, BuildingManagerReadyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeResourceLocation(value.dimension());
                buffer.writeBlockPos(value.pos());
                buffer.writeVarInt(Block.getId(value.state()));
            },
            buffer -> new BuildingManagerReadyPayload(buffer.readResourceLocation(), buffer.readBlockPos(),
                    Block.stateById(buffer.readVarInt())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingManagerReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BuildingManagerReadyPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension())
                || !mc.level.hasChunkAt(payload.pos())) return;
        mc.level.setServerVerifiedBlockState(payload.pos(), payload.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // The vanilla block-update packets discard UPDATE_IMMEDIATE. Request the mesh here,
        // even when the manager already existed and setBlock would consider it unchanged.
        mc.levelRenderer.blockChanged(mc.level, payload.pos(), payload.state(), payload.state(), Block.UPDATE_IMMEDIATE);
    }
}
