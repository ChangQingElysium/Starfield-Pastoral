package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.util.TriState;
import java.util.Objects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public abstract class ItemEntityPickupEvent extends Event {
    private final Player player;
    private final ItemEntity item;
    protected ItemEntityPickupEvent(Player player, ItemEntity item) {
        this.player = Objects.requireNonNull(player);
        this.item = Objects.requireNonNull(item);
    }
    public Player getPlayer() { return player; }
    public ItemEntity getItemEntity() { return item; }

    public static final class Pre extends ItemEntityPickupEvent {
        private TriState canPickup = TriState.DEFAULT;
        public Pre(Player player, ItemEntity item) { super(player, item); }
        public TriState canPickup() { return canPickup; }
        public void setCanPickup(TriState value) { canPickup = Objects.requireNonNull(value); }
    }
    public static final class Post extends ItemEntityPickupEvent {
        private final ItemStack original;
        public Post(Player player, ItemEntity item, ItemStack original) {
            super(player, item);
            this.original = original.copy();
        }
        public ItemStack getOriginalStack() { return original.copy(); }
        public ItemStack getCurrentStack() { return getItemEntity().getItem(); }
    }
}
