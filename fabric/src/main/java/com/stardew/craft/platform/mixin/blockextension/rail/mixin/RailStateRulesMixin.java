package com.stardew.craft.platform.mixin.blockextension.rail.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.blockextension.rail.RailBehaviorProvider;
import com.stardew.craft.platform.fabric.blockextension.rail.RailHooks;
import com.stardew.craft.platform.fabric.blockextension.rail.RailStatePosition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RailState.class)
abstract class RailStateRulesMixin implements RailStatePosition {
    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private BaseRailBlock block;
    @Shadow private BlockState state;
    @Shadow @Final private List<BlockPos> connections;
    @Unique private boolean stardewcraft$canMakeSlopes = true;
    @Override public BlockPos stardewcraft$railPosition() { return pos; }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BaseRailBlock;isStraight()Z"), require = 1)
    private boolean stardewcraft$flexibility(BaseRailBlock block, Operation<Boolean> original, Level level, BlockPos pos, BlockState state) {
        return block instanceof RailBehaviorProvider provider ? !provider.isFlexibleRail(state, level, pos) : original.call(block);
    }
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void stardewcraft$cacheSlopeRule(Level level, BlockPos pos, BlockState state, CallbackInfo result) {
        stardewcraft$canMakeSlopes = !(block instanceof RailBehaviorProvider provider) || provider.canMakeSlopes(state, level, pos);
    }
    @WrapOperation(method = {"place", "connectTo"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BaseRailBlock;isRail(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"), require = 8)
    private boolean stardewcraft$noUnwantedSlopes(Level level, BlockPos pos, Operation<Boolean> original) {
        return stardewcraft$canMakeSlopes && original.call(level, pos);
    }
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RailState;updateConnections(Lnet/minecraft/world/level/block/state/properties/RailShape;)V"), require = 1)
    private void stardewcraft$validatePlacedShape(RailState routing, RailShape shape, Operation<Void> original,
            boolean powered, boolean alwaysPlace, RailShape fallback, @Local(ordinal = 1) LocalRef<RailShape> candidate) {
        RailShape valid = RailHooks.placementShape(block, shape, fallback);
        candidate.set(valid); original.call(routing, valid);
    }
    @Inject(method = "connectTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BaseRailBlock;getShapeProperty()Lnet/minecraft/world/level/block/state/properties/Property;"), cancellable = true, require = 1)
    private void stardewcraft$rejectInvalidConnection(RailState other, CallbackInfo result, @Local RailShape shape) {
        if (!RailHooks.isValid(block, shape)) {
            connections.remove(((RailStatePosition) other).stardewcraft$railPosition()); result.cancel();
        }
    }
}
