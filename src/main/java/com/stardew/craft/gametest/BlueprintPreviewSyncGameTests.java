package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.building.runtime.BuildingBlueprintItem;
import com.stardew.craft.building.runtime.BuildingDrafts;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_blueprint_sync")
@PrefixGameTestTemplate(false)
public final class BlueprintPreviewSyncGameTests {
    @GameTest(template = "empty")
    public static void everyBlueprintHydratesMainHandOffhandAndReconnectedDocuments(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BlueprintSync"));
        var ground = new BlockPos(30, 64, 30);
        int count = 0;
        for (var item : BuiltInRegistries.ITEM.stream().filter(BuildingBlueprintItem.class::isInstance)
                .map(BuildingBlueprintItem.class::cast).toList()) {
            h.assertTrue(PrefabDefinitions.available(item.family()), "Missing fixture definition: " + item.family());
            count++;
            for (var hand : InteractionHand.values()) for (var facing : Direction.Plane.HORIZONTAL) {
                player.getInventory().clearContent();
                var stack = new ItemStack(item);
                var permit = UUID.randomUUID(); BuildingBlueprintItem.bind(stack, permit);
                var tag = BuildingBlueprintItem.draft(stack); tag.putString("DraftFacing", facing.getName());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                // Persist/reload an existing document with no newly introduced preview data.
                stack = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
                player.setItemInHand(hand, stack);
                h.assertTrue(BuildingBlueprintItem.previewTargetAnchor(stack, ground, facing) == null, "Old document should wait for sync");
                item.inventoryTick(stack, h.getLevel(), player, hand == InteractionHand.MAIN_HAND ? 0 : 40, hand == InteractionHand.MAIN_HAND);
                var expected = BuildingBlueprintItem.targetAnchor(stack, ground, facing);
                h.assertTrue(expected.equals(BuildingBlueprintItem.previewTargetAnchor(stack, ground, facing)), "Wrong synchronized anchor: " + item.family() + " " + hand);
                h.assertTrue(permit.equals(BuildingBlueprintItem.permit(stack)), "Sync changed the purchased permit");
                h.assertTrue(BuildingBlueprintItem.facing(stack) == facing, "Sync reset document rotation");
                var component = stack.get(DataComponents.CUSTOM_DATA);
                item.inventoryTick(stack, h.getLevel(), player, 0, hand == InteractionHand.MAIN_HAND);
                h.assertTrue(component == stack.get(DataComponents.CUSTOM_DATA), "Unchanged geometry rewrites inventory every tick");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
                try {
                    com.stardew.craft.port.PortCodecs.ITEM_STACK.encode(buffer, stack);
                    var received = com.stardew.craft.port.PortCodecs.ITEM_STACK.decode(buffer);
                    h.assertTrue(expected.equals(BuildingBlueprintItem.previewTargetAnchor(received, ground, facing)), "Inventory network sync lost geometry");
                } finally { buffer.release(); }
                // A stale/custom item component must never influence authoritative placement.
                tag = BuildingBlueprintItem.draft(stack); tag.putInt("BlueprintPreviewDepth", 999);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                h.assertTrue(expected.equals(BuildingBlueprintItem.targetAnchor(stack, ground, facing)), "Server trusted client preview geometry");
                item.inventoryTick(stack, h.getLevel(), player, 0, hand == InteractionHand.MAIN_HAND);
                h.assertTrue(expected.equals(BuildingBlueprintItem.previewTargetAnchor(stack, ground, facing)), "Stale metadata was not refreshed");
                BuildingDrafts.get(player.server).write(stack);
                BuildingDrafts.get(player.server).apply(stack);
                h.assertTrue(expected.equals(BuildingBlueprintItem.previewTargetAnchor(stack, ground, facing)), "Draft synchronization removed preview data");
                BuildingDrafts.get(player.server).consume(stack);
            }
        }
        h.assertTrue(count >= 7, "Not all registered blueprints were exercised");
        player.getInventory().clearContent();
        h.succeed();
    }
}
