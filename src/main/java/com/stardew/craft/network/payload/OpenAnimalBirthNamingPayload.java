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

@SuppressWarnings("null")
public record OpenAnimalBirthNamingPayload(
        long eventId,
        String parentName,
        String animalTypeId
) implements CustomPacketPayload {
    public static final Type<OpenAnimalBirthNamingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "open_animal_birth_naming"
            ));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAnimalBirthNamingPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    OpenAnimalBirthNamingPayload::eventId,
                    ByteBufCodecs.STRING_UTF8,
                    OpenAnimalBirthNamingPayload::parentName,
                    ByteBufCodecs.STRING_UTF8,
                    OpenAnimalBirthNamingPayload::animalTypeId,
                    OpenAnimalBirthNamingPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            OpenAnimalBirthNamingPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> openClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClient(OpenAnimalBirthNamingPayload payload) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.stardew.craft.client.gui.AnimalBirthNamingScreen(payload)
        );
    }
}
