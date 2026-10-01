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
public record OpenDesertFestivalMarlonRatingPayload(int rating) implements CustomPacketPayload {
    public static final Type<OpenDesertFestivalMarlonRatingPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_desert_festival_marlon_rating"));

    public static final StreamCodec<FriendlyByteBuf, OpenDesertFestivalMarlonRatingPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.rating()),
        buf -> new OpenDesertFestivalMarlonRatingPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDesertFestivalMarlonRatingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenDesertFestivalMarlonRatingPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.desert_festival.marlon.submit_rating", payload.rating()),
                List.of(
                    Component.translatable("stardewcraft.ui.yes"),
                    Component.translatable("stardewcraft.ui.no")
                ),
                index -> {
                    if (index == 0) {
                        PacketDistributor.sendToServer(new DesertFestivalMarlonRatingClaimPayload());
                    }
                },
                -1
            )
        ));
    }
}
