package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

public final class ItemFishedEvent extends PlayerEvent {
    private final NonNullList<ItemStack> drops = NonNullList.create();
    private final FishingHook hook;
    private int rodDamage;
    public ItemFishedEvent(List<ItemStack> drops, int rodDamage, FishingHook hook) {
        super(hook.getPlayerOwner(), true, true);
        this.drops.addAll(drops);
        this.rodDamage = rodDamage;
        this.hook = hook;
    }
    public int getRodDamage() { return rodDamage; }
    public void damageRodBy(int damage) {
        if (damage < 0) throw new IllegalArgumentException("Rod damage must be nonnegative");
        rodDamage = damage;
    }
    /** Neo exposes a shallow list copy; editing this list does not replace native loot. */
    public NonNullList<ItemStack> getDrops() { return drops; }
    public FishingHook getHookEntity() { return hook; }
}
