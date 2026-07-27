package com.stardew.craft.network;

import com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalPurchasePayloadCodecTest {
    @Test
    void dataDefinedPresentationMetadataRoundTrips() {
        OpenAnimalPurchaseScreenPayload expected =
                OpenAnimalPurchaseScreenPayload.normal(
                        12_345,
                        List.of(new OpenAnimalPurchaseScreenPayload.AnimalOption(
                                "example:goose",
                                "Goose",
                                "coop",
                                2,
                                900,
                                true,
                                "example.animal.shop.desc.goose",
                                "example.animal.shop.lock.goose",
                                "example:textures/gui/goose.png",
                                48,
                                24,
                                "example:goose"
                        )),
                        List.of(new OpenAnimalPurchaseScreenPayload.BuildingOption(
                                "coop-7",
                                "North Coop",
                                "coop",
                                2,
                                3,
                                8
                        ))
                );
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(
                        Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            OpenAnimalPurchaseScreenPayload.STREAM_CODEC.encode(
                    buffer, expected);
            assertEquals(
                    expected,
                    OpenAnimalPurchaseScreenPayload.STREAM_CODEC.decode(
                            buffer));
        } finally {
            buffer.release();
        }
    }
}
