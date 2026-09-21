package com.stardew.craft.item.artisan;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class DehydratorIngredientHelper {
	private static final Set<String> MUSHROOM_ITEMS = Set.of(
			"common_mushroom",
			"red_mushroom",
			"purple_mushroom",
			"morel",
			"chanterelle",
			"magma_cap"
	);

	private DehydratorIngredientHelper() {
	}

	public static boolean isFruitCrop(ResourceLocation id) {
		return PreservesCropTypeHelper.getCropPreserveType(id) == PreserveType.JELLY;
	}

	public static boolean isMushroom(ResourceLocation id) {
		if (id == null) {
			return false;
		}
		String path = id.getPath();
		if (MUSHROOM_ITEMS.contains(path)) {
			return true;
		}
		return path.contains("mushroom");
	}
}
