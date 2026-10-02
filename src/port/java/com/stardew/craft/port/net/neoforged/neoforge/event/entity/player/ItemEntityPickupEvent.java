package com.stardew.craft.port.net.neoforged.neoforge.event.entity.player;

import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 item pickup events. {@code PortItemEntityPickupMixin} fires {@link Pre} before
 * pickup-delay checks and {@link Post} after inventory insertion, before {@code Player#take}, with a complete
 * pre-insertion stack. Forge's native pickup events retain their own normal firing points and cancellation.
 */
public abstract class ItemEntityPickupEvent extends Event {
    private final Player player;
    private final ItemEntity item;

    public ItemEntityPickupEvent(Player player, ItemEntity item) {
        this.player = player;
        this.item = item;
    }

    public Player getPlayer() {
        return this.player;
    }

    public ItemEntity getItemEntity() {
        return this.item;
    }

    public static class Pre extends ItemEntityPickupEvent {
        private TriState canPickup = TriState.DEFAULT;

        public Pre() {
            this(null, null);
        }

        public Pre(Player player, ItemEntity item) {
            super(player, item);
        }

        public void setCanPickup(TriState state) {
            this.canPickup = state;
        }

        public TriState canPickup() {
            return this.canPickup;
        }
    }

    public static class Post extends ItemEntityPickupEvent {
        private final ItemStack originalStack;

        public Post() {
            this(null, null, ItemStack.EMPTY);
        }

        public Post(Player player, ItemEntity item, ItemStack originalStack) {
            super(player, item);
            this.originalStack = originalStack;
        }

        public ItemStack getOriginalStack() {
            return this.originalStack.copy();
        }

        public ItemStack getCurrentStack() {
            return this.getItemEntity().getItem();
        }
    }
}
