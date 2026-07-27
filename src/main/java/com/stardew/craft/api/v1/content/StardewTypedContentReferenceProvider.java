package com.stardew.craft.api.v1.content;

import java.util.Collection;

/**
 * Extracts cross-system references from a registered typed payload.
 *
 * @param <T> the same payload type used by its codec
 */
@FunctionalInterface
public interface StardewTypedContentReferenceProvider<T> {
    Collection<StardewContentReference> references(
            StardewContentKey owner,
            T data
    );
}
