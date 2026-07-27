package com.stardew.craft.api.v1.item;

import java.util.List;

/** Supplies zero or more acquisition descriptions for a target item. */
@FunctionalInterface
public interface StardewAcquisitionSourceProvider {
    List<StardewAcquisitionSource> findSources(
            StardewAcquisitionContext context);
}
