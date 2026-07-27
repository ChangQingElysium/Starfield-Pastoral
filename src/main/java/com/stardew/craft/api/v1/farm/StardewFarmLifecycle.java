package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmLifecycleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Stable registration facade for farm create, transfer and delete observers. */
public final class StardewFarmLifecycle {
    private StardewFarmLifecycle() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFarmLifecycleListener listener
    ) {
        StardewFarmLifecycleRegistry.register(id, priority, listener);
    }

    public record CreateRequest(
            @Nullable MinecraftServer server,
            UUID ownerUuid,
            String ownerName,
            String farmName,
            ResourceLocation farmTypeId
    ) {
        public CreateRequest {
            ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
            ownerName = Objects.requireNonNull(ownerName, "ownerName");
            farmName = Objects.requireNonNull(farmName, "farmName");
            farmTypeId = Objects.requireNonNull(farmTypeId, "farmTypeId");
        }
    }

    public record FarmContext(
            @Nullable MinecraftServer server,
            StardewFarmSnapshot farm
    ) {
        public FarmContext {
            farm = Objects.requireNonNull(farm, "farm");
        }
    }

    public record TransferRequest(
            @Nullable MinecraftServer server,
            StardewFarmSnapshot source,
            UUID newOwnerUuid,
            String newOwnerName
    ) {
        public TransferRequest {
            source = Objects.requireNonNull(source, "source");
            newOwnerUuid = Objects.requireNonNull(newOwnerUuid, "newOwnerUuid");
            newOwnerName = Objects.requireNonNull(newOwnerName, "newOwnerName");
        }
    }

    public record TransferResult(
            @Nullable MinecraftServer server,
            StardewFarmSnapshot source,
            StardewFarmSnapshot transferred
    ) {
        public TransferResult {
            source = Objects.requireNonNull(source, "source");
            transferred = Objects.requireNonNull(transferred, "transferred");
        }
    }
}
