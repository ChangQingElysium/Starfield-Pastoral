package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import joptsimple.OptionSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code Main} starts the game test server through
 * {@code GameTestServer.create(...)} right after creating the pack repository and returns: a superflat world
 * ({@code WorldPresets.FLAT}, only the preset's overworld/nether/end stems, no datapack {@code dimension/} stems),
 * creative mode, NORMAL difficulty, {@code TEST_GAME_RULES}, seed 0 without structures, every available pack enabled
 * with all feature flags. Forge 47 instead loads the ordinary dedicated-server world from {@code server.properties}
 * (normal terrain, easy, survival, default game rules, mod dimensions such as {@code stardewcraft:stardew_mining}
 * loaded as levels) and only swaps the server class. This reproduces the 1.21.1 path for the game test server only
 * ({@code -Dforge.gameTestServer=true}); ordinary dedicated servers are untouched.
 */
@Mixin(Main.class)
public abstract class PortGameTestMainMixin {
    @Inject(method = "main", remap = false, cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/Main;loadOrCreateConfig(Lnet/minecraft/server/dedicated/DedicatedServerProperties;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;ZLnet/minecraft/server/packs/repository/PackRepository;)Lnet/minecraft/server/WorldLoader$InitConfig;",
            remap = true))
    private static void stardewcraft$startGameTestServer121(String[] args, CallbackInfo ci,
            @Local OptionSet options, @Local LevelStorageSource.LevelStorageAccess storage,
            @Local PackRepository packs) {
        if (!ForgeGameTestHooks.isGametestServer()) return;
        ForgeGameTestHooks.registerGametests();
        BlockPos spawnPos = (BlockPos) options.valueOf("spawnPos");
        Collection<GameTestBatch> batches = GameTestRunner.groupTestsIntoBatches(GameTestRegistry.getAllTestFunctions());
        MinecraftServer.spin(thread -> GameTestServer.create(thread, storage, packs, batches, spawnPos));
        // As in 1.21.1: no normal resource load and no shutdown thread; the game test server exits itself.
        ci.cancel();
    }
}
