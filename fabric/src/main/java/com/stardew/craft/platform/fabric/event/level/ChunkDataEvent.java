package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.ChunkEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;

public abstract class ChunkDataEvent extends ChunkEvent {
    private final CompoundTag data;
    protected ChunkDataEvent(ChunkAccess chunk, LevelAccessor level, CompoundTag data) {
        super(chunk, level);
        this.data = java.util.Objects.requireNonNull(data);
    }
    public CompoundTag getData() { return data; }
    public static final class Load extends ChunkDataEvent {
        private final ChunkType type;
        public Load(ChunkAccess chunk, LevelAccessor level, CompoundTag data, ChunkType type) {
            super(chunk, level, data);
            this.type = java.util.Objects.requireNonNull(type);
        }
        public ChunkType getType() { return type; }
    }
}
