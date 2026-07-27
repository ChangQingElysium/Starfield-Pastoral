package com.stardew.craft.api.v1.agriculture;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/** Immutable actor and tool inputs for one authoritative crop harvest attempt. */
public record StardewCropHarvestContext(
        Source source,
        @Nullable ServerPlayer player,
        ItemStack tool,
        int farmingLevel,
        boolean forceToolHarvest
) {
    public StardewCropHarvestContext {
        source = Objects.requireNonNull(source, "source");
        tool = Objects.requireNonNull(tool, "tool").copy();
        if (farmingLevel < 0) {
            throw new IllegalArgumentException("Farming level cannot be negative");
        }
        if (source != Source.AUTOMATION && player == null) {
            throw new IllegalArgumentException(
                    "Player and tool crop harvests require a server player");
        }
    }

    @Override
    public ItemStack tool() {
        return tool.copy();
    }

    public enum Source {
        PLAYER,
        TOOL,
        AUTOMATION
    }
}
