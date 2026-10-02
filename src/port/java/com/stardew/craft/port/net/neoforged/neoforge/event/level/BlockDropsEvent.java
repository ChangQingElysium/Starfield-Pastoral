package com.stardew.craft.port.net.neoforged.neoforge.event.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code BlockDropsEvent}, fired from every {@code Block.dropResources} overload by
 * {@code com.stardew.craft.mixin.PortBlockDropsMixin} with the captured drop entities. If not cancelled the drops are
 * spawned, {@code spawnAfterBreak} runs and {@link #getDroppedExperience()} is popped. Cancelling suppresses all three.
 * <p>
 * Experience: inside a survival player break ({@code ServerPlayerGameMode#destroyBlock}) the initial value is the
 * Forge {@code BreakEvent} experience (Forge's own later pop is suppressed); for other breakers it starts at 0, which
 * is what Forge 1.20.1 drops for non-player breaks.
 */
@Cancelable
public class BlockDropsEvent extends BlockEvent {
    @Nullable
    private final BlockEntity blockEntity;
    private final List<ItemEntity> drops;
    @Nullable
    private final Entity breaker;
    private final ItemStack tool;
    private int experience;

    public BlockDropsEvent() {
        this(null, BlockPos.ZERO, null, null, List.of(), null, ItemStack.EMPTY, 0);
    }

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            List<ItemEntity> drops, @Nullable Entity breaker, ItemStack tool, int experience) {
        super(level, pos, state);
        this.blockEntity = blockEntity;
        this.drops = drops;
        this.breaker = breaker;
        this.tool = tool;
        this.experience = experience;
    }

    public List<ItemEntity> getDrops() {
        return this.drops;
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    @Nullable
    public Entity getBreaker() {
        return this.breaker;
    }

    public ItemStack getTool() {
        return this.tool;
    }

    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    public int getDroppedExperience() {
        return this.experience;
    }

    public void setDroppedExperience(int experience) {
        if (experience < 0) throw new IllegalArgumentException("May not set a negative experience drop.");
        this.experience = experience;
    }
}
