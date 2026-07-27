package com.stardew.craft.item.artisan;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.machine.StardewArtisanResolverRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared smoked-output resolution used by gameplay and JEI. */
public final class SmokedOutputResolver {
    private SmokedOutputResolver() {
    }

    public static ItemStack resolve(ItemStack inputStack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(inputStack.getItem());
        if (id != null && StardewCraft.MODID.equals(id.getNamespace())) {
            ResourceLocation smokedId = ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "smoked_" + id.getPath());
            if (BuiltInRegistries.ITEM.containsKey(smokedId)) {
                Item smokedItem = BuiltInRegistries.ITEM.get(smokedId);
                if (smokedItem instanceof SmokedFishItem) {
                    return new ItemStack(smokedItem);
                }
            }
        }
        return StardewArtisanResolverRegistry.resolveSmokedOutput(inputStack);
    }
}
