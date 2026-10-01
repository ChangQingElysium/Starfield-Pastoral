package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

@SuppressWarnings("null")
public record OpenFlowerDanceInvitePayload(String npcId) implements CustomPacketPayload {
    public static final Type<OpenFlowerDanceInvitePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_flower_dance_invite"));

    public static final StreamCodec<FriendlyByteBuf, OpenFlowerDanceInvitePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.npcId(), 64),
        buf -> new OpenFlowerDanceInvitePayload(buf.readUtf(64))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFlowerDanceInvitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFlowerDanceInvitePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component npcName = Component.translatable("entity.stardewcraft.npc." + payload.npcId());
        Component question = Component.translatable("message.stardewcraft.festival.flower_dance.invite_npc", npcName);
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                List.of(
                    Component.translatable("message.stardewcraft.festival.confirm.yes"),
                    Component.translatable("message.stardewcraft.festival.confirm.no")
                ),
                index -> PacketDistributor.sendToServer(new FlowerDanceInviteResponsePayload(payload.npcId(), index == 0)),
                -1
            )
        ));
    }
}
