package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class UtilityDropHelper {
    public static final int LOW_MACHINE_VANILLA_XP = 1;
    public static final int STANDARD_MACHINE_VANILLA_XP = 2;
    public static final int PREMIUM_MACHINE_VANILLA_XP = 3;
    public static final int DEFAULT_MACHINE_VANILLA_XP = LOW_MACHINE_VANILLA_XP;

    private UtilityDropHelper() {
    }

    public static boolean tryHarvest(Level level, BlockPos pos, Player player,
                                     BooleanSupplier readyCheck, Supplier<ItemStack> harvestAction) {
        return tryHarvest(level, pos, player, readyCheck, harvestAction, DEFAULT_MACHINE_VANILLA_XP);
    }

    public static boolean tryHarvest(Level level, BlockPos pos, Player player,
                                     BooleanSupplier readyCheck, Supplier<ItemStack> harvestAction,
                                     int vanillaExperience) {
        if (!readyCheck.getAsBoolean()) {
            return false;
        }

        ItemStack product = harvestAction.get();
        if (product.isEmpty()) {
            return false;
        }

        ItemStack harvested = product.copy();
        int harvestedCount = harvested.getCount();
        boolean addedAll = player.addItem(product);
        int receivedCount = addedAll ? harvestedCount : Math.max(0, harvestedCount - product.getCount());
        if (!addedAll && !product.isEmpty()) {
            player.drop(product, false);
        }
        if (receivedCount > 0 && player instanceof ServerPlayer serverPlayer) {
            String itemId = BuiltInRegistries.ITEM.getKey(harvested.getItem()).toString();
            com.stardew.craft.quest.StardewQuestEvents.fireItemReceived(serverPlayer, itemId, receivedCount);
        }
        grantHarvestRewards(level, pos, player, vanillaExperience);
        return true;
    }

    public static void grantHarvestRewards(Level level, BlockPos pos, Player player) {
        grantHarvestRewards(level, pos, player, DEFAULT_MACHINE_VANILLA_XP);
    }

    public static void grantHarvestRewards(Level level, BlockPos pos, Player player, int vanillaExperience) {
        if (vanillaExperience > 0 && level instanceof ServerLevel serverLevel) {
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), vanillaExperience);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    @SuppressWarnings("null")
    public static void dropAutomationContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof UtilityAutomationAccess access)) {
            return;
        }

        ItemStack drop = primaryDrop(access);
        if (!drop.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop.copy());
        }

        ItemStack extra = access.getAutomationExtraDrop();
        if (!extra.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), extra.copy());
        }
    }

    static ItemStack primaryDrop(UtilityAutomationAccess access) {
        if (access == null) {
            return ItemStack.EMPTY;
        }
        ItemStack selected = access.isAutomationReady()
                ? access.getAutomationOutput()
                : access.getAutomationInput();
        return selected == null ? ItemStack.EMPTY : selected.copy();
    }
}
