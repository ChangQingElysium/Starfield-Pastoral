package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

@SuppressWarnings("null")
public record OpenFairSlingshotGamePayload(int starTokens, boolean startImmediately)
    implements CustomPacketPayload {
    public static final Type<OpenFairSlingshotGamePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_slingshot_game"));

    public static final StreamCodec<ByteBuf, OpenFairSlingshotGamePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            OpenFairSlingshotGamePayload::starTokens,
            ByteBufCodecs.BOOL,
            OpenFairSlingshotGamePayload::startImmediately,
            OpenFairSlingshotGamePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairSlingshotGamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenFairSlingshotGamePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!payload.startImmediately()) {
            StardewQuestionDialogSpec spec = StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.fair.slingshot.prompt"),
                List.of(
                    Component.translatable("stardewcraft.fair.slingshot.play"),
                    Component.translatable("stardewcraft.fair.slingshot.leave")
                ),
                index -> {
                    if (index == 0) {
                        PacketDistributor.sendToServer(new FairSlingshotGameActionPayload(
                            FairSlingshotGameActionPayload.ACTION_START,
                            0
                        ));
                    }
                },
                -1
            );
            mc.setScreen(StardewConfirmDialogScreen.createQuestionDialog(spec));
            return;
        }
        mc.setScreen(new com.stardew.craft.client.gui.festival.FairSlingshotGameScreen(
            payload.starTokens()
        ));
    }
}
