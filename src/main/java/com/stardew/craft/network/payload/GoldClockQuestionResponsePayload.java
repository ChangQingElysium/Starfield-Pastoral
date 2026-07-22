package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record GoldClockQuestionResponsePayload(BlockPos clockPos, boolean accepted)
        implements CustomPacketPayload {
    public static final Type<GoldClockQuestionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "gold_clock_question_response"));
    public static final StreamCodec<FriendlyByteBuf, GoldClockQuestionResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.clockPos());
                buf.writeBoolean(payload.accepted());
            },
            buf -> new GoldClockQuestionResponsePayload(buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GoldClockQuestionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.accepted() || !(context.player() instanceof ServerPlayer player)
                    || player.distanceToSqr(payload.clockPos().getX() + 0.5D,
                            payload.clockPos().getY() + 0.5D, payload.clockPos().getZ() + 0.5D) > 64.0D
                    || !FarmAreaProtectionEvents.canModifyAt(player, payload.clockPos())) {
                return;
            }
            if (player.level().getBlockEntity(payload.clockPos()) instanceof WizardBuildingBlockEntity clock
                    && clock.kind().isGoldClock()) {
                clock.toggleGoldClock(player);
            }
        });
    }
}
