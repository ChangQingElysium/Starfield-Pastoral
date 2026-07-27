package com.stardew.craft.api.v1.world;

import com.stardew.craft.api.v1.content.StardewContentReferenceProvider;
import com.stardew.craft.api.v1.internal.world.StardewArtifactSpotDropRegistry;
import com.stardew.craft.manager.ArtifactDropService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Ordered position-aware artifact-spot drop overrides. */
public final class StardewArtifactSpotDrops {
    private StardewArtifactSpotDrops() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewArtifactSpotDropRegistry.register(id, priority, provider);
    }

    public static void register(
            ResourceLocation id,
            int priority,
            Provider provider,
            StardewContentReferenceProvider references
    ) {
        StardewArtifactSpotDropRegistry.register(
                id, priority, provider, references);
    }

    /** Immutable projection of the currently accepted effective drop groups. */
    public static List<PoolSnapshot> snapshot() {
        return ArtifactDropService.artifactSpotSnapshot();
    }

    @FunctionalInterface
    public interface Provider {
        /**
         * Returns replacement drops, {@code null} to pass, or an empty list to
         * explicitly suppress this artifact spot.
         */
        List<ItemStack> roll(Context context);
    }

    public record Context(
            ServerLevel level,
            BlockPos position,
            @Nullable ServerPlayer player,
            RandomSource random,
            @Nullable StardewLocation location,
            String coreLocation,
            String coreDropGroup
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            position = Objects.requireNonNull(position, "position").immutable();
            random = Objects.requireNonNull(random, "random");
            coreLocation = Objects.requireNonNull(coreLocation, "coreLocation");
            coreDropGroup = Objects.requireNonNull(coreDropGroup, "coreDropGroup");
        }
    }

    public record PoolSnapshot(
            String group,
            List<DropSnapshot> entries
    ) {
        public PoolSnapshot {
            group = Objects.requireNonNull(group, "group");
            entries = List.copyOf(entries);
        }
    }

    /**
     * Exact static item candidates for one rule. Dynamic rules may additionally
     * produce player-dependent notes or books.
     */
    public record DropSnapshot(
            String id,
            List<ResourceLocation> items,
            boolean dynamic
    ) {
        public DropSnapshot {
            id = Objects.requireNonNull(id, "id");
            items = List.copyOf(items);
        }
    }
}
