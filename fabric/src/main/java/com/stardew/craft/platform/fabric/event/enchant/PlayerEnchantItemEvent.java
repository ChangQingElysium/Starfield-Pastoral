package com.stardew.craft.platform.fabric.event.enchant;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** The complete enchantment list has already been applied to this mutable stack. */
public final class PlayerEnchantItemEvent extends PlayerEvent {
    private final ItemStack item;
    private final List<EnchantmentInstance> enchantments;
    public PlayerEnchantItemEvent(Player player, ItemStack item, List<EnchantmentInstance> enchantments) {
        super(player);
        this.item = item;
        this.enchantments = enchantments;
    }
    public ItemStack getEnchantedItem() { return item; }
    public List<EnchantmentInstance> getEnchantments() { return enchantments; }
}
