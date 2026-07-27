package com.stardew.craft.api.v1.world;

import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import com.stardew.craft.api.v1.farm.StardewFarms;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified read-only lookup over absolute world anchors and resolved
 * farm-layout attachments.
 */
public final class StardewMapSlots {
    private StardewMapSlots() {
    }

    public static Optional<StardewMapSlot> worldAnchor(
            ResourceLocation id
    ) {
        return StardewWorldAnchors.get(id)
                .map(StardewMapSlots::fromWorldAnchor);
    }

    public static Optional<StardewMapSlot> resolveWorldAnchor(
            String id
    ) {
        return StardewWorldAnchors.resolve(id)
                .map(StardewMapSlots::fromWorldAnchor);
    }

    public static List<StardewMapSlot> worldAnchors() {
        return StardewWorldAnchors.all().stream()
                .map(StardewMapSlots::fromWorldAnchor)
                .toList();
    }

    public static Optional<StardewMapSlot> farm(
            MinecraftServer server,
            UUID ownerUuid,
            ResourceLocation slotId
    ) {
        return StardewFarms.find(server, ownerUuid)
                .flatMap(farm -> farm.findLayoutAttachment(slotId)
                        .map(attachment -> fromFarmAttachment(
                                farm, attachment)));
    }

    /**
     * Resolves a compound slot identity without assuming that slot IDs are
     * globally unique.
     */
    public static Optional<StardewMapSlot> get(
            MinecraftServer server,
            ResourceLocation scopeType,
            String scopeId,
            ResourceLocation slotId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(slotId, "slotId");
        if (scopeType.equals(StardewMapSlotScopes.WORLD)) {
            return "global".equals(scopeId)
                    ? worldAnchor(slotId)
                    : Optional.empty();
        }
        if (!scopeType.equals(StardewMapSlotScopes.FARM)) {
            return Optional.empty();
        }
        try {
            return farm(server, UUID.fromString(scopeId), slotId);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static List<StardewMapSlot> farm(
            MinecraftServer server,
            UUID ownerUuid
    ) {
        return StardewFarms.find(server, ownerUuid)
                .map(StardewMapSlots::fromFarm)
                .orElse(List.of());
    }

    public static Optional<StardewMapSlot> playerFarm(
            MinecraftServer server,
            UUID playerUuid,
            ResourceLocation slotId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(slotId, "slotId");
        return StardewFarms.findForPlayer(server, playerUuid)
                .flatMap(farm -> farm.findLayoutAttachment(slotId)
                        .map(attachment -> fromFarmAttachment(
                                farm, attachment)));
    }

    public static List<StardewMapSlot> playerFarm(
            MinecraftServer server,
            UUID playerUuid
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        return StardewFarms.findForPlayer(server, playerUuid)
                .map(StardewMapSlots::fromFarm)
                .orElse(List.of());
    }

    public static List<StardewMapSlot> playerFarmWithRole(
            MinecraftServer server,
            UUID playerUuid,
            ResourceLocation role
    ) {
        Objects.requireNonNull(role, "role");
        return playerFarm(server, playerUuid).stream()
                .filter(slot -> slot.hasRole(role))
                .toList();
    }

    public static List<StardewMapSlot> all(
            MinecraftServer server
    ) {
        Objects.requireNonNull(server, "server");
        ArrayList<StardewMapSlot> result =
                new ArrayList<>(worldAnchors());
        StardewFarms.all(server).stream()
                .flatMap(farm -> fromFarm(farm).stream())
                .forEach(result::add);
        return result.stream()
                .sorted(slotOrder())
                .toList();
    }

    public static List<StardewMapSlot> withRole(
            MinecraftServer server,
            ResourceLocation role
    ) {
        Objects.requireNonNull(role, "role");
        return all(server).stream()
                .filter(slot -> slot.hasRole(role))
                .toList();
    }

    public static StardewMapSlot fromWorldAnchor(
            StardewWorldAnchor anchor
    ) {
        Objects.requireNonNull(anchor, "anchor");
        return new StardewMapSlot(
                anchor.id(),
                anchor.dimension(),
                anchor.position(),
                anchor.yaw(),
                anchor.indoor(),
                anchor.useGroundHeight(),
                StardewMapSlotScopes.WORLD,
                "global",
                anchor.locationId(),
                anchor.roles());
    }

    public static StardewMapSlot fromFarmAttachment(
            StardewFarmSnapshot farm,
            StardewFarmLayoutAttachment attachment
    ) {
        Objects.requireNonNull(farm, "farm");
        Objects.requireNonNull(attachment, "attachment");
        return new StardewMapSlot(
                attachment.id(),
                ModDimensions.STARDEW_VALLEY.location(),
                Vec3.atLowerCornerOf(
                        attachment.resolve(farm.origin())),
                attachment.yaw(),
                false,
                false,
                StardewMapSlotScopes.FARM,
                farm.ownerUuid().toString(),
                farm.farmTypeId(),
                attachment.tags());
    }

    private static List<StardewMapSlot> fromFarm(
            StardewFarmSnapshot farm
    ) {
        return farm.farmLayoutAttachments().stream()
                .map(attachment ->
                        fromFarmAttachment(farm, attachment))
                .sorted(slotOrder())
                .toList();
    }

    private static Comparator<StardewMapSlot> slotOrder() {
        return Comparator
                .comparing((StardewMapSlot slot) ->
                        slot.scopeType().toString())
                .thenComparing(StardewMapSlot::scopeId)
                .thenComparing(slot -> slot.id().toString());
    }
}
