package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentSlotResolver;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Resolves built-in and public data-driven Stardew weapon identity once. */
public final class WeaponCombatIdentity {
    private WeaponCombatIdentity() {
    }

    public static boolean isWeapon(ItemStack stack) {
        return resolve(stack).isPresent();
    }

    public static Optional<Resolved> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof IStardewWeapon weapon) {
            ResourceLocation id = builtInId(weapon.getWeaponId());
            return Optional.of(new Resolved(id, id.getPath(), true));
        }
        if (!EquipmentSlotResolver.isWeapon(stack)) {
            return Optional.empty();
        }
        if (WeaponStats.fromItemStack(stack).getWeaponType()
                == WeaponType.SLINGSHOT) {
            return Optional.empty();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                stack.getItem()
        );
        return Optional.of(new Resolved(itemId, itemId.toString(), false));
    }

    public static Resolved require(ItemStack stack) {
        return resolve(stack).orElseThrow(
                () -> new IllegalArgumentException(
                        "Item stack is not a Stardew weapon"
                )
        );
    }

    private static ResourceLocation builtInId(String weaponId) {
        if (weaponId != null && weaponId.contains(":")) {
            ResourceLocation parsed = ResourceLocation.tryParse(weaponId);
            if (parsed != null) {
                return parsed;
            }
        }
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                weaponId == null || weaponId.isBlank()
                        ? "unknown_weapon"
                        : weaponId
        );
    }

    public record Resolved(
            ResourceLocation id,
            String logicId,
            boolean builtIn
    ) {
    }
}
