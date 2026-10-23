package com.stardew.craft.platform.fabric.event.entity.living;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public abstract class LivingEntityUseItemEvent extends LivingEvent {
    private final ItemStack item;
    private final InteractionHand hand;
    private int duration;
    protected LivingEntityUseItemEvent(LivingEntity entity, ItemStack item, int duration) {
        super(entity, false);
        this.item = java.util.Objects.requireNonNull(item);
        this.duration = duration;
        this.hand = entity.getUsedItemHand();
    }
    public ItemStack getItem() { return item; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public InteractionHand getHand() { return hand; }
    public static final class Finish extends LivingEntityUseItemEvent {
        private ItemStack result;
        public Finish(LivingEntity entity, ItemStack item, int duration, ItemStack result) {
            super(entity, item, duration);
            this.result = java.util.Objects.requireNonNull(result);
        }
        public ItemStack getResultStack() { return result; }
        public void setResultStack(ItemStack result) { this.result = java.util.Objects.requireNonNull(result); }
    }
}
