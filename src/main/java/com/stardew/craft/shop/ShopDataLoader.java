package com.stardew.craft.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.shop.StardewShopBinding;
import com.stardew.craft.api.v1.shop.StardewShopDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic datapack loaders for namespaced shops and their interaction bindings. */
@SuppressWarnings("null")
public final class ShopDataLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewShopDefinition> SHOPS = new AtomicDefinitionStore<>();
    private static final AtomicDefinitionStore<StardewShopBinding> BINDINGS = new AtomicDefinitionStore<>();

    private ShopDataLoader() {
    }

    @Nullable
    public static StardewShopDefinition getDefinition(ResourceLocation id) {
        return SHOPS.snapshot().definitions().get(id);
    }

    public static DefinitionSnapshot<StardewShopDefinition> snapshot() {
        return SHOPS.snapshot();
    }

    public static DefinitionSnapshot<StardewShopBinding> bindingSnapshot() {
        return BINDINGS.snapshot();
    }

    public static final class ShopReloadListener extends SimpleJsonResourceReloadListener {
        public ShopReloadListener() {
            super(GSON, "shops");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Candidate<StardewShopDefinition> candidate = parse(objects, StardewShopDefinition.CODEC, "Shop");
            finish("Shop", SHOPS.applyLocal(candidate.definitions(), candidate.sources(), candidate.diagnostics()));
        }
    }

    public static final class BindingReloadListener extends SimpleJsonResourceReloadListener {
        public BindingReloadListener() {
            super(GSON, "shop_bindings");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Candidate<StardewShopBinding> candidate = parse(objects, StardewShopBinding.CODEC, "Shop binding");
            finish("Shop binding", BINDINGS.applyLocal(
                    candidate.definitions(), candidate.sources(), candidate.diagnostics()));
        }
    }

    private static <T> Candidate<T> parse(Map<ResourceLocation, JsonElement> objects, Codec<T> codec, String name) {
        Map<ResourceLocation, T> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement root = entry.getValue();
            if (root == null || !root.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(id, id, name + " definition must be an object"));
                continue;
            }
            codec.parse(JsonOps.INSTANCE, root)
                    .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                    .ifPresent(definition -> {
                        definitions.put(id, definition);
                        sources.put(id, GSON.toJson(root));
                    });
        }
        return new Candidate<>(definitions, sources, diagnostics);
    }

    private static <T> void finish(String name, AtomicDefinitionStore.ApplyResult<T> result) {
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            String source = diagnostic.source() == null ? "<reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[{}] Definition error [{}]: {}", name, source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[{}] Definition warning [{}]: {}", name, source, diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[{}] Rejected snapshot; keeping v{} with {} definitions",
                    name, result.snapshot().version(), result.snapshot().definitions().size());
        } else {
            StardewCraft.LOGGER.info("[{}] Applied snapshot v{} ({} definitions)",
                    name, result.snapshot().version(), result.snapshot().definitions().size());
        }
    }

    private record Candidate<T>(
            Map<ResourceLocation, T> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
    }
}
