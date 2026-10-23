package com.stardew.craft.platform.fabric.event.level.block;

import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Growth decision, fired after the native random draw, including unsuccessful draws. */
public abstract class CropGrowEvent extends BlockEvent {
    protected CropGrowEvent(Level level, BlockPos pos, BlockState state) { super(level, pos, state, false); }
    public static final class Pre extends CropGrowEvent {
        public enum Result { GROW, DEFAULT, DO_NOT_GROW }
        private Result result = Result.DEFAULT;
        public Pre(Level level, BlockPos pos, BlockState state) { super(level, pos, state); }
        public Result getResult() { return result; }
        public void setResult(Result result) { this.result = Objects.requireNonNull(result); }
    }
}
