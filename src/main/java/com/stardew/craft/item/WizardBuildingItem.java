package com.stardew.craft.item;

import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A non-transferable Wizard building item whose ownership is fixed on acquisition.
 */
@SuppressWarnings("null")
public final class WizardBuildingItem extends StardewBlockItem {
    private static final String TAG_OWNER = "WizardBuildingOwner";
    private static final String TAG_OWNER_NAME = "WizardBuildingOwnerName";

    public WizardBuildingItem(Block block, String descriptionKey, Properties properties) {
        super(block, "stardewcraft.type.utility", -1, descriptionKey, properties);
    }

    public static void bindTo(ItemStack stack, ServerPlayer player) {
        bindTo(stack, player.getUUID(), PlayerDisplayName.get(player));
    }

    /**
     * Binds an unowned stack. Existing ownership is intentionally immutable.
     */
    public static void bindTo(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(TAG_OWNER)) {
            return;
        }
        tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_OWNER_NAME, ownerName == null ? "" : ownerName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getString(TAG_OWNER_NAME);
    }

    public static boolean isOwnedBy(ItemStack stack, UUID playerId) {
        UUID owner = getOwner(stack);
        return owner != null && owner.equals(playerId);
    }

    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
                              int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && getOwner(stack) == null) {
            bindTo(stack, player);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = context.getItemInHand();
            if (getOwner(stack) == null) {
                bindTo(stack, player);
            }
            if (!isOwnedBy(stack, player.getUUID())) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.owner_only", ownerLabel(stack)), true);
                return InteractionResult.FAIL;
            }
            FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm == null) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.farm_only"), true);
                return InteractionResult.FAIL;
            }
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        UUID owner = getOwner(stack);
        if (owner == null) {
            tooltipComponents.add(Component.translatable(
                    "tooltip.stardewcraft.wizard_building.owner_unassigned")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.add(Component.translatable(
                    "tooltip.stardewcraft.wizard_building.owner", ownerLabel(stack))
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltipComponents.add(Component.translatable(
                "tooltip.stardewcraft.wizard_building.bound")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.stardewcraft.wizard_building.farm_only")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    private static String ownerLabel(ItemStack stack) {
        String name = getOwnerName(stack).trim();
        if (!name.isBlank()) {
            return name;
        }
        UUID owner = getOwner(stack);
        return owner == null ? "?" : owner.toString();
    }
}
