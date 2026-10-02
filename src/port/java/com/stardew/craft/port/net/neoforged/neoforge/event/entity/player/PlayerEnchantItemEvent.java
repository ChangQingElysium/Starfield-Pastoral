package com.stardew.craft.port.net.neoforged.neoforge.event.entity.player;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code PlayerEnchantItemEvent}, fired on the server after an enchanting-table
 * enchantment was applied (from {@code com.stardew.craft.mixin.PortEnchantedItemTriggerMixin}, i.e. at the
 * {@code ENCHANTED_ITEM} advancement trigger, after the lapis was consumed). The list is rebuilt from the enchanted
 * stack because Forge does not expose the rolled list there.
 */
public class PlayerEnchantItemEvent extends PlayerEvent {
    private final ItemStack enchantedItem;
    private final List<EnchantmentInstance> enchantments;

    public PlayerEnchantItemEvent() {
        this(null, ItemStack.EMPTY, List.of());
    }

    public PlayerEnchantItemEvent(Player player, ItemStack enchantedItem, List<EnchantmentInstance> enchantments) {
        super(player);
        this.enchantedItem = enchantedItem;
        this.enchantments = enchantments;
    }

    public ItemStack getEnchantedItem() {
        return this.enchantedItem;
    }

    public List<EnchantmentInstance> getEnchantments() {
        return this.enchantments;
    }
}
