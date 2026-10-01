package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachmentKeys;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutRegistration;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.farm.FarmType;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Internal farm-layout catalog with built-in enum adapters. */
public final class StardewFarmLayoutRegistry {
    private static final OrderedExtensionRegistry<StardewFarmLayoutRegistration> LAYOUTS =
            new OrderedExtensionRegistry<>(
                    new ResourceLocation(
                            StardewCraft.MODID, "farm/layout"));
    private static final Map<ResourceLocation, StardewFarmLayoutRegistration> BY_ID =
            new LinkedHashMap<>();
    private static volatile DataSnapshot dataSnapshot =
            new DataSnapshot(Map.of(), 0L);

    static {
        // Farm-map migration is being rolled out one layout at a time. Keep the
        // dormant enum definitions for old save snapshots, and only advertise
        // layouts whose authored maps have completed the import audit.
        registerBuiltin(fromBuiltin(FarmType.STANDARD), 1000);
        registerBuiltin(fromBuiltin(FarmType.RIVERLAND), 990);
        registerBuiltin(fromBuiltin(FarmType.FOREST), 980);
        registerBuiltin(fromBuiltin(FarmType.HILLTOP), 970);
        registerBuiltin(fromBuiltin(FarmType.WILDERNESS), 960);
        registerBuiltin(fromBuiltin(FarmType.FOUR_CORNERS), 2, 950);
        registerBuiltin(fromBuiltin(FarmType.BEACH), 940);
        registerBuiltin(fromBuiltin(FarmType.MEADOWLANDS), 930);
    }

    private StardewFarmLayoutRegistry() {
    }

    public static synchronized void register(StardewFarmLayout layout) {
        Objects.requireNonNull(layout, "layout");
        register(new StardewFarmLayoutRegistration(layout, 1, List.of()));
    }

    public static synchronized void register(
            StardewFarmLayoutRegistration registration
    ) {
        Objects.requireNonNull(registration, "registration");
        registerChecked(registration, 0);
    }

    private static synchronized void registerBuiltin(
            StardewFarmLayout layout,
            int priority
    ) {
        registerBuiltin(layout, 1, priority);
    }

    private static synchronized void registerBuiltin(
            StardewFarmLayout layout,
            int version,
            int priority
    ) {
        registerChecked(
                new StardewFarmLayoutRegistration(
                        layout, version, List.of(),
                        projectLegacyAttachments(layout)),
                priority);
    }

    private static void registerChecked(
            StardewFarmLayoutRegistration registration,
            int priority
    ) {
        registration = withLegacyAttachments(registration);
        StardewFarmLayout layout = registration.layout();
        if (BY_ID.containsKey(layout.id())
                || dataSnapshot.registrations().containsKey(layout.id())) {
            throw new IllegalStateException(
                    "Farm layout already registered: " + layout.id());
        }
        BY_ID.put(layout.id(), registration);
        LAYOUTS.register(layout.id(), priority, registration);
    }

    private static StardewFarmLayoutRegistration withLegacyAttachments(
            StardewFarmLayoutRegistration registration
    ) {
        java.util.LinkedHashMap<ResourceLocation, StardewFarmLayoutAttachment>
                merged = new java.util.LinkedHashMap<>();
        for (StardewFarmLayoutAttachment attachment
                : projectLegacyAttachments(registration.layout())) {
            merged.put(attachment.id(), attachment);
        }
        for (StardewFarmLayoutAttachment attachment
                : registration.attachments()) {
            merged.put(attachment.id(), attachment);
        }
        return new StardewFarmLayoutRegistration(
                registration.layout(),
                registration.version(),
                registration.configurationFields(),
                List.copyOf(merged.values()));
    }

    public static synchronized Optional<StardewFarmLayout> find(
            ResourceLocation id
    ) {
        return findRegistration(id).map(StardewFarmLayoutRegistration::layout);
    }

    public static synchronized Optional<StardewFarmLayoutRegistration> findRegistration(
            ResourceLocation id
    ) {
        StardewFarmLayoutRegistration registration = BY_ID.get(id);
        return Optional.ofNullable(registration != null
                ? registration : dataSnapshot.registrations().get(id));
    }

    public static List<StardewFarmLayout> all() {
        return allRegistrations().stream()
                .map(StardewFarmLayoutRegistration::layout)
                .toList();
    }

    public static List<StardewFarmLayoutRegistration> allRegistrations() {
        java.util.ArrayList<StardewFarmLayoutRegistration> combined =
                new java.util.ArrayList<>(LAYOUTS.entries().stream()
                .map(OrderedExtensionRegistry.Entry::extension)
                .toList());
        DataSnapshot current = dataSnapshot;
        current.registrations().values().stream()
                .sorted(java.util.Comparator.comparing(
                        value -> value.layout().id().toString()))
                .forEach(combined::add);
        return List.copyOf(combined);
    }

    public static List<StardewFarmLayout> selectable() {
        return all().stream()
                .filter(StardewFarmLayout::selectable)
                .toList();
    }

    public static List<StardewFarmLayoutRegistration> selectableRegistrations() {
        return allRegistrations().stream()
                .filter(registration -> registration.layout().selectable())
                .toList();
    }

    public static synchronized void publishData(
            Map<ResourceLocation, StardewFarmLayoutRegistration> registrations
    ) {
        Objects.requireNonNull(registrations, "registrations");
        for (Map.Entry<ResourceLocation, StardewFarmLayoutRegistration> entry
                : registrations.entrySet()) {
            if (BY_ID.containsKey(entry.getKey())) {
                throw new IllegalStateException(
                        "Data farm layout conflicts with Java/builtin layout: "
                                + entry.getKey());
            }
            if (!entry.getKey().equals(entry.getValue().layout().id())) {
                throw new IllegalArgumentException(
                        "Farm layout data key does not match layout ID: "
                                + entry.getKey());
            }
        }
        LinkedHashMap<ResourceLocation, StardewFarmLayoutRegistration>
                prepared = new LinkedHashMap<>();
        registrations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(
                                ResourceLocation::toString)))
                .forEach(entry -> prepared.put(
                        entry.getKey(),
                        withLegacyAttachments(entry.getValue())));
        DataSnapshot current = dataSnapshot;
        dataSnapshot = new DataSnapshot(
                Map.copyOf(prepared),
                current.revision() + 1L);
    }

    public static Map<ResourceLocation, StardewFarmLayoutRegistration>
    dataRegistrations() {
        return dataSnapshot.registrations();
    }

    public static long dataRevision() {
        return dataSnapshot.revision();
    }

    public static DataSnapshot dataSnapshot() {
        return dataSnapshot;
    }

    public static ResourceLocation builtinId(FarmType type) {
        return new ResourceLocation(
                StardewCraft.MODID, type.getId());
    }

    /** Geometry fallback for legacy saves; does not register or advertise the layout. */
    public static StardewFarmLayout builtinSnapshot(FarmType type) {
        return fromBuiltin(type);
    }

    public static List<StardewFarmLayoutAttachment> projectLegacyAttachments(
            StardewFarmLayout layout
    ) {
        java.util.ArrayList<StardewFarmLayoutAttachment> attachments =
                new java.util.ArrayList<>();
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.SPAWN,
                layout.spawnOffset(),
                layout.spawnYaw()));
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.GREENHOUSE,
                layout.greenhouseOffset(),
                0.0F,
                java.util.Set.of(
                        StardewFarmLayoutAttachmentKeys.BUILDING)));
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.FARM_TOTEM,
                layout.totemOffset(),
                0.0F,
                java.util.Set.of(
                        StardewFarmLayoutAttachmentKeys.PORTAL)));
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.ENTRY_SOUTH,
                layout.entrySouth().teleportOffset(),
                layout.entrySouth().yaw(),
                java.util.Set.of(
                        StardewFarmLayoutAttachmentKeys.PORTAL)));
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.ENTRY_EAST,
                layout.entryEast().teleportOffset(),
                layout.entryEast().yaw(),
                java.util.Set.of(
                        StardewFarmLayoutAttachmentKeys.PORTAL)));
        attachments.add(new StardewFarmLayoutAttachment(
                StardewFarmLayoutAttachmentKeys.ENTRY_WEST,
                layout.entryWest().teleportOffset(),
                layout.entryWest().yaw(),
                java.util.Set.of(
                        StardewFarmLayoutAttachmentKeys.PORTAL)));
        if (layout.caveExitSpawn() != null) {
            attachments.add(new StardewFarmLayoutAttachment(
                    StardewFarmLayoutAttachmentKeys.CAVE_EXIT,
                    layout.caveExitSpawn(),
                    layout.caveExitYaw(),
                    java.util.Set.of(
                            StardewFarmLayoutAttachmentKeys.PORTAL)));
        }
        return List.copyOf(attachments);
    }

    private static StardewFarmLayout fromBuiltin(FarmType type) {
        FarmType.FarmLayout layout = type.getLayout() != null
                ? type.getLayout()
                : Objects.requireNonNull(
                        FarmType.STANDARD.getLayout(), "standard layout");
        return new StardewFarmLayout(
                builtinId(type),
                type.isUnlocked(),
                type.getDisplayName(),
                type.getDescription(),
                type.getIconTexture(),
                new ResourceLocation(
                        StardewCraft.MODID,
                        "farm/" + type.getId() + ".schem"),
                layout.originY(),
                layout.schemWidth(),
                layout.schemHeight(),
                layout.schemLength(),
                layout.spawnOffset(),
                layout.spawnYaw(),
                layout.greenhouseOffset(),
                layout.totemOffset(),
                entry(layout.entrySouth()),
                entry(layout.entryEast()),
                entry(layout.entryWest()),
                layout.biomeId(),
                layout.forageZoneMin(),
                layout.forageZoneMax(),
                region(layout.caveBlackWall()),
                region(layout.cavePortalWall()),
                region(layout.caveClearBox()),
                layout.caveExitSpawn(),
                layout.caveExitYaw());
    }

    private static StardewFarmLayout.Entry entry(FarmType.EntryData entry) {
        return new StardewFarmLayout.Entry(
                entry.teleportOffset(),
                entry.yaw(),
                entry.exitMin(),
                entry.exitMax(),
                entry.barrierMin(),
                entry.barrierMax());
    }

    private static StardewFarmLayout.Region region(
            FarmType.CaveRegion region
    ) {
        return region == null ? null : new StardewFarmLayout.Region(
                region.min(), region.max());
    }

    public record DataSnapshot(
            Map<ResourceLocation, StardewFarmLayoutRegistration> registrations,
            long revision
    ) {
        public DataSnapshot {
            registrations = Map.copyOf(registrations);
        }
    }
}
