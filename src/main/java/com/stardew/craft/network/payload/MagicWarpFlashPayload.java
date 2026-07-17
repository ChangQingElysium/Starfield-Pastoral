package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: reproduce MagicWarp's immediate white flash. */
public record MagicWarpFlashPayload(byte unused) implements CustomPacketPayload {
    public static final Type<MagicWarpFlashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "magic_warp_flash"));

    public static final StreamCodec<FriendlyByteBuf, MagicWarpFlashPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(0),
            buf -> new MagicWarpFlashPayload(buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MagicWarpFlashPayload payload, IPayloadContext context) {
        context.enqueueWork(MagicWarpFlashPayload::handleClient);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        com.stardew.craft.communitycenter.cutscene.ScreenFade.startFlashWhite();
    }
}
