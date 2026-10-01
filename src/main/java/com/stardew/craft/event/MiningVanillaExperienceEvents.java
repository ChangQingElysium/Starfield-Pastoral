package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineralNodeBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.function.Supplier;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MiningVanillaExperienceEvents {
    private MiningVanillaExperienceEvents() {
    }

    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        int experience = getMiningExperience(event.getState());
        if (experience <= 0) {
            return;
        }
        if (!player.hasCorrectToolForDrops(event.getState()) || hasSilkTouch(event.getTool(), player)) {
            event.setDroppedExperience(0);
            return;
        }
        event.setDroppedExperience(experience);
    }

    public static void awardNodePickupExperience(Player player, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) {
            return;
        }
        int experience = getMiningExperience(state);
        if (experience <= 0) {
            return;
        }
        ExperienceOrb.award((ServerLevel) serverPlayer.level(), Vec3.atCenterOf(serverPlayer.blockPosition()), experience);
    }

    @SafeVarargs
    private static boolean isAny(BlockState state, Supplier<? extends Block>... blocks) {
        for (Supplier<? extends Block> block : blocks) {
            if (state.is(block.get())) {
                return true;
            }
        }
        return false;
    }

    private static int getMiningExperience(BlockState state) {
        if (state.hasProperty(MineralNodeBlock.PLACED_BY_PLAYER)
                && state.getValue(MineralNodeBlock.PLACED_BY_PLAYER)) {
            return 0;
        }

        if (isAny(state, ModBlocks.QUARTZ, ModBlocks.EARTH_CRYSTAL)) return 1;
        if (state.is(ModBlocks.FROZEN_TEAR.get())) return 2;
        if (state.is(ModBlocks.FIRE_QUARTZ.get())) return 3;
        return 0;
    }

    @SuppressWarnings({ "null", "deprecation" })
    private static boolean hasSilkTouch(net.minecraft.world.item.ItemStack tool, ServerPlayer player) {
        var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = lookup.getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, tool) > 0;
    }
}