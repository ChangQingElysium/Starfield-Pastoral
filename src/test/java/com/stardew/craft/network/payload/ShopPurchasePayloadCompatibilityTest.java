package com.stardew.craft.network.payload;

import com.stardew.craft.shop.ShopItemEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPurchasePayloadCompatibilityTest {
    @Test
    void legacyFairPurchaseInjectionPointKeepsItsDescriptor()
            throws NoSuchMethodException {
        var method = ShopPurchasePayload.class.getDeclaredMethod(
                "handleFairStarTokenPurchase",
                ServerPlayer.class,
                ShopPurchasePayload.class,
                ShopItemEntry.class,
                int.class,
                int.class
        );

        assertEquals(void.class, method.getReturnType());
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void optionalReplaySafeRequestRoundTripsWithoutChangingLegacyPayload() {
        ShopPurchaseRequestPayload expected =
                new ShopPurchaseRequestPayload(
                        UUID.randomUUID(), "SeedShop", 3,
                        "minecraft:apple", 5);
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(
                        Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ShopPurchaseRequestPayload.STREAM_CODEC.encode(
                    buffer, expected);
            ShopPurchaseRequestPayload decoded =
                    ShopPurchaseRequestPayload.STREAM_CODEC.decode(
                            buffer);

            assertEquals(expected, decoded);
            assertEquals(
                    new ShopPurchasePayload(
                            "SeedShop", 3,
                            "minecraft:apple", 5),
                    decoded.legacyRequest());
        } finally {
            buffer.release();
        }
    }
}
