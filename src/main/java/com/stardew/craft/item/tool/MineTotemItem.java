package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 矿洞图腾 — 可重复使用的矿井内传送道具。
 * 右键使用：传送到当前矿井层的入口位置（即玩家进入该层时的位置）。
 * 20 秒冷却，不可出售。
 */
@SuppressWarnings("null")
public class MineTotemItem extends Item implements IStardewItem {

    private static final int COOLDOWN_TICKS = 20 * 20; // 20 seconds

    public MineTotemItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.magic";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1; // not sellable
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // Must be in the mine dimension
        if (level.dimension() != ModMiningDimensions.STARDEW_MINING) {
            player.displayClientMessage(
                Component.translatable("item.stardewcraft.mine_totem.fail_not_in_mine")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        ServerLevel mineLevel = serverPlayer.serverLevel();

        // Get the player's current floor
        MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
        int currentFloor = (playerData != null) ? playerData.getCurrentFloor() : 0;

        // Floor 0 is the lobby — no need to teleport
        if (currentFloor <= 0) {
            player.displayClientMessage(
                Component.translatable("item.stardewcraft.mine_totem.fail_lobby")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }

        // Teleport to the floor entry (same position as teleportPlayerToFloor)
        MiningCoordinates.teleportPlayerToFloor(serverPlayer, mineLevel, currentFloor);

        // Sound + particles
        mineLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
            ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        // Apply cooldown
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        player.displayClientMessage(
            Component.translatable("item.stardewcraft.mine_totem.success")
                .withStyle(ChatFormatting.GREEN), true);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stardewcraft.mine_totem.cooldown")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
