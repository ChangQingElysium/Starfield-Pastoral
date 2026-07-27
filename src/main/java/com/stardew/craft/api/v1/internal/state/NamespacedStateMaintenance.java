package com.stardew.craft.api.v1.internal.state;

import net.minecraft.commands.CommandSourceStack;

import java.util.Objects;

/** Unforgeable operator authority for destructive state maintenance. */
public final class NamespacedStateMaintenance {
    private NamespacedStateMaintenance() {
    }

    public static Authority authorize(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        if (!source.hasPermission(3)) {
            throw new SecurityException(
                    "Namespaced-state maintenance requires permission 3");
        }
        return new Authority();
    }

    public static void require(Authority authority) {
        Objects.requireNonNull(authority, "authority");
    }

    public static final class Authority {
        Authority() {
        }
    }
}
