package com.stardew.craft.port.net.neoforged.neoforge.event.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code CropGrowEvent}, bridged from Forge {@code BlockEvent.CropGrowEvent.Pre/Post}.
 * Pre results map GROW <-> ALLOW, DO_NOT_GROW <-> DENY (kept in the Forge result slot).
 */
public abstract class CropGrowEvent extends BlockEvent {
    public CropGrowEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        super(level, pos, state);
    }

    @Event.HasResult
    public static class Pre extends com.stardew.craft.port.net.neoforged.neoforge.event.level.block.CropGrowEvent {
        public Pre() {
            this(null, BlockPos.ZERO, null);
        }

        public Pre(LevelAccessor level, BlockPos pos, BlockState state) {
            super(level, pos, state);
        }

        /**
         * PORT(1.20.1): stored in Forge's {@link Event#getResult()} (GROW = ALLOW, DO_NOT_GROW = DENY), because
         * NeoForge's {@code Result getResult()} cannot override Forge's {@code Event.Result getResult()}.
         */
        public void setResult(Result result) {
            super.setResult(switch (result) {
                case GROW -> Event.Result.ALLOW;
                case DEFAULT -> Event.Result.DEFAULT;
                case DO_NOT_GROW -> Event.Result.DENY;
            });
        }

        public enum Result {
            GROW,
            DEFAULT,
            DO_NOT_GROW
        }
    }

    public static class Post extends com.stardew.craft.port.net.neoforged.neoforge.event.level.block.CropGrowEvent {
        private final BlockState originalState;

        public Post() {
            this(null, BlockPos.ZERO, null, null);
        }

        public Post(LevelAccessor level, BlockPos pos, BlockState original, BlockState state) {
            super(level, pos, state);
            this.originalState = original;
        }

        public BlockState getOriginalState() {
            return this.originalState;
        }

        @Override
        public BlockState getState() {
            return this.getLevel().getBlockState(this.getPos());
        }
    }
}
