package com.stardew.craft.api.v1.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable farm state exposed to addon callbacks.
 *
 * <p>The snapshot deliberately does not expose the mutable internal farm record. Addons that need
 * to persist their own state should use {@link StardewFarmPersistentData}.
 */
public record StardewFarmSnapshot(
        UUID ownerUuid,
        String ownerName,
        String farmName,
        int slotIndex,
        BlockPos origin,
        ResourceLocation farmTypeId,
        int farmLayoutVersion,
        StardewFarmLayoutConfiguration farmLayoutConfiguration,
        List<StardewFarmLayoutAttachment> farmLayoutAttachments,
        boolean initialized,
        long createdTimestamp,
        int lastOnlineDay,
        int lastOnlineSeason,
        int graceDaysLeft,
        String caveChoice,
        boolean goldClockPresent,
        boolean goldClockEnabled,
        List<UUID> members
) {
    public StardewFarmSnapshot {
        ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        ownerName = Objects.requireNonNull(ownerName, "ownerName");
        farmName = Objects.requireNonNull(farmName, "farmName");
        origin = Objects.requireNonNull(origin, "origin").immutable();
        farmTypeId = Objects.requireNonNull(farmTypeId, "farmTypeId");
        if (farmLayoutVersion < 1) {
            throw new IllegalArgumentException(
                    "Farm layout version must be at least 1");
        }
        farmLayoutConfiguration = Objects.requireNonNull(
                farmLayoutConfiguration, "farmLayoutConfiguration");
        farmLayoutAttachments = List.copyOf(
                Objects.requireNonNull(
                        farmLayoutAttachments,
                        "farmLayoutAttachments"));
        caveChoice = Objects.requireNonNull(caveChoice, "caveChoice");
        members = List.copyOf(members);
    }

    /** Source-compatible constructor for snapshots created before layout metadata. */
    public StardewFarmSnapshot(
            UUID ownerUuid,
            String ownerName,
            String farmName,
            int slotIndex,
            BlockPos origin,
            ResourceLocation farmTypeId,
            boolean initialized,
            long createdTimestamp,
            int lastOnlineDay,
            int lastOnlineSeason,
            int graceDaysLeft,
            String caveChoice,
            boolean goldClockPresent,
            boolean goldClockEnabled,
            List<UUID> members
    ) {
        this(ownerUuid, ownerName, farmName, slotIndex, origin, farmTypeId,
                1, StardewFarmLayoutConfiguration.empty(),
                List.of(),
                initialized, createdTimestamp, lastOnlineDay,
                lastOnlineSeason, graceDaysLeft, caveChoice,
                goldClockPresent, goldClockEnabled, members);
    }

    public java.util.Optional<StardewFarmLayoutAttachment> findLayoutAttachment(
            ResourceLocation id
    ) {
        return farmLayoutAttachments.stream()
                .filter(attachment -> attachment.id().equals(id))
                .findFirst();
    }

    public java.util.Optional<BlockPos> resolveLayoutAttachment(
            ResourceLocation id
    ) {
        return findLayoutAttachment(id)
                .map(attachment -> attachment.resolve(origin));
    }

    public List<StardewFarmLayoutAttachment> layoutAttachmentsWithTag(
            ResourceLocation tag
    ) {
        return farmLayoutAttachments.stream()
                .filter(attachment -> attachment.tags().contains(tag))
                .toList();
    }
}
