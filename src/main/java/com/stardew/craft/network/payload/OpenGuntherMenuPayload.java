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
 * Server → Client: show Gunther's question dialog.
 * If donation mode is active: "End Donation" / "Leave"
 * If donation mode is NOT active and player has items: "Donate" / "Leave"
 * If donation mode is NOT active and NO items: just show normal dialogue (fallthrough)
 */
@SuppressWarnings("null")
public record OpenGuntherMenuPayload(boolean donationActive, boolean hasDonatable) implements CustomPacketPayload {

    public static final Type<OpenGuntherMenuPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_gunther_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenGuntherMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.donationActive());
            buf.writeBoolean(payload.hasDonatable());
        },
        buf -> new OpenGuntherMenuPayload(buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGuntherMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenGuntherMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        List<Component> options = new ArrayList<>();

        if (payload.donationActive()) {
            // Donation mode is active: offer to end it
            options.add(Component.translatable("stardewcraft.npc.gunther.menu.end_donation"));
            options.add(Component.translatable("stardewcraft.npc.gunther.menu.leave"));

            mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                    Component.translatable("stardewcraft.npc.gunther.menu.question_donating"),
                    options,
                    index -> {
                        if (index == 0) {
                            // End donation
                            PacketDistributor.sendToServer(new GuntherMenuChoicePayload(1));
                        }
                        // index == 1 → Leave, do nothing
                    },
                    -1
                )
            ));
        } else if (payload.hasDonatable()) {
            // Has donatable items: offer to start donation
            options.add(Component.translatable("stardewcraft.npc.gunther.menu.donate"));
            options.add(Component.translatable("stardewcraft.npc.gunther.menu.leave"));

            mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                    Component.translatable("stardewcraft.npc.gunther.menu.question"),
                    options,
                    index -> {
                        if (index == 0) {
                            // Start donation
                            PacketDistributor.sendToServer(new GuntherMenuChoicePayload(0));
                        }
                        // index == 1 → Leave, do nothing
                    },
                    -1
                )
            ));
        }
        // If !donationActive && !hasDonatable: no menu shown, normal dialogue already handled
    }
}
