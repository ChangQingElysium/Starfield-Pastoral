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
public record OpenMoneyContractActionPayload(UUID targetId, String targetName) implements CustomPacketPayload {
    public static final Type<OpenMoneyContractActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_money_contract_action"));

    public static final StreamCodec<FriendlyByteBuf, OpenMoneyContractActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.targetId());
            buf.writeUtf(payload.targetName(), 128);
        },
        buf -> new OpenMoneyContractActionPayload(buf.readUUID(), buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMoneyContractActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenMoneyContractActionPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.money_contract.action.question", payload.targetName()),
                List.of(
                    Component.translatable("stardewcraft.money_contract.action.share"),
                    Component.translatable("stardewcraft.money_contract.action.transfer"),
                    Component.translatable("stardewcraft.money_contract.action.cancel")
                ),
                index -> {
                    if (index >= 0 && index < 2) {
                        PacketDistributor.sendToServer(new MoneyContractActionPayload(payload.targetId(), index));
                    }
                },
                -1
            )
        ));
    }
}
