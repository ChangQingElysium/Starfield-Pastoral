package com.stardew.craft.port.event;

import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockDropsEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;

/**
 * PORT(1.20.1): NeoForge 21.1 hook points that Forge 1.20.1 lacks, called from the port mixins in
 * {@code com.stardew.craft.mixin} (entity ticks, block drops, sweep, sleep, enchanting).
 */
public final class PortEventHooks {
    private PortEventHooks() {}

    // ---------------------------------------------------------------- entity tick

    /** @return true if the tick must be skipped (Pre cancelled). */
    public static boolean fireEntityTickPre(Entity entity) {
        return MinecraftForge.EVENT_BUS.post(new EntityTickEvent.Pre(entity));
    }

    public static void fireEntityTickPost(Entity entity) {
        MinecraftForge.EVENT_BUS.post(new EntityTickEvent.Post(entity));
    }

    // ---------------------------------------------------------------- block drops

    private static final ThreadLocal<List<ItemEntity>> CAPTURED_DROPS = new ThreadLocal<>();
    private static final ThreadLocal<PlayerBreak> PLAYER_BREAK = new ThreadLocal<>();

    private static final class PlayerBreak {
        final Level level;
        final BlockPos pos;
        final int experience;
        boolean consumed;

        PlayerBreak(Level level, BlockPos pos, int experience) {
            this.level = level;
            this.pos = pos.immutable();
            this.experience = experience;
        }
    }

    /** Called from {@code Block.popResource(Level, Supplier, ItemStack)} in place of {@code addFreshEntity}. */
    public static boolean captureDrop(Level level, Entity entity) {
        List<ItemEntity> captured = CAPTURED_DROPS.get();
        if (captured != null && entity instanceof ItemEntity item) {
            captured.add(item);
            return true;
        }
        return level.addFreshEntity(entity);
    }

    /**
     * NeoForge {@code Block.dropResources} + {@code CommonHooks.handleBlockDrops}: pops the stacks while capturing the
     * item entities, posts {@link BlockDropsEvent}, then spawns the drops, runs {@code spawnAfterBreak} and pops XP.
     */
    public static void dropResources(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity,
            @Nullable Entity breaker, ItemStack tool, boolean dropXp, Supplier<List<ItemStack>> stacks) {
        List<ItemEntity> previous = CAPTURED_DROPS.get();
        List<ItemEntity> drops = new ArrayList<>();
        CAPTURED_DROPS.set(drops);
        try {
            stacks.get().forEach(stack -> Block.popResource(level, pos, stack));
        } finally {
            CAPTURED_DROPS.set(previous);
        }
        int experience = 0;
        PlayerBreak playerBreak = PLAYER_BREAK.get();
        if (playerBreak != null && !playerBreak.consumed && playerBreak.level == level && playerBreak.pos.equals(pos)) {
            playerBreak.consumed = true;
            experience = playerBreak.experience;
        }
        BlockDropsEvent event = new BlockDropsEvent(level, pos, state, blockEntity, drops, breaker, tool, experience);
        if (MinecraftForge.EVENT_BUS.post(event)) return;
        for (ItemEntity drop : event.getDrops()) {
            level.addFreshEntity(drop);
        }
        state.spawnAfterBreak(level, pos, tool, dropXp);
        if (event.getDroppedExperience() > 0) {
            state.getBlock().popExperience(level, pos, event.getDroppedExperience());
        }
    }

    /** {@code ServerPlayerGameMode#destroyBlock}: remember the Forge BreakEvent XP for the drop event. */
    public static int beginPlayerBreak(Level level, BlockPos pos, int experience) {
        PLAYER_BREAK.set(new PlayerBreak(level, pos, experience));
        return experience;
    }

    /** @return true if Forge's own XP pop must be skipped because {@link BlockDropsEvent} owned the XP. */
    public static boolean playerBreakXpHandled() {
        PlayerBreak playerBreak = PLAYER_BREAK.get();
        return playerBreak != null && playerBreak.consumed;
    }

    public static void endPlayerBreak() {
        PLAYER_BREAK.remove();
    }

    // ---------------------------------------------------------------- mob effects

    private static final ThreadLocal<Entity> EFFECT_SOURCE = new ThreadLocal<>();

    /** Runs a {@code canBeAffected} check with NeoForge's {@code Applicable#getEffectSource()} available. */
    public static boolean withEffectSource(@Nullable Entity source, java.util.function.BooleanSupplier check) {
        Entity previous = EFFECT_SOURCE.get();
        EFFECT_SOURCE.set(source);
        try {
            return check.getAsBoolean();
        } finally {
            if (previous == null) EFFECT_SOURCE.remove(); else EFFECT_SOURCE.set(previous);
        }
    }

    /** PORT(1.20.1): NeoForge's {@code MobEffectEvent.Applicable#getEffectSource()} for the effect being checked. */
    @Nullable
    public static Entity effectSource() {
        return EFFECT_SOURCE.get();
    }

    // ---------------------------------------------------------------- combat / sleep / enchanting

    public static boolean fireSweepAttack(Player player, Entity target, boolean vanillaSweep) {
        SweepAttackEvent event = new SweepAttackEvent(player, target, vanillaSweep);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isSweeping();
    }

    @Nullable
    public static Player.BedSleepingProblem fireCanPlayerSleep(ServerPlayer player, BlockPos pos,
            @Nullable Player.BedSleepingProblem vanillaProblem) {
        CanPlayerSleepEvent event = new CanPlayerSleepEvent(player, pos, vanillaProblem);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getProblem();
    }

    public static void firePlayerEnchantItem(ServerPlayer player, ItemStack enchanted) {
        List<EnchantmentInstance> enchantments = new ArrayList<>();
        EnchantmentHelper.getEnchantments(enchanted)
                .forEach((enchantment, level) -> enchantments.add(new EnchantmentInstance(enchantment, level)));
        MinecraftForge.EVENT_BUS.post(new PlayerEnchantItemEvent(player, enchanted, enchantments));
    }
}
