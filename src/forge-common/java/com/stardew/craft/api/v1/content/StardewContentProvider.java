package com.stardew.craft.api.v1.content;

import java.util.Collection;

/** Supplies read-only content projections for one addon or subsystem. */
@FunctionalInterface
public interface StardewContentProvider {
    Collection<StardewContentDefinition> definitions();
}
