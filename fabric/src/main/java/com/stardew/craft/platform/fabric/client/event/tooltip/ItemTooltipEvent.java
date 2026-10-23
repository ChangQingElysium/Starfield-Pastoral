package com.stardew.craft.platform.fabric.client.event.tooltip;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/** The actual tooltip-line query, including startup search-tree queries with a null player. */
public final class ItemTooltipEvent extends PlayerEvent {
    private final ItemStack stack;
    private final List<Component> lines;
    private final TooltipFlag flags;
    private final Item.TooltipContext context;
    public ItemTooltipEvent(ItemStack stack, @Nullable Player player, List<Component> lines, TooltipFlag flags, Item.TooltipContext context) {
        super(player, false, true);
        this.stack = stack; this.lines = lines; this.flags = flags; this.context = context;
    }
    public ItemStack getItemStack() { return stack; }
    public List<Component> getToolTip() { return lines; }
    public TooltipFlag getFlags() { return flags; }
    public Item.TooltipContext getContext() { return context; }
    @Override @Nullable public Player getEntity() { return super.getEntity(); }
}
