package com.stardew.craft.platform.mixin.extension.mixin;


import com.stardew.craft.platform.fabric.extension.BlockActions;
import com.stardew.craft.platform.fabric.extension.ItemAbilities;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShovelItem.class)
public abstract class ShovelToolActionMixin {
    // Both the native map lookup and hard-coded campfire test must use the
    // cancellable query. Retain the native ordering and one durability charge.
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$resolveShovel(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (context.getClickedFace() == Direction.DOWN) {
            callback.setReturnValue(InteractionResult.PASS);
            return;
        }
        var level = context.getLevel();
        var position = context.getClickedPos();
        var player = context.getPlayer();
        var original = level.getBlockState(position);
        BlockState flattened = BlockActions.getModifiedState(original, context, ItemAbilities.SHOVEL_FLATTEN, false);
        BlockState changed;
        if (flattened != null && level.getBlockState(position.above()).isAir()) {
            level.playSound(player, position, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1, 1);
            changed = flattened;
        } else {
            changed = BlockActions.getModifiedState(original, context, ItemAbilities.SHOVEL_DOUSE, false);
            if (changed != null && !level.isClientSide) level.levelEvent(null, 1009, position, 0);
        }
        if (changed == null) {
            callback.setReturnValue(InteractionResult.PASS);
            return;
        }
        if (!level.isClientSide) {
            level.setBlock(position, changed, 11);
            level.gameEvent(GameEvent.BLOCK_CHANGE, position, GameEvent.Context.of(player, changed));
            if (player != null) context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
        }
        callback.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
