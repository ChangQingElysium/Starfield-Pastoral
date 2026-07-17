package com.stardew.craft.lostandfound;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Farm-team shared storage for items returned through Lewis's lost-and-found service. */
public final class LostAndFoundData extends SavedData {
    private static final String DATA_NAME = "stardew_lost_and_found";

    private final Map<UUID, TeamReturns> returnsByFarm = new LinkedHashMap<>();

    public static LostAndFoundData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        ServerLevel storageLevel = overworld == null ? level : overworld;
        return storageLevel.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    TeamReturns returns(UUID farmId) {
        return returnsByFarm.computeIfAbsent(farmId, ignored -> new TeamReturns());
    }

    TeamReturns returnsIfPresent(UUID farmId) {
        return returnsByFarm.get(farmId);
    }

    void removeIfEmpty(UUID farmId) {
        TeamReturns returns = returnsByFarm.get(farmId);
        if (returns != null && returns.items.isEmpty()) {
            returnsByFarm.remove(farmId);
            setDirty();
        }
    }

    Map<UUID, TeamReturns> allReturns() {
        return returnsByFarm;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        CompoundTag farms = new CompoundTag();
        for (Map.Entry<UUID, TeamReturns> farmEntry : returnsByFarm.entrySet()) {
            TeamReturns returns = farmEntry.getValue();
            if (returns.items.isEmpty()) {
                continue;
            }
            CompoundTag returnsTag = new CompoundTag();
            returnsTag.putLong("Revision", returns.revision);
            ListTag items = new ListTag();
            for (StoredReturn stored : returns.items) {
                if (stored.stack.isEmpty()) {
                    continue;
                }
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("AvailableDay", stored.availableDay);
                itemTag.putBoolean("Announced", stored.announced);
                itemTag.put("Stack", stored.stack.saveOptional(registries));
                items.add(itemTag);
            }
            returnsTag.put("Items", items);
            farms.put(farmEntry.getKey().toString(), returnsTag);
        }
        tag.put("Farms", farms);
        return tag;
    }

    private static LostAndFoundData load(CompoundTag tag, HolderLookup.Provider registries) {
        LostAndFoundData data = new LostAndFoundData();
        CompoundTag farms = tag.getCompound("Farms");
        for (String key : farms.getAllKeys()) {
            try {
                UUID farmId = UUID.fromString(key);
                CompoundTag returnsTag = farms.getCompound(key);
                TeamReturns returns = new TeamReturns();
                returns.revision = returnsTag.getLong("Revision");
                ListTag items = returnsTag.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag itemTag = items.getCompound(i);
                    ItemStack stack = ItemStack.parseOptional(registries, itemTag.getCompound("Stack"));
                    if (!stack.isEmpty()) {
                        returns.items.add(new StoredReturn(
                            stack,
                            itemTag.getInt("AvailableDay"),
                            itemTag.getBoolean("Announced")));
                    }
                }
                if (!returns.items.isEmpty()) {
                    data.returnsByFarm.put(farmId, returns);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    private static SavedData.Factory<LostAndFoundData> factory() {
        return new SavedData.Factory<>(LostAndFoundData::new, LostAndFoundData::load);
    }

    static final class TeamReturns {
        private long revision;
        private final List<StoredReturn> items = new ArrayList<>();

        long revision() {
            return revision;
        }

        List<StoredReturn> items() {
            return items;
        }

        void changed() {
            revision++;
        }
    }

    static final class StoredReturn {
        private ItemStack stack;
        private final int availableDay;
        private boolean announced;

        StoredReturn(ItemStack stack, int availableDay, boolean announced) {
            this.stack = stack;
            this.availableDay = availableDay;
            this.announced = announced;
        }

        ItemStack stack() {
            return stack;
        }

        int availableDay() {
            return availableDay;
        }

        boolean announced() {
            return announced;
        }

        void setStack(ItemStack stack) {
            this.stack = stack;
        }

        void markAnnounced() {
            this.announced = true;
        }
    }
}
