package com.stardew.craft.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21 {@code GameTestBatchFactory#fromTestFunction}: tests are grouped by batch name (same
 * {@code groupingBy} order as 1.20.1) and partitioned into batches of at most 50 tests named {@code name:index}
 * from 0. 1.20.1 partitions into 100 and numbers from 1, so twice as many tests would run at the same time as in
 * the 1.21.1 baseline.
 */
@Mixin(GameTestRunner.class)
public abstract class PortGameTestRunnerBatchMixin {
    private static final int STARDEWCRAFT$MAX_TESTS_PER_BATCH_121 = 50;

    @Inject(method = "groupTestsIntoBatches", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$groupTestsIntoBatches121(Collection<TestFunction> testFunctions,
            CallbackInfoReturnable<Collection<GameTestBatch>> cir) {
        Map<String, List<TestFunction>> byBatch = testFunctions.stream()
                .collect(Collectors.groupingBy(TestFunction::getBatchName));
        List<GameTestBatch> batches = new ArrayList<>();
        for (Map.Entry<String, List<TestFunction>> entry : byBatch.entrySet()) {
            String name = entry.getKey();
            Consumer<ServerLevel> before = GameTestRegistry.getBeforeBatchFunction(name);
            Consumer<ServerLevel> after = GameTestRegistry.getAfterBatchFunction(name);
            int index = 0;
            for (List<TestFunction> part : Lists.partition(entry.getValue(), STARDEWCRAFT$MAX_TESTS_PER_BATCH_121)) {
                batches.add(new GameTestBatch(name + ":" + index++, ImmutableList.copyOf(part), before, after));
            }
        }
        cir.setReturnValue(ImmutableList.copyOf(batches));
    }
}
