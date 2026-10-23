package com.stardew.craft.platform.fabric.client.extension;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientItemExtensionsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void sleeveArmorColorsKeepTheDyeableFallbackAndOpaqueUndyedLayers() {
        var stack = new ItemStack(Items.APPLE);
        var texture = ResourceLocation.withDefaultNamespace("leather");
        var dyeable = new ArmorMaterial.Layer(texture, "", true);
        var normal = new ArmorMaterial.Layer(texture, "", false);
        int originalColor = 0xFF123456;
        assertEquals(0xFFFFFFFF, IClientItemExtensions.DEFAULT.getDefaultDyeColor(stack));
        assertEquals(originalColor, IClientItemExtensions.DEFAULT.getArmorLayerTintColor(stack, null, dyeable, 0, originalColor));
        assertEquals(0xFFFFFFFF, IClientItemExtensions.DEFAULT.getArmorLayerTintColor(stack, null, normal, 1, originalColor));
    }

    @Test
    void defaultHandAndFontCallbacksLeaveTheNativePathAvailableWithoutAccessingMinecraft() {
        assertFalse(IClientItemExtensions.DEFAULT.applyForgeHandTransform(null, null, HumanoidArm.LEFT, ItemStack.EMPTY, .25f, .5f, .75f));
        for (var context : IClientItemExtensions.FontContext.values())
            assertNull(IClientItemExtensions.DEFAULT.getFont(ItemStack.EMPTY, context));
    }
}
