package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningSpeedHooks {
    private static final ThreadLocal<BlockPos> POSITION = new ThreadLocal<>();
    private MiningSpeedHooks() {}
    public static <T> T at(BlockPos position, Supplier<T> query) {
        BlockPos previous = POSITION.get();
        POSITION.set(position.immutable());
        try { return query.get(); }
        finally { if (previous == null) POSITION.remove(); else POSITION.set(previous); }
    }
    public static float result(Player player, BlockState state, float nativeSpeed) {
        var event = new PlayerEvent.BreakSpeed(player, state, nativeSpeed, POSITION.get());
        FabricEvents.GAME.post(event);
        return event.isCanceled() ? -1 : event.getNewSpeed();
    }
}
