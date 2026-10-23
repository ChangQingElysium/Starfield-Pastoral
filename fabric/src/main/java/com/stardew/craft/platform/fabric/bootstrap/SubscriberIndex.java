package com.stardew.craft.platform.fabric.bootstrap;

import com.google.gson.Gson;
import com.stardew.craft.platform.fabric.event.EventBusSubscriber;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.stardew.craft.platform.fabric.event.ModBusEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;

/** Registers the complete compiled subscriber list after filtering the physical side. */
public final class SubscriberIndex {
    public static final String RESOURCE = "/META-INF/stardewcraft/fabric-subscribers.json";
    private final Set<Registration> registered = new HashSet<>();
    private record Registration(String className, EventBusSubscriber.Bus bus) {}

    public record Entry(String className, EventBusSubscriber.Bus bus, List<EnvType> environments,
            int staticHandlers) {
        public Entry {
            if (className == null || className.isBlank()) throw new IllegalArgumentException("Missing subscriber class");
            Objects.requireNonNull(bus, "Subscriber bus");
            environments = List.copyOf(environments);
            if (environments.isEmpty() || new HashSet<>(environments).size() != environments.size()) {
                throw new IllegalArgumentException("Invalid subscriber sides: " + className);
            }
            if (staticHandlers < 0) throw new IllegalArgumentException("Invalid handler count: " + className);
        }
    }

    public record Contents(int schemaVersion, int staticHandlers, List<Entry> classes) {
        public Contents {
            if (schemaVersion != 2) throw new IllegalArgumentException("Unsupported subscriber index schema");
            classes = List.copyOf(classes);
            if (classes.isEmpty()) throw new IllegalArgumentException("Subscriber index must not be empty");
            Set<Registration> names = new HashSet<>();
            for (Entry entry : classes) {
                if (!names.add(new Registration(entry.className(), entry.bus()))) throw new IllegalArgumentException("Duplicate subscriber: " + entry.className());
            }
            if (staticHandlers != classes.stream().mapToInt(Entry::staticHandlers).sum()) {
                throw new IllegalArgumentException("Subscriber handler count mismatch");
            }
        }
    }

    @FunctionalInterface
    interface ClassResolver {
        Class<?> resolve(String name) throws ClassNotFoundException;
    }

    public void register(EnvType side, IEventBus gameBus, IEventBus modBus) {
        var stream = SubscriberIndex.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Compiled Fabric subscriber index is missing");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Contents index = new Gson().fromJson(reader, Contents.class);
            register(index, side, gameBus, modBus,
                    name -> Class.forName(name, false, SubscriberIndex.class.getClassLoader()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read compiled subscriber index", exception);
        }
    }

    synchronized void register(Contents index, EnvType side, IEventBus gameBus, IEventBus modBus,
            ClassResolver resolver) {
        Objects.requireNonNull(index, "Subscriber index");
        Objects.requireNonNull(side, "Physical side");
        Objects.requireNonNull(gameBus, "Game bus");
        Objects.requireNonNull(modBus, "Mod bus");
        for (Entry entry : index.classes()) {
            // This check must precede even resolving the class: server verification must
            // never link Minecraft client signatures from a client-only subscriber.
            Registration registration = new Registration(entry.className(), entry.bus());
            if (!entry.environments().contains(side) || registered.contains(registration)) continue;
            try {
                Class<?> type = resolver.resolve(entry.className());
                boolean modEvents = entry.bus() == EventBusSubscriber.Bus.MOD;
                (modEvents ? modBus : gameBus).register(type, eventType ->
                        ModBusEvent.class.isAssignableFrom(eventType) == modEvents);
                registered.add(registration);
            } catch (ClassNotFoundException | LinkageError exception) {
                throw new IllegalStateException("Cannot load Fabric subscriber " + entry.className(), exception);
            }
        }
    }
}
