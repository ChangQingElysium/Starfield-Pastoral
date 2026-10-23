package com.stardew.craft.platform.fabric.event.level.growth;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.entity.player.BonemealEvent;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import org.jetbrains.annotations.Nullable;

/** Captures the useOn player around the exact crop attempt, including nested applications. */
public final class BoneMealApplications {
    private record Context(@Nullable Player player) {}
    private static final ThreadLocal<Context> PLAYER = new ThreadLocal<>();
    private BoneMealApplications() {}
    public static boolean withPlayer(@Nullable Player player, BooleanSupplier application) {
        var previous = PLAYER.get();
        PLAYER.set(new Context(player));
        try { return application.getAsBoolean(); }
        finally { if (previous == null) PLAYER.remove(); else PLAYER.set(previous); }
    }
    public static boolean growCrop(ItemStack stack, Level level, BlockPos pos) {
        var context = PLAYER.get();
        // Neo's deprecated non-player entry is server only; useOn applies on both sides.
        if (context == null && !(level instanceof ServerLevel)) return false;
        return apply(FabricEvents.GAME, context == null ? null : context.player(), stack, level, pos);
    }
    public static boolean apply(IEventBus bus, @Nullable Player player, ItemStack stack, Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var event = bus.post(new BonemealEvent(player, level, pos, state, stack));
        if (event.isCanceled()) return event.isSuccessful();
        if (!(state.getBlock() instanceof BonemealableBlock plant) || !plant.isValidBonemealTarget(level, pos, state)) return false;
        if (level instanceof ServerLevel server) {
            if (plant.isBonemealSuccess(level, level.random, pos, state)) plant.performBonemeal(server, level.random, pos, state);
            stack.shrink(1);
        }
        return true;
    }
}
