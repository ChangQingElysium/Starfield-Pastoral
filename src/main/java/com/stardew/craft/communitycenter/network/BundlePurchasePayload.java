package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S: Client requests purchasing a Vault bundle (money-only).
 */
@SuppressWarnings("null")
public record BundlePurchasePayload(
        int bundleId
) implements CustomPacketPayload {

    public static final Type<BundlePurchasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_purchase")
    );

    public static final StreamCodec<ByteBuf, BundlePurchasePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BundlePurchasePayload::bundleId,
                    BundlePurchasePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundlePurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            StardewCommunityCenterActions.purchaseVault(sp, payload.bundleId);
        });
    }
}
