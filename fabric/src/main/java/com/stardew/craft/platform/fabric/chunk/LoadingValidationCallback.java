package com.stardew.craft.platform.fabric.chunk;

import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface LoadingValidationCallback {
    void validateTickets(ServerLevel level, TicketHelper helper);
}
