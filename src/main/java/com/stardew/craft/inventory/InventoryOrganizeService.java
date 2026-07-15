package com.stardew.craft.inventory;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("null")
public final class InventoryOrganizeService {
    private static final List<String> TYPE_ORDER = List.of(
            "stardewcraft.type.seed",
            "stardewcraft.type.crop_seed",
            "stardewcraft.type.fertilizer",
            "stardewcraft.type.crop",
            "stardewcraft.type.forage",
            "stardewcraft.type.fruit",
            "stardewcraft.type.animal_product",
            "stardewcraft.type.fish",
            "stardewcraft.type.crabpot",
            "stardewcraft.type.legendary_fish",
            "stardewcraft.type.fishing",
            "stardewcraft.type.resource",
            "stardewcraft.type.mineral",
            "stardewcraft.type.artifact",
            "stardewcraft.type.artifact_quality",
            "stardewcraft.type.cooking_ingredient",
            "stardewcraft.type.cooking",
            "stardewcraft.type.festival_food",
            "stardewcraft.type.artisan_goods",
            "stardewcraft.type.artisan_animal_quality",
            "stardewcraft.type.monster_loot",
            "stardewcraft.type.bomb",
            "stardewcraft.type.ring",
            "stardewcraft.type.boots",
            "stardewcraft.type.trinket",
            "stardewcraft.type.craftable",
            "stardewcraft.type.utility",
            "stardewcraft.type.furniture",
            "stardewcraft.type.special_furniture",
            "stardewcraft.type.furniture_painting",
            "stardewcraft.type.carpet",
            "stardewcraft.type.festival_decoration",
            "stardewcraft.type.scarecrow",
            "stardewcraft.type.book",
            "stardewcraft.type.quest",
            "stardewcraft.type.special",
            "stardewcraft.type.magic",
            "stardewcraft.type.trash",
            "stardewcraft.type.misc"
    );

    private static final Comparator<ItemStack> STACK_COMPARATOR =
            Comparator.comparingInt(InventoryOrganizeService::typeSortValue)
                    .thenComparing(InventoryOrganizeService::typeKey)
                    .thenComparing(InventoryOrganizeService::registryPath)
                    .thenComparing(Comparator.comparingInt(InventoryOrganizeService::quality).reversed())
                    .thenComparingInt(ItemStack::getCount);

    private InventoryOrganizeService() {
    }

    public static void organizePlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        organizeList(inventory.items, inventory.getMaxStackSize());
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.connection.send(new ClientboundContainerSetSlotPacket(
                -1, player.containerMenu.getStateId(), -1, player.containerMenu.getCarried()));
    }

    public static void organizeContainer(Container container, int size) {
        int boundedSize = Math.min(size, container.getContainerSize());
        List<ItemStack> stacks = new ArrayList<>(boundedSize);
        for (int i = 0; i < boundedSize; i++) {
            stacks.add(container.getItem(i).copy());
        }

        organizeList(stacks, container.getMaxStackSize());

        for (int i = 0; i < boundedSize; i++) {
            container.setItem(i, stacks.get(i));
        }
        container.setChanged();
    }

    private static void organizeList(List<ItemStack> items, int inventoryMaxStackSize) {
        List<ItemStack> tools = new ArrayList<>();
        List<ItemStack> sortable = new ArrayList<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            if (isTool(copy)) {
                tools.add(copy);
            } else {
                sortable.add(copy);
            }
        }

        mergeStacks(sortable, inventoryMaxStackSize);
        sortable.sort(STACK_COMPARATOR);

        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        int index = 0;
        for (ItemStack stack : tools) {
            if (index >= items.size()) {
                return;
            }
            items.set(index++, stack);
        }
        for (ItemStack stack : sortable) {
            if (index >= items.size()) {
                return;
            }
            items.set(index++, stack);
        }
    }

    private static void organizeList(NonNullList<ItemStack> items, int inventoryMaxStackSize) {
        organizeList((List<ItemStack>) items, inventoryMaxStackSize);
    }

    private static void mergeStacks(List<ItemStack> stacks, int inventoryMaxStackSize) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack current = stacks.get(i);
            int max = Math.min(current.getMaxStackSize(), inventoryMaxStackSize);
            if (current.isEmpty() || max <= 1 || current.getCount() >= max) {
                continue;
            }

            for (int j = i + 1; j < stacks.size() && current.getCount() < max; ) {
                ItemStack other = stacks.get(j);
                if (!ItemStack.isSameItemSameComponents(current, other)) {
                    j++;
                    continue;
                }

                int move = Math.min(max - current.getCount(), other.getCount());
                current.grow(move);
                other.shrink(move);
                if (other.isEmpty()) {
                    stacks.remove(j);
                } else {
                    j++;
                }
            }
        }
    }

    private static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem
                || item instanceof FishingRodItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof ShearsItem
                || item instanceof ShieldItem
                || item instanceof BrushItem
                || item instanceof IStardewWeapon) {
            return true;
        }

        String typeKey = StardewItemDataApi.getTypeKey(stack);
        return "stardewcraft.type.tool".equals(typeKey)
                || "stardewcraft.type.weapon".equals(typeKey)
                || typeKey.startsWith("stardewcraft.type.weapon.");
    }

    private static int typeSortValue(ItemStack stack) {
        String typeKey = typeKey(stack);
        int index = TYPE_ORDER.indexOf(typeKey);
        if (index >= 0) {
            return index;
        }

        Item item = stack.getItem();
        if (!typeKey.isBlank()) {
            return TYPE_ORDER.size();
        }
        return TYPE_ORDER.size() + vanillaSortBucket(item);
    }

    private static int vanillaSortBucket(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return "minecraft".equals(id.getNamespace()) ? 10 : 20;
    }

    private static String typeKey(ItemStack stack) {
        return StardewItemDataApi.getTypeKey(stack);
    }

    private static String registryPath(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace() + ":" + id.getPath();
    }

    private static int quality(ItemStack stack) {
        return QualityHelper.getQuality(stack);
    }
}
