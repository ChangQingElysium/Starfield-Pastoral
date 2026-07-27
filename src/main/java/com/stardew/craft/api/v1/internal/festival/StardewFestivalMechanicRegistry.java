package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalDefinition;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicCapability;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicContext;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicRegistration;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvent;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.festival.ActiveFestivalHandlers;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalType;
import com.stardew.craft.festival.PassiveFestivalHandlers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Internal ordered dispatch and diagnostics for additive festival mechanics. */
public final class StardewFestivalMechanicRegistry {
    private static final OrderedExtensionRegistry<Entry> ENTRIES =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "festival/mechanics"));

    private StardewFestivalMechanicRegistry() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation mechanicId,
            Set<StardewFestivalMechanicCapability> capabilities,
            StardewFestivalMechanicHandler handler
    ) {
        ENTRIES.register(
                registrationId,
                priority,
                new Entry(
                        Objects.requireNonNull(mechanicId, "mechanicId"),
                        Set.copyOf(Objects.requireNonNull(
                                capabilities, "capabilities")),
                        Objects.requireNonNull(handler, "handler")));
    }

    public static List<StardewFestivalMechanicRegistration> registrations(
            ResourceLocation mechanicId
    ) {
        if (mechanicId == null) {
            return List.of();
        }
        return ENTRIES.entries().stream()
                .filter(entry -> entry.extension().mechanicId()
                        .equals(mechanicId))
                .map(entry -> new StardewFestivalMechanicRegistration(
                        entry.id(),
                        entry.priority(),
                        entry.extension().mechanicId(),
                        entry.extension().capabilities()))
                .toList();
    }

    public static Set<StardewFestivalMechanicCapability> capabilities(
            ResourceLocation mechanicId
    ) {
        EnumSet<StardewFestivalMechanicCapability> result =
                EnumSet.noneOf(StardewFestivalMechanicCapability.class);
        registrations(mechanicId).forEach(
                registration -> result.addAll(
                        registration.capabilities()));
        return Set.copyOf(result);
    }

    public static Optional<StardewFestivalMechanicSnapshot> inspect(
            ResourceLocation festivalId
    ) {
        FestivalDefinition runtime = FestivalRegistry.get(festivalId)
                .orElse(null);
        StardewFestivalDefinition definition = FestivalRegistry.snapshot()
                .definitions().get(festivalId);
        if (runtime == null || definition == null) {
            return Optional.empty();
        }
        ResourceLocation mechanicId = mechanicId(runtime);
        List<StardewFestivalMechanicRegistration> contributions =
                registrations(mechanicId);
        EnumSet<StardewFestivalMechanicCapability> capabilities =
                EnumSet.of(
                        StardewFestivalMechanicCapability.SESSION_LIFECYCLE,
                        StardewFestivalMechanicCapability.PARTICIPANTS);
        if (!runtime.mapOverlayId().isBlank()) {
            capabilities.add(
                    StardewFestivalMechanicCapability.MAP_OVERLAY);
        }
        if (!runtime.shopIds().isEmpty()) {
            capabilities.add(StardewFestivalMechanicCapability.SHOPS);
        }
        contributions.forEach(registration ->
                capabilities.addAll(registration.capabilities()));
        boolean legacyHandler = runtime.type() == FestivalType.ACTIVE
                ? ActiveFestivalHandlers.get(runtime).isPresent()
                : PassiveFestivalHandlers.get(runtime).isPresent();
        ArrayList<String> issues = new ArrayList<>();
        if (!runtime.mechanicId().isBlank()
                && !legacyHandler
                && contributions.isEmpty()) {
            issues.add("unresolved_mechanic:" + mechanicId);
        }
        return Optional.of(new StardewFestivalMechanicSnapshot(
                festivalId,
                mechanicId,
                definition.type(),
                capabilities,
                contributions,
                legacyHandler,
                issues));
    }

    public static void tick(
            ServerLevel level,
            FestivalDefinition definition,
            FestivalSessionState session
    ) {
        StardewFestivalMechanicContext context =
                context(level, definition, session);
        if (context == null) {
            return;
        }
        for (var registered : ENTRIES.entries()) {
            Entry entry = registered.extension();
            if (!entry.mechanicId().equals(context.mechanicId())) {
                continue;
            }
            try {
                ENTRIES.invokeVoid(
                        registered,
                        registeredEntry ->
                                registeredEntry.handler().tick(context));
            } catch (RuntimeException exception) {
                logFailure(registered.id(), context, "tick", exception);
            }
        }
    }

    public static void onSessionChanged(StardewFestivalSessionEvent event) {
        FestivalDefinition definition = FestivalRegistry
                .get(event.session().festivalId()).orElse(null);
        if (definition == null) {
            return;
        }
        StardewFestivalMechanicContext context = context(
                event.level(),
                definition,
                event.session());
        if (context == null) {
            return;
        }
        for (var registered : ENTRIES.entries()) {
            Entry entry = registered.extension();
            if (!entry.mechanicId().equals(context.mechanicId())) {
                continue;
            }
            try {
                ENTRIES.invokeVoid(
                        registered,
                        registeredEntry -> registeredEntry.handler()
                                .onSessionChanged(context, event));
            } catch (RuntimeException exception) {
                logFailure(
                        registered.id(),
                        context,
                        event.type().name(),
                        exception);
            }
        }
    }

    public static ResourceLocation mechanicId(FestivalDefinition definition) {
        String raw = definition.mechanicId();
        if (raw == null || raw.isBlank()) {
            return definition.resourceId();
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null && raw.indexOf(':') >= 0) {
            return parsed;
        }
        return ResourceLocation.fromNamespaceAndPath(
                definition.resourceId().getNamespace(),
                raw.toLowerCase(java.util.Locale.ROOT));
    }

    private static StardewFestivalMechanicContext context(
            ServerLevel level,
            FestivalDefinition runtime,
            FestivalSessionState session
    ) {
        return context(level, runtime, StardewFestivalSessions.snapshot(session));
    }

    private static StardewFestivalMechanicContext context(
            ServerLevel level,
            FestivalDefinition runtime,
            com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot session
    ) {
        StardewFestivalDefinition definition = FestivalRegistry.snapshot()
                .definitions().get(runtime.resourceId());
        if (definition == null) {
            return null;
        }
        return new StardewFestivalMechanicContext(
                level,
                runtime.resourceId(),
                mechanicId(runtime),
                definition,
                session);
    }

    private static void logFailure(
            ResourceLocation registrationId,
            StardewFestivalMechanicContext context,
            String operation,
            RuntimeException exception
    ) {
        StardewCraft.LOGGER.error(
                "Festival mechanic contribution {} failed during {} for {}",
                registrationId,
                operation,
                context.festivalId(),
                exception);
    }

    private record Entry(
            ResourceLocation mechanicId,
            Set<StardewFestivalMechanicCapability> capabilities,
            StardewFestivalMechanicHandler handler
    ) {
    }
}
