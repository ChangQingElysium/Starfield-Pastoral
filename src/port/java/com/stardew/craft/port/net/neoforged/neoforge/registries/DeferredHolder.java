package com.stardew.craft.port.net.neoforged.neoforge.registries;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.DataMapType;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge {@code DeferredHolder} backed by a Forge {@link RegistryObject}.
 * <p>
 * 1.20.1's {@link Holder} already extends {@code Supplier<R>}, so this class cannot also implement
 * {@code Supplier<T>}; {@link #get()} and {@link #value()} still return {@code T} covariantly.
 * Holders of registries Forge does not know (NeoForge attachment types) resolve their supplier lazily instead.
 */
public class DeferredHolder<R, T extends R> implements Holder<R> {
    protected final ResourceKey<R> key;
    @Nullable
    private final RegistryObject<R> object;
    @Nullable
    private final Supplier<? extends T> detached;
    @Nullable
    private T detachedValue;

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation valueName) {
        return create(ResourceKey.create(registryKey, valueName));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceLocation registryName, ResourceLocation valueName) {
        return create(ResourceKey.createRegistryKey(registryName), valueName);
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    protected DeferredHolder(ResourceKey<R> key) {
        this(key, RegistryObject.<R, R>createOptional(key.location(), ResourceKey.<R>createRegistryKey(key.registry()), key.location().getNamespace()));
    }

    DeferredHolder(ResourceKey<R> key, RegistryObject<R> object) {
        this.key = key;
        this.object = object;
        this.detached = null;
    }

    /** Holder for an entry of a registry that only exists in the port layer (resolved on first access). */
    static <R, T extends R> DeferredHolder<R, T> portOnly(ResourceKey<R> key, Supplier<? extends T> supplier) {
        return new DeferredHolder<>(key, supplier, true);
    }

    private DeferredHolder(ResourceKey<R> key, Supplier<? extends T> detached, boolean portOnly) {
        this.key = key;
        this.object = null;
        this.detached = detached;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T value() {
        if (object == null) {
            synchronized (this) {
                if (detachedValue == null) {
                    detachedValue = detached.get();
                    if (detachedValue == null) throw new NullPointerException("Supplier returned null for " + key);
                }
                return detachedValue;
            }
        }
        if (!object.isPresent()) throw new NullPointerException("Trying to access unbound value: " + this.key);
        return (T) object.get();
    }

    @Override
    public T get() {
        return value();
    }

    public Optional<T> asOptional() {
        return isBound() ? Optional.of(value()) : Optional.empty();
    }

    public ResourceLocation getId() {
        return key.location();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    /** The vanilla registry holder, or {@code null} while unbound / for port-only registries. */
    @Nullable
    public Holder<R> getDelegate() {
        return object == null ? null : object.getHolder().orElse(null);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof DeferredHolder<?, ?> other) return other.key == key;
        return obj instanceof Holder<?> h && h.kind() == Kind.REFERENCE && h.unwrapKey().orElse(null) == key;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "DeferredHolder{%s}", key);
    }

    @Override
    public boolean isBound() {
        if (object == null) return true;
        return object.isPresent();
    }

    @Override
    public boolean is(ResourceLocation id) {
        return id.equals(key.location());
    }

    @Override
    public boolean is(ResourceKey<R> key) {
        return key == this.key;
    }

    @Override
    public boolean is(Predicate<ResourceKey<R>> filter) {
        return filter.test(key);
    }

    @Override
    public boolean is(TagKey<R> tag) {
        Holder<R> delegate = getDelegate();
        return delegate != null && delegate.is(tag);
    }

    @Deprecated
    public boolean is(Holder<R> holder) {
        return holder.unwrapKey().map(k -> k == key).orElse(false);
    }

    @Nullable
    public <Z> Z getData(DataMapType<R, Z> type) {
        Holder<R> delegate = getDelegate();
        return delegate == null ? null : com.stardew.craft.port.PortDataMaps.getData(delegate, type);
    }

    @Override
    public Stream<TagKey<R>> tags() {
        Holder<R> delegate = getDelegate();
        return delegate == null ? Stream.empty() : delegate.tags();
    }

    @Override
    public Either<ResourceKey<R>, R> unwrap() {
        return Either.left(key);
    }

    @Override
    public Optional<ResourceKey<R>> unwrapKey() {
        return Optional.of(key);
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<R> owner) {
        Holder<R> delegate = getDelegate();
        return delegate != null && delegate.canSerializeIn(owner);
    }
}
