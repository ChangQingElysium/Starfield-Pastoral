package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.event.DimensionEventHandler;
import com.stardew.craft.event.SleepInteractionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record SleepConfirmChoicePayload(boolean confirmed, int sleepMinute) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SleepConfirmChoicePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sleep_confirm_choice"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, SleepConfirmChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.confirmed());
            buf.writeVarInt(payload.sleepMinute());
        },
        buf -> new SleepConfirmChoicePayload(buf.readBoolean(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(SleepConfirmChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!payload.confirmed()) {
                SleepInteractionHandler.consumePendingBedPos(player);
                if (player.isSleeping()) {
                    player.stopSleepInBed(true, true);
                }
                return;
            }
            if (!DimensionEventHandler.requestSleepAdvance(
                    player, payload.sleepMinute())) {
                PacketDistributor.sendToPlayer(
                        player, new SleepFadeRestorePayload());
            }
        });
    }
}
