package com.stardew.craft.port.net.neoforged.neoforge.registries;

import com.stardew.craft.port.PortBootstrap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge {@code DeferredRegister} API on top of Forge's
 * {@link net.minecraftforge.registries.DeferredRegister}. Registries that have no Forge counterpart
 * (attachment types) are kept by the port layer; their entries are resolved lazily on first use.
 * {@link #register(IEventBus)} also installs the port bootstrap on the mod bus (see {@link PortBootstrap}).
 */
public class DeferredRegister<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    @Nullable
    private final net.minecraftforge.registries.DeferredRegister<T> forge;
    private final Map<DeferredHolder<T, ? extends T>, Supplier<? extends T>> entries = new LinkedHashMap<>();
    private final Collection<DeferredHolder<T, ? extends T>> entriesView = Collections.unmodifiableSet(entries.keySet());
    private boolean registeredToBus;

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry.key(), namespace);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace) {
        return new DeferredRegister<>(key, namespace);
    }

    public static <B> DeferredRegister<B> create(ResourceLocation registryName, String modid) {
        return new DeferredRegister<>(ResourceKey.createRegistryKey(registryName), modid);
    }

    public static Items createItems(String modid) {
        return new Items(modid);
    }

    public static Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.namespace = Objects.requireNonNull(namespace);
        this.forge = PortBootstrap.isPortOnlyRegistry(registryKey) ? null
                : net.minecraftforge.registries.DeferredRegister.create(registryKey, namespace);
    }

    public <I extends T> DeferredHolder<T, I> register(final String name, final Supplier<? extends I> sup) {
        return register(name, key -> sup.get());
    }

    @SuppressWarnings("unchecked")
    public <I extends T> DeferredHolder<T, I> register(final String name, final Function<ResourceLocation, ? extends I> func) {
        if (registeredToBus) throw new IllegalStateException("Cannot register new entries to DeferredRegister after register(IEventBus) has been called.");
        Objects.requireNonNull(name);
        Objects.requireNonNull(func);
        final ResourceLocation id = new ResourceLocation(namespace, name);
        ResourceKey<T> key = ResourceKey.create(registryKey, id);
        Supplier<I> supplier = () -> func.apply(id);
        DeferredHolder<T, I> holder;
        if (forge == null) {
            holder = DeferredHolder.portOnly(key, supplier);
        } else {
            RegistryObject<I> object = forge.register(name, supplier);
            holder = createHolder(key, (RegistryObject<T>) object);
        }
        if (entries.putIfAbsent(holder, supplier) != null) throw new IllegalArgumentException("Duplicate registration " + name);
        if (forge == null) PortBootstrap.trackPortOnlyEntry(registryKey, id, holder);
        return holder;
    }

    @SuppressWarnings("unchecked")
    protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<T> key, RegistryObject<T> object) {
        return new DeferredHolder<>(key, object);
    }

    public TagKey<T> createTagKey(String path) {
        return createTagKey(new ResourceLocation(namespace, path));
    }

    public TagKey<T> createTagKey(ResourceLocation location) {
        return TagKey.create(registryKey, location);
    }

    public void register(IEventBus bus) {
        if (registeredToBus) throw new IllegalStateException("Cannot register DeferredRegister to more than one event bus.");
        registeredToBus = true;
        PortBootstrap.install(bus);
        if (forge != null) forge.register(bus);
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return entriesView;
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

    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Function<ResourceLocation, ? extends B> func) {
            return (DeferredBlock<B>) super.register(name, func);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> sup) {
            return (DeferredBlock<B>) super.register(name, sup);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
            return this.register(name, () -> func.apply(props));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func) {
            return this.registerBlock(name, func, BlockBehaviour.Properties.of());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, BlockBehaviour.Properties props) {
            return this.registerBlock(name, Block::new, props);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return this.registerSimpleBlock(name, BlockBehaviour.Properties.of());
        }

        @Override
        protected <I extends Block> DeferredHolder<Block, I> createHolder(ResourceKey<Block> key, RegistryObject<Block> object) {
            return (DeferredHolder<Block, I>) (DeferredHolder<Block, ?>) new DeferredBlock<>(key, object);
        }
    }

    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends Item> DeferredItem<I> register(String name, Function<ResourceLocation, ? extends I> func) {
            return (DeferredItem<I>) super.register(name, func);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> sup) {
            return (DeferredItem<I>) super.register(name, sup);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
            return this.register(name, key -> new BlockItem(block.get(), properties));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return this.registerSimpleBlockItem(name, block, new Item.Properties());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block, Item.Properties properties) {
            return this.registerSimpleBlockItem(block.unwrapKey().orElseThrow().location().getPath(), block::value, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block) {
            return this.registerSimpleBlockItem(block, new Item.Properties());
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func, Item.Properties props) {
            return this.register(name, () -> func.apply(props));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func) {
            return this.registerItem(name, func, new Item.Properties());
        }

        public DeferredItem<Item> registerSimpleItem(String name, Item.Properties props) {
            return this.registerItem(name, Item::new, props);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return this.registerItem(name, Item::new);
        }

        @Override
        protected <I extends Item> DeferredHolder<Item, I> createHolder(ResourceKey<Item> key, RegistryObject<Item> object) {
            return (DeferredHolder<Item, I>) (DeferredHolder<Item, ?>) new DeferredItem<>(key, object);
        }
    }
}
