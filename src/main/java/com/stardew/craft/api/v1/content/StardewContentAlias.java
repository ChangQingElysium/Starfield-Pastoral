package com.stardew.craft.api.v1.content;

import java.util.Objects;

/**
 * One historical or compatibility identity pointing at canonical content.
 *
 * <p>Aliases must stay within one content type. They affect catalog lookup
 * and reference diagnostics only; they do not replace the owning domain's
 * definition, runtime state or migration rules.
 */
public record StardewContentAlias(
        StardewContentKey alias,
        StardewContentKey target
) {
    public StardewContentAlias {
        alias = Objects.requireNonNull(alias, "alias");
        target = Objects.requireNonNull(target, "target");
    }
}
