package com.stardew.craft.cooking.service;

import com.stardew.craft.menu.CookingPotMenu;
import com.stardew.craft.network.payload.CookingPotIngredientAvailabilityPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.PacketDistributor;

public final class CookingPotService {
    public static final String LAST_COOKING_POT_POS_TAG = "stardewcraft_last_cooking_pot_pos";

    private CookingPotService() {
    }

    @SuppressWarnings("null")
    public static void openForPlayer(ServerPlayer player) {
        openForPlayer(player, player.blockPosition());
    }

    @SuppressWarnings("null")
    public static void openForPlayer(ServerPlayer player, BlockPos cookingPotPos) {
        player.getPersistentData().putLong(LAST_COOKING_POT_POS_TAG, cookingPotPos.asLong());
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) -> new CookingPotMenu(containerId, playerInventory),
                Component.translatable("stardewcraft.cooking.title")
        ));
        PacketDistributor.sendToPlayer(player, CookingPotIngredientAvailabilityPayload.fromPlayer(player));
    }
}
