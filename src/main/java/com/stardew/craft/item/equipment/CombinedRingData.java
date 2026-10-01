package com.stardew.craft.item.equipment;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.ModItems;
import java.util.ArrayList;
import java.util.List;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;

public final class CombinedRingData {
    public static final String TAG_COMBINED_RING = "StardewCombinedRing";
    private static final String TAG_RING_IDS = "RingIds";

    private CombinedRingData() {
    }

    public static ItemStack create(ItemStack first, ItemStack second) {
        ItemStack result = new ItemStack(ModItems.COMBINED_RING.get(), 1);
        write(result, List.of(ringId(first), ringId(second)));
        return result;
    }

    public static ItemStack create(List<String> ringIds) {
        ItemStack result = new ItemStack(ModItems.COMBINED_RING.get(), 1);
        write(result, ringIds);
        return result;
    }

    public static String encodeForEquipmentSlot(ItemStack stack) {
        List<String> ringIds = read(stack);
        if (ringIds.size() != 2) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(ModItems.COMBINED_RING.get()) + "|" + String.join("|", ringIds);
    }

    public static boolean isEncodedEquipmentSlot(String value) {
        return value != null && value.startsWith(BuiltInRegistries.ITEM.getKey(ModItems.COMBINED_RING.get()) + "|");
    }

    public static ItemStack stackFromEquipmentSlot(String value) {
        if (!isEncodedEquipmentSlot(value)) {
            return stackFor(value);
        }
        List<String> ringIds = ringIdsFromEquipmentSlot(value);
        return ringIds.size() == 2 ? create(ringIds) : ItemStack.EMPTY;
    }

    public static List<ItemStack> splitEquipmentSlot(String value) {
        if (!isEncodedEquipmentSlot(value)) {
            ItemStack stack = stackFor(value);
            return stack.isEmpty() ? List.of() : List.of(stack);
        }
        List<ItemStack> rings = new ArrayList<>();
        for (String ringId : ringIdsFromEquipmentSlot(value)) {
            ItemStack ringStack = stackFor(ringId);
            if (!ringStack.isEmpty()) {
                rings.add(ringStack);
            }
        }
        return List.copyOf(rings);
    }

    public static List<String> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        if (data == null) {
            return List.of();
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_COMBINED_RING, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag combinedTag = root.getCompound(TAG_COMBINED_RING);
        ListTag ringList = combinedTag.getList(TAG_RING_IDS, Tag.TAG_STRING);
        List<String> ringIds = new ArrayList<>(ringList.size());
        for (int index = 0; index < ringList.size(); index++) {
            String ringId = ringList.getString(index);
            if (!ringId.isEmpty()) {
                ringIds.add(ringId);
            }
        }
        return List.copyOf(ringIds);
    }

    public static List<ItemStack> split(ItemStack stack) {
        List<ItemStack> rings = new ArrayList<>();
        for (String ringId : read(stack)) {
            ItemStack ringStack = stackFor(ringId);
            if (!ringStack.isEmpty()) {
                rings.add(ringStack);
            }
        }
        return List.copyOf(rings);
    }

    public static boolean isCombinedRing(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.COMBINED_RING.get()) && read(stack).size() == 2;
    }

    public static boolean isCombinableRing(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof StardewRingItem && !stack.is(ModItems.COMBINED_RING.get());
    }

    public static ItemStack stackFor(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(location)
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
    }

    private static void write(ItemStack stack, List<String> ringIds) {
        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        CompoundTag root = data != null ? data.copyTag() : new CompoundTag();
        CompoundTag combinedTag = new CompoundTag();
        ListTag ringList = new ListTag();
        for (String ringId : ringIds) {
            if (ringId != null && !ringId.isEmpty()) {
                ringList.add(StringTag.valueOf(ringId));
            }
        }
        combinedTag.put(TAG_RING_IDS, ringList);
        root.put(TAG_COMBINED_RING, combinedTag);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static List<String> ringIdsFromEquipmentSlot(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        String[] parts = value.split("\\|");
        List<String> ringIds = new ArrayList<>(2);
        for (int index = 1; index < parts.length; index++) {
            if (!parts[index].isEmpty()) {
                ringIds.add(parts[index]);
            }
        }
        return List.copyOf(ringIds);
    }

    private static String ringId(ItemStack stack) {
        if (!isCombinableRing(stack)) {
            return "";
        }
        Item item = stack.getItem();
        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        return location.toString();
    }
}
