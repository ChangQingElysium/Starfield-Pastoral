package com.stardew.craft.communitycenter.menu;

import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterRewardRegistry;
import com.stardew.craft.communitycenter.data.BundleIngredient;
import com.stardew.craft.communitycenter.data.BundleItemResolver;
import com.stardew.craft.communitycenter.junimo.JunimoSpawner;
import com.stardew.craft.communitycenter.network.BundleSyncPayload;
import com.stardew.craft.communitycenter.restore.CCAreaRegistry;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.menu.ModMenuTypes;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side container for the Community Center bundle interface.
 * Mirrors SDV JunimoNoteMenu's inventory + slot logic.
 *
 * Opened via /cc open [areaId] command (or JunimoNoteBlock interaction later).
 * The areaId is encoded as containerId data — for now this menu only hosts
 * the player inventory; all bundle rendering logic lives client-side in BundleScreen.
 */
@SuppressWarnings("null")
public class BundleMenu extends AbstractContainerMenu {

    /** Which area this menu was opened for (0-6). */
    private int areaId;

    /**
     * Read-only mode — opened from inventory tab's junimo note icon (SDV: JunimoNoteMenu(fromGameMenu=true)).
     * In read-only mode: cannot deposit, purchase, partial-donate, or receive rewards.
     * Kept in sync with client via DataSlot.
     */
    private boolean readOnly = false;

    // ── Partial Donation State (SDV parity: JunimoNoteMenu.partialDonationItem) ──
    // Tracks items deposited partially when player doesn't have enough for the full count.
    // Items are held temporarily until the full required count is reached, then the deposit completes.
    // On menu close, partial items are returned to the player.
    @Nullable private ItemStack partialDonationItem = null;
    private final List<ItemStack> partialDonationComponents = new ArrayList<>();
    private int partialBundleId = -1;
    private int partialIngredientIndex = -1;

    // ── Client-side constructor (from network) ──
    public BundleMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, 1, false); // default, will be synced via DataSlot
    }

    // ── Server-side constructor ──
    public BundleMenu(int containerId, Inventory playerInventory, int areaId) {
        this(containerId, playerInventory, areaId, false);
    }

    // ── Server-side constructor (supports read-only viewer from GameMenu) ──
    public BundleMenu(int containerId, Inventory playerInventory, int areaId, boolean readOnly) {
        super(ModMenuTypes.BUNDLE.get(), containerId);
        this.areaId = areaId;
        this.readOnly = readOnly;

        // Sync areaId to client via DataSlot (automatic S↔C each tick)
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return BundleMenu.this.areaId; }
            @Override public void set(int value) { BundleMenu.this.areaId = value; }
        });
        // Sync readOnly as DataSlot (0/1)
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return BundleMenu.this.readOnly ? 1 : 0; }
            @Override public void set(int value) { BundleMenu.this.readOnly = (value != 0); }
        });

        // SDV JunimoNoteMenu: InventoryMenu(x+128, y+140, capacity=36, rows=6, hGap=8, vGap=8)
        // → 6 columns × 6 rows, all 36 player slots shown sequentially.
        // Slot positions are placeholders; BundleScreen repositions dynamically.
        int xOffset = 32;
        int yOffset = 35;

        // Add all 36 inventory slots sequentially (0-35) → SDV's flat 6×6 grid
        for (int j = 0; j < 36; j++) {
            int col = j % 6;
            int row = j / 6;
            this.addSlot(new Slot(playerInventory, j,
                    xOffset + col * 18, yOffset + row * 18));
        }
    }

    public int getAreaId() {
        return areaId;
    }

    /**
     * Update the area being viewed (used by the read-only viewer's Next/Back buttons).
     * Server-authoritative: syncs to client via the areaId DataSlot on broadcastChanges().
     */
    public void setAreaId(int newArea) {
        this.areaId = newArea;
        this.broadcastChanges();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        return true;
    }

    @Override
    public @Nonnull ItemStack quickMoveStack(@Nonnull Player player, int index) {
        // Shift-click: move between top half (0-17) and bottom half (18-35) of the 6×6 grid
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (index < 18) {
                if (!this.moveItemStackTo(stackInSlot, 18, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 18, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    // ── Server-side bundle deposit logic ──

    /**
     * Try to deposit an item into a bundle slot.
     * Called when the client sends a deposit request.
     *
     * @param player    the depositing player
     * @param bundleId  which bundle
     * @param slotIndex which ingredient slot
     * @param stack     the item to deposit
     * @return true if deposit succeeded
     */
    public boolean tryDeposit(Player player, int bundleId, int slotIndex, ItemStack stack) {
        if (player.level().isClientSide()) return false;
        if (readOnly) return false;

        BundleDefinition def = StardewCommunityCenterVariantRegistry.bundle(
                player.getUUID(), bundleId);
        if (def == null || def.areaId() != this.areaId) return false;

        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = player.getUUID();
        if (data.isSlotComplete(uuid, bundleId, slotIndex)) return false;

        List<BundleIngredient> ingredients = def.ingredients();
        if (slotIndex < 0 || slotIndex >= ingredients.size()) return false;

        BundleIngredient ingredient = ingredients.get(slotIndex);
        if (!isValidItem(stack, ingredient)) return false;

        // Consume items
        int toConsume = ingredient.stack();
        if (stack.getCount() < toConsume) return false;

        stack.shrink(toConsume);
        data.markSlotComplete(uuid, bundleId, slotIndex);

        // SDV parity: multiplayer chat message — "BundleDonate" with player name + item name
        if (player instanceof ServerPlayer sp) {
            ItemStack displayItem = BundleItemResolver.resolveItemStack(ingredient.itemId());
            Component itemName = displayItem.isEmpty() ? Component.literal("???") : displayItem.getHoverName();
            broadcastBundleDonateMessage(sp, itemName);
        }

        // Check bundle completion
        checkBundleCompletion(player, bundleId, def, data);

        return true;
    }

    /**
     * Try to purchase a Vault bundle (money only).
     */
    public boolean tryPurchaseVault(Player player, int bundleId) {
        if (player.level().isClientSide()) return false;
        if (readOnly) return false;
        if (this.areaId != 4) return false;

        BundleDefinition def = StardewCommunityCenterVariantRegistry.bundle(
                player.getUUID(), bundleId);
        if (def == null || !def.isVaultBundle()) return false;

        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = player.getUUID();
        if (data.isBundleComplete(uuid, bundleId)) return false;

        BundleIngredient moneyIngredient = def.ingredients().get(0);
        if (!moneyIngredient.isMoneyIngredient()) return false;

        int cost = moneyIngredient.moneyRequired();

        if (!(player instanceof ServerPlayer sp)) return false;
        int currentMoney = PlayerStardewDataAPI.getMoney(sp);
        if (currentMoney < cost) return false;
        PlayerStardewDataAPI.removeMoney(sp, cost);

        data.markBundleAllSlotsComplete(uuid, bundleId);
        data.setRewardAvailable(uuid, bundleId, true);

        // SDV parity: multiplayer chat — "Bundle" (no item name for vault)
        broadcastBundleCompleteMessage(sp);

        spawnBundleCarrierJunimo(player, 4, def.color());

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(sl).getCCOrigin(uuid);
            com.stardew.craft.communitycenter.JunimoNotePlacer.ensureJunimoNotes(sl, uuid, ccOrigin);
        }

        boolean allDone = true;
        for (BundleDefinition bd
                : StardewCommunityCenterVariantRegistry.area(uuid, 4)) {
            if (!data.isBundleComplete(uuid, bd.bundleId())) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            data.markAreaComplete(uuid, 4);
            onAreaComplete(player, 4);
        }

        return true;
    }

    /**
     * 区域完成后的处理：设置邮件标记 + 发送完成通知邮件
     */
    private void onAreaComplete(Player player, int areaId) {
        if (!(player instanceof ServerPlayer sp)) return;

        // 添加区域完成标记 (ccPantry, ccCraftsRoom, ...)
        String flag = CCStoryFlags.areaFlag(areaId);
        if (!flag.isEmpty()) {
            CCStoryFlags.addFlag(sp, flag);
        }

        // 复用共享完成逻辑（mail + rewards + greenhouse + cutscene + CC_IS_COMPLETE）
        if (sp.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.stardew.craft.communitycenter.reward.AreaCompletionService.onAreaComplete(
                sp, areaId, serverLevel, /*jojaPath=*/false);
            // bundle 特有：未领取奖励自动发放
            deliverUnclaimedRewards(sp, areaId);
        }
    }

    /**
     * 区域完成时，自动将该区域所有未领取的 bundle 奖励发到玩家背包。
     * 修复后祝尼魔卷轴被移除，玩家无法再通过 UI 领取。
     */
    private void deliverUnclaimedRewards(ServerPlayer sp, int areaId) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = sp.getUUID();

        for (BundleDefinition def
                : StardewCommunityCenterVariantRegistry.area(uuid, areaId)) {
            if (!data.isRewardAvailable(uuid, def.bundleId())) continue;

            ItemStack reward = com.stardew.craft.communitycenter.network.BundleClaimRewardPayload
                    .parseRewardString(def.rewardString());
            reward = StardewCommunityCenterRewardRegistry.resolve(
                    sp, def.bundleId(), def.rewardString(), reward);
            if (!reward.isEmpty()) {
                if (!sp.getInventory().add(reward)) {
                    sp.drop(reward, false);
                }
            }
            data.setRewardAvailable(uuid, def.bundleId(), false);
        }

        // 同步到客户端
        com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(sp);
    }

    /**
     * Mirrors SDV Bundle.IsValidItemForThisIngredientDescription().
     * Checks item type + quality match. Does NOT check stack count
     * (SDV separates type match from stack count check).
     * Used for highlighting AND deposit validation.
     */
    public static boolean isValidItem(ItemStack stack, BundleIngredient ingredient) {
        if (!isItemTypeMatch(stack, ingredient)) return false;
        // Stack size check for deposit
        return stack.getCount() >= ingredient.stack();
    }

    /**
     * Checks if an item matches an ingredient by type + quality only.
     * Mirrors SDV HighlightObjects logic — no stack count check.
     * Used for inventory dimming.
     */
    public static boolean isItemTypeMatch(ItemStack stack, BundleIngredient ingredient) {
        if (stack.isEmpty()) return false;
        if (ingredient.isMoneyIngredient()) return false;

        // Quality check — SDV: item quality >= required quality
        int itemQuality = QualityHelper.getQuality(stack);
        if (itemQuality < ingredient.quality()) return false;

        // Category match (negative IDs like -4=fish, -5=egg, etc.)
        if (ingredient.category() < -1) {
            return false;
        }

        // Exact item match
        if (ingredient.itemId() == null) return false;

        ItemStack required = BundleItemResolver.resolveItemStack(ingredient.itemId());
        if (required.isEmpty()) return false;

        return ItemStack.isSameItem(stack, required);
    }

    /**
     * 在 bundle 完成后，从 CCAreaRegistry 查找 JunimoNote 位置并生成搬运 Junimo。
     */
    private void spawnBundleCarrierJunimo(Player player, int areaId, int bundleColorIndex) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        CCAreaRegistry.AreaBounds bounds = CCAreaRegistry.getArea(areaId);
        if (bounds == null) return;
        net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(serverLevel).getCCOrigin(player.getUUID());
        BlockPos notePos = bounds.noteWorldPos(ccOrigin);
        JunimoSpawner.spawnBundleCarrier(serverLevel, notePos, areaId, bundleColorIndex);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Partial Donation System (SDV parity: HandlePartialDonation)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handle a partial deposit from the client.
     * SDV parity: HandlePartialDonation — takes partial items from cursor,
     * accumulates until full count is reached, then completes the deposit.
     */
    public boolean handlePartialDeposit(ServerPlayer player, int bundleId, int ingredientIndex, int amount, ItemStack carried) {
        if (readOnly) return false;
        BundleDefinition def = StardewCommunityCenterVariantRegistry.bundle(
                player.getUUID(), bundleId);
        if (def == null || def.areaId() != this.areaId || def.isVaultBundle()) return false;

        List<BundleIngredient> ingredients = def.ingredients();
        if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) return false;

        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        java.util.UUID uuid = player.getUUID();
        if (data.isSlotComplete(uuid, bundleId, ingredientIndex)) return false;

        BundleIngredient ingredient = ingredients.get(ingredientIndex);
        if (!isItemTypeMatch(carried, ingredient)) return false;

        // SDV: can only have one partial donation at a time; must be same ingredient
        if (partialDonationItem != null && (partialBundleId != bundleId || partialIngredientIndex != ingredientIndex)) {
            return false;
        }

        // SDV: CanBePartiallyOrFullyDonated check — ensure total available >= required
        int totalAvailable = carried.getCount();
        if (partialDonationItem != null) {
            totalAvailable += partialDonationItem.getCount();
        }
        // Also count matching items in player inventory
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && isItemTypeMatch(invStack, ingredient)) {
                totalAvailable += invStack.getCount();
            }
        }
        if (totalAvailable < ingredient.stack()) return false;

        int actualAmount = Math.min(amount, carried.getCount());
        if (actualAmount <= 0) return false;

        if (partialDonationItem == null) {
            // Start new partial
            int toTake = Math.min(ingredient.stack(), actualAmount);
            partialDonationItem = carried.copyWithCount(toTake);
            carried.shrink(toTake);
            partialBundleId = bundleId;
            partialIngredientIndex = ingredientIndex;

            // Track component for return
            ItemStack component = partialDonationItem.copyWithCount(toTake);
            partialDonationComponents.add(component);
        } else {
            // Add to existing partial
            int remaining = ingredient.stack() - partialDonationItem.getCount();
            int toTake = Math.min(remaining, actualAmount);
            if (toTake <= 0) return false;
            partialDonationItem.grow(toTake);
            carried.shrink(toTake);

            // Track component
            // Try to merge with existing components
            boolean merged = false;
            for (ItemStack existing : partialDonationComponents) {
                if (ItemStack.isSameItemSameComponents(existing, carried)) {
                    existing.grow(toTake);
                    merged = true;
                    break;
                }
            }
            if (!merged && toTake > 0) {
                partialDonationComponents.add(carried.copyWithCount(toTake));
            }
        }

        // SDV: if partial reaches required count, complete the deposit
        if (partialDonationItem.getCount() >= ingredient.stack()) {
            // Complete the deposit
            data.markSlotComplete(uuid, bundleId, ingredientIndex);

            // SDV parity: multiplayer chat
            ItemStack displayItem = BundleItemResolver.resolveItemStack(ingredient.itemId());
            Component itemName = displayItem.isEmpty() ? Component.literal("???") : displayItem.getHoverName();
            broadcastBundleDonateMessage(player, itemName);

            // Return any excess to player
            int excess = partialDonationItem.getCount() - ingredient.stack();
            if (excess > 0) {
                ItemStack returnStack = partialDonationItem.copyWithCount(excess);
                if (!player.getInventory().add(returnStack)) {
                    player.drop(returnStack, false);
                }
            }

            resetPartialDonation();

            checkBundleCompletion(player, bundleId, def, data);
            BundleSyncPayload.sendFullSync(player);
        }

        return true;
    }

    /**
     * SDV parity: right-click on partial donation slot → retrieve 1 item to cursor.
     */
    public void retrieveOneFromPartial(ServerPlayer player, int bundleId) {
        if (readOnly) return;
        if (partialDonationItem == null || partialBundleId != bundleId) return;
        if (partialDonationComponents.isEmpty()) return;

        ItemStack cursor = this.getCarried();
        ItemStack firstComponent = partialDonationComponents.get(0);
        ItemStack oneItem = firstComponent.copyWithCount(1);

        if (cursor.isEmpty()) {
            this.setCarried(oneItem);
        } else if (ItemStack.isSameItemSameComponents(cursor, oneItem)) {
            cursor.grow(1);
        } else {
            return; // Can't merge with current cursor item
        }

        firstComponent.shrink(1);
        if (firstComponent.isEmpty()) {
            partialDonationComponents.remove(0);
        }

        // Update partial total
        int newTotal = 0;
        for (ItemStack comp : partialDonationComponents) {
            newTotal += comp.getCount();
        }
        if (newTotal <= 0) {
            resetPartialDonation();
        } else {
            partialDonationItem.setCount(newTotal);
        }
    }

    /**
     * SDV parity: ReturnPartialDonations — return all partial items to player.
     * Called when closing the bundle page or the menu.
     *
     * @param toCursor if true, first item goes to cursor (SDV: to_hand parameter)
     */
    public void returnAllPartials(Player player, boolean toCursor) {
        if (partialDonationComponents.isEmpty()) {
            resetPartialDonation();
            return;
        }

        for (ItemStack component : partialDonationComponents) {
            if (component.isEmpty()) continue;
            if (toCursor && this.getCarried().isEmpty()) {
                this.setCarried(component.copy());
                toCursor = false;
            } else {
                if (!player.getInventory().add(component.copy())) {
                    player.drop(component.copy(), false);
                }
            }
        }
        resetPartialDonation();
    }

    /**
     * SDV parity: ResetPartialDonation — clear all partial donation state.
     */
    private void resetPartialDonation() {
        partialDonationComponents.clear();
        partialDonationItem = null;
        partialBundleId = -1;
        partialIngredientIndex = -1;
    }

    /** Whether there is an active partial donation. */
    public boolean hasPartialDonation() {
        return partialDonationItem != null;
    }

    /** The bundle ID of the current partial donation, or -1. */
    public int getPartialBundleId() { return partialBundleId; }

    /** The ingredient index of the current partial donation, or -1. */
    public int getPartialIngredientIndex() { return partialIngredientIndex; }

    /** The current partial stack count, or 0. */
    public int getPartialCount() {
        return partialDonationItem != null ? partialDonationItem.getCount() : 0;
    }

    /**
     * SDV parity: on menu close, return partial donations to player.
     */
    @Override
    public void removed(@Nonnull Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            returnAllPartials(player, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Bundle Completion Helper
    // ═══════════════════════════════════════════════════════════════════

    private void checkBundleCompletion(Player player, int bundleId, BundleDefinition def, CommunityCenterSavedData data) {
        java.util.UUID uuid = player.getUUID();
        if (data.isBundleComplete(uuid, bundleId)) {
            com.stardew.craft.StardewCraft.LOGGER.info("[REWARD-DEBUG] Bundle {} COMPLETE! Setting reward available.", bundleId);
            data.setRewardAvailable(uuid, bundleId, true);
            data.markBundleAllSlotsComplete(uuid, bundleId);

            spawnBundleCarrierJunimo(player, def.areaId(), def.color());

            if (player.level() instanceof ServerLevel sl) {
                net.minecraft.core.BlockPos ccOrigin = com.stardew.craft.interior.PlayerInteriorAllocator.get(sl).getCCOrigin(uuid);
                com.stardew.craft.communitycenter.JunimoNotePlacer.ensureJunimoNotes(sl, uuid, ccOrigin);
            }

            // SDV: multiplayer "Bundle" complete message
            if (player instanceof ServerPlayer sp) {
                broadcastBundleCompleteMessage(sp);
            }

            boolean allDone = true;
            for (BundleDefinition bd
                    : StardewCommunityCenterVariantRegistry.area(uuid, def.areaId())) {
                if (!data.isBundleComplete(uuid, bd.bundleId())) {
                    allDone = false;
                    break;
                }
            }
            if (allDone) {
                data.markAreaComplete(uuid, def.areaId());
                onAreaComplete(player, def.areaId());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Multiplayer Chat Messages (SDV parity)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SDV: Game1.multiplayer.globalChatInfoMessage("BundleDonate", playerName, itemName)
     * Broadcasts to all players on the server.
     */
    private void broadcastBundleDonateMessage(ServerPlayer player, Component itemName) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        Component msg = Component.translatable("stardewcraft.chat.bundleDonate",
                com.stardew.craft.player.PlayerDisplayName.get(player), itemName);
        for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
            sp.sendSystemMessage(msg);
        }
    }

    /**
     * SDV: Game1.multiplayer.globalChatInfoMessage("Bundle")
     * Broadcasts bundle completion to all players.
     */
    private void broadcastBundleCompleteMessage(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        Component msg = Component.translatable("stardewcraft.chat.bundleComplete",
                com.stardew.craft.player.PlayerDisplayName.get(player));
        for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
            sp.sendSystemMessage(msg);
        }
    }
}
