package com.stardew.craft.platform.fabric.event.enchant;

import com.stardew.craft.platform.fabric.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fired before the native anvil calculation, when the left input is nonempty. */
public final class AnvilUpdateEvent extends Event {
    private final ItemStack left;
    private final ItemStack right;
    private final String name;
    private final Player player;
    private ItemStack output = ItemStack.EMPTY;
    private long cost;
    private int materialCost;

    public AnvilUpdateEvent(ItemStack left, ItemStack right, @Nullable String name, long cost, Player player) {
        super(true);
        this.left = left;
        this.right = right;
        this.name = name;
        this.cost = cost;
        this.player = player;
    }

    public ItemStack getLeft() { return left; }
    public ItemStack getRight() { return right; }
    @Nullable public String getName() { return name; }
    public Player getPlayer() { return player; }
    public ItemStack getOutput() { return output; }
    public void setOutput(ItemStack output) { this.output = java.util.Objects.requireNonNull(output); }
    public long getCost() { return cost; }
    public void setCost(long cost) { this.cost = cost; }
    public int getMaterialCost() { return materialCost; }
    public void setMaterialCost(int cost) { this.materialCost = cost; }
}
