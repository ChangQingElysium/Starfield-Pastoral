package com.stardew.craft.gingerisland;

import com.stardew.craft.port.PortItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;

/** Creative and JEI share real state-bearing stacks, never duplicate registrations. */
public final class GingerIslandVariantStacks {
    public static final String MODEL_PROPERTY = "ginger_island_variant";
    private static final String NAME = "stardewcraft.ginger_island.variant_name";
    private static final String LABEL = "stardewcraft.ginger_island.state.";

    private GingerIslandVariantStacks() {}

    public static boolean isLegacyAlias(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof GingerIslandLegacyAliasBlock;
    }

    public static boolean supports(Item item) {
        return item instanceof BlockItem blockItem && (blockItem.getBlock() instanceof GingerIslandLegacyAliasBlock
                || blockItem.getBlock() instanceof GingerIslandStateDecorBlock
                || blockItem.getBlock() instanceof GingerIslandSurfaceBlock);
    }

    public static List<ItemStack> stacksFor(Item item) {
        if (!(item instanceof BlockItem blockItem)) return List.of();
        if (isLegacyAlias(item)) return List.of();
        if (blockItem.getBlock() instanceof GingerIslandStateDecorBlock decor) {
            if (!decor.hasBuildingVariants()) return List.of(new ItemStack(item));
            return decor.visualStateValues().stream().map(decor::stackForVisualState)
                    .map(GingerIslandVariantStacks::nameStack).toList();
        }
        if (blockItem.getBlock() instanceof GingerIslandSurfaceBlock surface) {
            var stacks = new ArrayList<ItemStack>();
            // The ordinary item still lets server-side natural placement choose a texture.
            stacks.add(nameStack(new ItemStack(item)));
            for (int value = 0; value < surface.variantCount(); value++) {
                ItemStack stack = new ItemStack(item);
                PortItemData.set(stack, DataComponents.BLOCK_STATE,
                        new BlockItemStateProperties(Map.of("variant", Integer.toString(value))));
                stacks.add(nameStack(stack));
            }
            return List.copyOf(stacks);
        }
        return List.of();
    }

    public static ItemStack nameStack(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return stack;
        Component label;
        if (item.getBlock() instanceof GingerIslandStateDecorBlock decor) {
            if (!decor.hasBuildingVariants()) return stack;
            String property = decor.visualStateProperty();
            String value = decor.itemState(stack).properties().get(property);
            label = property.equals("variant") ? Component.literal(Integer.toString(Integer.parseInt(value) + 1))
                    : property.equals("gem") ? Component.translatable(value.equals("empty")
                            ? LABEL + "empty" : "item.stardewcraft." + value)
                    : Component.translatable(LABEL + property + "." + value);
        } else if (item.getBlock() instanceof GingerIslandSurfaceBlock surface) {
            Integer value = surface.itemState(stack).get(GingerIslandSurfaceBlock.VARIANT);
            label = value == null ? Component.translatable(LABEL + "natural")
                    : Component.literal(Integer.toString(value + 1));
        } else return stack;
        PortItemData.set(stack, DataComponents.ITEM_NAME, Component.translatable(NAME,
                Component.translatable(stack.getItem().getDescriptionId()), label));
        return stack;
    }

    /** JEI's unbound default and its explicit default state represent one decor choice. */
    public static BlockItemStateProperties subtypeProperties(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof GingerIslandStateDecorBlock decor)
            return decor.hasBuildingVariants() ? decor.itemState(stack) : BlockItemStateProperties.EMPTY;
        if (stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof GingerIslandSurfaceBlock surface) return surface.itemState(stack);
        // Natural ground has a distinct unbound/random item and explicit fixed choices.
        return PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
    }

    /** Shared normalized override contract: authored array index divided by count - 1. */
    public static float modelValue(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return 0;
        if (item.getBlock() instanceof GingerIslandStateDecorBlock decor) {
            if (!decor.hasBuildingVariants()) return 0;
            List<String> values = decor.visualStateValues();
            int index = values.indexOf(decor.itemState(stack).properties().get(decor.visualStateProperty()));
            return values.size() < 2 ? 0 : Math.max(0, index) / (float) (values.size() - 1);
        }
        if (item.getBlock() instanceof GingerIslandSurfaceBlock surface) {
            Integer value = surface.itemState(stack).get(GingerIslandSurfaceBlock.VARIANT);
            return value == null || value < 0 || value >= surface.variantCount() || surface.variantCount() < 2
                    ? 0 : value / (float) (surface.variantCount() - 1);
        }
        return 0;
    }
}
