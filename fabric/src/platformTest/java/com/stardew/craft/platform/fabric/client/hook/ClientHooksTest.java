package com.stardew.craft.platform.fabric.client.hook;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.model.ExtendedBakedModel;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class ClientHooksTest {
    @Test
    void cameraFacadeCallsTheActualExtendedModelAndReturnsItsReplacement() {
        var calls = new AtomicInteger(); var pose = new PoseStack();
        var replacement = (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { BakedModel.class },
                (proxy, method, args) -> { throw new AssertionError("replacement should not be rendered here"); });
        var source = (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ExtendedBakedModel.class }, (proxy, method, args) -> {
            assertEquals("applyTransform", method.getName()); assertSame(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, args[0]);
            assertSame(pose, args[1]); assertEquals(true, args[2]); pose.translate(3, 4, 5); calls.incrementAndGet(); return replacement;
        });
        assertSame(replacement, ClientHooks.handleCameraTransforms(pose, source, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true));
        assertEquals(1, calls.get()); assertEquals(3, pose.last().pose().m30()); assertEquals(4, pose.last().pose().m31()); assertEquals(5, pose.last().pose().m32());
    }

    @Test
    void armorFallbackUsesTheProvidedMaterialLayerAndTheActualInnerOuterFlag() {
        var layer = new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("stardewcraft", "test_armor"), "overlay", true);
        assertNotEquals(layer.texture(false), layer.texture(true));
        for (boolean inner : new boolean[] { false, true })
            assertEquals(layer.texture(inner), ClientHooks.getArmorTexture(null, ItemStack.EMPTY, layer, inner, EquipmentSlot.CHEST));
    }
}
