package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: result of a Furniture Catalogue purchase.
 */
@SuppressWarnings("null")
public record FurnitureCatalogueResultPayload(
    boolean success,
    int     newMoney,
    String  itemId,
    int     quantity
) implements CustomPacketPayload {

    public static final Type<FurnitureCatalogueResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "furniture_catalogue_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FurnitureCatalogueResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,        FurnitureCatalogueResultPayload::success,
            ByteBufCodecs.INT,         FurnitureCatalogueResultPayload::newMoney,
            ByteBufCodecs.STRING_UTF8, FurnitureCatalogueResultPayload::itemId,
            ByteBufCodecs.INT,         FurnitureCatalogueResultPayload::quantity,
            FurnitureCatalogueResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FurnitureCatalogueResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(FurnitureCatalogueResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.gui.FurnitureCatalogueScreen screen) {
            screen.onPurchaseResult(payload);
        }
    }
}
