package com.stardew.craft.mining;

import com.stardew.craft.api.v1.world.StardewWorldLootPools;
import com.stardew.craft.api.v1.query.*;
import com.stardew.craft.world.data.WorldLootPoolData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Treasure content and pre-rolls both come from reloadable world-loot definitions. */
public final class SkullCavernTreasurePool {
    private SkullCavernTreasurePool() {}
    public static ItemStack roll(RandomSource random) { return roll(random, null); }
    public static ItemStack roll(RandomSource random, ServerPlayer player) {
        var server = player == null ? net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() : player.getServer();
        if (server == null) return ItemStack.EMPTY;
        var level = player == null ? server.overworld() : player.serverLevel();
        var special = WorldLootPoolData.resolve(StardewWorldLootPools.SKULL_CAVERN_TREASURE, "special", level, player, random);
        if (!special.isEmpty()) return com.stardew.craft.port.PortJava.getFirst(special);
        var rewards = WorldLootPoolData.resolve(StardewWorldLootPools.SKULL_CAVERN_TREASURE, "default", level, player, random);
        return rewards.isEmpty() ? ItemStack.EMPTY : com.stardew.craft.port.PortJava.getFirst(rewards);
    }
    /** Compatibility for older addon queries; slots now resolve actual data, not a Java reward switch. */
    public static void registerQuery() {
        StardewItemQueries.register(new ResourceLocation("stardewcraft:skull_treasure_slot"),
                com.mojang.serialization.Codec.intRange(0,25).fieldOf("slot").codec(), (context, slot) -> {
                    var stack = rollSlot(slot, RandomSource.create(context.random().nextLong()), context.player());
                    return stack.isEmpty() ? List.of() : List.of(stack);
                });
    }
    private static final ThreadLocal<Boolean> RESOLVING_LEGACY_SLOT = ThreadLocal.withInitial(() -> false);
    public static ItemStack rollSlot(int slot, RandomSource random, ServerPlayer player) {
        if (RESOLVING_LEGACY_SLOT.get()) return ItemStack.EMPTY;
        var definition = WorldLootPoolData.snapshot().definitions().get(new ResourceLocation("stardewcraft:skull_cavern_treasure"));
        if (definition == null || slot < 0 || slot >= definition.entries().size()) return ItemStack.EMPTY;
        var server = player == null ? net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() : player.getServer();
        if (server == null) return ItemStack.EMPTY;
        RESOLVING_LEGACY_SLOT.set(true);
        try {
            var result = StardewItemQueries.resolve(definition.entries().get(slot).query(),
                    new StardewItemQueryContext(player == null ? server.overworld() : player.serverLevel(), player, random::nextLong))
                    .getOrThrow();
            return result.isEmpty() ? ItemStack.EMPTY : result.getFirst();
        } finally { RESOLVING_LEGACY_SLOT.remove(); }
    }
}
