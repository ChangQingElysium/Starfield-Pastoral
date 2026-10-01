package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.festival.FairStarTokenNumberSelectionScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenFairStarTokenNumberSelectionPayload(
    String questionJson,
    int price,
    int minValue,
    int maxValue,
    int defaultValue
) implements CustomPacketPayload {

    public static final Type<OpenFairStarTokenNumberSelectionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_fair_star_token_number_selection"));

    public static final StreamCodec<FriendlyByteBuf, OpenFairStarTokenNumberSelectionPayload> STREAM_CODEC = StreamCodec.of(
        OpenFairStarTokenNumberSelectionPayload::write,
        OpenFairStarTokenNumberSelectionPayload::read
    );

    private static void write(FriendlyByteBuf buf, OpenFairStarTokenNumberSelectionPayload payload) {
        buf.writeUtf(payload.questionJson(), 4096);
        buf.writeVarInt(payload.price());
        buf.writeVarInt(payload.minValue());
        buf.writeVarInt(payload.maxValue());
        buf.writeVarInt(payload.defaultValue());
    }

    private static OpenFairStarTokenNumberSelectionPayload read(FriendlyByteBuf buf) {
        return new OpenFairStarTokenNumberSelectionPayload(
            buf.readUtf(4096),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFairStarTokenNumberSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenFairStarTokenNumberSelectionPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Component question;
        try {
            Component parsed = Component.Serializer.fromJson(payload.questionJson());
            question = parsed == null ? Component.literal(payload.questionJson()) : parsed;
        } catch (Exception ignored) {
            question = Component.literal(payload.questionJson() == null ? "" : payload.questionJson());
        }
        mc.setScreen(new FairStarTokenNumberSelectionScreen(
            question,
            payload.price(),
            payload.minValue(),
            payload.maxValue(),
            payload.defaultValue()
        ));
    }
}
