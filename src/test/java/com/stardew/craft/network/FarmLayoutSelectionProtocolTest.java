package com.stardew.craft.network;

import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutPreview;
import com.stardew.craft.network.payload.FarmSelectionSubmitPayload;
import com.stardew.craft.network.payload.OpenFarmSelectionPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FarmLayoutSelectionProtocolTest {
    private static final ResourceLocation LAYOUT =
            ResourceLocation.fromNamespaceAndPath("protocol_test", "orchard");
    private static final ResourceLocation CABINS =
            ResourceLocation.fromNamespaceAndPath("protocol_test", "cabins");

    @Test
    void serverPreviewRoundTripsWithoutWorldGeometry() {
        OpenFarmSelectionPayload expected = new OpenFarmSelectionPayload(
                List.of(new StardewFarmLayoutPreview(
                        LAYOUT,
                        true,
                        Component.literal("Orchard"),
                        Component.literal("A safe client preview"),
                        ResourceLocation.fromNamespaceAndPath(
                                "protocol_test", "textures/gui/orchard.png"),
                        3,
                        List.of(StardewFarmLayoutConfigField.integer(
                                CABINS,
                                Component.literal("Cabins"),
                                Component.literal("Starter cabin count"),
                                1,
                                0,
                                4)))));

        RegistryFriendlyByteBuf buffer = buffer();
        try {
            OpenFarmSelectionPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected,
                    OpenFarmSelectionPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void selectedConfigurationRoundTripsWithLegacyConstructorDefault() {
        FarmSelectionSubmitPayload expected =
                new FarmSelectionSubmitPayload(
                        LAYOUT.toString(),
                        "Orchard Farm",
                        false,
                        "Farmer",
                        "Apples",
                        true,
                        Map.of(CABINS, "4"));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            FarmSelectionSubmitPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected,
                    FarmSelectionSubmitPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }

        assertEquals(Map.of(),
                new FarmSelectionSubmitPayload(
                        LAYOUT.toString(), "Farm", false,
                        "Farmer", "Apples", true)
                        .layoutConfiguration());
    }

    @Test
    void previewCatalogRejectsUnboundedLayoutCounts() {
        StardewFarmLayoutPreview preview = new StardewFarmLayoutPreview(
                LAYOUT,
                true,
                Component.literal("Orchard"),
                Component.empty(),
                ResourceLocation.fromNamespaceAndPath(
                        "protocol_test", "textures/gui/orchard.png"),
                1,
                List.of());
        ArrayList<StardewFarmLayoutPreview> layouts = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            layouts.add(preview);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new OpenFarmSelectionPayload(layouts));
    }

    @SuppressWarnings("deprecation")
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
