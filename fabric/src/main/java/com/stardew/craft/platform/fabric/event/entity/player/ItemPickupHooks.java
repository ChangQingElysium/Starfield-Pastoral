package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.util.TriState;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ItemPickupHooks {
    private ItemPickupHooks() {}
    public record Frame(TriState decision, ItemStack original) {}

    public static Frame before(ItemEntity entity, Player player) {
        var event = FabricEvents.GAME.post(new ItemEntityPickupEvent.Pre(player, entity));
        return new Frame(event.canPickup(), entity.getItem().copy());
    }
    public static int delay(Frame frame, int nativeDelay) {
        return frame.decision() == TriState.FALSE ? 1 : frame.decision() == TriState.TRUE ? 0 : nativeDelay;
    }
    public static UUID owner(Frame frame, UUID nativeOwner) {
        return frame != null && frame.decision() == TriState.TRUE ? null : nativeOwner;
    }
    public static int after(ItemEntity entity, Player player, Frame frame, ItemStack nativeStack) {
        FabricEvents.GAME.post(new ItemEntityPickupEvent.Post(player, entity, frame.original()));
        // NeoForge evaluates this after Post listeners and fixes vanilla's full-stack count.
        return frame.original().getCount() - nativeStack.getCount();
    }
    public static UUID target(ItemEntity entity) {
        return ((com.stardew.craft.platform.mixin.event.entity.player.mixin.ItemPickupTargetAccessor) entity).stardewcraft$pickupTarget();
    }
}
