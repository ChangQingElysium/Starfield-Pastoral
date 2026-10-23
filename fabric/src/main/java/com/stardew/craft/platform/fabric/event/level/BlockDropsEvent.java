package com.stardew.craft.platform.fabric.event.level;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockDropsEvent extends BlockEvent {
    private final BlockEntity blockEntity;
    private final List<ItemEntity> drops;
    private final Entity breaker;
    private final ItemStack tool;
    private int experience;
    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
            List<ItemEntity> drops, Entity breaker, ItemStack tool) {
        this(level, pos, state, blockEntity, drops, breaker, tool, BlockDropHooks.experience(level, state, tool));
    }
    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
            List<ItemEntity> drops, Entity breaker, ItemStack tool, int experience) {
        super(level, pos, state);
        this.blockEntity = blockEntity;
        this.drops = Objects.requireNonNull(drops);
        this.breaker = breaker;
        this.tool = Objects.requireNonNull(tool);
        setDroppedExperience(experience);
    }
    @Override public ServerLevel getLevel() { return (ServerLevel) super.getLevel(); }
    public BlockEntity getBlockEntity() { return blockEntity; }
    public List<ItemEntity> getDrops() { return drops; }
    public Entity getBreaker() { return breaker; }
    public ItemStack getTool() { return tool; }
    public int getDroppedExperience() { return experience; }
    public void setDroppedExperience(int experience) {
        if (experience < 0) throw new IllegalArgumentException("May not set a negative experience drop");
        this.experience = experience;
    }
}
