package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record OpenFlowerDancePlayerInvitePayload(UUID inviteId, String senderName) implements CustomPacketPayload {
    public static final Type<OpenFlowerDancePlayerInvitePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_flower_dance_player_invite"));

    public static final StreamCodec<FriendlyByteBuf, OpenFlowerDancePlayerInvitePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.inviteId());
            buf.writeUtf(payload.senderName(), 64);
        },
        buf -> new OpenFlowerDancePlayerInvitePayload(buf.readUUID(), buf.readUtf(64))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFlowerDancePlayerInvitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFlowerDancePlayerInvitePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component question = Component.translatable("message.stardewcraft.festival.flower_dance.player_invite", payload.senderName());
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                List.of(
                    Component.translatable("message.stardewcraft.festival.confirm.yes"),
                    Component.translatable("message.stardewcraft.festival.confirm.no")
                ),
                index -> PacketDistributor.sendToServer(new FlowerDancePlayerInviteResponsePayload(payload.inviteId(), index == 0)),
                -1
            )
        ));
    }
}
