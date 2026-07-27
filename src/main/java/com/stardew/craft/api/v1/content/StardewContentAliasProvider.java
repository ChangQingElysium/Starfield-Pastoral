package com.stardew.craft.api.v1.content;

import java.util.Collection;

/**
 * Supplies content aliases from one addon or reloadable domain snapshot.
 *
 * <p>The provider registration freezes at server start, while its returned
 * aliases are read again whenever the content catalog is invalidated.
 */
@FunctionalInterface
public interface StardewContentAliasProvider {
    Collection<StardewContentAlias> aliases();
}
