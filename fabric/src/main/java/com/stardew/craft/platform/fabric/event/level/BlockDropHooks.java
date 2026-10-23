package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockDropHooks {
    private static final ThreadLocal<Deque<Capture>> CAPTURES = ThreadLocal.withInitial(ArrayDeque::new);
    private BlockDropHooks() {}

    public static void run(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity,
            Entity breaker, ItemStack tool, Runnable nativeDrops) {
        if (!(level instanceof ServerLevel server)) { nativeDrops.run(); return; }
        var capture = new Capture(level);
        CAPTURES.get().push(capture);
        try { nativeDrops.run(); }
        finally {
            if (CAPTURES.get().pop() != capture) throw new IllegalStateException("Block drop captures out of order");
            if (CAPTURES.get().isEmpty()) CAPTURES.remove();
        }
        var event = new BlockDropsEvent(server, pos, state, blockEntity, capture.items, breaker, tool);
        publish(event, level::addFreshEntity, () -> state.spawnAfterBreak(server, pos, tool, false),
                amount -> state.getBlock().popExperience(server, pos, amount));
    }

    public static void publish(BlockDropsEvent event, Consumer<ItemEntity> spawn, Runnable afterBreak, IntConsumer experience) {
        FabricEvents.GAME.post(event);
        if (event.isCanceled()) return;
        event.getDrops().forEach(spawn);
        afterBreak.run();
        if (event.getDroppedExperience() > 0) experience.accept(event.getDroppedExperience());
    }

    public static boolean capture(Level level, ItemEntity item) {
        Capture capture = CAPTURES.get().peek();
        if (capture == null) { CAPTURES.remove(); return false; }
        if (capture.level != level) return false;
        capture.items.add(item);
        return true;
    }

    public static boolean isCapturing(Level level) {
        Capture capture = CAPTURES.get().peek();
        if (capture == null) CAPTURES.remove();
        return capture != null && capture.level == level;
    }

    /** The six vanilla Neo getExpDrop implementations, before the native enchantment transform. */
    public static int experience(ServerLevel level, BlockState state, ItemStack tool) {
        var block = state.getBlock();
        int amount = 0;
        if (block instanceof DropExperienceBlock ore) amount = ore.xpRange.sample(level.random);
        else if (block instanceof RedStoneOreBlock) amount = UniformInt.of(1, 5).sample(level.random);
        else if (block instanceof SculkCatalystBlock catalyst) amount = catalyst.xpRange.sample(level.random);
        else if (block instanceof SculkSensorBlock || block instanceof SculkShriekerBlock) amount = 5;
        else if (block instanceof SpawnerBlock) amount = 15 + level.random.nextInt(15) + level.random.nextInt(15);
        return EnchantmentHelper.processBlockExperience(level, tool, amount);
    }

    private static final class Capture {
        private final Level level;
        private final List<ItemEntity> items = new ArrayList<>();
        private Capture(Level level) { this.level = level; }
    }
}
