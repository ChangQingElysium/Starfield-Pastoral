package com.stardew.craft.platform.fabric.extension;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.AttackEntityEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

/** The two common hook contracts actually consumed by the mod and its tests. */
public final class CommonHooks {
    private CommonHooks() {}

    public static boolean onPlayerAttackTarget(Player player, Entity target) {
        if (FabricEvents.GAME.post(new AttackEntityEvent(player, target)).isCanceled()) return false;
        var stack = player.getMainHandItem();
        return stack.isEmpty() || !(stack.getItem() instanceof ItemAttackProvider provider)
                || !provider.onLeftClickEntity(stack, player, target);
    }

    public static InteractionResult onPlaceItemIntoWorld(UseOnContext context) {
        // ItemPlacementMixin wraps this real native call with the same snapshot transaction.
        return context.getItemInHand().useOn(context);
    }
}
