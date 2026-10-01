package com.stardew.craft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): the 1.21.1 game test server harness, so the GameTest suite runs under the same world conditions
 * as the 1.21.1 baseline (only affects {@code runGameTestServer}).
 * <ul>
 * <li>1.21 {@code GameTestServer.TEST_GAME_RULES} also sets {@code randomTickSpeed=0} and {@code doFireTick=false}
 * (1.20.1 only disables mob spawning and the weather cycle).</li>
 * <li>1.21 {@code startTests} lays the test grid out at a random far position
 * ({@code level.random.nextIntBetweenInclusive(-14999992, 14999992)} for x and z), outside the always-loaded spawn
 * chunks; 1.20.1 uses x=z=0. The grid corner is the 1.21 one (y=-59, the structure origin): the 1.21 harness
 * ({@code PortGameTestBatchRunnerHarnessMixin}) puts the structure block at {@code corner.below()}, so the structure
 * block stays at y=-60 and the structure at y=-59 as in both vanilla versions.</li>
 * </ul>
 */
@Mixin(GameTestServer.class)
public abstract class PortGameTestServerParityMixin {
    @Shadow
    @Final
    private static GameRules TEST_GAME_RULES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void stardewcraft$applyTestGameRules121(CallbackInfo ci) {
        TEST_GAME_RULES.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        TEST_GAME_RULES.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
    }

    @ModifyArg(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gametest/framework/GameTestRunner;runTestBatches(Ljava/util/Collection;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/gametest/framework/GameTestTicker;I)Ljava/util/Collection;"),
            index = 1)
    private BlockPos stardewcraft$testGridOrigin121(BlockPos original) {
        var random = ((GameTestServer) (Object) this).overworld().random;
        int x = random.nextIntBetweenInclusive(-14999992, 14999992);
        int z = random.nextIntBetweenInclusive(-14999992, 14999992);
        return new BlockPos(x, -59, z);
    }
}
