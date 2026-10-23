package com.stardew.craft.platform.fabric.registry;

import com.mojang.datafixers.util.Either;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** A stable registry key whose value is resolved from the real vanilla registry. */
public class DeferredHolder<R, T extends R> implements Holder<R>, Supplier<T> {
    protected final ResourceKey<R> key;
    private volatile Holder.Reference<R> delegate;
    private volatile T registeredValue;

    protected DeferredHolder(ResourceKey<R> key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    public static <R, T extends R> DeferredHolder<R, T> create(
            ResourceKey<? extends Registry<R>> registryKey, ResourceLocation id) {
        return create(ResourceKey.create(registryKey, id));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(
            ResourceLocation registryName, ResourceLocation id) {
        return create(ResourceKey.createRegistryKey(registryName), id);
    }

    @Override
    public T get() {
        return value();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T value() {
        T value = registeredValue;
        if (value != null) return value;
        Holder.Reference<R> holder = resolve();
        if (holder == null || !holder.isBound()) {
            throw new IllegalStateException("Registry entry is not registered yet: " + key
                    + ". Bootstrap its DeferredRegister before accessing the value.");
        }
        return (T) holder.value();
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

    /** Returns the vanilla holder, or {@code null} until its registry entry exists. */
    public Holder<R> getDelegate() {
        return resolve();
    }

    @SuppressWarnings("unchecked")
    private Holder.Reference<R> resolve() {
        Holder.Reference<R> current = delegate;
        if (current == null) {
            Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getOptional(key.registry()).orElse(null);
            if (registry != null) {
                current = registry.getHolder(key).orElse(null);
                if (current != null) delegate = current;
            }
        }
        return current;
    }

    final void bind(Holder.Reference<R> holder, T value) {
        if (!holder.is(key)) {
            throw new IllegalArgumentException("Cannot bind " + key + " to " + holder);
        }
        if (delegate != null && delegate != holder) {
            throw new IllegalStateException("Registry entry was already bound: " + key);
        }
        delegate = holder;
        // Vanilla binds non-intrusive holders only when the registry freezes.
        // Preserve the registered object for factories that depend on it meanwhile.
        registeredValue = Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean isBound() {
        if (registeredValue != null) return true;
        Holder.Reference<R> holder = resolve();
        return holder != null && holder.isBound();
    }

    @Override
    public boolean is(ResourceLocation id) {
        return key.location().equals(id);
    }

    @Override
    public boolean is(ResourceKey<R> other) {
        return key.equals(other);
    }

    @Override
    public boolean is(Predicate<ResourceKey<R>> predicate) {
        return predicate.test(key);
    }

    @Override
    public boolean is(TagKey<R> tag) {
        Holder.Reference<R> holder = resolve();
        return holder != null && holder.is(tag);
    }

    @Deprecated
    @Override
    public boolean is(Holder<R> holder) {
        return holder.unwrapKey().filter(key::equals).isPresent();
    }

    @Override
    public Stream<TagKey<R>> tags() {
        Holder.Reference<R> holder = resolve();
        return holder == null ? Stream.empty() : holder.tags();
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
        Holder.Reference<R> holder = resolve();
        return holder != null && holder.canSerializeIn(owner);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DeferredHolder<?, ?> holder && key.equals(holder.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + key + "}";
    }
}
