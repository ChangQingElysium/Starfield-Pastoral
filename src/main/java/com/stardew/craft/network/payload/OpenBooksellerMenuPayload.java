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

@SuppressWarnings("null")
public record OpenBooksellerMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenBooksellerMenuPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_bookseller_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenBooksellerMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            buf -> new OpenBooksellerMenuPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBooksellerMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient());
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        List<Component> responses = List.of(
                Component.translatable("stardewcraft.bookseller.menu.buy"),
                Component.translatable("stardewcraft.bookseller.menu.trade"),
                Component.translatable("stardewcraft.bookseller.menu.leave")
        );
        com.stardew.craft.client.gui.common.StardewQuestionDialogSpec spec =
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                        Component.translatable("stardewcraft.bookseller.menu.question"),
                        responses,
                        choiceIndex -> PacketDistributor.sendToServer(new BooksellerActionPayload(choiceIndex)),
                        2
                );
        minecraft.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(spec));
    }
}
