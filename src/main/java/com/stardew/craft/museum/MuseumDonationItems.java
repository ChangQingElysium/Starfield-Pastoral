package com.stardew.craft.museum;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import net.minecraft.world.item.ItemStack;

/** Shared classification for items accepted by the museum. */
public final class MuseumDonationItems {

    private static final String MINERAL = "stardewcraft.type.mineral";
    private static final String ARTIFACT = "stardewcraft.type.artifact";
    private static final String QUALITY_ARTIFACT = "stardewcraft.type.artifact_quality";

    private MuseumDonationItems() {}

    public static boolean isDonatable(ItemStack stack) {
        return isMineral(stack) || isArtifact(stack);
    }

    public static boolean isMineral(ItemStack stack) {
        return MINERAL.equals(typeKey(stack));
    }

    public static boolean isArtifact(ItemStack stack) {
        String typeKey = typeKey(stack);
        return ARTIFACT.equals(typeKey) || QUALITY_ARTIFACT.equals(typeKey);
    }

    private static String typeKey(ItemStack stack) {
        return StardewItemDataApi.getTypeKey(stack);
    }
}
