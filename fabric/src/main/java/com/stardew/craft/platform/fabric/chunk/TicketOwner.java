package com.stardew.craft.platform.fabric.chunk;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Controller, owner kind, owner value and ticking mode all participate in native ticket identity. */
record TicketOwner(ResourceLocation controller, Object owner, boolean ticking) implements Comparable<TicketOwner> {
    TicketOwner {
        Objects.requireNonNull(controller, "controller"); Objects.requireNonNull(owner, "owner");
        if (owner instanceof BlockPos pos) owner = pos.immutable();
        else if (!(owner instanceof UUID)) throw new IllegalArgumentException("Ticket owners must be a BlockPos or UUID");
    }
    @Override public int compareTo(TicketOwner other) {
        int order = controller.compareTo(other.controller);
        if (order == 0) order = Boolean.compare(owner instanceof UUID, other.owner instanceof UUID);
        if (order == 0) order = owner instanceof UUID uuid ? uuid.compareTo((UUID) other.owner) : ((BlockPos) owner).compareTo((BlockPos) other.owner);
        return order == 0 ? Boolean.compare(ticking, other.ticking) : order;
    }
}
