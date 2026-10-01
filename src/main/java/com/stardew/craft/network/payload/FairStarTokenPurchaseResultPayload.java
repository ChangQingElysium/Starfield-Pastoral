package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.festival.FairStarTokenNumberSelectionScreen;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairStarTokenPurchaseResultPayload(boolean success) implements CustomPacketPayload {
    public static final Type<FairStarTokenPurchaseResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_star_token_purchase_result"));

    public static final StreamCodec<FriendlyByteBuf, FairStarTokenPurchaseResultPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.success()),
        buf -> new FairStarTokenPurchaseResultPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairStarTokenPurchaseResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(FairStarTokenPurchaseResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof FairStarTokenNumberSelectionScreen screen) {
            screen.handlePurchaseResult(payload.success());
        }
    }
}
