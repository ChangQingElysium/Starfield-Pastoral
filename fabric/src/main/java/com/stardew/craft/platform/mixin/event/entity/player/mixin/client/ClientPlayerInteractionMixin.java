package com.stardew.craft.platform.mixin.event.entity.player.mixin.client;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.InteractionDispatch;
import com.stardew.craft.platform.fabric.event.entity.player.PlayerInteractEvent;
import com.stardew.craft.platform.fabric.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** This mixin belongs exclusively in the client list; common bridges never reference it. */
@Mixin(value = MultiPlayerGameMode.class, priority = 900)
public abstract class ClientPlayerInteractionMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private GameType localPlayerMode;
    @Shadow private float destroyProgress;

    @Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$useBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionDispatch.useBlock(player, player.level(), player.getItemInHand(hand), hand, hit,
                localPlayerMode == GameType.SPECTATOR, localPlayerMode.isCreative()));
    }

    @WrapOperation(method = "startDestroyBlock", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"))
    private void stardewcraft$start(MultiPlayerGameMode gameMode, ClientLevel level, PredictiveAction action,
            Operation<Void> original, BlockPos pos, Direction face) {
        stardewcraft$predictStart(gameMode, level, action, original, pos, face);
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"))
    private void stardewcraft$creativeContinue(MultiPlayerGameMode gameMode, ClientLevel level, PredictiveAction action,
            Operation<Void> original, BlockPos pos, Direction face) {
        stardewcraft$predictStart(gameMode, level, action, original, pos, face);
    }

    @Unique
    private void stardewcraft$predictStart(MultiPlayerGameMode gameMode, ClientLevel level, PredictiveAction action,
            Operation<Void> original, BlockPos pos, Direction face) {
        original.call(gameMode, level, (PredictiveAction) sequence -> {
            var event = FabricEvents.GAME.post(new PlayerInteractEvent.LeftClickBlock(minecraft.player, pos, face,
                    PlayerInteractEvent.LeftClickBlock.Action.START));
            if (event.isCanceled() || !localPlayerMode.isCreative() && event.getUseItem() == TriState.FALSE) {
                // A canceled client prediction still informs the authoritative server of the click.
                if (!event.isCanceled() && event.getUseBlock() != TriState.FALSE && destroyProgress == 0.0F) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) state.attack(level, pos, minecraft.player);
                }
                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        pos, face, sequence);
            }
            return InteractionDispatch.withLeftClick(event, () -> action.predict(sequence));
        });
    }

    // Synthetic names have no Mojang mapping. The invocation itself is remapped normally.
    @WrapOperation(method = "method_41930", remap = false, at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private void stardewcraft$attack(BlockState state, Level world, BlockPos pos, Player player, Operation<Void> original) {
        var event = InteractionDispatch.currentLeftClick();
        if (event == null || event.getUseBlock() != TriState.FALSE) original.call(state, world, pos, player);
    }

    @Inject(method = "continueDestroyBlock", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER, target =
            "Lnet/minecraft/client/tutorial/Tutorial;onDestroyBlock(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)V"), cancellable = true)
    private void stardewcraft$hold(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.LeftClickBlock(minecraft.player, pos, face,
                PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD));
        if (event.getUseItem() == TriState.FALSE) cir.setReturnValue(true);
    }
}
