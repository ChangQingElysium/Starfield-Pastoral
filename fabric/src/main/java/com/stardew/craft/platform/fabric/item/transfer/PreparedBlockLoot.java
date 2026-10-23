package com.stardew.craft.platform.fabric.item.transfer;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Vanilla crop getDrops with its original loot context and RNG source, reserved rather than spent. */
public record PreparedBlockLoot(List<ItemStack> outputs, BooleanSupplier stillValid) {
    public PreparedBlockLoot { outputs = outputs.stream().map(ItemStack::copy).toList(); }
    @Override public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    public static PreparedBlockLoot prepare(ServerLevel level, BlockPos pos, BlockState state, TransactionContext transaction) {
        return calculate(level, pos, state, transaction);
    }
    public static List<ItemStack> preview(ServerLevel level, BlockPos pos, BlockState state) {
        return calculate(level, pos, state, null).outputs();
    }
    private static PreparedBlockLoot calculate(ServerLevel level, BlockPos pos, BlockState state, TransactionContext transaction) {
        var key = state.getBlock().getLootTable();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        if (!(table instanceof LootTableRandomSequence named)) {
            throw new UnsupportedOperationException("Garden vanilla loot requires its native LootTable sequence accessor Mixin");
        }
        var sequence = named.stardewcraft$randomSequence();
        RandomSource random = sequence.isPresent()
                ? transaction == null ? TransactionalLootRandom.preview(level.getRandomSequences(), sequence.get())
                    : TransactionalLootRandom.stream(level.getRandomSequences(), sequence.get(), transaction)
                : transaction == null ? TransactionalRandom.preview(level.random) : TransactionalRandom.stream(level.random, transaction);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);
        return new PreparedBlockLoot(table.getRandomItems(params, random),
                () -> level.getServer().reloadableRegistries().getLootTable(key) == table);
    }
}
