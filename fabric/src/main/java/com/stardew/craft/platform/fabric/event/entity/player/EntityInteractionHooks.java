package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class EntityInteractionHooks {
    private EntityInteractionHooks() {}

    public static InteractionResult interactAt(Entity target, Player player, Vec3 localPos, InteractionHand hand,
            Supplier<InteractionResult> original) {
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.EntityInteractSpecific(player, hand, target, localPos));
        return event.isCanceled() ? event.getCancellationResult() : original.get();
    }
}
