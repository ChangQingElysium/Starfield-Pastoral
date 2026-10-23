package com.stardew.craft.platform.fabric.datamap;

import static com.stardew.craft.platform.fabric.datamap.DataMapTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

class DataMapLoaderTest {
    @Test
    void higherPriorityPacksOverrideAndFileReplaceAndRemovalsRunInTheCorrectOrder() {
        var registry = registry();
        var type = DataMapType.builder(ResourceLocation.parse("fixture:numbers"), REGISTRY, Codec.INT).build();
        var low = read(type, "{\"values\":{\"#fixture:group\":1,\"fixture:b\":2}}");
        var high = read(type, "{\"values\":{\"fixture:a\":3},\"remove\":[\"fixture:b\",\"fixture:missing\"]}");
        assertEquals(Map.of(A, 3), DataMapLoader.build(registry, type, List.of(low, high)));
        var replace = read(type, "{\"replace\":true,\"values\":{\"fixture:b\":4}}");
        assertEquals(Map.of(B, 4), DataMapLoader.build(registry, type, List.of(low, replace)));
        var removeTag = read(type, "{\"values\":{},\"remove\":[\"#fixture:group\"]}");
        assertEquals(Map.of(B, 2), DataMapLoader.build(registry, type, List.of(low, removeTag)));
    }

    @Test
    void advancedMergersReceiveActualSourcesAndEntryReplaceBypassesTheMerger() {
        var registry = registry();
        var firstSources = new java.util.ArrayList<String>();
        var type = AdvancedDataMapType.builder(ResourceLocation.parse("fixture:effects"), REGISTRY, Codec.STRING.listOf())
                .merger((reg, first, old, second, newer) -> {
                    assertSame(registry, reg);
                    firstSources.add(first.map(tag -> "#" + tag.location(), key -> key.location().toString()));
                    return java.util.stream.Stream.concat(old.stream(), newer.stream()).toList();
                }).build();
        var low = read(type, "{\"values\":{\"#fixture:group\":[\"old\"],\"fixture:b\":[\"b-old\"]}}");
        var high = read(type, "{\"values\":{\"fixture:a\":[\"new\"],\"fixture:b\":{\"value\":[\"b-new\"],\"replace\":true}}}");
        assertEquals(Map.of(A, List.of("old", "new"), B, List.of("b-new")), DataMapLoader.build(registry, type, List.of(low, high)));
        assertEquals(List.of("#fixture:group"), firstSources);
    }

    @Test
    void advancedCustomRemoversSupportObjectAndObjectListFormsWithoutMutatingTheOriginal() {
        var registry = registry();
        var type = AdvancedDataMapType.builder(ResourceLocation.parse("fixture:partial"), REGISTRY, Codec.STRING.listOf())
                .remover(Codec.STRING.xmap(RemoveValue::new, RemoveValue::key)).build();
        var low = read(type, "{\"values\":{\"fixture:a\":[\"one\",\"two\"],\"fixture:b\":[\"three\"]}}");
        var partial = read(type, "{\"values\":{},\"remove\":{\"fixture:a\":\"one\"}}");
        assertEquals(Map.of(A, List.of("two"), B, List.of("three")), DataMapLoader.build(registry, type, List.of(low, partial)));
        assertEquals(List.of("one", "two"), low.values().getFirst().value());
        var objects = read(type, "{\"values\":{},\"remove\":[{\"key\":\"fixture:a\",\"remover\":\"two\"},{\"key\":\"fixture:b\"}]}");
        assertEquals(Map.of(A, List.of("one")), DataMapLoader.build(registry, type, List.of(low, objects)));
    }

    @Test
    void reloadCommitsOnlyAfterNewTagsAreBoundAndRemovedFilesClearPreviousValues() {
        var registry = registry();
        var type = DataMapType.builder(ResourceLocation.parse("fixture:reload"), REGISTRY, Codec.INT).build();
        DataMaps.publishServer(Map.of(registry.holderOwner(), Map.of(type, Map.of(A, 4))));
        var loader = new DataMapLoader(access(registry), List.of(type));
        var manager = resources(DataMapLoader.fileLocation(type), "{\"values\":{\"#fixture:group\":6}}");
        loader.reload(immediateBarrier(), manager, null, null, Runnable::run, Runnable::run).join();
        assertEquals(Map.of(A, 4), DataMaps.getDataMap(registry, type));
        registry.bindTags(Map.of(GROUP, List.of(registry.getHolder(B).orElseThrow())));
        loader.applyAfterTags();
        assertEquals(Map.of(B, 6), DataMaps.getDataMap(registry, type));
        loader.reload(immediateBarrier(), resources(DataMapLoader.fileLocation(type)), null, null, Runnable::run, Runnable::run).join();
        loader.applyAfterTags();
        assertTrue(DataMaps.getDataMap(registry, type).isEmpty());
    }

    @Test
    void unsupportedPackConditionsFailReloadInsteadOfAttachingOrDeletingDataUnconditionally() {
        var registry = registry();
        var type = DataMapType.builder(ResourceLocation.parse("fixture:conditional"), REGISTRY, Codec.INT).build();
        DataMaps.publishServer(Map.of(registry.holderOwner(), Map.of(type, Map.of(A, 9))));
        var loader = new DataMapLoader(access(registry), List.of(type));
        var manager = resources(DataMapLoader.fileLocation(type), "{\"neoforge:conditions\":[],\"values\":{\"fixture:a\":1}}");
        var failure = assertThrows(CompletionException.class, () -> loader.reload(immediateBarrier(), manager, null, null, Runnable::run, Runnable::run).join());
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
        assertEquals(Map.of(A, 9), DataMaps.getDataMap(registry, type));
        assertThrows(UnsupportedOperationException.class, () -> read(type,
                "{\"values\":{\"fixture:a\":{\"neoforge:conditions\":[],\"value\":1}}}"));
    }

    @Test
    void resourcePathsKeepTheMapNamespaceAndOnlyOmitMinecraftRegistryNamespace() {
        var fixture = DataMapType.builder(ResourceLocation.parse("addon:folder/map"), REGISTRY, Codec.INT).build();
        assertEquals("addon:data_maps/fixture/objects/folder/map.json", DataMapLoader.fileLocation(fixture).toString());
        var items = DataMapType.builder(ResourceLocation.parse("addon:map"), net.minecraft.core.registries.Registries.ITEM, Codec.INT).build();
        assertEquals("addon:data_maps/item/map.json", DataMapLoader.fileLocation(items).toString());
    }

    private static <R, T> DataMapLoader.File<R, T> read(DataMapType<R, T> type, String json) {
        return DataMapLoader.read(type, JsonParser.parseString(json), JsonOps.INSTANCE);
    }

    private static PreparableReloadListener.PreparationBarrier immediateBarrier() {
        return new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T prepared) { return CompletableFuture.completedFuture(prepared); }
        };
    }

    private record RemoveValue(String key) implements DataMapValueRemover<String, List<String>> {
        @Override public Optional<List<String>> remove(List<String> value, net.minecraft.core.Registry<String> registry,
                com.mojang.datafixers.util.Either<net.minecraft.tags.TagKey<String>, net.minecraft.resources.ResourceKey<String>> source,
                String object) { return Optional.of(value.stream().filter(item -> !item.equals(key)).toList()); }
    }
}
