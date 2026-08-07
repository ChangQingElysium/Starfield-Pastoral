package com.stardew.craft.event;

import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public final class ResourceClumpEvents {
    private static final int TOOL_SWING_TICKS = 7;
    private static final Map<UUID, MiningState> MINING = new ConcurrentHashMap<>();

    private ResourceClumpEvents() {
    }

    private record MiningState(BlockPos pos, long startTick) {
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null || player.isCreative()) {
            return;
        }

        BlockState state = event.getState();
        if (!(state.getBlock() instanceof ResourceClumpBlock clump)) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (!player.level().isClientSide
                && player instanceof ServerPlayer serverPlayer
                && player.level().dimension() == ModDimensions.STARDEW_VALLEY
                && !PlayerStardewDataAPI.canConsumeEnergy(serverPlayer, Float.MIN_NORMAL)) {
            event.setNewSpeed(0.0F);
            return;
        }

        event.setNewSpeed(clump.getDestroySpeed(tool));

        if (!player.level().isClientSide) {
            UUID playerId = player.getUUID();
            long now = player.level().getGameTime();
            MiningState prev = MINING.get(playerId);
            BlockPos pos = event.getPosition().orElse(null);
            if (pos != null && (prev == null || !prev.pos.equals(pos))) {
                MINING.put(playerId, new MiningState(pos, now));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        BlockState state = event.getState();
        if (!(state.getBlock() instanceof ResourceClumpBlock clump)) {
            return;
        }

        event.setCanceled(true);

        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        BlockPos pos = event.getPos();
        if (!player.isCreative() && clump.getRequiredPower(tool) <= 0.0F) {
            com.stardew.craft.network.payload.HudHintPayload.send(
                    player, clump.getRequirementTranslationKey());
            return;
        }

        player.server.execute(() -> {
            BlockState currentState = player.serverLevel().getBlockState(pos);
            if (!(currentState.getBlock() instanceof ResourceClumpBlock currentClump)) {
                return;
            }
            if (!consumeEnergyForClumpBreak(player, currentClump, pos)) {
                return;
            }
            currentClump.breakClump(player.serverLevel(), pos, currentState, player);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MINING.remove(event.getEntity().getUUID());
    }

    private static boolean consumeEnergyForClumpBreak(ServerPlayer player, ResourceClumpBlock clump, BlockPos pos) {
        if (player.isCreative() || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            MINING.remove(player.getUUID());
            return true;
        }

        MiningState mining = MINING.remove(player.getUUID());
        if (mining == null || !mining.pos.equals(pos)) {
            return true;
        }

        long durationTicks = Math.max(1L, player.serverLevel().getGameTime() - mining.startTick);
        SkillType skill = clump.getRequiredTool() == ResourceClumpBlock.RequiredTool.AXE
                ? SkillType.FORAGING
                : SkillType.MINING;
        int skillLevel = PlayerStardewDataAPI.getSkillLevel(player, skill);
        float perSwing = Math.max(0.5F, 2.0F - skillLevel * 0.1F);
        float swings = (float) durationTicks / (float) TOOL_SWING_TICKS;
        float cost = perSwing * swings;
        return cost <= 0.0F || PlayerStardewDataAPI.consumeEnergyOrNotify(player, cost);
    }
}
