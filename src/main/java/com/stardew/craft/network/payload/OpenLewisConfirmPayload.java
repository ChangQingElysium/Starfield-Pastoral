package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
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
import java.util.UUID;

@SuppressWarnings("null")
public record OpenLewisConfirmPayload(UUID requestId, int kind, String questionKey, List<String> args,
                                      String acceptKey, String rejectKey)
        implements CustomPacketPayload {
    public static final int KIND_MONEY_SHARE = 0;
    public static final int KIND_FARM_CANCEL = 1;
    public static final int KIND_MONEY_CONTRACT_CLAIM = 2;
    public static final int KIND_AUCTION_START = 3;
    public static final int KIND_AUCTION_CANCEL = 4;

    public static final Type<OpenLewisConfirmPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_lewis_confirm"));

    public static final StreamCodec<FriendlyByteBuf, OpenLewisConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.requestId());
            buf.writeVarInt(payload.kind());
            buf.writeUtf(payload.questionKey(), 256);
            buf.writeVarInt(payload.args().size());
            for (String arg : payload.args()) {
                buf.writeUtf(arg, 256);
            }
            buf.writeUtf(payload.acceptKey(), 256);
            buf.writeUtf(payload.rejectKey(), 256);
        },
        buf -> {
            UUID requestId = buf.readUUID();
            int kind = buf.readVarInt();
            String questionKey = buf.readUtf(256);
            int count = buf.readVarInt();
            java.util.ArrayList<String> args = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                args.add(buf.readUtf(256));
            }
            return new OpenLewisConfirmPayload(requestId, kind, questionKey, args, buf.readUtf(256), buf.readUtf(256));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLewisConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenLewisConfirmPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable(payload.questionKey(), payload.args().toArray()),
                List.of(Component.translatable(payload.acceptKey()), Component.translatable(payload.rejectKey())),
                index -> PacketDistributor.sendToServer(new LewisConfirmResponsePayload(payload.requestId(), payload.kind(), index == 0)),
                1
            )
        ));
    }
}
