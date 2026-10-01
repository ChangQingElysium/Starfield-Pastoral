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

public record RouteGuidanceStartPayload(String routeId, List<BlockPos> points, int durationTicks) implements CustomPacketPayload {
    public static final Type<RouteGuidanceStartPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "route_guidance_start"));

    public static final StreamCodec<FriendlyByteBuf, RouteGuidanceStartPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.routeId());
            buf.writeVarInt(payload.durationTicks());
            buf.writeVarInt(payload.points().size());
            for (BlockPos point : payload.points()) {
                buf.writeBlockPos(point);
            }
        },
        buf -> {
            String routeId = buf.readUtf();
            int durationTicks = buf.readVarInt();
            int count = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                points.add(buf.readBlockPos().immutable());
            }
            return new RouteGuidanceStartPayload(routeId, points, durationTicks);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteGuidanceStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RouteGuidanceStartPayload payload) {
        com.stardew.craft.client.route.RouteGuidanceClientState.start(payload.routeId(), payload.points(), payload.durationTicks());
    }
}
