package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record RouteEditorSyncPayload(String routeId, List<BlockPos> points, boolean openScreen) implements CustomPacketPayload {
    public static final Type<RouteEditorSyncPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "route_editor_sync"));

    public static final StreamCodec<FriendlyByteBuf, RouteEditorSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.routeId());
            buf.writeVarInt(payload.points().size());
            for (BlockPos point : payload.points()) {
                buf.writeBlockPos(point);
            }
            buf.writeBoolean(payload.openScreen());
        },
        buf -> {
            String routeId = buf.readUtf();
            int count = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                points.add(buf.readBlockPos().immutable());
            }
            return new RouteEditorSyncPayload(routeId, points, buf.readBoolean());
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteEditorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RouteEditorSyncPayload payload) {
        com.stardew.craft.client.route.RouteEditorClientState.replace(payload.routeId(), payload.points());
        if (payload.openScreen()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.stardew.craft.client.gui.RouteEditorScreen());
        } else if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.stardew.craft.client.gui.RouteEditorScreen screen) {
            screen.refreshFromState();
        }
    }
}
