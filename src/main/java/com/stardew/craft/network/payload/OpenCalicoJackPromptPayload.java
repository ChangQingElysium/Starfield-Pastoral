package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCalicoJackPromptPayload(boolean highStakes) implements CustomPacketPayload {
    public static final Type<OpenCalicoJackPromptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_calico_jack_prompt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCalicoJackPromptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.highStakes),
                    buf -> new OpenCalicoJackPromptPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCalicoJackPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> openClient(payload.highStakes));
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClient(boolean highStakes) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        String prefix = "stardewcraft.casino.calico_jack.";
        minecraft.setScreen(
                com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                        com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                                net.minecraft.network.chat.Component.translatable(
                                        prefix + (highStakes ? "prompt_high_stakes" : "prompt")),
                                highStakes
                                        ? java.util.List.of(
                                                net.minecraft.network.chat.Component.translatable(prefix + "play"),
                                                net.minecraft.network.chat.Component.translatable(prefix + "leave"))
                                        : java.util.List.of(
                                                net.minecraft.network.chat.Component.translatable(prefix + "play"),
                                                net.minecraft.network.chat.Component.translatable(prefix + "leave"),
                                                net.minecraft.network.chat.Component.translatable(prefix + "rules")),
                                choice -> {
                                    if (choice == 0) {
                                        PacketDistributor.sendToServer(new CasinoGameActionPayload(
                                                highStakes ? 1L : 0L,
                                                CasinoGameActionPayload.CALICO_START));
                                    } else if (!highStakes && choice == 2) {
                                        minecraft.setScreen(
                                                new com.stardew.craft.client.gui.common.StardewObjectDialogueScreen(
                                                        java.util.List.of(
                                                                net.minecraft.network.chat.Component.translatable(prefix + "rules_1"),
                                                                net.minecraft.network.chat.Component.translatable(prefix + "rules_2")
                                                        )
                                                )
                                        );
                                    }
                                },
                                -1
                        )
                )
        );
    }
}
