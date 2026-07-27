package com.stardew.craft.api.v1.internal.world;

import com.mojang.serialization.DataResult;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceProvider;
import com.stardew.craft.api.v1.internal.content.StardewContentRegistry;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.world.StardewArtifactSpotDrops;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Internal artifact-spot provider dispatch. */
public final class StardewArtifactSpotDropRegistry {
    private static final OrderedExtensionRegistry<
            StardewArtifactSpotDrops.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "world/artifact_spot_drops"));
    private static final Map<ResourceLocation,
            StardewContentReferenceProvider> REFERENCE_PROVIDERS =
            new ConcurrentHashMap<>();

    private StardewArtifactSpotDropRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewArtifactSpotDrops.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
        StardewContentRegistry.invalidate();
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewArtifactSpotDrops.Provider provider,
            StardewContentReferenceProvider references
    ) {
        Objects.requireNonNull(references, "references");
        PROVIDERS.register(id, priority, provider);
        REFERENCE_PROVIDERS.put(id, references);
        StardewContentRegistry.invalidate();
    }

    public static List<ResourceLocation> registeredIds() {
        return PROVIDERS.entries().stream()
                .map(OrderedExtensionRegistry.Entry::id)
                .toList();
    }

    public static DataResult<List<StardewContentReference>>
    contentReferences(
            ResourceLocation id,
            StardewContentKey owner
    ) {
        StardewContentReferenceProvider provider =
                REFERENCE_PROVIDERS.get(id);
        if (provider == null) {
            return DataResult.success(List.of());
        }
        try {
            var references = provider.references(owner);
            if (references == null) {
                return DataResult.error(() ->
                        "Artifact-spot reference provider "
                                + id + " returned null");
            }
            return DataResult.success(List.copyOf(references));
        } catch (RuntimeException exception) {
            return DataResult.error(() ->
                    "Artifact-spot reference provider "
                            + id + " failed: "
                            + exception.getMessage());
        }
    }

    @Nullable
    public static List<ItemStack> resolve(
            ServerLevel level,
            BlockPos position,
            @Nullable ServerPlayer player,
            String coreLocation,
            String coreDropGroup
    ) {
        for (var registered : PROVIDERS.entries()) {
            try {
                RandomSource providerRandom = RandomSource.create(
                        providerSeed(level, position, registered.id()));
                StardewArtifactSpotDrops.Context context =
                        new StardewArtifactSpotDrops.Context(
                                level,
                                position,
                                player,
                                providerRandom,
                                StardewLocations.find(level, position)
                                        .orElse(null),
                                coreLocation,
                                coreDropGroup);
                List<ItemStack> result =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.roll(context));
                if (result != null) {
                    return copy(result);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Artifact-spot drop provider {} failed at {}",
                        registered.id(), position, exception);
            }
        }
        return null;
    }

    private static long providerSeed(
            ServerLevel level,
            BlockPos position,
            ResourceLocation providerId
    ) {
        long idBits = Integer.toUnsignedLong(providerId.toString().hashCode());
        return level.getSeed()
                ^ position.asLong()
                ^ Long.rotateLeft(level.getGameTime(), 17)
                ^ Long.rotateLeft(idBits, 41);
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        ArrayList<ItemStack> copied = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null) {
                throw new IllegalArgumentException(
                        "Artifact-spot drops must not contain null stacks");
            }
            if (!stack.isEmpty()) {
                copied.add(stack.copy());
            }
        }
        return List.copyOf(copied);
    }

}
