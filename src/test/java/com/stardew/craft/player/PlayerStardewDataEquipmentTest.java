package com.stardew.craft.player;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStardewDataEquipmentTest {
    private static final HolderLookup.Provider REGISTRIES = VanillaRegistries.createLookup();

    @Test
    void equipmentSlotsKeepACompleteIndependentStack() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        ItemStack source = new ItemStack(Items.IRON_BOOTS, 7);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("API boots"));

        data.setEquippedBootsStack(source);
        assertEquals(7, source.getCount());
        source.set(DataComponents.CUSTOM_NAME, Component.literal("mutated"));

        ItemStack equipped = data.getEquippedBootsStack();
        assertEquals(1, equipped.getCount());
        assertEquals(Component.literal("API boots"), equipped.get(DataComponents.CUSTOM_NAME));

        equipped.set(DataComponents.CUSTOM_NAME, Component.literal("outside mutation"));
        assertEquals(Component.literal("API boots"),
                data.getEquippedBootsStack().get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void allLegacyStringEquipmentSlotsMigrateAndKeepCompatibilityIds() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("EquippedLeftRing", "minecraft:diamond");
        legacy.putString("EquippedRightRing", "minecraft:emerald");
        legacy.putString("EquippedBoots", "minecraft:iron_boots");

        PlayerStardewData data = PlayerStardewData.fromNBT(legacy, UUID.randomUUID());

        assertTrue(data.getEquippedLeftRingStack().is(Items.DIAMOND));
        assertTrue(data.getEquippedRightRingStack().is(Items.EMERALD));
        assertTrue(data.getEquippedBootsStack().is(Items.IRON_BOOTS));
        assertEquals("minecraft:diamond", data.getEquippedLeftRing());
        assertEquals("minecraft:emerald", data.getEquippedRightRing());
        assertEquals("minecraft:iron_boots", data.getEquippedBoots());

        CompoundTag migrated = data.toNBT(REGISTRIES);
        assertEquals("minecraft:diamond", migrated.getString("EquippedLeftRing"));
        assertEquals("minecraft:emerald", migrated.getString("EquippedRightRing"));
        assertEquals("minecraft:iron_boots", migrated.getString("EquippedBoots"));
        assertTrue(migrated.contains("EquippedLeftRingStack", Tag.TAG_COMPOUND));
        assertTrue(migrated.contains("EquippedRightRingStack", Tag.TAG_COMPOUND));
        assertTrue(migrated.contains("EquippedBootsStack", Tag.TAG_COMPOUND));
    }

    @Test
    void newStackFormatWinsOverLegacyIdAndRoundTripsComponents() {
        PlayerStardewData sourceData = new PlayerStardewData(UUID.randomUUID());
        ItemStack source = new ItemStack(Items.DIAMOND_BOOTS, 4);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Kept components"));
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        sourceData.setEquippedBootsStack(source);

        CompoundTag saved = sourceData.toNBT(REGISTRIES);
        saved.putString("EquippedBoots", "minecraft:iron_boots");

        PlayerStardewData restored = PlayerStardewData.fromNBT(saved, UUID.randomUUID(), REGISTRIES);
        ItemStack restoredStack = restored.getEquippedBootsStack();
        assertTrue(restoredStack.is(Items.DIAMOND_BOOTS));
        assertEquals(1, restoredStack.getCount());
        assertEquals(Component.literal("Kept components"), restoredStack.get(DataComponents.CUSTOM_NAME));
        assertEquals(true, restoredStack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));

        restoredStack.set(DataComponents.CUSTOM_NAME, Component.literal("outside mutation"));
        assertEquals(Component.literal("Kept components"),
                restored.getEquippedBootsStack().get(DataComponents.CUSTOM_NAME));
        assertEquals(4, source.getCount());
        assertFalse(sourceData.getEquippedBootsStack().isEmpty());
    }
}
