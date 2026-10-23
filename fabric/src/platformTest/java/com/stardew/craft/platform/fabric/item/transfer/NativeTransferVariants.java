package com.stardew.craft.platform.fabric.item.transfer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.impl.transfer.fluid.FluidVariantImpl;
import net.fabricmc.fabric.impl.transfer.item.ItemVariantImpl;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * Native values for the plain JVM tests, which do not load Fabric Mixins.
 * These are the exact public implementation constructors used by FAPI's Item
 * and Fluid cache Mixins, with real registered objects and an empty patch.
 * No production code, registry, component equality or transaction is replaced.
 */
public final class NativeTransferVariants {
    private NativeTransferVariants() {}
    public static ItemVariant item(Item item) {
        return new ItemVariantImpl(item, DataComponentPatch.EMPTY);
    }
    public static FluidVariant fluid(Fluid fluid) {
        return new FluidVariantImpl(fluid, DataComponentPatch.EMPTY);
    }
}
