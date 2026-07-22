package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client: original Gold Clock on/off confirmation. */
@SuppressWarnings("null")
public record OpenGoldClockQuestionPayload(BlockPos clockPos, boolean currentlyEnabled)
        implements CustomPacketPayload {
    public static final Type<OpenGoldClockQuestionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_gold_clock_question"));
    public static final StreamCodec<FriendlyByteBuf, OpenGoldClockQuestionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.clockPos());
                buf.writeBoolean(payload.currentlyEnabled());
            },
            buf -> new OpenGoldClockQuestionPayload(buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGoldClockQuestionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> openClientScreen(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientScreen(OpenGoldClockQuestionPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(StardewConfirmDialogScreen.createQuestionDialog(
                StardewQuestionDialogSpec.of(
                        Component.translatable(payload.currentlyEnabled()
                                ? "message.stardewcraft.gold_clock.confirm_off"
                                : "message.stardewcraft.gold_clock.confirm_on"),
                        List.of(Component.translatable("gui.yes"), Component.translatable("gui.no")),
                        answer -> PacketDistributor.sendToServer(
                                new GoldClockQuestionResponsePayload(payload.clockPos(), answer == 0)),
                        -1)));
    }
}
