package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** The recorded level remains authoritative during a player's dimension transfer. */
public abstract class ChunkWatchEvent extends Event {
    private final ServerPlayer player;
    private final ChunkPos pos;
    private final ServerLevel level;

    protected ChunkWatchEvent(ServerPlayer player, ChunkPos pos, ServerLevel level) {
        this.player = Objects.requireNonNull(player);
        this.pos = Objects.requireNonNull(pos);
        this.level = Objects.requireNonNull(level);
    }
    public ServerPlayer getPlayer() { return player; }
    public ChunkPos getPos() { return pos; }
    public ServerLevel getLevel() { return level; }

    public static final class Watch extends ChunkWatchEvent {
        private final LevelChunk chunk;
        public Watch(ServerPlayer player, LevelChunk chunk, ServerLevel level) {
            super(player, chunk.getPos(), level);
            this.chunk = chunk;
        }
        public LevelChunk getChunk() { return chunk; }
    }
    public static final class Sent extends ChunkWatchEvent {
        private final LevelChunk chunk;
        public Sent(ServerPlayer player, LevelChunk chunk, ServerLevel level) {
            super(player, chunk.getPos(), level);
            this.chunk = chunk;
        }
        public LevelChunk getChunk() { return chunk; }
    }
    public static final class UnWatch extends ChunkWatchEvent {
        public UnWatch(ServerPlayer player, ChunkPos pos, ServerLevel level) { super(player, pos, level); }
    }
}
