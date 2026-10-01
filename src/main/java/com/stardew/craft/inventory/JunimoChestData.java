package com.stardew.craft.inventory;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One persistent inventory per farm, independent of chest blocks, dimensions and loaded chunks. */
public final class JunimoChestData extends SavedData {
    private final Map<UUID, NonNullList<ItemStack>> inventories = new HashMap<>();
    private final Map<UUID, UUID> viewers = new HashMap<>();
    public static JunimoChestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(JunimoChestData::new, JunimoChestData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(JunimoChestData::new, JunimoChestData::load)), "stardewcraft_junimo_chests");
    }
    public NonNullList<ItemStack> items(UUID owner) {
        return inventories.computeIfAbsent(owner, ignored -> NonNullList.withSize(9, ItemStack.EMPTY));
    }
    public boolean inUse(UUID owner, MinecraftServer server) {
        UUID viewer = viewers.get(owner);
        if (viewer == null) return false;
        var player = server.getPlayerList().getPlayer(viewer);
        if (player != null && player.containerMenu instanceof com.stardew.craft.menu.WoodenChestMenu menu
                && owner.equals(menu.sharedOwner())) return true;
        viewers.remove(owner);
        return false;
    }
    public void opened(UUID owner, UUID player) { viewers.put(owner, player); }
    public void closed(UUID owner, UUID player) { viewers.remove(owner, player); }
    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        ListTag farms = new ListTag();
        inventories.forEach((owner, items) -> {
            CompoundTag farm = new CompoundTag();
            farm.putUUID("Owner", owner);
            ListTag stacks = new ListTag();
            for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", i); entry.put("Stack", PortItemStacks.save(items.get(i), registries)); stacks.add(entry);
            }
            farm.put("Items", stacks); farms.add(farm);
        });
        tag.put("Farms", farms);
        return tag;
    }
    public static JunimoChestData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new JunimoChestData();
        for (var value : tag.getList("Farms", 10)) {
            CompoundTag farm = (CompoundTag) value;
            if (!farm.hasUUID("Owner")) continue;
            var items = data.items(farm.getUUID("Owner"));
            for (var v : farm.getList("Items", 10)) {
                CompoundTag entry = (CompoundTag) v;
                int slot = entry.getInt("Slot");
                if (slot >= 0 && slot < 9) items.set(slot, PortItemStacks.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY));
            }
        }
        return data;
    }
}
