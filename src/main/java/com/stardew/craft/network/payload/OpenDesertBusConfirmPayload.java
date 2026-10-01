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

/**
 * Server → Client: open the "buy a desert bus ticket" confirm dialog.
 * Mirrors SDV {@code Strings\Locations:BusStop_BuyTicketToDesert}.
 */
@SuppressWarnings("null")
public record OpenDesertBusConfirmPayload(int price) implements CustomPacketPayload {

    public static final Type<OpenDesertBusConfirmPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_desert_bus_confirm"));

    public static final StreamCodec<FriendlyByteBuf, OpenDesertBusConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> buf.writeVarInt(p.price()),
        buf -> new OpenDesertBusConfirmPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDesertBusConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenDesertBusConfirmPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        Component question = payload.price() <= 0
            ? Component.translatable("message.stardewcraft.desert_bus.confirm_return")
            : Component.translatable("message.stardewcraft.desert_bus.confirm", payload.price());

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                List.of(
                    Component.translatable("message.stardewcraft.desert_bus.confirm.yes"),
                    Component.translatable("message.stardewcraft.desert_bus.confirm.no")
                ),
                index -> PacketDistributor.sendToServer(new DesertBusConfirmPayload(index == 0)),
                -1
            )
        ));
    }
}
