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
 * C→S: Client requests depositing the carried (cursor) item
 * into a bundle ingredient slot.
 */
@SuppressWarnings("null")
public record BundleDepositPayload(
        int bundleId,
        int ingredientSlotIndex
) implements CustomPacketPayload {

    public static final Type<BundleDepositPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_deposit")
    );

    public static final StreamCodec<ByteBuf, BundleDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BundleDepositPayload::bundleId,
                    ByteBufCodecs.VAR_INT, BundleDepositPayload::ingredientSlotIndex,
                    BundleDepositPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundleDepositPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            StardewCommunityCenterActions.deposit(
                    sp, payload.bundleId, payload.ingredientSlotIndex);
        });
    }
}
