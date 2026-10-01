package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: reproduce MagicWarp's immediate white flash. */
public record MagicWarpFlashPayload(byte unused) implements CustomPacketPayload {
    public static final Type<MagicWarpFlashPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "magic_warp_flash"));

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

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        com.stardew.craft.communitycenter.cutscene.ScreenFade.startFlashWhite();
    }
}
