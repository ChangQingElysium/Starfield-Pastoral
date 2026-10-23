package com.stardew.craft.platform.fabric.chunk;

/** Implemented on the native chunks.dat SavedData; vanilla Forced remains owned by vanilla. */
public interface ForcedChunkDataAccess {
    ChunkTicketData stardewcraft$controllerTickets();
    void stardewcraft$controllerTickets(ChunkTicketData data);
}
