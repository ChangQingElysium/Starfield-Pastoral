package com.stardew.craft.api.v1.cutscene;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewTypedContentReferenceProvider;
import com.stardew.craft.cutscene.command.EventCommand;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Public client command factory registry. Custom commands are visual-only; state uses Actions. */
public final class StardewCutsceneCommands {
    private static final Map<ResourceLocation, StardewCutsceneCommandFactory> FACTORIES = new LinkedHashMap<>();
    private static final Map<ResourceLocation,
            StardewTypedContentReferenceProvider<JsonObject>>
            REFERENCE_PROVIDERS = new LinkedHashMap<>();

    private StardewCutsceneCommands() {
    }

    public static synchronized void register(ResourceLocation id, StardewCutsceneCommandFactory factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("Cutscene command already registered: " + id);
        }
    }

    public static synchronized void register(
            ResourceLocation id,
            StardewCutsceneCommandFactory factory,
            StardewTypedContentReferenceProvider<JsonObject> references
    ) {
        Objects.requireNonNull(references, "references");
        register(id, factory);
        REFERENCE_PROVIDERS.put(id, references);
    }

    public static <T> void register(
            ResourceLocation id,
            Codec<T> codec,
            Function<T, EventCommand> factory,
            StardewTypedContentReferenceProvider<T> references
    ) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(references, "references");
        register(
                id,
                data -> factory.apply(decode(codec, data)),
                (owner, data) -> references.references(
                        owner, decode(codec, data)));
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(FACTORIES.keySet());
    }

    @Nullable
    public static synchronized EventCommand create(ResourceLocation id, JsonObject data) {
        StardewCutsceneCommandFactory factory = FACTORIES.get(id);
        return factory == null ? null : factory.create(data);
    }

    public static synchronized DataResult<List<StardewContentReference>>
    contentReferences(
            StardewContentKey owner,
            ResourceLocation id,
            JsonObject data
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
        if (!FACTORIES.containsKey(id)) {
            return DataResult.error(() ->
                    "Unknown cutscene command type: " + id);
        }
        StardewTypedContentReferenceProvider<JsonObject> provider =
                REFERENCE_PROVIDERS.get(id);
        if (provider == null) {
            return DataResult.success(List.of());
        }
        try {
            return DataResult.success(List.copyOf(
                    Objects.requireNonNull(
                            provider.references(owner, data.deepCopy()),
                            "cutscene command reference provider result")));
        } catch (RuntimeException exception) {
            return DataResult.error(() ->
                    "Cutscene command reference provider " + id
                            + " failed: " + exception.getMessage());
        }
    }

    private static <T> T decode(Codec<T> codec, JsonObject data) {
        return codec.parse(JsonOps.INSTANCE, data).getOrThrow();
    }
}
