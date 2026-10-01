package com.stardew.craft.port;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

/**
 * 1.21 {@code NbtUtils#readBlockPos(CompoundTag, String)} / {@code writeBlockPos(BlockPos)}. 1.21 stores a
 * block position as an {@code int[3]} array tag; 1.20.1's same-named methods use a {X,Y,Z} compound
 * ({@code writeBlockPos} would even compile silently with the wrong layout). These keep the 1.21 layout.
 */
public final class PortNbtUtils {
    private PortNbtUtils() {
    }

    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        return values.length == 3 ? Optional.of(new BlockPos(values[0], values[1], values[2])) : Optional.empty();
    }

    public static Tag writeBlockPos(BlockPos pos) {
        return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }
}
