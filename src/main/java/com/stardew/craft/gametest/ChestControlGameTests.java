package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.menu.WoodenChestMenu;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_chest_controls")
@PrefixGameTestTemplate(false)
public final class ChestControlGameTests {
    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void normalChestsUseTwelveColumnsAndKeepAllPlayerSlots(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var inventory = player.getInventory();
        inventory.clearContent();
        var wood = new com.stardew.craft.blockentity.WoodenChestBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.stardew.craft.block.ModBlocks.WOODEN_CHEST.get().defaultBlockState());
        var stone = new com.stardew.craft.blockentity.StoneChestBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.stardew.craft.block.ModBlocks.STONE_CHEST.get().defaultBlockState());
        for (var chest : new net.minecraft.world.Container[]{wood, stone}) {
            helper.assertTrue(chest.getContainerSize() == 36, "Normal chest must hold 36 stacks");
            var menu = chest == wood ? wood.createMenu(2, inventory, player) : stone.createMenu(3, inventory, player);
            var client = chest == wood ? WoodenChestMenu.storageClient(2, inventory)
                    : new com.stardew.craft.menu.StoneChestMenu(3, inventory);
            helper.assertTrue(menu.getType() == client.getType() && menu.slots.size() == 72 && client.slots.size() == 72,
                    "Client and server disagree about the menu or slot count");
            for (int index = 0; index < 36; index++) {
                var slot = menu.slots.get(index);
                helper.assertTrue(slot.x == 8 + (index % 12) * 18 && slot.y == 18 + (index / 12) * 18,
                        "Chest slots do not form a 12 by 3 grid");
                helper.assertTrue(slot.x == client.slots.get(index).x && slot.y == client.slots.get(index).y,
                        "Client hit targets differ from server slots");
            }
            for (int index = 0; index < 36; index++) {
                var slot = menu.slots.get(36 + index);
                helper.assertTrue(slot.x == 35 + (index % 9) * 18, "Player inventory is not centered at nine columns");
                helper.assertTrue(slot.getContainerSlot() == (index < 27 ? index + 9 : index - 27),
                        "Player inventory index was changed by the chest column count");
            }
            // Exercise the newly added end of the storage, not just the old first 27 slots.
            chest.setItem(35, new ItemStack(Items.DIAMOND, 7));
            menu.quickMoveStack(player, 35);
            helper.assertTrue(chest.getItem(35).isEmpty() && inventory.countItem(Items.DIAMOND) == 7,
                    "Shift-click failed to withdraw the final chest slot");
            for (int index = 0; index < 35; index++) chest.setItem(index, new ItemStack(Items.COBBLESTONE, 64));
            menu.quickMoveStack(player, 71);
            helper.assertTrue(chest.getItem(35).is(Items.DIAMOND) && chest.getItem(35).getCount() == 7
                            && inventory.countItem(Items.DIAMOND) == 0,
                    "Shift-click failed to deposit into the final chest slot");
            chest.clearContent();
            menu.removed(player);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void oldStoneContentsSurviveAndRecoveryCannotExpandStorage(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var state = com.stardew.craft.block.ModBlocks.STONE_CHEST.get().defaultBlockState();
        var chest = new com.stardew.craft.blockentity.StoneChestBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
        var oldSave = new net.minecraft.nbt.CompoundTag();
        var list = new net.minecraft.nbt.ListTag();
        var entry = new net.minecraft.nbt.CompoundTag();
        entry.putInt("Slot", 53);
        entry.put("Stack", PortItemStacks.save(new ItemStack(Items.DIAMOND, 12), registries));
        list.add(entry);
        oldSave.put("items", list);
        oldSave.putInt("colorSelection", 5);
        chest.load(oldSave);
        var reloaded = new com.stardew.craft.blockentity.StoneChestBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
        reloaded.load(chest.saveWithFullMetadata());
        helper.assertTrue(reloaded.getItem(53).getCount() == 12 && reloaded.getColorSelection() == 5,
                "Shrinking an old chest discarded its tail slots or color");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        var menu = (com.stardew.craft.menu.StoneChestMenu) reloaded.createMenu(4, player.getInventory(), player);
        var client = com.stardew.craft.menu.StoneChestMenu.recoveryClient(4, player.getInventory());
        helper.assertTrue(menu.getType() == client.getType() && menu.slots.size() == 90 && client.slots.size() == 90,
                "Recovery slots are not synchronized to the client");
        for (int index = 36; index < 54; index++) {
            helper.assertTrue(!menu.slots.get(index).mayPlace(new ItemStack(Items.DIRT))
                            && !client.slots.get(index).mayPlace(new ItemStack(Items.DIRT))
                            && !reloaded.canPlaceItem(index, new ItemStack(Items.DIRT)),
                    "Recovery slots accepted a new deposit");
        }
        reloaded.setItem(53, new ItemStack(Items.DIAMOND, 20));
        helper.assertTrue(reloaded.getItem(53).getCount() == 12, "Direct deposit enlarged a recovery stack");
        menu.organizeContainer();
        helper.assertTrue(reloaded.getItem(53).getCount() == 12, "Organize consumed a recovery slot");
        menu.quickMoveStack(player, 53);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 12 && reloaded.getItem(53).isEmpty(),
                "Recovery withdrawal lost or duplicated items");
        menu.removed(player);
        var reopened = (com.stardew.craft.menu.StoneChestMenu) reloaded.createMenu(5, player.getInventory(), player);
        helper.assertTrue(reopened.layout().recoverySlots() == 0 && reloaded.getContainerSize() == 36,
                "Empty recovery slots became permanent extra storage");
        reopened.removed(player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void rewardCapabilitiesReachClientAndRejectActions(GameTestHelper helper) {
        var inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();
        for (boolean reward : new boolean[]{false, true}) {
            for (boolean colorable : new boolean[]{false, true}) {
                var container = new SimpleContainer(27);
                container.setItem(3, new ItemStack(Items.DIAMOND, 2));
                var changes = new AtomicInteger();
                var server = new WoodenChestMenu(1, inventory, container,
                        colorable ? ignored -> changes.incrementAndGet() : null, -1, reward);
                var client = new WoodenChestMenu(1, inventory);
                helper.assertTrue(!client.canChangeColor() && !client.canOrganize(),
                        "Client enabled actions before receiving server capabilities");
                server.setSynchronizer(new ContainerSynchronizer() {
                    @Override
                    public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                                ItemStack carried, int[] data) {
                        for (int index = 0; index < data.length; index++) client.setData(index, data[index]);
                    }
                    @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {}
                    @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}
                    @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
                        client.setData(id, value);
                    }
                });
                helper.assertTrue(client.canChangeColor() == (!reward && colorable), "Incorrect client color capability");
                helper.assertTrue(client.canOrganize() == !reward, "Incorrect client organize capability");
                helper.assertTrue(com.stardew.craft.port.PortJava.getFirst(client.slots).mayPlace(new ItemStack(Items.DIRT)) == !reward,
                        "Client slot retained the constructor's stale reward flag");
                server.setColorSelectionFromClient(5);
                helper.assertTrue(changes.get() == (!reward && colorable ? 1 : 0), "Color handler ignored reward restriction");
                server.organizeContainer();
                if (reward) {
                    helper.assertTrue(container.getItem(3).is(Items.DIAMOND) && container.getItem(3).getCount() == 2
                            && container.getItem(0).isEmpty(), "Reward inventory was reorganized");
                } else {
                    helper.assertTrue(container.getItem(0).is(Items.DIAMOND) && container.getItem(3).isEmpty(),
                            "Ordinary storage lost its organize action");
                }
            }
        }
        helper.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void newVariantsPreserveLastSlotColorAndClientLayout(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        for (var variant : new com.stardew.craft.block.utility.ChestVariant[]{
                com.stardew.craft.block.utility.ChestVariant.BIG_WOOD,
                com.stardew.craft.block.utility.ChestVariant.BIG_STONE,
                com.stardew.craft.block.utility.ChestVariant.JUNIMO}) {
            player.getInventory().clearContent();
            var state = variant.block().defaultBlockState();
            var chest = new com.stardew.craft.blockentity.StorageChestBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
            var server = (WoodenChestMenu) chest.createMenu(6, player.getInventory(), player);
            var client = WoodenChestMenu.variantClient(6, player.getInventory(), variant);
            h.assertTrue(chest.getContainerSize() == variant.capacity && server.getType() == client.getType()
                    && client.slots.size() == variant.capacity + 36, "Variant capacity or client type differs");
            h.assertTrue(server.canChangeColor() == variant.dyeable && server.canOrganize(), "Incorrect variant controls");
            for (int i = 0; i < client.slots.size(); i++) {
                h.assertTrue(server.slots.get(i).x == client.slots.get(i).x && server.slots.get(i).y == client.slots.get(i).y,
                        "Variant client slot is misplaced");
            }
            h.assertTrue(variant.layout.columns() == (variant.dyeable ? 12 : 9), "Incorrect chest columns");
            chest.setItem(variant.capacity - 1, new ItemStack(Items.DIAMOND, 11));
            server.quickMoveStack(player, variant.capacity - 1);
            h.assertTrue(chest.isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 11, "Final slot cannot be withdrawn");
            if (variant.dyeable) {
                chest.setColorSelection(7);
                chest.setItem(69, new ItemStack(Items.EMERALD, 19));
                var reloaded = new com.stardew.craft.blockentity.StorageChestBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
                reloaded.load(chest.saveWithoutMetadata());
                h.assertTrue(reloaded.getColorSelection() == 7 && reloaded.getItem(69).getCount() == 19,
                        "Big chest reload lost its color or final slot");
            } else {
                chest.setColorSelection(7);
                h.assertTrue(chest.getColorSelection() == -1, "Junimo chest accepted dye");
            }
            server.removed(player);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void fillStacksPreservesComponentsAndNeverUsesRecoveryOrUnrelatedKinds(GameTestHelper h) {
        var chest = new SimpleContainer(54);
        var bag = new SimpleContainer(36);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 64));
        chest.setItem(1, new ItemStack(Items.EMERALD, 60));
        chest.setItem(53, new ItemStack(Items.GOLD_INGOT, 1));
        bag.setItem(0, new ItemStack(Items.DIAMOND, 10));
        bag.setItem(1, new ItemStack(Items.EMERALD, 9));
        bag.setItem(2, new ItemStack(Items.GOLD_INGOT, 2));
        bag.setItem(3, new ItemStack(Items.DIRT, 3));
        var named = new ItemStack(Items.DIAMOND, 4);
        PortItemData.set(named, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Different specimen"));
        bag.setItem(4, named);
        com.stardew.craft.inventory.ChestMenuActions.fillStacks(chest, 36, bag);
        h.assertTrue(chest.getItem(0).getCount() == 64 && chest.getItem(2).getCount() == 10
                && chest.getItem(1).getCount() == 64 && chest.getItem(3).getCount() == 5,
                "Fill stacks did not fill partial stacks before empty slots, or ignored an initially full kind");
        h.assertTrue(bag.getItem(0).isEmpty() && bag.getItem(1).isEmpty() && bag.getItem(2).getCount() == 2
                && bag.getItem(3).getCount() == 3 && bag.getItem(4).getCount() == 4 && chest.getItem(53).getCount() == 1,
                "Fill stacks consumed unrelated kinds, different components, or legacy recovery storage");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void junimoInventorySurvivesBlockReloadAndIsolatesFarms(GameTestHelper h) {
        var level = h.getLevel();
        var owner = java.util.UUID.randomUUID();
        var otherOwner = java.util.UUID.randomUUID();
        var state = com.stardew.craft.block.ModBlocks.JUNIMO_CHEST.get().defaultBlockState();
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putUUID("SharedOwner", owner);
        var first = new com.stardew.craft.blockentity.StorageChestBlockEntity(net.minecraft.core.BlockPos.ZERO, state);
        first.setLevel(level); first.load(tag);
        first.setItem(8, new ItemStack(Items.DIAMOND, 23));
        var savedBlock = first.saveWithoutMetadata();
        h.assertTrue(savedBlock.getList("items", 10).isEmpty(), "Shared items were copied into chunk data");
        var second = new com.stardew.craft.blockentity.StorageChestBlockEntity(net.minecraft.core.BlockPos.ZERO.above(), state);
        second.setLevel(level); second.load(savedBlock);
        h.assertTrue(second.getItem(8).getCount() == 23, "Block reload erased shared inventory");
        second.removeItem(8, 3);
        h.assertTrue(first.getItem(8).getCount() == 20, "Two Junimo chests have different inventories");
        var store = com.stardew.craft.inventory.JunimoChestData.get(level.getServer());
        var restored = com.stardew.craft.inventory.JunimoChestData.load(store.save(new net.minecraft.nbt.CompoundTag()), level.registryAccess());
        h.assertTrue(restored.items(owner).get(8).getCount() == 20 && restored.items(otherOwner).get(8).isEmpty(),
                "Saved shared storage lost items or crossed farm ownership");
        first.dropAllContents(level, net.minecraft.core.BlockPos.ZERO);
        h.assertTrue(second.getItem(8).getCount() == 20, "Removing one chest destroyed shared storage");
        store.opened(owner, java.util.UUID.randomUUID());
        h.assertTrue(!store.inUse(owner, level.getServer()), "Disconnected viewer left a permanent lock");
        first.clearContent();
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void swappingChestsIsAtomicAndRejectsOverflow(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var wood = com.stardew.craft.block.ModBlocks.WOODEN_CHEST.get();
        var big = com.stardew.craft.block.ModBlocks.BIG_CHEST.get();
        level.setBlockAndUpdate(pos, wood.defaultBlockState());
        var original = (com.stardew.craft.blockentity.WoodenChestBlockEntity) level.getBlockEntity(pos);
        original.setItem(35, new ItemStack(Items.DIAMOND, 17)); original.setColorSelection(6);
        var upgrade = new ItemStack(big, 2);
        com.stardew.craft.inventory.ChestInteractions.swap(upgrade, level, pos, player);
        var chest = (com.stardew.craft.blockentity.StorageChestBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(level.getBlockState(pos).is(big) && upgrade.getCount() == 1
                && chest.getItem(0).getCount() == 17 && chest.getColorSelection() == 6,
                "Swap lost contents, color, or consumed the wrong number of chests");
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1));
        h.assertTrue(drops.stream().mapToInt(e -> e.getItem().is(wood.asItem()) ? e.getItem().getCount() : 0).sum() == 1
                && drops.stream().noneMatch(e -> e.getItem().is(Items.DIAMOND)), "Swap duplicated contents or the old chest");
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        for (int i = 0; i < 37; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 1));
        var downgrade = new ItemStack(wood);
        com.stardew.craft.inventory.ChestInteractions.swap(downgrade, level, pos, player);
        h.assertTrue(level.getBlockState(pos).is(big) && downgrade.getCount() == 1 && chest.getItem(36).getCount() == 1,
                "Overflowing downgrade changed the chest or its inventory");
        chest.clearContent(); chest.setItem(69, new ItemStack(Items.EMERALD, 8));
        com.stardew.craft.inventory.ChestInteractions.swap(downgrade, level, pos, player);
        var smaller = (com.stardew.craft.blockentity.WoodenChestBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(level.getBlockState(pos).is(wood) && downgrade.isEmpty() && smaller.getItem(0).getCount() == 8,
                "Sparse high slots were discarded during a valid downgrade");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void movingFullChestPreservesItemsAndAutomation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new net.minecraft.core.BlockPos(2, 3, 2));
        var player = FakePlayerFactory.getMinecraft(level);
        player.setYRot(0); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var dest = pos.relative(direction);
            level.setBlockAndUpdate(dest.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(dest, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(dest.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        var state = com.stardew.craft.block.ModBlocks.BIG_STONE_CHEST.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var chest = (com.stardew.craft.blockentity.StorageChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(69, new ItemStack(Items.EMERALD, 13)); chest.setColorSelection(3);
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
        com.stardew.craft.inventory.ChestInteractions.protectContentsAndMove(event);
        var destination = pos.south();
        var moved = level.getBlockEntity(destination);
        h.assertTrue(event.isCanceled() && level.isEmptyBlock(pos) && moved instanceof com.stardew.craft.blockentity.StorageChestBlockEntity,
                "Full chest was broken or failed to move in the preferred direction");
        var result = (com.stardew.craft.blockentity.StorageChestBlockEntity) moved;
        h.assertTrue(result.getItem(69).getCount() == 13 && result.getColorSelection() == 3, "Moving lost items or color");
        var handler = com.stardew.craft.port.PortCapabilities.getCapability(level, com.stardew.craft.port.net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, destination, net.minecraft.core.Direction.UP);
        h.assertTrue(handler != null && handler.getSlots() == 70 && handler.extractItem(69, 2, false).getCount() == 2
                && result.getItem(69).getCount() == 11, "Automation did not follow the moved chest");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void recipesAndItemMetadataUseTheChestContract(GameTestHelper h) {
        for (var variant : com.stardew.craft.block.utility.ChestVariant.values()) {
            var stack = new ItemStack(variant.block());
            h.assertTrue(com.stardew.craft.item.catalog.StardewItemCatalog.typeKey(stack.getItem()).equals("stardewcraft.type.utility"), "Chest is not a utility item");
            h.assertTrue(((com.stardew.craft.item.IStardewItem) stack.getItem()).getSellPrice(stack) < 0, "Storage chest became sellable");
        }
        for (String id : new String[]{"wooden_chest", "stone_chest", "big_chest", "big_stone_chest"}) {
            var recipe = com.stardew.craft.player.StardewCraftingRecipeData.getRecipe(id).orElseThrow();
            h.assertTrue(recipe.output().item().equals("stardewcraft:" + id) && recipe.output().count() == 1
                    && com.stardew.craft.player.StardewCraftingRecipeData.isBigCraftable(id), "Recipe output or category differs");
            h.assertTrue(recipe.unlockCondition().equals(id.equals("wooden_chest") ? "default" : "null"), "Recipe unlocked without its source requirement");
            h.assertTrue(com.stardew.craft.port.PortJava.getFirst(recipe.ingredients()).count() == (id.equals("big_chest") ? 120 : id.equals("big_stone_chest") ? 250 : 50), "Incorrect material cost");
            if (id.equals("big_chest")) h.assertTrue(recipe.ingredients().size() == 2
                    && recipe.ingredients().get(1).item().equals("stardewcraft:copper_bar") && recipe.ingredients().get(1).count() == 2, "Big chest copper cost differs");
        }
        h.assertTrue(com.stardew.craft.player.StardewCraftingRecipeData.getRecipe("junimo_chest").isEmpty(), "Deferred Junimo acquisition was added");
        h.succeed();
    }
}
