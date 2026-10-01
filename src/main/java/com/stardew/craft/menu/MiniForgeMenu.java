package com.stardew.craft.menu;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.forge.ForgeRuleService;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class MiniForgeMenu extends AbstractContainerMenu {
    public static final int ACTION_FORGE = 0;
    public static final int ACTION_UNFORGE = 1;
    public static final int ACTION_TRASH = 2;

    public static final int LEFT_SLOT = 0;
    public static final int RIGHT_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int PLAYER_INV_START = 3;

    public static final int LEFT_SLOT_X = 204;
    public static final int LEFT_SLOT_Y = 212;
    public static final int RIGHT_SLOT_X = 348;
    public static final int RIGHT_SLOT_Y = 212;
    public static final int START_BUTTON_X = 204;
    public static final int START_BUTTON_Y = 308;
    public static final int START_BUTTON_WIDTH = 52;
    public static final int START_BUTTON_HEIGHT = 56;
    public static final int UNFORGE_BUTTON_X = 484;
    public static final int UNFORGE_BUTTON_Y = 312;
    public static final int RESULT_SLOT_X = 668;
    public static final int RESULT_SLOT_Y = 232;

    private static final int FORGE_SLOTS = 2;

    private final Inventory playerInventory;
    private final Container forgeContainer;
    private final Container resultContainer;
    private int craftState = ForgeRuleService.CraftState.MISSING_INGREDIENTS.id();
    private int forgeCost;
    private int cinderShardCount;

    public MiniForgeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(FORGE_SLOTS), new SimpleContainer(1));
    }

    public MiniForgeMenu(int containerId, Inventory playerInventory, Container forgeContainer, Container resultContainer) {
        super(ModMenuTypes.MINI_FORGE.get(), containerId);
        this.playerInventory = playerInventory;
        this.forgeContainer = forgeContainer;
        this.resultContainer = resultContainer;

        checkContainerSize(forgeContainer, FORGE_SLOTS);
        checkContainerSize(resultContainer, 1);
        forgeContainer.startOpen(playerInventory.player);
        resultContainer.startOpen(playerInventory.player);

        this.addSlot(new Slot(forgeContainer, 0, LEFT_SLOT_X + 24, LEFT_SLOT_Y + 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ForgeRuleService.isValidLeftIngredient(stack);
            }
        });
        this.addSlot(new Slot(forgeContainer, 1, RIGHT_SLOT_X + 24, RIGHT_SLOT_Y + 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ForgeRuleService.isValidRightIngredient(stack);
            }
        });
        this.addSlot(new Slot(resultContainer, 0, RESULT_SLOT_X + 24, RESULT_SLOT_Y + 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        int inventoryX = 204;
        int inventoryY = 360;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, inventoryX + col * 18, inventoryY + row * 18));
            }
        }

        int hotbarY = 418;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, inventoryX + col * 18, hotbarY));
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MiniForgeMenu.this.craftState;
            }

            @Override
            public void set(int value) {
                MiniForgeMenu.this.craftState = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MiniForgeMenu.this.forgeCost;
            }

            @Override
            public void set(int value) {
                MiniForgeMenu.this.forgeCost = value;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MiniForgeMenu.this.cinderShardCount;
            }

            @Override
            public void set(int value) {
                MiniForgeMenu.this.cinderShardCount = value;
            }
        });

        updatePreview();
    }

    public ForgeRuleService.CraftState getCraftState() {
        return ForgeRuleService.CraftState.byId(craftState);
    }

    public int getForgeCost() {
        return forgeCost;
    }

    public int getCinderShardCount() {
        return cinderShardCount;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        updatePreview();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == ACTION_FORGE && player instanceof ServerPlayer serverPlayer) {
            executeForge(serverPlayer);
            return true;
        }
        if (id == ACTION_UNFORGE && player instanceof ServerPlayer serverPlayer) {
            executeUnforge(serverPlayer);
            return true;
        }
        if (id == ACTION_TRASH && player instanceof ServerPlayer serverPlayer) {
            return com.stardew.craft.inventory.TrashCanService.trashCarried(serverPlayer, this).success();
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        if (index == LEFT_SLOT || index == RIGHT_SLOT) {
            if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index == RESULT_SLOT) {
            return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            boolean validLeft = ForgeRuleService.isValidLeftIngredient(stackInSlot);
            boolean validRight = ForgeRuleService.isValidRightIngredient(stackInSlot);
            if (validLeft && !this.slots.get(LEFT_SLOT).hasItem()) {
                moved = this.moveItemStackTo(stackInSlot, LEFT_SLOT, LEFT_SLOT + 1, false);
            }
            if (!moved && validRight && !this.slots.get(RIGHT_SLOT).hasItem()) {
                moved = this.moveItemStackTo(stackInSlot, RIGHT_SLOT, RIGHT_SLOT + 1, false);
            }
            if (!moved && validLeft) {
                moved = this.moveItemStackTo(stackInSlot, LEFT_SLOT, LEFT_SLOT + 1, false);
            }
            if (!moved && validRight) {
                moved = this.moveItemStackTo(stackInSlot, RIGHT_SLOT, RIGHT_SLOT + 1, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            this.clearContainer(player, this.forgeContainer);
        }
        forgeContainer.stopOpen(player);
        resultContainer.stopOpen(player);
    }

    private void updatePreview() {
        ForgeRuleService.Preview preview = ForgeRuleService.preview(
                forgeContainer.getItem(0), forgeContainer.getItem(1), playerInventory.player);
        craftState = preview.state().id();
        forgeCost = preview.cost();
        cinderShardCount = ForgeRuleService.countCinderShards(playerInventory);
        resultContainer.setItem(0, preview.result());
        resultContainer.setChanged();
        broadcastChanges();
    }

    private void executeForge(ServerPlayer player) {
        ItemStack left = forgeContainer.getItem(0);
        ItemStack right = forgeContainer.getItem(1);
        ForgeRuleService.Preview preview = ForgeRuleService.preview(left, right, player);
        if (preview.state() != ForgeRuleService.CraftState.VALID || preview.result().isEmpty()) {
            logForgeFailure(player, "preview", preview.state(), left, right, preview.result(), getCarried());
            updatePreview();
            return;
        }

        int cost = ForgeRuleService.getForgeCost(left, right);
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            logForgeFailure(player, "carried_not_empty", ForgeRuleService.CraftState.NO_ROOM, left, right, preview.result(), carried);
            updatePreview();
            return;
        }

        ItemStack result = ForgeRuleService.craftForReal(left, right, player);
        if (result.isEmpty()) {
            logForgeFailure(player, "craft_result_empty", ForgeRuleService.CraftState.INVALID_RECIPE, left, right, preview.result(), carried);
            updatePreview();
            return;
        }

        if (!ForgeRuleService.consumeCinderShards(player.getInventory(), cost)) {
            logForgeFailure(player, "consume_shards", ForgeRuleService.CraftState.MISSING_SHARDS, left, right, result, carried);
            updatePreview();
            return;
        }

        forgeContainer.setItem(0, result);
        right.shrink(1);
        if (right.isEmpty()) {
            forgeContainer.setItem(1, ItemStack.EMPTY);
        }
        forgeContainer.setChanged();
        player.getInventory().setChanged();
        updatePreview();
    }

    private void logForgeFailure(ServerPlayer player, String stage, ForgeRuleService.CraftState state,
            ItemStack left, ItemStack right, ItemStack result, ItemStack carried) {
        StardewCraft.LOGGER.warn("[MiniForge] Forge failed: player={}, stage={}, state={}, left={}, right={}, result={}, carried={}, cost={}, shards={}, validCraft={}, validLeft={}, validRight={}",
                player.getGameProfile().getName(), stage, state, stackSummary(left), stackSummary(right), stackSummary(result),
                stackSummary(carried), ForgeRuleService.getForgeCost(left, right), ForgeRuleService.countCinderShards(player.getInventory()),
                ForgeRuleService.isValidCraft(left, right), ForgeRuleService.isValidLeftIngredient(left), ForgeRuleService.isValidRightIngredient(right));
    }

    private static String stackSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    private void executeUnforge(ServerPlayer player) {
        ItemStack left = forgeContainer.getItem(0);
        ItemStack right = forgeContainer.getItem(1);
        ForgeRuleService.UnforgeResult unforge = ForgeRuleService.unforgeForReal(left, right);
        if (unforge.result().isEmpty() && unforge.returnedItems().isEmpty()) {
            logUnforgeFailure(player, "invalid", left, right, getCarried());
            updatePreview();
            return;
        }

        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            logUnforgeFailure(player, "carried_not_empty", left, right, carried);
            updatePreview();
            return;
        }

        ItemStack refund = unforge.cinderShardRefund() > 0
                ? new ItemStack(ModItems.CINDER_SHARD.get(), unforge.cinderShardRefund())
                : ItemStack.EMPTY;
        List<ItemStack> returns = new ArrayList<>(unforge.returnedItems());
        returns.add(refund);
        if (!canInventoryAcceptAll(player.getInventory(), returns)) {
            logUnforgeFailure(player, "inventory_no_room", left, right, carried);
            updatePreview();
            return;
        }

        if (!unforge.result().isEmpty()) {
            setCarried(unforge.result());
            player.connection.send(new ClientboundContainerSetSlotPacket(-1, getStateId(), -1, unforge.result()));
        }
        forgeContainer.setItem(0, ItemStack.EMPTY);
        forgeContainer.setChanged();
        for (ItemStack returnedItem : returns) {
            addReturn(player, returnedItem);
        }
        player.getInventory().setChanged();
        updatePreview();
    }

    private void logUnforgeFailure(ServerPlayer player, String stage, ItemStack left, ItemStack right, ItemStack carried) {
        StardewCraft.LOGGER.warn("[MiniForge] Unforge failed: player={}, stage={}, left={}, right={}, carried={}, validTarget={}, refund={}",
                player.getGameProfile().getName(), stage, stackSummary(left), stackSummary(right), stackSummary(carried),
                ForgeRuleService.isValidUnforgeTarget(left), ForgeRuleService.unforgeForReal(left, ItemStack.EMPTY).cinderShardRefund());
    }

    private static void addReturn(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().add(stack);
        }
    }

    private static boolean canInventoryAcceptAll(Inventory inventory, List<ItemStack> stacks) {
        ItemStack[] simulated = new ItemStack[inventory.items.size()];
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            simulated[slot] = inventory.items.get(slot).copy();
        }
        for (ItemStack stack : stacks) {
            if (!simulateAdd(simulated, stack.copy())) {
                return false;
            }
        }
        return true;
    }

    private static boolean simulateAdd(ItemStack[] inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        for (ItemStack existing : inventory) {
            if (existing.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameTags(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < inventory.length; slot++) {
            if (inventory[slot].isEmpty()) {
                inventory[slot] = stack.copy();
                return true;
            }
        }
        return false;
    }
}
