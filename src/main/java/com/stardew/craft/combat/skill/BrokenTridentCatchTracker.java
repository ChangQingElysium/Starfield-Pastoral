package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.BrokenTridentCatchPayload;
import com.stardew.craft.core.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BrokenTridentCatchTracker {

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    private BrokenTridentCatchTracker() {}

    public record CatchSnapshot(long endTick) {}

    @SuppressWarnings("null")
    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        Long prevEnd = ACTIVE.get(player.getUUID());
        boolean wasActive = prevEnd != null && nowTick < prevEnd;
        ACTIVE.put(player.getUUID(), nowTick + durationTicks);
        PacketDistributor.sendToPlayer(player, new BrokenTridentCatchPayload(true, durationTicks));
        if (!wasActive) {
            player.displayClientMessage(
                Component.translatable("stardewcraft.hud.fish_catch").withStyle(ChatFormatting.AQUA),
                true
            );
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new BrokenTridentCatchPayload(false, 0));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) {
            return false;
        }
        Long endTick = ACTIVE.get(player.getUUID());
        if (endTick == null) {
            return false;
        }
        if (nowTick >= endTick) {
            clear(player);
            return false;
        }
        return true;
    }

    public static boolean consume(ServerPlayer player, long nowTick) {
        if (!isActive(player, nowTick)) {
            return false;
        }
        clear(player);
        return true;
    }

    public static Optional<CatchSnapshot> consumeForBegin(
            ServerPlayer player,
            long nowTick
    ) {
        if (!isActive(player, nowTick)) {
            return Optional.empty();
        }
        Long endTick = ACTIVE.get(player.getUUID());
        if (endTick == null) {
            return Optional.empty();
        }
        clear(player);
        return Optional.of(new CatchSnapshot(endTick));
    }

    public static void restore(
            ServerPlayer player,
            CatchSnapshot snapshot,
            long nowTick
    ) {
        if (player == null || snapshot == null
                || snapshot.endTick() <= nowTick) {
            return;
        }
        ACTIVE.put(player.getUUID(), snapshot.endTick());
        int remainingTicks = (int) Math.min(
                Integer.MAX_VALUE,
                snapshot.endTick() - nowTick
        );
        PacketDistributor.sendToPlayer(
                player,
                new BrokenTridentCatchPayload(true, remainingTicks)
        );
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        Long endTick = ACTIVE.get(player.getUUID());
        if (endTick == null) {
            return;
        }
        if (nowTick >= endTick) {
            clear(player);
        }
    }

    @SuppressWarnings("null")
    public static boolean hasFishInInventory(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ModTags.Items.ALL_FISHING_CATCHES)) {
                return true;
            }
        }
        return false;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
