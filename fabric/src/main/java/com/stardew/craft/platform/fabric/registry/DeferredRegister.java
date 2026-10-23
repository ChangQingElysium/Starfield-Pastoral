package com.stardew.craft.platform.fabric.registry;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Collects factories without constructing game objects during class initialization.
 * Call {@link #register()} from the Fabric initializer before registries freeze.
 * Registers and entries are processed in their declared order, so dependencies must
 * be bootstrapped first (for example, blocks before their block items).
 */
public class DeferredRegister<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final Registry<T> explicitRegistry;
    private final Map<ResourceLocation, Entry<T, ?>> entries = new LinkedHashMap<>();
    private final Collection<DeferredHolder<T, ? extends T>> holders = new LinkedHashSet<>();
    private final Collection<DeferredHolder<T, ? extends T>> holdersView = Collections.unmodifiableCollection(holders);
    private State state = State.COLLECTING;
    private Throwable failure;
    private IEventBus boundBus;

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this(registryKey, namespace, null);
    }

    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace, Registry<T> registry) {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        ResourceLocation.fromNamespaceAndPath(namespace, "validate_namespace");
        this.explicitRegistry = registry;
    }

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        Objects.requireNonNull(registry, "registry");
        return new DeferredRegister<>(registry.key(), namespace, registry);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    public static <T> DeferredRegister<T> create(ResourceLocation registryName, String namespace) {
        return create(ResourceKey.createRegistryKey(registryName), namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return register(name, id -> supplier.get());
    }

    public synchronized <I extends T> DeferredHolder<T, I> register(
            String name, Function<ResourceLocation, ? extends I> factory) {
        if (state != State.COLLECTING) {
            throw new IllegalStateException("Cannot add entries after bootstrap started: " + registryKey);
        }
        Objects.requireNonNull(factory, "factory");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, Objects.requireNonNull(name, "name"));
        if (entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate registration in " + registryKey.location() + ": " + id);
        }
        DeferredHolder<T, I> holder = createHolder(ResourceKey.create(registryKey, id));
        entries.put(id, new Entry<>(holder, () -> factory.apply(id)));
        holders.add(holder);
        return holder;
    }

    protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<T> key) {
        return new DeferredHolder<>(key);
    }

    /** Binds this register to the real, ordered initialization pass without constructing its entries. */
    public synchronized void register(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (boundBus == bus) return;
        if (boundBus != null) throw new IllegalStateException("Deferred register is already bound to another bus: " + registryKey);
        if (state != State.COLLECTING) throw new IllegalStateException("Cannot bind an already bootstrapped register: " + registryKey);
        RegistryBootstrap.bind(bus, this);
        boundBus = bus;
        bus.addListener(RegistryRegistrationEvent.class, event -> {
            if (event.getRegistryKey().equals(registryKey)) register();
        });
    }

    synchronized boolean isRegistered() { return state == State.REGISTERED; }

    /** Registers every collected factory once. Repeated successful calls are harmless. */
    public synchronized void register() {
        if (state == State.REGISTERED) return;
        if (state == State.REGISTERING) {
            throw new IllegalStateException("Recursive bootstrap of " + registryKey);
        }
        if (state == State.FAILED) {
            throw new IllegalStateException("Previous registry bootstrap failed: " + registryKey, failure);
        }
        Registry<T> registry = getRegistry();
        // Detect ID conflicts before constructing intrusive holders or mutating the registry.
        for (ResourceLocation id : entries.keySet()) {
            if (registry.containsKey(id)) {
                throw new IllegalArgumentException("Registry ID already exists in " + registryKey.location() + ": " + id);
            }
        }
        state = State.REGISTERING;
        try {
            for (Entry<T, ?> entry : entries.values()) entry.register(registry);
            state = State.REGISTERED;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            state = State.FAILED;
            throw exception;
        }
    }

    /** Bootstraps registers in the supplied dependency order; it does not guess dependencies. */
    public static void registerAll(DeferredRegister<?>... registers) {
        for (DeferredRegister<?> register : registers) Objects.requireNonNull(register, "register").register();
    }

    @SuppressWarnings("unchecked")
    public Registry<T> getRegistry() {
        if (explicitRegistry != null) return explicitRegistry;
        return (Registry<T>) BuiltInRegistries.REGISTRY.getOptional(registryKey.location()).orElseThrow(
                () -> new IllegalStateException("No static Fabric registry exists for " + registryKey.location()
                        + ". Create the custom registry first; dynamic registries require a data-pack bootstrap."));
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return holdersView;
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    public ResourceLocation getRegistryName() {
        return registryKey.location();
    }

    public String getNamespace() {
        return namespace;
    }

    public TagKey<T> createTagKey(String path) {
        return createTagKey(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public TagKey<T> createTagKey(ResourceLocation id) {
        return TagKey.create(registryKey, id);
    }

    private enum State { COLLECTING, REGISTERING, REGISTERED, FAILED }

    private record Entry<R, I extends R>(DeferredHolder<R, I> holder, Supplier<? extends I> supplier) {
        private void register(Registry<R> registry) {
            I value = Objects.requireNonNull(supplier.get(), "Factory returned null for " + holder.getKey());
            if (registry.containsKey(holder.getKey())) {
                throw new IllegalArgumentException("Factory registered an existing ID: " + holder.getKey());
            }
            if (registry.getResourceKey(value).isPresent()) {
                throw new IllegalArgumentException("Object is already registered: " + holder.getKey());
            }
            holder.bind(Registry.registerForHolder(registry, holder.getKey(), value), value);
        }
    }

    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> supplier) {
            return (DeferredBlock<B>) super.register(name, supplier);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Function<ResourceLocation, ? extends B> factory) {
            return (DeferredBlock<B>) super.register(name, factory);
        }

        @Override
        protected <B extends Block> DeferredBlock<B> createHolder(ResourceKey<Block> key) {
            return new DeferredBlock<>(key);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(
                String name, Function<BlockBehaviour.Properties, ? extends B> factory, BlockBehaviour.Properties properties) {
            return register(name, () -> factory.apply(properties));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(
                String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
            return registerBlock(name, factory, BlockBehaviour.Properties.of());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, BlockBehaviour.Properties properties) {
            return registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return registerSimpleBlock(name, BlockBehaviour.Properties.of());
        }
    }

    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> supplier) {
            return (DeferredItem<I>) super.register(name, supplier);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends Item> DeferredItem<I> register(String name, Function<ResourceLocation, ? extends I> factory) {
            return (DeferredItem<I>) super.register(name, factory);
        }

        @Override
        protected <I extends Item> DeferredItem<I> createHolder(ResourceKey<Item> key) {
            return new DeferredItem<>(key);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(
                String name, Supplier<? extends Block> block, Item.Properties properties) {
            return register(name, () -> new BlockItem(block.get(), properties));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return registerSimpleBlockItem(name, block, new Item.Properties());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block, Item.Properties properties) {
            return registerSimpleBlockItem(block.unwrapKey().orElseThrow().location().getPath(), block::value, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block) {
            return registerSimpleBlockItem(block, new Item.Properties());
        }

        public <I extends Item> DeferredItem<I> registerItem(
                String name, Function<Item.Properties, ? extends I> factory, Item.Properties properties) {
            return register(name, () -> factory.apply(properties));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, new Item.Properties());
        }

        public DeferredItem<Item> registerSimpleItem(String name, Item.Properties properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return registerSimpleItem(name, new Item.Properties());
        }
    }
}
