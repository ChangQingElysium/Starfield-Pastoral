package com.stardew.craft.gametest;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft")
@PrefixGameTestTemplate(false)
public final class PortItemComponentParityGameTests {
    private PortItemComponentParityGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void wateringCanClearRetainsExplicitEmptyCustomDataUntilRemove(GameTestHelper helper) throws Exception {
        ItemStack can = new ItemStack(ModItems.WATERING_CAN.get());
        CompoundTag action = new CompoundTag();
        action.putInt("StardewAction", 1);
        PortItemData.set(can, DataComponents.CUSTOM_DATA, CustomData.of(action));
        var clearAction = com.stardew.craft.item.tool.WateringCanItem.class.getDeclaredMethod("clearAction", ItemStack.class);
        clearAction.setAccessible(true);
        clearAction.invoke(null, can);
        helper.assertTrue(PortItemData.has(can, DataComponents.CUSTOM_DATA)
                && PortItemData.get(can, DataComponents.CUSTOM_DATA).isEmpty(), "Existing clearAction lost an explicit empty component");
        ItemStack saved = ItemStack.of(can.save(new CompoundTag()));
        helper.assertTrue(PortItemData.has(saved, DataComponents.CUSTOM_DATA) && PortItemData.get(saved, DataComponents.CUSTOM_DATA).isEmpty()
                && !ItemStack.isSameItemSameTags(saved, new ItemStack(ModItems.WATERING_CAN.get())), "Explicit empty identity did not survive native save");
        PortItemData.remove(saved, DataComponents.CUSTOM_DATA);
        helper.assertTrue(!PortItemData.has(saved, DataComponents.CUSTOM_DATA)
                && ItemStack.isSameItemSameTags(saved, new ItemStack(ModItems.WATERING_CAN.get())), "Remove did not restore ordinary NBT/stack equality");
        PortItemData.set(saved, DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData.update(DataComponents.CUSTOM_DATA, saved, tag -> {});
        helper.assertTrue(!PortItemData.has(saved, DataComponents.CUSTOM_DATA), "CustomData.update must still remove an empty result like 1.21");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void specialMineChestHasBaseNameNotPlayerRename(GameTestHelper helper) {
        ItemStack special = StardewItemDisplayStacks.stacksForItem(ModItems.MINE_CHEST.get()).get(1);
        Component base = Component.translatable("item.stardewcraft.special_mine_chest");
        helper.assertTrue(base.equals(PortItemData.get(special, DataComponents.ITEM_NAME))
                && base.equals(special.getHoverName()), "Special mine chest lost its independent base name");
        helper.assertTrue(!special.hasCustomHoverName() && !PortItemData.has(special, DataComponents.CUSTOM_NAME),
                "Catalog base name was incorrectly exposed as a player rename");
        helper.assertTrue(!special.getTooltipLines(null, TooltipFlag.NORMAL).get(0).getStyle().isItalic(),
                "A base name must not gain native custom-name italics");
        Component custom = Component.literal("Player's chest");
        // Native/anvil writes must still use display.Name, overriding but never destroying the base component.
        special.setHoverName(custom);
        helper.assertTrue(custom.equals(special.getHoverName()) && custom.equals(PortItemData.get(special, DataComponents.CUSTOM_NAME))
                && base.equals(PortItemData.get(special, DataComponents.ITEM_NAME)), "Native rename overwrote the catalog base name");
        special.resetHoverName();
        helper.assertTrue(base.equals(special.getHoverName()) && !special.hasCustomHoverName(), "Native clear-name lost the base name");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty")
    public static void independentNamesRoundTripAndCustomDataRemainInteroperable(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        Component base = Component.literal("Base diamond");
        Component custom = Component.literal("Player's diamond");
        PortItemData.set(stack, DataComponents.CUSTOM_NAME, custom);
        PortItemData.set(stack, DataComponents.ITEM_NAME, base);
        CompoundTag data = new CompoundTag();
        data.putInt("AddonSentinel", 37);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(data));
        helper.assertTrue(custom.equals(stack.getHoverName()) && base.equals(PortItemData.get(stack, DataComponents.ITEM_NAME)),
                "Setting item_name overwrote an existing native/custom name");
        helper.assertTrue(PortItemData.get(stack, DataComponents.CUSTOM_DATA).copyTag().equals(data), "Port-owned base name leaked into custom_data");
        ItemStack saved = ItemStack.of(stack.save(new CompoundTag()));
        FriendlyByteBuf network = new FriendlyByteBuf(Unpooled.buffer());
        try {
            network.writeItem(saved);
            ItemStack received = network.readItem();
            helper.assertTrue(custom.equals(received.getHoverName()) && base.equals(PortItemData.get(received, DataComponents.ITEM_NAME))
                    && data.equals(PortItemData.get(received, DataComponents.CUSTOM_DATA).copyTag()), "Native save/share-tag lost independent components");
            PortItemData.remove(received, DataComponents.CUSTOM_DATA);
            helper.assertTrue(custom.equals(received.getHoverName()) && base.equals(PortItemData.get(received, DataComponents.ITEM_NAME)),
                    "Clearing custom_data erased reserved names");
            PortItemData.remove(received, DataComponents.ITEM_NAME);
            helper.assertTrue(custom.equals(received.getHoverName()) && received.hasCustomHoverName(), "Clearing item_name erased player rename");
            PortItemData.remove(received, DataComponents.CUSTOM_NAME);
            helper.assertTrue(received.getTag() == null && ItemStack.isSameItemSameTags(received, new ItemStack(Items.DIAMOND)),
                    "Cleared explicit names changed ordinary native NBT/stacking");
        } finally {
            network.release();
        }
        helper.succeed();
    }
}
