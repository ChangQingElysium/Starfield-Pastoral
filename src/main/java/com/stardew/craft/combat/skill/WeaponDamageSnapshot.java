package com.stardew.craft.combat.skill;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable view of the weapon that released a skill.
 *
 * <p>The contained stack is always copied on ingress and egress so delayed
 * damage cannot observe later mutations to the player's inventory stack.</p>
 */
public final class WeaponDamageSnapshot {
    private final ResourceLocation weaponId;
    private final ItemStack weapon;

    private WeaponDamageSnapshot(ResourceLocation weaponId, ItemStack weapon) {
        this.weaponId = Objects.requireNonNull(weaponId, "weaponId");
        this.weapon = copyStack(Objects.requireNonNull(weapon, "weapon"));
    }

    public static WeaponDamageSnapshot capture(
            ResourceLocation weaponId,
            ItemStack weapon
    ) {
        return new WeaponDamageSnapshot(weaponId, weapon);
    }

    public ResourceLocation weaponId() {
        return weaponId;
    }

    /**
     * Returns a defensive copy suitable for damage calculation.
     */
    public ItemStack weapon() {
        return copyStack(weapon);
    }

    private static ItemStack copyStack(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }
}
