package com.stardew.craft.platform.fabric.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A constructor-free payload fixture. It creates no server, world, network, or game loop. */
public final class InteractionTestPlayer extends ServerPlayer {
    public ItemStack held;
    public BlockPos position;

    private InteractionTestPlayer() { super(null, null, null, null); }

    public static InteractionTestPlayer create(ItemStack held) throws ReflectiveOperationException {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        var field = type.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Fixture allocator unavailable");
        Object allocator = field.get(null);
        var player = (InteractionTestPlayer) type.getMethod("allocateInstance", Class.class)
                .invoke(allocator, InteractionTestPlayer.class);
        player.held = held;
        player.position = BlockPos.ZERO;
        return player;
    }

    @Override public ItemStack getItemInHand(InteractionHand hand) { return held; }
    @Override public BlockPos blockPosition() { return position; }
}
