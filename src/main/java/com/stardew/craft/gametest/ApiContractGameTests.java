package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.combat.equipment.EquipmentSlotResolver;
import com.stardew.craft.player.PlayerStardewData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime acceptance tests for contracts which require live registries. */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ApiContractGameTests {
    private ApiContractGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void equipmentStackRoundTrip(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.DIAMOND, 16);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("API component sentinel"));

        PlayerStardewData original = new PlayerStardewData(UUID.randomUUID());
        original.setEquippedLeftRingStack(source);
        CompoundTag saved = original.toNBT(helper.getLevel().registryAccess());
        PlayerStardewData loaded = PlayerStardewData.fromNBT(
                saved, UUID.randomUUID(), helper.getLevel().registryAccess());

        ItemStack restored = loaded.getEquippedLeftRingStack();
        helper.assertTrue(restored.is(Items.DIAMOND), "equipment item changed during NBT round trip");
        helper.assertValueEqual(restored.getCount(), 1, "equipment slot must contain one item");
        helper.assertValueEqual(restored.get(DataComponents.CUSTOM_NAME),
                Component.literal("API component sentinel"), "equipment component was lost");
        helper.assertValueEqual(source.getCount(), 16, "equipment setter mutated the source stack");

        restored.setCount(0);
        helper.assertValueEqual(loaded.getEquippedLeftRingStack().getCount(), 1,
                "equipment getter leaked its internal stack");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void legacyEquipmentIdsMigrate(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("EquippedLeftRing", "minecraft:diamond");
        legacy.putString("EquippedRightRing", "minecraft:emerald");
        legacy.putString("EquippedBoots", "minecraft:iron_boots");

        PlayerStardewData loaded = PlayerStardewData.fromNBT(
                legacy, UUID.randomUUID(), helper.getLevel().registryAccess());

        helper.assertTrue(loaded.getEquippedLeftRingStack().is(Items.DIAMOND),
                "legacy left ring ID did not migrate");
        helper.assertTrue(loaded.getEquippedRightRingStack().is(Items.EMERALD),
                "legacy right ring ID did not migrate");
        helper.assertTrue(loaded.getEquippedBootsStack().is(Items.IRON_BOOTS),
                "legacy boots ID did not migrate");
        CompoundTag rewritten = loaded.toNBT(helper.getLevel().registryAccess());
        helper.assertTrue(rewritten.contains("EquippedLeftRingStack", CompoundTag.TAG_COMPOUND),
                "migrated equipment was not written in the full-stack format");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void playerProfileRoundTripAndLegacyDetection(GameTestHelper helper) {
        PlayerStardewData legacy = PlayerStardewData.fromNBT(new CompoundTag(), UUID.randomUUID());
        helper.assertFalse(legacy.isProfileComplete(), "legacy save was not marked for profile collection");

        PlayerStardewData original = new PlayerStardewData(UUID.randomUUID());
        original.setProfile("Farmer", "strawberries", 1);
        PlayerStardewData loaded = PlayerStardewData.fromNBT(original.toNBT(), UUID.randomUUID());
        helper.assertTrue(loaded.isProfileComplete(), "completed player profile was lost");
        helper.assertFalse(loaded.isMale(), "female gender changed during NBT round trip");
        helper.assertValueEqual(loaded.getPreferredName(), "Farmer", "preferred name changed");
        helper.assertValueEqual(loaded.getFavoriteThing(), "strawberries", "favorite thing changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonEquipmentSlotControlsGameplayResolver(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "brick_ring");
        try {
            StardewEquipmentDataApi.registerProvider(providerId, 1000, stack ->
                    stack.is(Items.BRICK) ? ringData() : null);
        } catch (IllegalStateException duplicateOnRerun) {
            // The development server may rerun the same test registry without restarting the JVM.
        }

        helper.assertTrue(EquipmentSlotResolver.isRing(new ItemStack(Items.BRICK)),
                "public equipment slot was not consumed by the gameplay resolver");
        helper.assertFalse(EquipmentSlotResolver.isBoots(new ItemStack(Items.BRICK)),
                "public equipment slot resolved to the wrong gameplay slot");
        helper.succeed();
    }

    private static StardewEquipmentData ringData() {
        return new StardewEquipmentData(
                EquipmentSlotResolver.RING,
                1, 0, 0, 0.0F, 0.0F, 0, 0.0F, 0.0F, 0,
                List.of(), Optional.empty());
    }
}
