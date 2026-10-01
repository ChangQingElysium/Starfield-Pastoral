package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Confirms that a pre-2AM multiplayer exhaustion pass-out has returned to bed.
 *
 * <p>The shared day keeps running. The client finishes animation 293, removes
 * the black overlay, and remains prone/input-locked until the eventual
 * overnight settlement.</p>
 */
public record OvernightCollapseReturnToBedPayload(int settlementDay)
        implements CustomPacketPayload {
    public static final Type<OvernightCollapseReturnToBedPayload> TYPE =
            new Type<>(new ResourceLocation(
                    StardewCraft.MODID, "overnight_collapse_return_to_bed"));

    public static final StreamCodec<ByteBuf, OvernightCollapseReturnToBedPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    OvernightCollapseReturnToBedPayload::new,
                    OvernightCollapseReturnToBedPayload::settlementDay);

    public OvernightCollapseReturnToBedPayload {
        settlementDay = Math.max(1, settlementDay);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            OvernightCollapseReturnToBedPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OvernightCollapseReturnToBedPayload payload) {
        OvernightCollapseClientState.returnedToBed(payload.settlementDay());
    }
}
