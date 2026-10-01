package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: result of a carpenter purchase attempt.
 */
@SuppressWarnings("null")
public record CarpenterPurchaseResultPayload(
    boolean success,
    int     newMoney,
    String  resultItemId,
    int     blueprintIndex,
    java.util.UUID requestId
) implements CustomPacketPayload {

    public CarpenterPurchaseResultPayload(boolean success, int newMoney, String resultItemId, int blueprintIndex) {
        this(success, newMoney, resultItemId, blueprintIndex, new java.util.UUID(0, 0));
    }

    public static final Type<CarpenterPurchaseResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "carpenter_purchase_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarpenterPurchaseResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        CarpenterPurchaseResultPayload::success,
            ByteBufCodecs.INT,         CarpenterPurchaseResultPayload::newMoney,
            ByteBufCodecs.STRING_UTF8, CarpenterPurchaseResultPayload::resultItemId,
            ByteBufCodecs.INT,         CarpenterPurchaseResultPayload::blueprintIndex,
            com.stardew.craft.port.PortCodecs.UUID, CarpenterPurchaseResultPayload::requestId,
            CarpenterPurchaseResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarpenterPurchaseResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(CarpenterPurchaseResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.building.BuildingRoutesScreen routes) routes.result(payload);
        else if (mc.screen instanceof com.stardew.craft.client.gui.CarpenterMenuScreen screen) {
            screen.onPurchaseResult(payload);
        }
    }
}
