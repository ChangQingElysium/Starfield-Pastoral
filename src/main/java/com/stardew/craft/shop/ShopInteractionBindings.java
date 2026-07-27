package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.shop.StardewShopBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** Resolves datapack-defined NPC and region interactions to shops. */
public final class ShopInteractionBindings {
    private ShopInteractionBindings() {
    }

    public static boolean tryOpenNpc(ServerPlayer player, String npcId) {
        String normalizedNpc = npcId == null ? "" : npcId.trim().toLowerCase(java.util.Locale.ROOT);
        for (var entry : ShopDataLoader.bindingSnapshot().definitions().entrySet()) {
            StardewShopBinding binding = entry.getValue();
            if (binding.npc().isEmpty() || !binding.npc().get().equalsIgnoreCase(normalizedNpc)) continue;
            if (!matchesWorld(player, binding) || !contains(binding, player.blockPosition())) continue;
            if (!conditionsMatch(player, entry.getKey(), binding)) continue;
            if (ShopService.open(player, binding.shop())) return true;
        }
        return false;
    }

    public static boolean tryOpenBlock(ServerPlayer player, BlockPos clickedPos) {
        for (var entry : ShopDataLoader.bindingSnapshot().definitions().entrySet()) {
            StardewShopBinding binding = entry.getValue();
            if (binding.npc().isPresent() || binding.min().isEmpty() || binding.max().isEmpty()) continue;
            if (!matchesWorld(player, binding) || !contains(binding, clickedPos)) continue;
            if (!conditionsMatch(player, entry.getKey(), binding)) continue;
            if (ShopService.open(player, binding.shop())) return true;
        }
        return false;
    }

    /** Read-only view of bindings whose dimension and optional box contain a position. */
    public static List<BindingStatus> inspectAt(
            ServerPlayer player,
            BlockPos position
    ) {
        return ShopDataLoader.bindingSnapshot().definitions().entrySet()
                .stream()
                .filter(entry -> matchesWorld(player, entry.getValue()))
                .filter(entry -> contains(entry.getValue(), position))
                .map(entry -> {
                    StardewShopBinding binding = entry.getValue();
                    return new BindingStatus(
                            entry.getKey(),
                            binding.shop(),
                            binding.npc(),
                            conditionsMatch(player, entry.getKey(), binding));
                })
                .toList();
    }

    private static boolean matchesWorld(ServerPlayer player, StardewShopBinding binding) {
        return binding.dimension().isEmpty()
                || binding.dimension().get().equals(player.level().dimension().location());
    }

    private static boolean contains(StardewShopBinding binding, BlockPos pos) {
        if (binding.min().isEmpty() && binding.max().isEmpty()) return true;
        if (binding.min().isEmpty() || binding.max().isEmpty()) return false;
        StardewShopBinding.BlockPoint min = binding.min().get();
        StardewShopBinding.BlockPoint max = binding.max().get();
        return pos.getX() >= Math.min(min.x(), max.x()) && pos.getX() <= Math.max(min.x(), max.x())
                && pos.getY() >= Math.min(min.y(), max.y()) && pos.getY() <= Math.max(min.y(), max.y())
                && pos.getZ() >= Math.min(min.z(), max.z()) && pos.getZ() <= Math.max(min.z(), max.z());
    }

    private static boolean conditionsMatch(
            ServerPlayer player,
            net.minecraft.resources.ResourceLocation bindingId,
            StardewShopBinding binding
    ) {
        for (var condition : binding.availableWhen()) {
            boolean allowed = StardewConditions.test(condition, StardewConditionContext.forPlayer(player))
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Shop binding] Condition failed for {}: {}", bindingId, message))
                    .orElse(false);
            if (!allowed) return false;
        }
        return true;
    }

    public record BindingStatus(
            ResourceLocation id,
            String shop,
            Optional<String> npc,
            boolean available
    ) {
    }
}
