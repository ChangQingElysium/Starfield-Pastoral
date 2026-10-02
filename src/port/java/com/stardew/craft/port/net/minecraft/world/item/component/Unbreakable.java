package com.stardew.craft.port.net.minecraft.world.item.component;

/** 1.21 {@code minecraft:unbreakable}; stored as {@code Unbreakable:1b} plus the HideFlags UNBREAKABLE bit. */
public record Unbreakable(boolean showInTooltip) {
    public Unbreakable withTooltip(boolean showInTooltip) {
        return new Unbreakable(showInTooltip);
    }
}
