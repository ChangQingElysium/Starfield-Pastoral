package com.stardew.craft.api.v1.specialorder;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Generic server event offered to add-on special-order objective types. */
public record SpecialOrderProgressEvent(
        Kind kind,
        ItemStack stack,
        int amount,
        @Nullable String target
) {
    public enum Kind {
        ITEM_RECEIVED,
        FISH_CAUGHT,
        ITEM_SHIPPED,
        NPC_DELIVERY,
        MONSTER_SLAIN,
        DROP_BOX_DONATION
    }

    public SpecialOrderProgressEvent {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        amount = Math.max(0, amount);
    }
}
