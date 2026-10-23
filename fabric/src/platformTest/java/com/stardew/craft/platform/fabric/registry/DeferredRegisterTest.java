package com.stardew.craft.platform.fabric.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeferredRegisterTest {
    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoriesRemainLazyAndRunInDeclarationOrder() {
        MappedRegistry<Value> registry = registry("ordered");
        DeferredRegister<Value> register = DeferredRegister.create(registry, "registry_test");
        List<String> calls = new ArrayList<>();
        DeferredHolder<Value, Value> first = register.register("first", id -> {
            calls.add(id.getPath());
            return new Value(4);
        });
        DeferredHolder<Value, Value> second = register.register("second", () -> {
            calls.add("second");
            return new Value(first.get().number() + 1);
        });

        assertTrue(calls.isEmpty());
        register.register();
        assertEquals(List.of("first", "second"), calls);
        assertEquals(5, second.get().number());
        assertTrue(first.isBound());
        assertSame(registry.getHolderOrThrow(first.getKey()), first.getDelegate());
        // Non-intrusive vanilla holders only gain a value at freeze. Our wrapper
        // must already expose the successfully registered instance above.
        registry.freeze();
        assertSame(first.get(), registry.get(first.getKey()));
        assertSame(second.get(), second.getDelegate().value());
    }

    @Test
    void unregisteredAccessFailsWithoutConstructingTheObject() {
        DeferredRegister<Value> register = DeferredRegister.create(registry("unbound"), "registry_test");
        AtomicInteger calls = new AtomicInteger();
        DeferredHolder<Value, Value> holder = register.register("value", () -> new Value(calls.incrementAndGet()));

        assertFalse(holder.isBound());
        assertTrue(holder.asOptional().isEmpty());
        assertNull(holder.getDelegate());
        assertThrows(IllegalStateException.class, holder::get);
        assertEquals(0, calls.get());
    }

    @Test
    void aSuccessfulBootstrapIsIdempotentAndSealsTheEntryList() {
        MappedRegistry<Value> registry = registry("idempotent");
        DeferredRegister<Value> register = DeferredRegister.create(registry, "registry_test");
        AtomicInteger calls = new AtomicInteger();
        DeferredHolder<Value, Value> holder = register.register("value", () -> new Value(calls.incrementAndGet()));

        register.register();
        Value value = holder.get();
        register.register();
        assertSame(value, holder.get());
        assertEquals(1, calls.get());
        assertEquals(1, registry.size());
        assertThrows(IllegalStateException.class, () -> register.register("late", () -> new Value(2)));
    }

    @Test
    void duplicateNamesAreRejectedDuringCollection() {
        DeferredRegister<Value> register = DeferredRegister.create(registry("duplicate_name"), "registry_test");
        register.register("value", () -> new Value(1));

        assertThrows(IllegalArgumentException.class, () -> register.register("value", () -> new Value(2)));
        assertEquals(1, register.getEntries().size());
    }

    @Test
    void registryConflictsAreDetectedBeforeAnyFactoryRuns() {
        MappedRegistry<Value> registry = registry("duplicate_id");
        DeferredRegister<Value> first = DeferredRegister.create(registry, "registry_test");
        first.register("taken", () -> new Value(1));
        first.register();
        DeferredRegister<Value> second = DeferredRegister.create(registry, "registry_test");
        AtomicInteger calls = new AtomicInteger();
        second.register("unique", () -> new Value(calls.incrementAndGet()));
        second.register("taken", () -> new Value(calls.incrementAndGet()));

        assertThrows(IllegalArgumentException.class, second::register);
        assertEquals(0, calls.get());
        assertFalse(registry.containsKey(id("unique")));
        assertEquals(1, registry.size());
    }

    @Test
    void theSameObjectCannotBeRegisteredUnderTwoIds() {
        MappedRegistry<Value> registry = registry("duplicate_object");
        DeferredRegister<Value> register = DeferredRegister.create(registry, "registry_test");
        Value value = new Value(1);
        register.register("first", () -> value);
        register.register("second", () -> value);

        assertThrows(IllegalArgumentException.class, register::register);
        assertTrue(registry.containsKey(id("first")));
        assertFalse(registry.containsKey(id("second")));
    }

    @Test
    void aFailedPartialBootstrapCannotSilentlyRerunFactories() {
        MappedRegistry<Value> registry = registry("failed");
        DeferredRegister<Value> register = DeferredRegister.create(registry, "registry_test");
        DeferredHolder<Value, Value> first = register.register("first", () -> new Value(1));
        AtomicInteger calls = new AtomicInteger();
        register.register("failure", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("factory failure");
        });
        register.register("last", () -> new Value(3));

        assertThrows(IllegalStateException.class, register::register);
        assertTrue(first.isBound());
        assertFalse(registry.containsKey(id("last")));
        IllegalStateException retry = assertThrows(IllegalStateException.class, register::register);
        assertEquals("factory failure", retry.getCause().getMessage());
        assertEquals(1, calls.get());
    }

    @Test
    void registerAllHonorsCrossRegistryDependencyOrder() {
        DeferredRegister<Value> first = DeferredRegister.create(registry("dependency_first"), "registry_test");
        DeferredRegister<Value> second = DeferredRegister.create(registry("dependency_second"), "registry_test");
        DeferredHolder<Value, Value> dependency = first.register("source", () -> new Value(8));
        DeferredHolder<Value, Value> result = second.register("derived", () -> new Value(dependency.get().number() * 2));

        DeferredRegister.registerAll(first, second);
        assertEquals(16, result.get().number());
    }

    @Test
    void unknownRegistriesFailInsteadOfBecomingDetachedSuppliers() {
        ResourceKey<Registry<Value>> key = ResourceKey.createRegistryKey(id("missing_registry"));
        DeferredRegister<Value> register = DeferredRegister.create(key, "registry_test");
        AtomicInteger calls = new AtomicInteger();
        register.register("value", () -> new Value(calls.incrementAndGet()));

        assertThrows(IllegalStateException.class, register::register);
        assertEquals(0, calls.get());
        assertEquals(key, register.getRegistryKey());
        assertEquals(key.location(), register.getRegistryName());
        assertEquals("registry_test", register.getNamespace());
    }

    @Test
    void entryViewIsOrderedLiveAndUnmodifiable() {
        DeferredRegister<Value> register = DeferredRegister.create(registry("entry_view"), "registry_test");
        var view = register.getEntries();
        DeferredHolder<Value, Value> first = register.register("first", () -> new Value(1));
        DeferredHolder<Value, Value> second = register.register("second", () -> new Value(2));

        assertEquals(List.of(first, second), new ArrayList<>(view));
        assertThrows(UnsupportedOperationException.class, view::clear);
    }

    @Test
    void keyOnlyHoldersResolveVanillaEntriesWithoutDefaultFallback() {
        DeferredHolder<net.minecraft.world.item.Item, net.minecraft.world.item.Item> holder =
                DeferredHolder.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("stone"));

        assertTrue(holder.isBound());
        assertSame(Items.STONE, holder.get());
        assertSame(BuiltInRegistries.ITEM.getHolderOrThrow(holder.getKey()), holder.getDelegate());
        assertEquals(Holder.Kind.REFERENCE, holder.kind());
        assertEquals(holder.getKey(), holder.unwrapKey().orElseThrow());
        assertEquals(holder.getKey(), holder.unwrap().left().orElseThrow());
        assertTrue(holder.canSerializeIn(BuiltInRegistries.ITEM.holderOwner()));
        DeferredHolder<net.minecraft.world.item.Item, net.minecraft.world.item.Item> missing =
                DeferredHolder.create(Registries.ITEM, id("missing_item"));
        assertFalse(missing.isBound());
        assertThrows(IllegalStateException.class, missing::get);
    }

    @Test
    void tagsAndSerializationUseTheActualRegistryHolder() {
        MappedRegistry<Value> registry = registry("tags");
        DeferredRegister<Value> register = DeferredRegister.create(registry, "registry_test");
        DeferredHolder<Value, Value> holder = register.register("value", () -> new Value(1));
        TagKey<Value> tag = register.createTagKey("group");
        register.register();
        registry.freeze();
        registry.bindTags(Map.of(tag, List.of(holder.getDelegate())));

        assertTrue(holder.is(tag));
        assertEquals(List.of(tag), holder.tags().toList());
        assertTrue(holder.is(holder.getKey()));
        assertTrue(holder.is(id("value")));
        assertTrue(holder.is(key -> key.location().getPath().equals("value")));
        assertTrue(holder.is(holder.getDelegate()));
        assertTrue(holder.canSerializeIn(registry.holderOwner()));
        assertFalse(holder.canSerializeIn(registry("other_owner").holderOwner()));
    }

    @Test
    void itemAndBlockWrappersRetainItemLikeAndStackBehavior() {
        DeferredItem<net.minecraft.world.item.Item> item = DeferredItem.createItem(ResourceLocation.withDefaultNamespace("stone"));
        DeferredBlock<net.minecraft.world.level.block.Block> block = DeferredBlock.createBlock(ResourceLocation.withDefaultNamespace("stone"));

        assertSame(Items.STONE, item.asItem());
        assertSame(Blocks.STONE, block.get());
        assertSame(Items.STONE, block.asItem());
        assertEquals(3, item.toStack(3).getCount());
        assertEquals(2, block.toStack(2).getCount());
        assertThrows(IllegalStateException.class, () -> DeferredBlock.createBlock(ResourceLocation.withDefaultNamespace("air")).toStack());
    }

    @Test
    void recursiveBootstrapAndNullFactoriesFailClearly() {
        DeferredRegister<Value> recursive = DeferredRegister.create(registry("recursive"), "registry_test");
        recursive.register("value", () -> {
            recursive.register();
            return new Value(1);
        });
        assertThrows(IllegalStateException.class, recursive::register);

        MappedRegistry<Value> registry = registry("null_factory");
        DeferredRegister<Value> nullFactory = DeferredRegister.create(registry, "registry_test");
        nullFactory.register("value", () -> (Value) null);
        assertThrows(NullPointerException.class, nullFactory::register);
        assertTrue(registry.isEmpty());
    }

    private static MappedRegistry<Value> registry(String path) {
        return new MappedRegistry<>(ResourceKey.createRegistryKey(id(path)), Lifecycle.stable());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("registry_test", path);
    }

    private record Value(int number) {}
}
