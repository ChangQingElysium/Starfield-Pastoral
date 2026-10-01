package com.stardew.craft.specialorder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpecialOrderWorldData extends SavedData {
    private static final String DATA_NAME = "stardew_special_orders";

    private final List<SpecialOrderInstance> available = new ArrayList<>();
    private final List<SpecialOrderInstance> active = new ArrayList<>();
    private final Set<String> completedOrderIds = new HashSet<>();
    private final Set<String> sharedSpecialDropFlags = new HashSet<>();
    private final Map<UUID, List<SpecialOrderInstance.DonatedItem>> returnedDonations = new LinkedHashMap<>();
    private int lastRefreshDay = Integer.MIN_VALUE;
    private boolean normalOrderAcceptedThisRefresh;

    public static SpecialOrderWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        ServerLevel storageLevel = overworld == null ? level : overworld;
        return storageLevel.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    public List<SpecialOrderInstance> available() { return available; }
    public List<SpecialOrderInstance> active() { return active; }
    public Set<String> sharedSpecialDropFlags() { return sharedSpecialDropFlags; }
    public Set<String> completedOrderIds() { return completedOrderIds; }
    public Map<UUID, List<SpecialOrderInstance.DonatedItem>> returnedDonations() { return returnedDonations; }
    public int lastRefreshDay() { return lastRefreshDay; }
    public void setLastRefreshDay(int lastRefreshDay) { this.lastRefreshDay = lastRefreshDay; setDirty(); }
    public boolean normalOrderAcceptedThisRefresh() { return normalOrderAcceptedThisRefresh; }
    public void setNormalOrderAcceptedThisRefresh(boolean value) { this.normalOrderAcceptedThisRefresh = value; setDirty(); }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        ListTag availableList = new ListTag();
        for (SpecialOrderInstance order : available) {
            availableList.add(order.save());
        }
        tag.put("Available", availableList);
        ListTag activeList = new ListTag();
        for (SpecialOrderInstance order : active) {
            activeList.add(order.save());
        }
        tag.put("Active", activeList);
        ListTag completedList = new ListTag();
        for (String id : completedOrderIds) {
            completedList.add(StringTag.valueOf(id));
        }
        tag.put("Completed", completedList);
        ListTag dropFlags=new ListTag();for(String flag:sharedSpecialDropFlags)dropFlags.add(StringTag.valueOf(flag));
        tag.put("SharedSpecialDropFlags",dropFlags);
        CompoundTag returnedTag = new CompoundTag();
        for (Map.Entry<UUID, List<SpecialOrderInstance.DonatedItem>> entry : returnedDonations.entrySet()) {
            ListTag items = new ListTag();
            for (SpecialOrderInstance.DonatedItem item : entry.getValue()) {
                items.add(item.save());
            }
            returnedTag.put(entry.getKey().toString(), items);
        }
        tag.put("ReturnedDonations", returnedTag);
        tag.putInt("LastRefreshDay", lastRefreshDay);
        tag.putBoolean("NormalOrderAcceptedThisRefresh", normalOrderAcceptedThisRefresh);
        return tag;
    }

    private static SpecialOrderWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        SpecialOrderWorldData data = new SpecialOrderWorldData();
        for(var flag:tag.getList("SharedSpecialDropFlags",8))data.sharedSpecialDropFlags.add(flag.getAsString());
        ListTag availableList = tag.getList("Available", 10);
        for (int i = 0; i < availableList.size(); i++) {
            data.available.add(SpecialOrderInstance.load(availableList.getCompound(i)));
        }
        ListTag activeList = tag.getList("Active", 10);
        for (int i = 0; i < activeList.size(); i++) {
            data.active.add(SpecialOrderInstance.load(activeList.getCompound(i)));
        }
        ListTag completedList = tag.getList("Completed", 8);
        for (int i = 0; i < completedList.size(); i++) {
            data.completedOrderIds.add(completedList.getString(i));
        }
        CompoundTag returnedTag = tag.getCompound("ReturnedDonations");
        for (String key : returnedTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                ListTag items = returnedTag.getList(key, 10);
                List<SpecialOrderInstance.DonatedItem> out = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    out.add(SpecialOrderInstance.DonatedItem.load(items.getCompound(i)));
                }
                if (!out.isEmpty()) {
                    data.returnedDonations.put(playerId, out);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.lastRefreshDay = tag.contains("LastRefreshDay") ? tag.getInt("LastRefreshDay") : Integer.MIN_VALUE;
        data.normalOrderAcceptedThisRefresh = tag.getBoolean("NormalOrderAcceptedThisRefresh");
        return data;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<SpecialOrderWorldData> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(SpecialOrderWorldData::new, SpecialOrderWorldData::load);
    }
}
