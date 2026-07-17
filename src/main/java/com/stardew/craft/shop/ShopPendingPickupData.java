package com.stardew.craft.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent server-side escrow for shop items that are still held on the cursor. */
@SuppressWarnings("null")
public final class ShopPendingPickupData extends SavedData {
    private static final String DATA_NAME = "stardewcraft_shop_pending_pickups";

    private final Map<UUID, List<ItemStack>> pendingByPlayer = new LinkedHashMap<>();

    public static ShopPendingPickupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public void add(UUID playerId, ItemStack stack) {
        if (playerId == null || stack == null || stack.isEmpty()) return;
        pendingByPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public List<ItemStack> take(UUID playerId, ResourceLocation itemId, int requestedQuantity) {
        if (playerId == null || itemId == null || requestedQuantity <= 0) return List.of();
        List<ItemStack> pending = pendingByPlayer.get(playerId);
        if (pending == null || pending.isEmpty()) return List.of();

        int remaining = requestedQuantity;
        List<ItemStack> claimed = new ArrayList<>();
        Iterator<ItemStack> iterator = pending.iterator();
        while (iterator.hasNext() && remaining > 0) {
            ItemStack stack = iterator.next();
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) continue;

            int count = Math.min(remaining, stack.getCount());
            claimed.add(stack.copyWithCount(count));
            stack.shrink(count);
            remaining -= count;
            if (stack.isEmpty()) iterator.remove();
        }

        if (!claimed.isEmpty()) {
            if (pending.isEmpty()) pendingByPlayer.remove(playerId);
            setDirty();
        }
        return claimed;
    }

    public List<ItemStack> takeAll(UUID playerId) {
        List<ItemStack> pending = pendingByPlayer.remove(playerId);
        if (pending == null || pending.isEmpty()) return List.of();
        setDirty();
        return pending.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());
            ListTag stacksTag = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) stacksTag.add(stack.save(registries));
            }
            if (!stacksTag.isEmpty()) {
                playerTag.put("Stacks", stacksTag);
                playersTag.add(playerTag);
            }
        }
        tag.put("Players", playersTag);
        return tag;
    }

    private static ShopPendingPickupData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShopPendingPickupData data = new ShopPendingPickupData();
        ListTag playersTag = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            if (!playerTag.hasUUID("Player")) continue;

            List<ItemStack> stacks = new ArrayList<>();
            ListTag stacksTag = playerTag.getList("Stacks", Tag.TAG_COMPOUND);
            for (int j = 0; j < stacksTag.size(); j++) {
                ItemStack stack = ItemStack.parse(registries, stacksTag.getCompound(j)).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) stacks.add(stack);
            }
            if (!stacks.isEmpty()) data.pendingByPlayer.put(playerTag.getUUID("Player"), stacks);
        }
        return data;
    }

    private static SavedData.Factory<ShopPendingPickupData> factory() {
        return new SavedData.Factory<>(ShopPendingPickupData::new, ShopPendingPickupData::load);
    }
}
