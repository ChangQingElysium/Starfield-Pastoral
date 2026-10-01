package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record IncubatorClaimSubmitPayload(BlockPos incubatorPos, String customName) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<IncubatorClaimSubmitPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "incubator_claim_submit"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, IncubatorClaimSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.incubatorPos);
            buf.writeUtf(payload.customName, 128);
        },
        buf -> new IncubatorClaimSubmitPayload(buf.readBlockPos(), buf.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(IncubatorClaimSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            var be = serverPlayer.serverLevel().getBlockEntity(payload.incubatorPos());
            if (!(be instanceof IncubatorBlockEntity incubator)) {
                serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.failed"));
                return;
            }

            IncubatorBlockEntity.ClaimResult result = incubator.claimReadyAnimal(serverPlayer, payload.customName());
            switch (result) {
                case SUCCESS -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.success"));
                case NOT_READY -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.not_ready"));
                case NOT_IN_BUILDING -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.invalid_building"));
                case NOT_OWNER -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.not_owner"));
                case INVALID_BUILDING -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.invalid_building"));
                case BUILDING_FULL -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.building_full"));
                case INVALID_EGG -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.invalid_egg"));
                case NAME_DUPLICATE -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.animal.purchase.name_duplicate"));
                default -> serverPlayer.sendSystemMessage(Component.translatable("stardewcraft.incubator.claim.failed"));
            }
        });
    }
}
