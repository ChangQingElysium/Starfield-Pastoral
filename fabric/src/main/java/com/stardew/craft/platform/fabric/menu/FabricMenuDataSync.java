package com.stardew.craft.platform.fabric.menu;

import com.stardew.craft.platform.fabric.network.PacketDistributor;
import com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;

/** The 32-bit DataSlot behavior that NeoForge previously supplied through its own network payload. */
public final class FabricMenuDataSync {
    /** Include this in the common FabricNetwork.initialize registration callback. */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("fabric-menu-data/1").playToClient(
                MenuDataPayload.TYPE, MenuDataPayload.STREAM_CODEC, MenuDataPayload::handle);
    }

    public static ContainerSynchronizer wrap(AbstractContainerMenu menu, ContainerSynchronizer vanilla,
            ServerPlayer recipient) {
        Objects.requireNonNull(menu, "Menu");
        Objects.requireNonNull(vanilla, "Vanilla synchronizer");
        Objects.requireNonNull(recipient, "Menu recipient");
        // InventoryMenu has no network MenuType and getType() deliberately throws for it.
        if (menu == recipient.inventoryMenu) return vanilla;
        if (vanilla instanceof FullIntMenuSynchronizer existing && existing.isFor(menu)) return vanilla;
        ResourceLocation type = BuiltInRegistries.MENU.getKey(menu.getType());
        if (!needsAdapter(type)) return vanilla;
        return new FullIntMenuSynchronizer(menu, type, vanilla,
                payload -> PacketDistributor.sendToPlayer(recipient, payload));
    }

    static boolean needsAdapter(ResourceLocation menuType) {
        return menuType != null && "stardewcraft".equals(menuType.getNamespace());
    }

    private FabricMenuDataSync() {}
}
