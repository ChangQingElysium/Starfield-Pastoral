package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Emergency terminal state when an overnight settlement fails server-side. */
public record OvernightCollapseCancelPayload() implements CustomPacketPayload {
    public static final Type<OvernightCollapseCancelPayload> TYPE =
            new Type<>(new ResourceLocation(
                    StardewCraft.MODID, "overnight_collapse_cancel"));
    public static final StreamCodec<ByteBuf, OvernightCollapseCancelPayload> STREAM_CODEC =
            StreamCodec.unit(new OvernightCollapseCancelPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightCollapseCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(OvernightCollapseCancelPayload::handleClient);
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        OvernightCollapseClientState.cancel();
    }
}
