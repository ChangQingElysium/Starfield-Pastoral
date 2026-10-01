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
 * Server → Client: open Robin's question menu (Build / Shop / Leave).
 * SDV parity: GameLocation.carpenter() → createQuestionDialogue
 */
@SuppressWarnings("null")
public record OpenRobinMenuPayload() implements CustomPacketPayload {

    public static final Type<OpenRobinMenuPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_robin_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenRobinMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new OpenRobinMenuPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRobinMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenRobinMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // SDV Robin menu: 建造农场建筑 / 商店 / 离开
        List<Component> responses = List.of(
            Component.translatable("stardewcraft.robin.menu.build"),
            Component.translatable("stardewcraft.robin.menu.shop"),
            Component.translatable("pet.stardewcraft.buy_bowl"),
            Component.translatable("stardewcraft.robin.menu.leave")
        );

        Component question = Component.translatable("stardewcraft.robin.menu.question");

        com.stardew.craft.client.gui.common.StardewQuestionDialogSpec spec = com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
            question,
            responses,
            choiceIndex -> PacketDistributor.sendToServer(new RobinActionPayload(choiceIndex == 2 ? 3 : choiceIndex == 3 ? 2 : choiceIndex)),
            3 // default = Leave
        );

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(spec));
    }
}
