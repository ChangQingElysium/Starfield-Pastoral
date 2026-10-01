package com.stardew.craft.item;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.blockentity.FriendshipDoorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

@SuppressWarnings("null")
public class FriendshipDoorItem extends DoubleHighBlockItem implements IStardewItem {
    private final String itemTypeKey;
    private final int sellPrice;

    public FriendshipDoorItem(Block block, String itemTypeKey, int sellPrice, Item.Properties properties) {
        super(block, properties);
        this.itemTypeKey = itemTypeKey;
        this.sellPrice = sellPrice;
    }

    @Override
    public String getItemTypeKey() {
        return itemTypeKey;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice <= 0 ? -1 : sellPrice;
    }

    public static ItemStack create(String npcId, int requiredPoints) {
        return create(FriendshipDoorBlockEntity.parseNpcIds(npcId), requiredPoints);
    }

    public static ItemStack create(List<String> npcIds, int requiredPoints) {
        ItemStack stack = new ItemStack(ModItems.FRIENDSHIP_DOOR.get());
        applyBinding(stack, npcIds, requiredPoints);
        return stack;
    }

    public static void applyBinding(ItemStack stack, String npcId, int requiredPoints) {
        applyBinding(stack, FriendshipDoorBlockEntity.parseNpcIds(npcId), requiredPoints);
    }

    public static void applyBinding(ItemStack stack, List<String> npcIds, int requiredPoints) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        List<String> normalized = FriendshipDoorBlockEntity.normalizeNpcIds(npcIds);
        if (!normalized.isEmpty()) {
            tag.putString(FriendshipDoorBlockEntity.TAG_NPC_ID, com.stardew.craft.port.PortJava.getFirst(normalized));
            ListTag list = new ListTag();
            for (String npcId : normalized) {
                list.add(StringTag.valueOf(npcId));
            }
            tag.put(FriendshipDoorBlockEntity.TAG_NPC_IDS, list);
        }
        tag.putInt(FriendshipDoorBlockEntity.TAG_REQUIRED_POINTS, Math.max(0, requiredPoints));
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getNpcId(ItemStack stack) {
        List<String> npcIds = getNpcIds(stack);
        return npcIds.isEmpty() ? "" : com.stardew.craft.port.PortJava.getFirst(npcIds);
    }

    public static List<String> getNpcIds(ItemStack stack) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return FriendshipDoorBlockEntity.readNpcIds(tag);
    }

    public static int getRequiredPoints(ItemStack stack) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(FriendshipDoorBlockEntity.TAG_REQUIRED_POINTS)
                ? Math.max(0, tag.getInt(FriendshipDoorBlockEntity.TAG_REQUIRED_POINTS))
                : FriendshipDoorBlockEntity.DEFAULT_REQUIRED_POINTS;
    }

    @Override
    public Component getName(ItemStack stack) {
        List<String> npcIds = getNpcIds(stack);
        if (!npcIds.isEmpty()) {
            if (npcIds.size() > 1) {
                return Component.translatable("item.stardewcraft.friendship_door.bound_group_name", npcDisplayName(npcIds));
            }
            return Component.translatable("item.stardewcraft.friendship_door.bound_name", npcDisplayName(npcIds));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        List<String> npcIds = getNpcIds(stack);
        if (npcIds.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.stardewcraft.friendship_door.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        int requiredPoints = getRequiredPoints(stack);
        int requiredHearts = Math.max(0, (requiredPoints + 249) / 250);
        tooltipComponents.add(Component.translatable("tooltip.stardewcraft.friendship_door.npc", npcDisplayName(npcIds)).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.stardewcraft.friendship_door.required", requiredHearts, requiredPoints).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        List<String> npcIds = getNpcIds(stack);
        if (npcIds.isEmpty()) {
            return changed;
        }
        int requiredPoints = getRequiredPoints(stack);
        changed |= applyBinding(level, pos, npcIds, requiredPoints);
        changed |= applyBinding(level, pos.above(), npcIds, requiredPoints);
        return changed;
    }

    private static boolean applyBinding(Level level, BlockPos pos, List<String> npcIds, int requiredPoints) {
        if (level.getBlockEntity(pos) instanceof FriendshipDoorBlockEntity door) {
            door.setBinding(npcIds, requiredPoints);
            return true;
        }
        return false;
    }

    private static Component npcDisplayName(List<String> npcIds) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < npcIds.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(" / "));
            }
            result.append(Component.translatable("entity.stardewcraft.npc." + npcIds.get(i)));
        }
        return result;
    }
}