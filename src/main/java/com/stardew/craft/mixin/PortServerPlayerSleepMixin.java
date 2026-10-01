package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.stardew.craft.port.event.PortEventHooks;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * PORT(1.20.1): MinecraftForge 21.1 {@code CanPlayerSleepEvent}. The vanilla problem is computed with the Forge 1.20.1
 * checks (including Forge's {@code SleepingTimeCheckEvent}), the event may replace it, and:
 * unchanged -> the normal Forge {@code startSleepInBed} runs; another problem -> it is returned; {@code null} where
 * vanilla had a problem -> the player is put to sleep like MinecraftForge does (without the remaining vanilla checks).
 */
@Mixin(ServerPlayer.class)
public abstract class PortServerPlayerSleepMixin extends Player {
    private PortServerPlayerSleepMixin(Level level, BlockPos pos, float yRot, GameProfile profile) {
        super(level, pos, yRot, profile);
    }

    @Shadow
    private boolean bedInRange(BlockPos pos, Direction direction) {
        throw new AssertionError();
    }

    @Shadow
    private boolean bedBlocked(BlockPos pos, Direction direction) {
        throw new AssertionError();
    }

    @Shadow
    public abstract ServerLevel serverLevel();

    @WrapMethod(method = "startSleepInBed")
    private Either<Player.BedSleepingProblem, Unit> stardewcraft$canPlayerSleepEvent(BlockPos at,
            Operation<Either<Player.BedSleepingProblem, Unit>> original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Player.BedSleepingProblem vanilla = this.stardewcraft$vanillaProblem(at);
        Player.BedSleepingProblem result = PortEventHooks.fireCanPlayerSleep(self, at, vanilla);
        if (result == vanilla) return original.call(at);
        if (result != null) return Either.left(result);
        if (vanilla == Player.BedSleepingProblem.NOT_POSSIBLE_NOW || vanilla == Player.BedSleepingProblem.NOT_SAFE) {
            // MinecraftForge's vanilla supplier had already set the respawn point before these two checks.
            self.setRespawnPosition(this.level().dimension(), at, this.getYRot(), false, true);
        }
        Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(at).ifRight(unit -> {
            this.awardStat(Stats.SLEEP_IN_BED);
            CriteriaTriggers.SLEPT_IN_BED.trigger(self);
        });
        if (!this.serverLevel().canSleepThroughNights()) {
            this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
        }
        this.serverLevel().updateSleepingPlayerList();
        return either;
    }

    @Nullable
    private Player.BedSleepingProblem stardewcraft$vanillaProblem(BlockPos at) {
        BlockState state = this.level().getBlockState(at);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return null;
        Direction direction = state.getValue(HorizontalDirectionalBlock.FACING);
        if (this.isSleeping() || !this.isAlive()) return Player.BedSleepingProblem.OTHER_PROBLEM;
        if (!this.level().dimensionType().natural()) return Player.BedSleepingProblem.NOT_POSSIBLE_HERE;
        if (!this.bedInRange(at, direction)) return Player.BedSleepingProblem.TOO_FAR_AWAY;
        if (this.bedBlocked(at, direction)) return Player.BedSleepingProblem.OBSTRUCTED;
        if (!ForgeEventFactory.fireSleepingTimeCheck(this, Optional.of(at))) return Player.BedSleepingProblem.NOT_POSSIBLE_NOW;
        if (!this.isCreative()) {
            Vec3 center = Vec3.atBottomCenterOf(at);
            var monsters = this.level().getEntitiesOfClass(Monster.class,
                    new AABB(center.x() - 8.0, center.y() - 5.0, center.z() - 8.0,
                            center.x() + 8.0, center.y() + 5.0, center.z() + 8.0),
                    monster -> monster.isPreventingPlayerRest(this));
            if (!monsters.isEmpty()) return Player.BedSleepingProblem.NOT_SAFE;
        }
        return null;
    }
}
