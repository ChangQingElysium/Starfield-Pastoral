package com.stardew.craft.port.event;

import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

/** NeoForge's pickup boundaries without replacing Forge's native inventory/cancellation path. */
public final class PortPickupHooks {
    private static final ThreadLocal<Deque<Pickup>> PICKUPS = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Pickup {
        final ItemEntity item;
        boolean forcePickup;
        ItemStack originalStack;

        Pickup(ItemEntity item) {
            this.item = item;
        }
    }

    private PortPickupHooks() {}

    public static void playerTouch(ItemEntity item, Player player, Runnable nativePickup) {
        if (item.level().isClientSide) {
            nativePickup.run();
            return;
        }
        Deque<Pickup> pickups = PICKUPS.get();
        Pickup pickup = new Pickup(item);
        pickups.push(pickup);
        try {
            ItemEntityPickupEvent.Pre event = new ItemEntityPickupEvent.Pre(player, item);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.canPickup() == TriState.FALSE) return;
            pickup.forcePickup = event.canPickup() == TriState.TRUE;
            nativePickup.run();
        } finally {
            pickups.pop();
            if (pickups.isEmpty()) PICKUPS.remove();
        }
    }

    @Nullable
    private static Pickup current(ItemEntity item) {
        Pickup pickup = PICKUPS.get().peek();
        return pickup != null && pickup.item == item ? pickup : null;
    }

    public static int pickupDelay(ItemEntity item, int delay) {
        Pickup pickup = current(item);
        return pickup != null && pickup.forcePickup ? 0 : delay;
    }

    @Nullable
    public static UUID pickupTarget(ItemEntity item, @Nullable UUID target) {
        Pickup pickup = current(item);
        return pickup != null && pickup.forcePickup ? null : target;
    }

    /** Forge later rewrites its copy's count to the amount picked; keep an independent complete snapshot. */
    public static ItemStack captureOriginal(ItemEntity item, ItemStack nativeCopy) {
        Pickup pickup = current(item);
        if (pickup != null) pickup.originalStack = nativeCopy.copy();
        return nativeCopy;
    }

    public static void afterNativePickup(ItemEntity item, Player player) {
        Pickup pickup = current(item);
        if (pickup != null && pickup.originalStack != null) {
            MinecraftForge.EVENT_BUS.post(new ItemEntityPickupEvent.Post(player, item, pickup.originalStack));
        }
    }
}
