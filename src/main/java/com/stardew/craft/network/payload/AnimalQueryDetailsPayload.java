package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authored text which cannot be represented by vanilla container integer data slots. */
public record AnimalQueryDetailsPayload(
        long animalId,
        String parentName
) implements CustomPacketPayload {
    public static final Type<AnimalQueryDetailsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "animal_query_details"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AnimalQueryDetailsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    AnimalQueryDetailsPayload::animalId,
                    ByteBufCodecs.STRING_UTF8,
                    AnimalQueryDetailsPayload::parentName,
                    AnimalQueryDetailsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            AnimalQueryDetailsPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> applyClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(
            AnimalQueryDetailsPayload payload
    ) {
        com.stardew.craft.client.AnimalQueryClientDetails.put(
                payload.animalId(), payload.parentName());
    }
}
