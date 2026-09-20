package com.stardew.craft.building.runtime;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class BuildingBlueprintItemGameTests {
    private BuildingBlueprintItemGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void successfulPlacementConsumesTheAuthoritativeHandSlot(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BlueprintConsumer"));
        UUID permit = UUID.randomUUID();
        ItemStack held = new ItemStack(PrefabDefinitions.blueprintItem(UtilityBuildings.SILO));
        BuildingBlueprintItem.bind(held, permit);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        // Simulate an interaction path that supplied a copy rather than the inventory reference.
        BuildingBlueprintItem.consumePlacedBlueprint(player, InteractionHand.MAIN_HAND, held.copy());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Successful placement left the main-hand blueprint");

        ItemStack offhand = new ItemStack(PrefabDefinitions.blueprintItem(UtilityBuildings.SILO));
        BuildingBlueprintItem.bind(offhand, UUID.randomUUID());
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        BuildingBlueprintItem.consumePlacedBlueprint(player, InteractionHand.OFF_HAND, offhand.copy());
        helper.assertTrue(player.getOffhandItem().isEmpty(), "Successful placement left the off-hand blueprint");
        helper.succeed();
    }
}
