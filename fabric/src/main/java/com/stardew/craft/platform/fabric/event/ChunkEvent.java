package com.stardew.craft.platform.fabric.event;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/** Loaded-chunk lifecycle, deliberately separate from chunk NBT read/write events. */
public abstract class ChunkEvent extends LevelEvent {
    private final ChunkAccess chunk;

    protected ChunkEvent(ChunkAccess chunk, LevelAccessor level) {
        super(level);
        this.chunk = chunk;
    }

    public final ChunkAccess getChunk() {
        return chunk;
    }

    private static LevelAccessor owningLevel(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk levelChunk) return levelChunk.getLevel();
        throw new IllegalArgumentException("A ProtoChunk has no vanilla owning level; pass the level explicitly");
    }

    public static final class Load extends ChunkEvent {
        private final boolean newChunk;

        public Load(ChunkAccess chunk, boolean newChunk) {
            this(chunk, owningLevel(chunk), newChunk);
        }

        public Load(ChunkAccess chunk, LevelAccessor level, boolean newChunk) {
            super(chunk, level);
            this.newChunk = newChunk;
        }

        public boolean isNewChunk() { return newChunk; }
    }

    public static final class Unload extends ChunkEvent {
        public Unload(ChunkAccess chunk) {
            this(chunk, owningLevel(chunk));
        }

        public Unload(ChunkAccess chunk, LevelAccessor level) {
            super(chunk, level);
        }
    }
}
