package com.stardew.craft.port;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.stardew.craft.port.net.neoforged.neoforge.attachment.AttachmentType;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * PORT(1.20.1): NeoForge data attachments ({@code holder.getData/setData/hasData/removeData}) on Forge capabilities.
 * <p>
 * Each supported holder gets one {@link Store} capability, saved under {@code ForgeCaps/stardewcraft:attachments}
 * as {@code {attachment id: codec-encoded value}}. Supported holders are {@link LevelChunk} (saved with the chunk)
 * and {@link Player} (saved with the player, copied on respawn when the type is {@code copyOnDeath}, always copied
 * when returning from the End), which covers every attachment the mod registers. Other holders throw.
 */
public final class PortAttachments {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAPABILITY_ID = new ResourceLocation(PortBootstrap.NAMESPACE, "attachments");
    public static final Capability<Store> STORE = CapabilityManager.get(new CapabilityToken<>() {});
    private static final Map<ResourceLocation, DeferredHolder<?, ?>> HOLDERS = new LinkedHashMap<>();
    private static final Map<AttachmentType<?>, ResourceLocation> IDS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, AttachmentType<?>> TYPES = new LinkedHashMap<>();

    private PortAttachments() {}

    static synchronized void track(ResourceLocation id, DeferredHolder<?, ?> holder) {
        HOLDERS.put(id, holder);
    }

    /** Resolves every registered attachment type so ids are known before any holder is (de)serialized. */
    static synchronized void resolveAll() {
        HOLDERS.forEach((id, holder) -> {
            if (TYPES.containsKey(id)) return;
            AttachmentType<?> type = (AttachmentType<?>) holder.get();
            TYPES.put(id, type);
            IDS.put(type, id);
        });
    }

    @Nullable
    private static synchronized ResourceLocation idOf(AttachmentType<?> type) {
        ResourceLocation id = IDS.get(type);
        if (id == null && TYPES.size() < HOLDERS.size()) {
            resolveAll();
            id = IDS.get(type);
        }
        return id;
    }

    @Nullable
    private static synchronized AttachmentType<?> typeOf(ResourceLocation id) {
        if (TYPES.size() < HOLDERS.size()) resolveAll();
        return TYPES.get(id);
    }

    // ---- call-site API (replaces IAttachmentHolder methods) -------------------------------------------------

    public static <T> T getData(ICapabilityProvider holder, DeferredHolder<AttachmentType<?>, AttachmentType<T>> type) {
        return getData(holder, type.get());
    }

    public static <T> T getData(ICapabilityProvider holder, AttachmentType<T> type) {
        return store(holder).get(type);
    }

    public static <T> Optional<T> getExistingData(ICapabilityProvider holder, DeferredHolder<AttachmentType<?>, AttachmentType<T>> type) {
        return Optional.ofNullable(store(holder).getExisting(type.get()));
    }

    public static boolean hasData(ICapabilityProvider holder, DeferredHolder<AttachmentType<?>, AttachmentType<?>> type) {
        return store(holder).has(type.get());
    }

    public static boolean hasData(ICapabilityProvider holder, AttachmentType<?> type) {
        return store(holder).has(type);
    }

    @Nullable
    public static <T> T setData(ICapabilityProvider holder, DeferredHolder<AttachmentType<?>, AttachmentType<T>> type, T value) {
        return setData(holder, type.get(), value);
    }

    @Nullable
    public static <T> T setData(ICapabilityProvider holder, AttachmentType<T> type, T value) {
        T previous = store(holder).set(type, Objects.requireNonNull(value));
        if (holder instanceof LevelChunk chunk) chunk.setUnsaved(true);
        return previous;
    }

    @Nullable
    public static <T> T removeData(ICapabilityProvider holder, DeferredHolder<AttachmentType<?>, AttachmentType<T>> type) {
        return removeData(holder, type.get());
    }

    @Nullable
    public static <T> T removeData(ICapabilityProvider holder, AttachmentType<T> type) {
        T previous = store(holder).remove(type);
        if (previous != null && holder instanceof LevelChunk chunk) chunk.setUnsaved(true);
        return previous;
    }

    private static Store store(ICapabilityProvider holder) {
        return holder.getCapability(STORE).resolve().orElseThrow(() -> new UnsupportedOperationException(
                "PORT(1.20.1): data attachments are only available on LevelChunk and Player, not " + holder.getClass().getName()));
    }

    // ---- Forge wiring ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void attachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        attach(event);
    }

    @SubscribeEvent
    public static void attachEntity(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) attach(event);
    }

    private static void attach(AttachCapabilitiesEvent<?> event) {
        Store store = new Store();
        LazyOptional<Store> optional = LazyOptional.of(() -> store);
        event.addCapability(CAPABILITY_ID, new ICapabilitySerializable<CompoundTag>() {
            @Override
            public <C> @NotNull LazyOptional<C> getCapability(@NotNull Capability<C> cap, @Nullable Direction side) {
                return STORE.orEmpty(cap, optional);
            }

            @Override
            public CompoundTag serializeNBT() {
                return store.serialize();
            }

            @Override
            public void deserializeNBT(CompoundTag tag) {
                store.deserialize(tag);
            }
        });
        event.addListener(optional::invalidate);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            Store from = original.getCapability(STORE).resolve().orElse(null);
            Store to = event.getEntity().getCapability(STORE).resolve().orElse(null);
            if (from != null && to != null) from.copyTo(to, event.isWasDeath());
        } finally {
            original.invalidateCaps();
        }
    }

    /** Attachment values of one holder. */
    @AutoRegisterCapability
    public static final class Store {
        private final Map<AttachmentType<?>, Object> values = new IdentityHashMap<>();

        @SuppressWarnings("unchecked")
        synchronized <T> T get(AttachmentType<T> type) {
            return (T) values.computeIfAbsent(type, ignored -> Objects.requireNonNull(type.createDefault()));
        }

        @SuppressWarnings("unchecked")
        @Nullable
        synchronized <T> T getExisting(AttachmentType<T> type) {
            return (T) values.get(type);
        }

        synchronized boolean has(AttachmentType<?> type) {
            return values.containsKey(type);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        synchronized <T> T set(AttachmentType<T> type, T value) {
            return (T) values.put(type, value);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        synchronized <T> T remove(AttachmentType<T> type) {
            return (T) values.remove(type);
        }

        synchronized CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            values.forEach((type, value) -> {
                Tag encoded = encode(type, value);
                if (encoded == null) return;
                ResourceLocation id = idOf(type);
                if (id == null) {
                    LOGGER.error("Cannot save unregistered attachment type {}", type);
                    return;
                }
                tag.put(id.toString(), encoded);
            });
            return tag;
        }

        synchronized void deserialize(CompoundTag tag) {
            for (String key : tag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                AttachmentType<?> type = id == null ? null : typeOf(id);
                if (type == null || type.codec() == null) {
                    LOGGER.warn("Ignoring unknown or non-serializable attachment {}", key);
                    continue;
                }
                decode(type, tag.get(key)).ifPresent(value -> values.put(type, value));
            }
        }

        synchronized void copyTo(Store target, boolean death) {
            values.forEach((type, value) -> {
                if (death && !type.copyOnDeath()) return;
                Tag encoded = encode(type, value);
                if (encoded == null) return;
                decode(type, encoded).ifPresent(copy -> {
                    synchronized (target) {
                        target.values.put(type, copy);
                    }
                });
            });
        }

        @SuppressWarnings("unchecked")
        @Nullable
        private static <T> Tag encode(AttachmentType<T> type, Object value) {
            Codec<T> codec = type.codec();
            if (codec == null || !type.shouldSerialize((T) value)) return null;
            return codec.encodeStart(NbtOps.INSTANCE, (T) value)
                    .resultOrPartial(error -> LOGGER.error("Failed to encode attachment {}: {}", idOf(type), error))
                    .orElse(null);
        }

        private static <T> Optional<T> decode(AttachmentType<T> type, Tag tag) {
            return Objects.requireNonNull(type.codec()).parse(NbtOps.INSTANCE, tag)
                    .resultOrPartial(error -> LOGGER.error("Failed to decode attachment {}: {}", idOf(type), error));
        }
    }
}
