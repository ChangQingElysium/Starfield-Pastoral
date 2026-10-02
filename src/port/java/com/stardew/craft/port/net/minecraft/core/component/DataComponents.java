package com.stardew.craft.port.net.minecraft.core.component;

import com.stardew.craft.port.PortItemNbt;
import com.stardew.craft.port.net.minecraft.world.item.alchemy.PotionContents;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData;
import com.stardew.craft.port.net.minecraft.world.item.component.DyedItemColor;
import com.stardew.craft.port.net.minecraft.world.item.component.Fireworks;
import com.stardew.craft.port.net.minecraft.world.item.component.ItemAttributeModifiers;
import com.stardew.craft.port.net.minecraft.world.item.component.ItemContainerContents;
import com.stardew.craft.port.net.minecraft.world.item.component.ItemLore;
import com.stardew.craft.port.net.minecraft.world.item.component.Unbreakable;
import com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * The 1.21 item data components used by the mod, each mapped onto the vanilla 1.20.1 ItemStack NBT:
 *
 * <pre>
 * custom_data           root tag minus PortItemNbt.RESERVED_ROOT_KEYS
 * custom_model_data     CustomModelData (int)
 * block_state           BlockStateTag {property: "value"}
 * custom_name           display.Name (JSON)
 * item_name             stardewcraft:ItemName (JSON)         read falls back to the item name
 * lore                  display.Lore [JSON]                  absent = ItemLore.EMPTY
 * enchantments          Enchantments [{id, lvl}] + HideFlags ENCHANTMENTS   absent = ItemEnchantments.EMPTY
 * stored_enchantments   StoredEnchantments [{id, lvl}] + HideFlags ADDITIONAL (EMPTY default on enchanted books)
 * block_entity_data     BlockEntityTag minus Items
 * container             BlockEntityTag.Items [{Slot, id, Count, tag}]
 * unbreakable           Unbreakable:1b + HideFlags UNBREAKABLE
 * trim                  Trim {material, pattern} (ArmorTrim codec with side registry access)
 * dyed_color            display.color + HideFlags DYE
 * potion_contents       Potion / CustomPotionColor / CustomPotionEffects
 * food                  read-only: Item#getFoodProperties(stack, null)
 * fireworks             Fireworks {Flight, Explosions}
 * attribute_modifiers   AttributeModifiers [...] + HideFlags MODIFIERS
 * </pre>
 */
public final class DataComponents {
    private static final String BLOCK_STATE_TAG = "BlockStateTag";
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String CONTAINER_ITEMS = "Items";

    public static final DataComponentType<CustomData> CUSTOM_DATA = register("custom_data",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public CustomData read(ItemStack stack) {
                    CompoundTag tag = stack.getTag();
                    if (tag == null) {
                        return null;
                    }
                    CompoundTag custom = new CompoundTag();
                    for (String key : tag.getAllKeys()) {
                        if (!PortItemNbt.RESERVED_ROOT_KEYS.contains(key)) {
                            custom.put(key, tag.get(key).copy());
                        }
                    }
                    return custom.isEmpty() ? (tag.getBoolean(PortItemNbt.EMPTY_CUSTOM_DATA) ? CustomData.EMPTY : null)
                            : CustomData.of(custom);
                }

                @Override
                public void write(ItemStack stack, CustomData value) {
                    if (PortItemNbt.immutable(stack)) {
                        return;
                    }
                    clear(stack);
                    if (value.isEmpty()) {
                        PortItemNbt.putRoot(stack, PortItemNbt.EMPTY_CUSTOM_DATA, net.minecraft.nbt.ByteTag.valueOf(true));
                        return;
                    }
                    CompoundTag root = stack.getOrCreateTag();
                    CompoundTag custom = value.copyTag();
                    for (String key : custom.getAllKeys()) {
                        root.put(key, custom.get(key));
                    }
                }

                @Override
                public void clear(ItemStack stack) {
                    CompoundTag tag = stack.getTag();
                    if (tag == null || PortItemNbt.immutable(stack)) {
                        return;
                    }
                    tag.remove(PortItemNbt.EMPTY_CUSTOM_DATA);
                    for (String key : List.copyOf(tag.getAllKeys())) {
                        if (!PortItemNbt.RESERVED_ROOT_KEYS.contains(key)) {
                            tag.remove(key);
                        }
                    }
                    if (tag.isEmpty()) {
                        stack.setTag(null);
                    }
                }
            });

    public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA = register("custom_model_data",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public CustomModelData read(ItemStack stack) {
                    Tag tag = PortItemNbt.root(stack, "CustomModelData");
                    return tag instanceof net.minecraft.nbt.NumericTag numeric ? new CustomModelData(numeric.getAsInt()) : null;
                }

                @Override
                public void write(ItemStack stack, CustomModelData value) {
                    PortItemNbt.putRoot(stack, "CustomModelData", IntTag.valueOf(value.value()));
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, "CustomModelData");
                }
            });

    public static final DataComponentType<BlockItemStateProperties> BLOCK_STATE = register("block_state",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public BlockItemStateProperties read(ItemStack stack) {
                    CompoundTag tag = PortItemNbt.compound(stack, BLOCK_STATE_TAG);
                    if (tag == null) {
                        return null;
                    }
                    Map<String, String> properties = new LinkedHashMap<>();
                    for (String key : tag.getAllKeys()) {
                        properties.put(key, tag.get(key).getAsString());
                    }
                    return new BlockItemStateProperties(properties);
                }

                @Override
                public void write(ItemStack stack, BlockItemStateProperties value) {
                    CompoundTag tag = new CompoundTag();
                    value.properties().forEach(tag::putString);
                    PortItemNbt.putRoot(stack, BLOCK_STATE_TAG, tag);
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, BLOCK_STATE_TAG);
                }
            });

    public static final DataComponentType<Component> CUSTOM_NAME = register("custom_name",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public Component read(ItemStack stack) {
                    return displayName(stack);
                }

                @Override
                public void write(ItemStack stack, Component value) {
                    if (!PortItemNbt.immutable(stack)) {
                        stack.setHoverName(value);
                    }
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeChild(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_DISPLAY_NAME);
                }
            });

    /** PORT(1.20.1): explicit base names have a port-owned key; display.Name stays interoperable custom_name. */
    public static final DataComponentType<Component> ITEM_NAME = register("item_name",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public Component read(ItemStack stack) {
                    if (stack.isEmpty()) {
                        return null;
                    }
                    Component name = PortItemNbt.itemName(stack);
                    return name != null ? name : stack.getItem().getName(stack);
                }

                @Override
                public void write(ItemStack stack, Component value) {
                    PortItemNbt.putRoot(stack, PortItemNbt.ITEM_NAME, StringTag.valueOf(Component.Serializer.toJson(value)));
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, PortItemNbt.ITEM_NAME);
                }
            });

    public static final DataComponentType<ItemLore> LORE = register("lore",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public ItemLore read(ItemStack stack) {
                    if (stack.isEmpty()) {
                        return null;
                    }
                    Tag raw = PortItemNbt.child(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_LORE);
                    if (!(raw instanceof ListTag list) || list.getElementType() != Tag.TAG_STRING) {
                        return ItemLore.EMPTY;
                    }
                    List<Component> lines = new ArrayList<>();
                    for (int i = 0; i < list.size() && i < ItemLore.MAX_LINES; i++) {
                        Component line = parseJson(list.getString(i));
                        if (line != null) {
                            lines.add(line);
                        }
                    }
                    return new ItemLore(lines);
                }

                @Override
                public void write(ItemStack stack, ItemLore value) {
                    if (value.lines().isEmpty()) {
                        clear(stack);
                        return;
                    }
                    ListTag list = new ListTag();
                    for (Component line : value.lines()) {
                        list.add(StringTag.valueOf(Component.Serializer.toJson(line)));
                    }
                    PortItemNbt.putChild(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_LORE, list);
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeChild(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_LORE);
                }
            });

    public static final DataComponentType<ItemEnchantments> ENCHANTMENTS = register("enchantments",
            enchantments(ItemStack.TAG_ENCH, ItemStack.TooltipPart.ENCHANTMENTS, false));

    public static final DataComponentType<ItemEnchantments> STORED_ENCHANTMENTS = register("stored_enchantments",
            enchantments(EnchantedBookItem.TAG_STORED_ENCHANTMENTS, ItemStack.TooltipPart.ADDITIONAL, true));

    public static final DataComponentType<CustomData> BLOCK_ENTITY_DATA = register("block_entity_data",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public CustomData read(ItemStack stack) {
                    CompoundTag tag = PortItemNbt.compound(stack, BLOCK_ENTITY_TAG);
                    if (tag == null) {
                        return null;
                    }
                    CompoundTag data = tag.copy();
                    data.remove(CONTAINER_ITEMS);
                    return data.isEmpty() ? null : CustomData.of(data);
                }

                @Override
                public void write(ItemStack stack, CustomData value) {
                    if (PortItemNbt.immutable(stack)) {
                        return;
                    }
                    CompoundTag data = value.copyTag();
                    Tag items = PortItemNbt.child(stack, BLOCK_ENTITY_TAG, CONTAINER_ITEMS);
                    if (items != null && !data.contains(CONTAINER_ITEMS)) {
                        data.put(CONTAINER_ITEMS, items.copy());
                    }
                    if (data.isEmpty()) {
                        stack.removeTagKey(BLOCK_ENTITY_TAG);
                    } else {
                        stack.getOrCreateTag().put(BLOCK_ENTITY_TAG, data);
                    }
                }

                @Override
                public void clear(ItemStack stack) {
                    CompoundTag tag = PortItemNbt.compound(stack, BLOCK_ENTITY_TAG);
                    if (tag == null || PortItemNbt.immutable(stack)) {
                        return;
                    }
                    Tag items = tag.get(CONTAINER_ITEMS);
                    stack.removeTagKey(BLOCK_ENTITY_TAG);
                    if (items != null) {
                        PortItemNbt.putChild(stack, BLOCK_ENTITY_TAG, CONTAINER_ITEMS, items);
                    }
                }
            });

    public static final DataComponentType<ItemContainerContents> CONTAINER = register("container",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public ItemContainerContents read(ItemStack stack) {
                    Tag raw = PortItemNbt.child(stack, BLOCK_ENTITY_TAG, CONTAINER_ITEMS);
                    return raw instanceof ListTag list ? ItemContainerContents.fromItemsTag(list) : null;
                }

                @Override
                public void write(ItemStack stack, ItemContainerContents value) {
                    PortItemNbt.putChild(stack, BLOCK_ENTITY_TAG, CONTAINER_ITEMS, value.toItemsTag());
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeChild(stack, BLOCK_ENTITY_TAG, CONTAINER_ITEMS);
                }
            });

    public static final DataComponentType<Unbreakable> UNBREAKABLE = register("unbreakable",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public Unbreakable read(ItemStack stack) {
                    CompoundTag tag = stack.getTag();
                    return tag != null && tag.getBoolean("Unbreakable")
                            ? new Unbreakable(PortItemNbt.shown(stack, ItemStack.TooltipPart.UNBREAKABLE)) : null;
                }

                @Override
                public void write(ItemStack stack, Unbreakable value) {
                    PortItemNbt.putRoot(stack, "Unbreakable", net.minecraft.nbt.ByteTag.ONE);
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.UNBREAKABLE, value.showInTooltip());
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, "Unbreakable");
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.UNBREAKABLE, true);
                }
            });

    public static final DataComponentType<ArmorTrim> TRIM = register("trim",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public ArmorTrim read(ItemStack stack) {
                    if (PortItemNbt.compound(stack, ArmorTrim.TAG_TRIM_ID) == null) {
                        return null;
                    }
                    return ArmorTrim.getTrim(PortItemNbt.registryAccess(), stack).orElse(null);
                }

                @Override
                public void write(ItemStack stack, ArmorTrim value) {
                    if (!PortItemNbt.immutable(stack)
                            && !ArmorTrim.setTrim(PortItemNbt.registryAccess(), stack, value)) {
                        throw new IllegalStateException("Could not encode armor trim " + value + " on " + stack);
                    }
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, ArmorTrim.TAG_TRIM_ID);
                }
            });

    public static final DataComponentType<DyedItemColor> DYED_COLOR = register("dyed_color",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public DyedItemColor read(ItemStack stack) {
                    Tag raw = PortItemNbt.child(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_COLOR);
                    return raw instanceof net.minecraft.nbt.NumericTag numeric
                            ? new DyedItemColor(numeric.getAsInt(), PortItemNbt.shown(stack, ItemStack.TooltipPart.DYE))
                            : null;
                }

                @Override
                public void write(ItemStack stack, DyedItemColor value) {
                    PortItemNbt.putChild(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_COLOR, IntTag.valueOf(value.rgb()));
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.DYE, value.showInTooltip());
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeChild(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_COLOR);
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.DYE, true);
                }
            });

    public static final DataComponentType<PotionContents> POTION_CONTENTS = register("potion_contents",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public PotionContents read(ItemStack stack) {
                    return PotionContents.portRead(stack);
                }

                @Override
                public void write(ItemStack stack, PotionContents value) {
                    if (!PortItemNbt.immutable(stack)) {
                        value.portWrite(stack);
                    }
                }

                @Override
                public void clear(ItemStack stack) {
                    if (!PortItemNbt.immutable(stack)) {
                        PotionContents.portClear(stack);
                    }
                }
            });

    /** PORT(1.20.1): food is an Item property on 1.20.1, so this component is read-only. */
    public static final DataComponentType<FoodProperties> FOOD = register("food",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public FoodProperties read(ItemStack stack) {
                    return stack.isEmpty() ? null : stack.getItem().getFoodProperties(stack, null);
                }

                @Override
                public void write(ItemStack stack, FoodProperties value) {
                    throw new UnsupportedOperationException(
                            "PORT(1.20.1): food is an Item property on 1.20.1 and cannot be set per stack");
                }

                @Override
                public void clear(ItemStack stack) {
                    throw new UnsupportedOperationException(
                            "PORT(1.20.1): food is an Item property on 1.20.1 and cannot be removed per stack");
                }
            });

    public static final DataComponentType<Fireworks> FIREWORKS = register("fireworks",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public Fireworks read(ItemStack stack) {
                    CompoundTag tag = PortItemNbt.compound(stack, FireworkRocketItem.TAG_FIREWORKS);
                    return tag == null ? null : Fireworks.fromTag(tag);
                }

                @Override
                public void write(ItemStack stack, Fireworks value) {
                    PortItemNbt.putRoot(stack, FireworkRocketItem.TAG_FIREWORKS, value.toTag());
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, FireworkRocketItem.TAG_FIREWORKS);
                }
            });

    public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS = register("attribute_modifiers",
            new DataComponentType.NbtAccessor<>() {
                @Override
                public ItemAttributeModifiers read(ItemStack stack) {
                    CompoundTag tag = stack.getTag();
                    if (tag == null || !tag.contains("AttributeModifiers", Tag.TAG_LIST)) {
                        return null;
                    }
                    return ItemAttributeModifiers.fromTag(tag.getList("AttributeModifiers", Tag.TAG_COMPOUND),
                            PortItemNbt.shown(stack, ItemStack.TooltipPart.MODIFIERS));
                }

                @Override
                public void write(ItemStack stack, ItemAttributeModifiers value) {
                    PortItemNbt.putRoot(stack, "AttributeModifiers", value.toTag());
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.MODIFIERS, value.showInTooltip());
                }

                @Override
                public void clear(ItemStack stack) {
                    PortItemNbt.removeRoot(stack, "AttributeModifiers");
                    PortItemNbt.setShown(stack, ItemStack.TooltipPart.MODIFIERS, true);
                }
            });

    private DataComponents() {
    }

    private static <T> DataComponentType<T> register(String name, DataComponentType.NbtAccessor<T> accessor) {
        return new DataComponentType<>(name, accessor);
    }

    private static DataComponentType.NbtAccessor<ItemEnchantments> enchantments(String key, ItemStack.TooltipPart part,
            boolean stored) {
        return new DataComponentType.NbtAccessor<>() {
            @Override
            public ItemEnchantments read(ItemStack stack) {
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
                    // 1.21 gives every item an empty enchantments component and enchanted books an empty stored one.
                    boolean hasDefault = !stack.isEmpty() && (!stored || stack.getItem() instanceof EnchantedBookItem);
                    return hasDefault ? ItemEnchantments.EMPTY.withTooltip(PortItemNbt.shown(stack, part)) : null;
                }
                return ItemEnchantments.fromTag(tag.getList(key, Tag.TAG_COMPOUND), PortItemNbt.shown(stack, part));
            }

            @Override
            public void write(ItemStack stack, ItemEnchantments value) {
                if (value.isEmpty()) {
                    PortItemNbt.removeRoot(stack, key);
                } else {
                    PortItemNbt.putRoot(stack, key, value.toTag());
                }
                PortItemNbt.setShown(stack, part, value.showInTooltip());
            }

            @Override
            public void clear(ItemStack stack) {
                PortItemNbt.removeRoot(stack, key);
                PortItemNbt.setShown(stack, part, true);
            }
        };
    }

    @Nullable
    private static Component displayName(ItemStack stack) {
        Tag raw = PortItemNbt.child(stack, ItemStack.TAG_DISPLAY, ItemStack.TAG_DISPLAY_NAME);
        return raw instanceof StringTag string ? parseJson(string.getAsString()) : null;
    }

    @Nullable
    private static Component parseJson(String json) {
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
