package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only cutscene prop placement. The original block state is restored at
 * event cleanup, so props can exist only during a scene.
 */
public class TemporaryBlockCommand implements EventCommand {

    private static final Map<String, SavedBlock> SAVED_BLOCKS = new LinkedHashMap<>();

    private final String id;
    private final String blockId;
    private final BlockPos pos;
    private final Direction facing;
    private boolean done;

    public TemporaryBlockCommand(String id, String blockId, int x, int y, int z, Direction facing) {
        this.id = id;
        this.blockId = blockId;
        this.pos = new BlockPos(x, y, z);
        this.facing = facing == null ? Direction.NORTH : facing;
    }

    @Override
    public void start(EventPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            done = true;
            return;
        }
        if (!SAVED_BLOCKS.containsKey(id)) {
            SAVED_BLOCKS.put(id, new SavedBlock(pos, level.getBlockState(pos)));
        }
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        level.setBlock(pos, state, 3);
        done = true;
    }

    @Override
    public void tick(EventPlayer player) {
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    public static void restore(String id) {
        SavedBlock saved = SAVED_BLOCKS.remove(id);
        if (saved == null) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.setBlock(saved.pos(), saved.state(), 3);
        }
    }

    public static void restoreAll() {
        for (String id : java.util.List.copyOf(SAVED_BLOCKS.keySet())) {
            restore(id);
        }
    }

    private record SavedBlock(BlockPos pos, BlockState state) {
    }
}
