package com.stardew.craft.api.v1.content;

import java.util.Collection;

/** Extracts static cross-system references for a runtime-only extension. */
@FunctionalInterface
public interface StardewContentReferenceProvider {
    Collection<StardewContentReference> references(
            StardewContentKey owner
    );
}
