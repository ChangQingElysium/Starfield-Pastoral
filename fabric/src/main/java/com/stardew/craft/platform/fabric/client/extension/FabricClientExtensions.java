package com.stardew.craft.platform.fabric.client.extension;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public final class FabricClientExtensions {
    private static final ClientExtensionRegistry REGISTRY = new ClientExtensionRegistry();
    private static IEventBus installedBus;
    private static boolean attempted;
    private FabricClientExtensions() {}
    public static ClientExtensionRegistry registry() { return REGISTRY; }
    /** Safe at Fabric's early client entrypoint: no extension constructors run here. */
    public static synchronized void install(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        if (installedBus == modBus) return;
        if (installedBus != null) throw new IllegalStateException("Client extensions already have an installed bus");
        installedBus = modBus;
    }
    /** The Minecraft constructor mixin calls this only once renderer/model fields exist. */
    public static synchronized void initialize() {
        if (installedBus == null) throw new IllegalStateException("Client extension bus is not installed");
        if (attempted) throw new IllegalStateException("Client extensions initialization already attempted");
        attempted = true;
        initialize(REGISTRY, installedBus, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK);
    }
    static void initialize(ClientExtensionRegistry registry, IEventBus bus, Iterable<Item> items, Iterable<Block> blocks) {
        registry.readLegacyBlocks(blocks);
        registry.readLegacyItems(items);
        bus.post(new RegisterClientExtensionsEvent(registry));
    }
    public static Font font(ItemStack stack, Font fallback, IClientItemExtensions.FontContext context) {
        Font custom = IClientItemExtensions.of(stack).getFont(stack, context);
        return custom == null ? fallback : custom;
    }
}
