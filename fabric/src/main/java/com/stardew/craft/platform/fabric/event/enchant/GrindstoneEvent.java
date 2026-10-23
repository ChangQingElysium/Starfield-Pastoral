package com.stardew.craft.platform.fabric.event.enchant;

import com.stardew.craft.platform.fabric.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class GrindstoneEvent extends Event {
    private final ItemStack top;
    private final ItemStack bottom;
    private int xp;

    protected GrindstoneEvent(ItemStack top, ItemStack bottom, int xp) {
        super(true);
        this.top = top;
        this.bottom = bottom;
        this.xp = xp;
    }

    public ItemStack getTopItem() { return top; }
    public ItemStack getBottomItem() { return bottom; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public static final class OnPlaceItem extends GrindstoneEvent {
        private ItemStack output = ItemStack.EMPTY;
        public OnPlaceItem(ItemStack top, ItemStack bottom, int xp) { super(top, bottom, xp); }
        public ItemStack getOutput() { return output; }
        public void setOutput(ItemStack output) { this.output = java.util.Objects.requireNonNull(output); }
    }

    public static final class OnTakeItem extends GrindstoneEvent {
        private final ContainerLevelAccess access;
        private final Player player;
        private ItemStack newTop = ItemStack.EMPTY;
        private ItemStack newBottom = ItemStack.EMPTY;
        public OnTakeItem(ContainerLevelAccess access, @Nullable Player player, ItemStack top, ItemStack bottom, int xp) {
            super(top, bottom, xp);
            this.access = access;
            this.player = player;
        }
        public OnTakeItem(ItemStack top, ItemStack bottom, int xp) { this(ContainerLevelAccess.NULL, null, top, bottom, xp); }
        public ContainerLevelAccess getContainerAccess() { return access; }
        @Nullable public Player getPlayer() { return player; }
        public ItemStack getNewTopItem() { return newTop; }
        public ItemStack getNewBottomItem() { return newBottom; }
        public void setNewTopItem(ItemStack item) { newTop = java.util.Objects.requireNonNull(item); }
        public void setNewBottomItem(ItemStack item) { newBottom = java.util.Objects.requireNonNull(item); }
    }
}
