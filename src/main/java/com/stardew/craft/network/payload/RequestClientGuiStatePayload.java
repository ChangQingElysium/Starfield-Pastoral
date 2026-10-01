package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server probe used before fullscreen item feedback is allowed to play. */
public record RequestClientGuiStatePayload() implements CustomPacketPayload {
    public static final Type<RequestClientGuiStatePayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "request_client_gui_state"));
    public static final StreamCodec<ByteBuf, RequestClientGuiStatePayload> STREAM_CODEC =
            StreamCodec.unit(new RequestClientGuiStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestClientGuiStatePayload payload, IPayloadContext context) {
        context.enqueueWork(RequestClientGuiStatePayload::handleClient);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient() {
        com.stardew.craft.client.StardewPauseClientState.reportNow();
    }
}
