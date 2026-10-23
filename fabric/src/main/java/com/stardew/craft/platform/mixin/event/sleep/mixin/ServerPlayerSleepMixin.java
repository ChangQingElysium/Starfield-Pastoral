package com.stardew.craft.platform.mixin.event.sleep.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.sleep.FabricSleepEvents;
import com.stardew.craft.platform.fabric.event.sleep.SleepValidation;
import java.util.function.Consumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepMixin extends Player {
    protected ServerPlayerSleepMixin(Level level, BlockPos pos, float yaw, GameProfile profile) { super(level, pos, yaw, profile); }
    @WrapMethod(method = "startSleepInBed")
    private Either<BedSleepingProblem, Unit> stardewcraft$attempt(BlockPos pos, Operation<Either<BedSleepingProblem, Unit>> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        try (var frame = SleepValidation.open(player)) {
            // Neo skips every vanilla validation check for modded beds without FACING.
            Either<BedSleepingProblem, Unit> candidate = level().getBlockState(pos).hasProperty(HorizontalDirectionalBlock.FACING)
                    ? original.call(pos) : Either.right(Unit.INSTANCE);
            frame.finishValidation();
            return FabricSleepEvents.attempt(player, pos, candidate, () -> stardewcraft$finish(pos, player));
        }
    }
    @Unique private Either<BedSleepingProblem, Unit> stardewcraft$finish(BlockPos pos, ServerPlayer player) {
        // This is a real invokespecial to Player, including FAPI ALLOW_SLEEPING and the actual
        // startSleeping override. It is outside the native validation method's four deferred sites.
        var result = super.startSleepInBed(pos).ifRight(ignored -> {
            awardStat(Stats.SLEEP_IN_BED); CriteriaTriggers.SLEPT_IN_BED.trigger(player);
        });
        if (!player.serverLevel().canSleepThroughNights()) player.displayClientMessage(Component.translatable("sleep.not_possible"), true);
        player.serverLevel().updateSleepingPlayerList(); return result;
    }
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;"))
    private Either<BedSleepingProblem, Unit> stardewcraft$deferStart(ServerPlayer player, BlockPos pos, Operation<Either<BedSleepingProblem, Unit>> original) {
        return SleepValidation.probing(this) ? Either.right(Unit.INSTANCE) : original.call(player, pos);
    }
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/util/Either;ifRight(Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;"))
    private Either<BedSleepingProblem, Unit> stardewcraft$deferAwards(Either<BedSleepingProblem, Unit> result, Consumer<Unit> awards,
            Operation<Either<BedSleepingProblem, Unit>> original) {
        return SleepValidation.probing(this) ? result : original.call(result, awards);
    }
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void stardewcraft$deferMessage(ServerPlayer player, Component message, boolean actionbar, Operation<Void> original) {
        if (!SleepValidation.probing(this)) original.call(player, message, actionbar);
    }
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;updateSleepingPlayerList()V"))
    private void stardewcraft$deferSleepingList(ServerLevel level, Operation<Void> original) {
        if (!SleepValidation.probing(this)) original.call(level);
    }
}
