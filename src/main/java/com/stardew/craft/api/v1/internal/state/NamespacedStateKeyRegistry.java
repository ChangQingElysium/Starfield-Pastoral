package com.stardew.craft.api.v1.internal.state;

import com.stardew.craft.api.v1.extension.StardewStateKeySnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared unforgeable-key registry for versioned addon state across domain scopes. */
public final class NamespacedStateKeyRegistry {
    private static final Map<RegistrationId, Handle> KEYS = new HashMap<>();

    private NamespacedStateKeyRegistry() {
    }

    public static synchronized Handle register(
            ResourceLocation scope,
            ResourceLocation id,
            int currentVersion
    ) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(id, "id");
        if (currentVersion < 0) {
            throw new IllegalArgumentException(
                    "currentVersion must be non-negative");
        }
        RegistrationId registrationId = new RegistrationId(scope, id);
        if (KEYS.containsKey(registrationId)) {
            throw new IllegalStateException(
                    "Namespaced state key already registered for "
                            + scope + ": " + id);
        }
        Handle handle = new Handle(scope, id, currentVersion);
        KEYS.put(registrationId, handle);
        return handle;
    }

    public static synchronized void require(
            ResourceLocation expectedScope,
            Handle handle
    ) {
        Objects.requireNonNull(expectedScope, "expectedScope");
        Objects.requireNonNull(handle, "handle");
        RegistrationId registrationId =
                new RegistrationId(handle.scope(), handle.id());
        if (!expectedScope.equals(handle.scope())
                || KEYS.get(registrationId) != handle) {
            throw new IllegalArgumentException(
                    "State key is not registered in scope "
                            + expectedScope + ": " + handle.id());
        }
    }

    public static synchronized List<StardewStateKeySnapshot> snapshots() {
        return KEYS.values().stream()
                .map(handle -> new StardewStateKeySnapshot(
                        handle.scope(),
                        handle.id(),
                        handle.currentVersion()))
                .sorted(Comparator
                        .comparing((StardewStateKeySnapshot snapshot) ->
                                snapshot.scope().toString())
                        .thenComparing(snapshot -> snapshot.id().toString()))
                .toList();
    }

    static synchronized Map<ResourceLocation, Handle> handles(
            ResourceLocation scope
    ) {
        Objects.requireNonNull(scope, "scope");
        LinkedHashMap<ResourceLocation, Handle> handles =
                new LinkedHashMap<>();
        KEYS.values().stream()
                .filter(handle -> scope.equals(handle.scope()))
                .sorted(Comparator.comparing(handle ->
                        handle.id().toString()))
                .forEach(handle -> handles.put(handle.id(), handle));
        return Collections.unmodifiableMap(handles);
    }

    public static final class Handle {
        private final ResourceLocation scope;
        private final ResourceLocation id;
        private final int currentVersion;

        private Handle(
                ResourceLocation scope,
                ResourceLocation id,
                int currentVersion
        ) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.id = Objects.requireNonNull(id, "id");
            if (currentVersion < 0) {
                throw new IllegalArgumentException(
                        "currentVersion must be non-negative");
            }
            this.currentVersion = currentVersion;
        }

        public ResourceLocation scope() {
            return scope;
        }

        public ResourceLocation id() {
            return id;
        }

        public int currentVersion() {
            return currentVersion;
        }
    }

    private record RegistrationId(
            ResourceLocation scope,
            ResourceLocation id
    ) {
    }
}
