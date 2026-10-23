package com.stardew.craft.platform.fabric.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

/** Detached, immutable owner snapshot: callbacks may safely remove tickets while iterating. */
public record TicketSet(LongSet nonTicking, LongSet ticking) {
    public TicketSet { nonTicking = LongSets.unmodifiable(new LongOpenHashSet(nonTicking)); ticking = LongSets.unmodifiable(new LongOpenHashSet(ticking)); }
}
