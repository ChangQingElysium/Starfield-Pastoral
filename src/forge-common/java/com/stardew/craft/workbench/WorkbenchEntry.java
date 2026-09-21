package com.stardew.craft.workbench;

import net.minecraft.resources.ResourceLocation;

/**
 * A single workbench recipe entry: what you get, at what cost.
 */
public record WorkbenchEntry(
    ResourceLocation itemId,
    String category,
    int cost,
    int outputCount,
    String namespace,
    String materialItemId
) {
    public WorkbenchEntry(ResourceLocation itemId, String category, int cost, int outputCount, String namespace) {
        this(itemId, category, cost, outputCount, namespace, null);
    }

    public String inputItemId(WorkbenchType type) {
        return materialItemId == null ? type.getInputItemId() : materialItemId;
    }

    public String displayCategory() {
        return "stardewcraft.workbench.cat." + category;
    }
}
