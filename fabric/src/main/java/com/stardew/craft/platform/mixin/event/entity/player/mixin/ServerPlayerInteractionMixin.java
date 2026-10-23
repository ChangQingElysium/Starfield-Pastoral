package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.InteractionDispatch;
import com.stardew.craft.platform.fabric.event.entity.player.PlayerInteractEvent;
import com.stardew.craft.platform.fabric.event.level.BlockBreakDispatch;
import com.stardew.craft.platform.fabric.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerPlayerGameMode.class, priority = 900)
public abstract class ServerPlayerInteractionMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private GameType gameModeForPlayer;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$useBlock(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionDispatch.useBlock(player, level, stack, hand, hit,
                gameModeForPlayer == GameType.SPECTATOR, gameModeForPlayer.isCreative()));
    }

    @WrapMethod(method = "destroyBlock")
    private boolean stardewcraft$break(BlockPos pos, Operation<Boolean> original) {
        return BlockBreakDispatch.destroy(level, player, gameModeForPlayer, pos, () -> original.call(pos));
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/Item;canAttackBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean stardewcraft$approvedItem(Item item, BlockState state, Level world, BlockPos pos,
            Player actor, Operation<Boolean> original) {
        return BlockBreakDispatch.hasApprovedBreak() || original.call(item, state, world, pos, actor);
    }

    @WrapMethod(method = "handleBlockBreakAction")
    private void stardewcraft$leftClick(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face,
            int maxBuildHeight, int sequence, Operation<Void> original) {
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.LeftClickBlock(player, pos, face,
                PlayerInteractEvent.LeftClickBlock.Action.convert(action)));
        if (event.isCanceled()) {
            BlockBreakDispatch.correctPrediction(level, player, pos);
            return;
        }
        InteractionDispatch.withLeftClick(event, () -> {
            original.call(pos, action, face, maxBuildHeight, sequence);
            return null;
        });
    }

    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private void stardewcraft$attack(BlockState state, Level world, BlockPos pos, Player actor, Operation<Void> original) {
        var event = InteractionDispatch.currentLeftClick();
        if (event == null || event.getUseBlock() != TriState.FALSE) original.call(state, world, pos, actor);
    }
}
