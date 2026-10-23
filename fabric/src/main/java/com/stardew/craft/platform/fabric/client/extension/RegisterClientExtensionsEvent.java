package com.stardew.craft.platform.fabric.client.extension;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public final class RegisterClientExtensionsEvent extends Event implements ModBusEvent {
    private final ClientExtensionRegistry registry;
    public RegisterClientExtensionsEvent(ClientExtensionRegistry registry) { this.registry = registry; }
    public void registerItem(IClientItemExtensions extension, Item... items) { registry.registerItem(extension, items); }
    public void registerBlock(IClientBlockExtensions extension, Block... blocks) { registry.registerBlock(extension, blocks); }
    @SafeVarargs public final void registerItem(IClientItemExtensions extension, Holder<Item>... items) {
        registerItem(extension, Arrays.stream(items).map(Holder::value).toArray(Item[]::new));
    }
    @SafeVarargs public final void registerBlock(IClientBlockExtensions extension, Holder<Block>... blocks) {
        registerBlock(extension, Arrays.stream(blocks).map(Holder::value).toArray(Block[]::new));
    }
    public boolean isItemRegistered(Item item) { return registry.isItemRegistered(item); }
    public boolean isBlockRegistered(Block block) { return registry.isBlockRegistered(block); }
}
