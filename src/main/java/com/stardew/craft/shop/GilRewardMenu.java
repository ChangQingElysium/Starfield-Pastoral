package com.stardew.craft.shop;

import com.stardew.craft.menu.ModMenuTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Reward slots are buttons with static previews, never transferable inventory items. */
public final class GilRewardMenu extends AbstractContainerMenu {
    public static final int REWARD_SLOTS = 18;
    private final SimpleContainer rewards = new SimpleContainer(REWARD_SLOTS);
    private final MonsterSlayerGoalRegistry.SlayerGoal[] goals = new MonsterSlayerGoalRegistry.SlayerGoal[REWARD_SLOTS];
    public GilRewardMenu(int id, Inventory inventory) {
        super(ModMenuTypes.GIL_REWARD.get(), id);
        if (inventory.player instanceof ServerPlayer player) refill(player);
        for (int i = 0; i < REWARD_SLOTS; i++) {
            addSlot(new Slot(rewards, i, 12 + i % 9 * 18, 16 + i / 9 * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public ItemStack remove(int count) { return ItemStack.EMPTY; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 12 + col * 18, 78 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 12 + col * 18, 136));
    }
    private void refill(ServerPlayer player) {
        rewards.clearContent(); java.util.Arrays.fill(goals, null);
        int i = 0;
        for (var goal : GilService.rewardGoals(player)) {
            if (i == REWARD_SLOTS) break;
            if (GilService.available(player, goal.goalKey()) && goal.hasReward()) {
                goals[i] = goal; rewards.setItem(i++, GilService.rewardItem(goal));
            }
        }
    }
    public void takeGoal(ServerPlayer player, String goal) {
        for (int i = 0; i < goals.length; i++) if (goals[i] != null && goal.equals(goals[i].goalKey())) {
            quickMoveStack(player, i); return;
        }
    }
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < REWARD_SLOTS) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) quickMoveStack(player, slotId);
            return;
        }
        // Picking up all matching items must not sweep static preview slots into the carried stack.
        if (type == ClickType.PICKUP_ALL) return;
        super.clicked(slotId, button, type, player);
    }
    @Override public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer sp) || GilService.canReach(sp);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer sp) || !stillValid(player) || index < 0 || index >= REWARD_SLOTS
                || goals[index] == null) return ItemStack.EMPTY;
        var preview = rewards.getItem(index).copy();
        if (!GilService.claim(sp, goals[index])) { broadcastChanges(); return ItemStack.EMPTY; }
        // Keep existing buttons stationary; fill newly freed slots with overflow goals only.
        goals[index] = null; rewards.setItem(index, ItemStack.EMPTY);
        for (var goal : GilService.rewardGoals(sp)) {
            if (!GilService.available(sp, goal.goalKey()) || !goal.hasReward()) continue;
            if (java.util.Arrays.stream(goals).anyMatch(g -> g != null && g.goalKey().equals(goal.goalKey()))) continue;
            goals[index] = goal; rewards.setItem(index, GilService.rewardItem(goal)); break;
        }
        broadcastChanges(); return preview;
    }
}
