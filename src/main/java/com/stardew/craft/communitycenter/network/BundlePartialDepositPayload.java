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
 * C→S: Client requests partial deposit of the carried item into a bundle.
 * SDV parity: HandlePartialDonation — allows donating less than the required stack count.
 * The partial donation accumulates in the menu until the full count is reached.
 */
@SuppressWarnings("null")
public record BundlePartialDepositPayload(
        int bundleId,
        int ingredientIndex,
        int amount
) implements CustomPacketPayload {

    public static final Type<BundlePartialDepositPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_partial_deposit")
    );

    public static final StreamCodec<ByteBuf, BundlePartialDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BundlePartialDepositPayload::bundleId,
                    ByteBufCodecs.VAR_INT, BundlePartialDepositPayload::ingredientIndex,
                    ByteBufCodecs.VAR_INT, BundlePartialDepositPayload::amount,
                    BundlePartialDepositPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundlePartialDepositPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            StardewCommunityCenterActions.partialDeposit(
                    sp, payload.bundleId, payload.ingredientIndex, payload.amount);
        });
    }
}
