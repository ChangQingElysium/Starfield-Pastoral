package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S: Client requests retrieving items from a partial donation.
 * SDV parity: ReturnPartialDonations / right-click retrieval.
 *
 * mode=0: Return all partial items (to cursor if possible, else inventory)
 * mode=1: Return 1 item to cursor (right-click)
 */
@SuppressWarnings("null")
public record BundlePartialRetrievePayload(
        int bundleId,
        int mode
) implements CustomPacketPayload {

    public static final int MODE_RETURN_ALL = 0;
    public static final int MODE_RETURN_ONE = 1;

    public static final Type<BundlePartialRetrievePayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "bundle_partial_retrieve")
    );

    public static final StreamCodec<ByteBuf, BundlePartialRetrievePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BundlePartialRetrievePayload::bundleId,
                    ByteBufCodecs.VAR_INT, BundlePartialRetrievePayload::mode,
                    BundlePartialRetrievePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundlePartialRetrievePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof BundleMenu menu)) return;

            if (payload.mode == MODE_RETURN_ONE) {
                menu.retrieveOneFromPartial(sp, payload.bundleId);
            } else {
                menu.returnAllPartials(sp, false);
            }
            sp.containerMenu.broadcastChanges();
        });
    }
}
