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

@SuppressWarnings("null")
public record OpenLewisMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenLewisMenuPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_lewis_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenLewisMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new OpenLewisMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLewisMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient());
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.lewis.menu.question"),
                List.of(
                    Component.translatable("stardewcraft.lewis.menu.contract"),
                    Component.translatable("stardewcraft.lewis.menu.auction_create"),
                    Component.translatable("stardewcraft.lewis.menu.auction_join"),
                    Component.translatable("stardewcraft.lewis.menu.auction_cancel"),
                    Component.translatable("stardewcraft.lewis.menu.cancel_farm"),
                    Component.translatable("stardewcraft.lewis.menu.lost_and_found"),
                    Component.translatable("stardewcraft.lewis.menu.leave")
                ),
                index -> {
                    if (index >= 0 && index < 6) {
                        PacketDistributor.sendToServer(new LewisCivicActionPayload(index));
                    }
                },
                -1
            ).withSoundTheme(com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.SoundTheme.MONEY_CONTRACT)
        ));
    }
}
