package com.stardew.craft.api.v1.internal.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.Optional;

/** Stable NBT envelope shared by all namespaced state scopes. */
public final class NamespacedStateCodec {
    public static final String VERSION = "version";
    public static final String PAYLOAD = "payload";

    private NamespacedStateCodec() {
    }

    public static Optional<StoredValue> decode(CompoundTag entry) {
        if (entry == null
                || !entry.contains(VERSION, Tag.TAG_INT)
                || !entry.contains(PAYLOAD, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        int storedVersion = entry.getInt(VERSION);
        if (storedVersion < 0) {
            return Optional.empty();
        }
        return Optional.of(new StoredValue(
                storedVersion, entry.getCompound(PAYLOAD)));
    }

    public static CompoundTag encode(
            NamespacedStateKeyRegistry.Handle key,
            CompoundTag payload
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        CompoundTag entry = new CompoundTag();
        entry.putInt(VERSION, key.currentVersion());
        entry.put(PAYLOAD, payload.copy());
        return entry;
    }

    public record StoredValue(int storedVersion, CompoundTag payload) {
        public StoredValue {
            if (storedVersion < 0) {
                throw new IllegalArgumentException(
                        "storedVersion must be non-negative");
            }
            payload = Objects.requireNonNull(payload, "payload").copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }
}
