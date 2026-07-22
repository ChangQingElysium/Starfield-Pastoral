package com.stardew.craft.farm;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server-thread-only player-to-farm occupancy state. */
final class FarmOccupancyTracker<P> {
    record SlotCount(int slot, int count) {}

    record Transition(SlotCount current, Optional<SlotCount> previous, boolean changed) {}

    private final Map<P, Integer> playerSlots = new HashMap<>();
    private final Map<Integer, Integer> slotCounts = new HashMap<>();

    Transition enter(P player, int slot) {
        Objects.requireNonNull(player, "player");
        validateSlot(slot);

        Integer previousSlot = playerSlots.get(player);
        if (previousSlot != null && previousSlot == slot) {
            return new Transition(new SlotCount(slot, count(slot)), Optional.empty(), false);
        }

        Optional<SlotCount> previous = Optional.empty();
        if (previousSlot != null) {
            previous = Optional.of(new SlotCount(previousSlot, decrement(previousSlot)));
        }

        playerSlots.put(player, slot);
        int count = slotCounts.merge(slot, 1, Integer::sum);
        return new Transition(new SlotCount(slot, count), previous, true);
    }

    Optional<Transition> leave(P player) {
        Objects.requireNonNull(player, "player");
        Integer slot = playerSlots.get(player);
        if (slot == null) return Optional.empty();
        int count = decrement(slot);
        playerSlots.remove(player);
        return Optional.of(new Transition(new SlotCount(slot, count), Optional.empty(), true));
    }

    int count(int slot) {
        validateSlot(slot);
        return slotCounts.getOrDefault(slot, 0);
    }

    boolean isOccupied(int slot) {
        validateSlot(slot);
        return slotCounts.containsKey(slot);
    }

    void clear() {
        playerSlots.clear();
        slotCounts.clear();
    }

    private int decrement(int slot) {
        Integer current = slotCounts.get(slot);
        if (current == null || current <= 0) {
            throw new IllegalStateException("Missing positive occupancy count for slot " + slot);
        }
        if (current == 1) {
            slotCounts.remove(slot);
            return 0;
        }
        int next = current - 1;
        slotCounts.put(slot, next);
        return next;
    }

    private static void validateSlot(int slot) {
        if (slot < 0) throw new IllegalArgumentException("slot must be non-negative");
    }
}
