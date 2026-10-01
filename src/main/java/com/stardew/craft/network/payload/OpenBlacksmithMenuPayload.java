package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: open the blacksmith question menu (Shop / Upgrade / Process / Leave).
 * SDV parity: GameLocation.blacksmith() → createQuestionDialogue
 */
@SuppressWarnings("null")
public record OpenBlacksmithMenuPayload(
    boolean hasGeode
) implements CustomPacketPayload {

    public static final Type<OpenBlacksmithMenuPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_blacksmith_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenBlacksmithMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.hasGeode()),
        buf -> new OpenBlacksmithMenuPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBlacksmithMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenBlacksmithMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // Build responses list matching SDV: Shop / Upgrade / Process (conditional) / Leave
        List<Component> responses = new ArrayList<>();
        responses.add(Component.translatable("stardewcraft.blacksmith.menu.shop"));
        responses.add(Component.translatable("stardewcraft.blacksmith.menu.upgrade"));
        if (payload.hasGeode()) {
            responses.add(Component.translatable("stardewcraft.blacksmith.menu.process"));
        }
        responses.add(Component.translatable("stardewcraft.blacksmith.menu.leave"));

        Component question = Component.translatable("stardewcraft.blacksmith.menu.question");

        com.stardew.craft.client.gui.common.StardewQuestionDialogSpec spec = com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
            question,
            responses,
            choiceIndex -> {
                // Remap index: if no geode, choice 2 = Leave (maps to server choice 3)
                int serverChoice;
                if (!payload.hasGeode() && choiceIndex >= 2) {
                    serverChoice = 3; // Leave
                } else {
                    serverChoice = choiceIndex;
                }
                PacketDistributor.sendToServer(new BlacksmithActionPayload(serverChoice));
            },
            responses.size() - 1 // default = Leave
        );

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(spec));
    }
}
