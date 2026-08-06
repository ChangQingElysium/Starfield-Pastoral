package com.stardew.craft.config;

/**
 * Shared fixed stack-size policy used by regular code and mixins.
 *
 * <p>This lives outside the mixin package so regular mod code and mixins can
 * both reference it safely on NeoForge and hybrid servers such as Mohist.</p>
 */
public final class StackSizeHolder {
    private static final int MAX_STACK_SIZE = 999;

    public static int get() {
        return MAX_STACK_SIZE;
    }

    private StackSizeHolder() {
    }
}
