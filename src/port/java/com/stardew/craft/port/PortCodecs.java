package com.stardew.craft.port;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.VarInt;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Replacements for the 1.21.1 vanilla static stream codecs that 1.20.1 lacks
 * ({@code ItemStack.OPTIONAL_STREAM_CODEC}, {@code ResourceLocation.STREAM_CODEC}, ...).
 * The rewrite script points those references here.
 */
public final class PortCodecs {
    private PortCodecs() {
    }

    /**
     * 1.21.1 layout: VarInt count (0 = empty), item id, item data. Unlike 1.20.1's
     * FriendlyByteBuf#writeItem the count is a VarInt, so Stardew stacks above 127 survive.
     * Item data is the Forge share tag (what 1.20.1 itself syncs for stacks).
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_ITEM_STACK = new StreamCodec<>() {
        @Override
        public ItemStack decode(RegistryFriendlyByteBuf buffer) {
            int count = VarInt.read(buffer);
            if (count <= 0) {
                return ItemStack.EMPTY;
            }
            int id = VarInt.read(buffer);
            Item item = Item.byId(id);
            CompoundTag tag = buffer.readNbt();
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, count);
            stack.readShareTag(tag);
            return stack;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemStack stack) {
            if (stack.isEmpty()) {
                VarInt.write(buffer, 0);
                return;
            }
            VarInt.write(buffer, stack.getCount());
            VarInt.write(buffer, Item.getId(stack.getItem()));
            buffer.writeNbt(stack.getShareTag());
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_STACK = new StreamCodec<>() {
        @Override
        public ItemStack decode(RegistryFriendlyByteBuf buffer) {
            ItemStack stack = OPTIONAL_ITEM_STACK.decode(buffer);
            if (stack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            }
            return stack;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemStack stack) {
            if (stack.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            }
            OPTIONAL_ITEM_STACK.encode(buffer, stack);
        }
    };

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION = new StreamCodec<>() {
        @Override
        public ResourceLocation decode(ByteBuf buffer) {
            return wrap(buffer).readResourceLocation();
        }

        @Override
        public void encode(ByteBuf buffer, ResourceLocation value) {
            wrap(buffer).writeResourceLocation(value);
        }
    };

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = new StreamCodec<>() {
        @Override
        public BlockPos decode(ByteBuf buffer) {
            return BlockPos.of(buffer.readLong());
        }

        @Override
        public void encode(ByteBuf buffer, BlockPos value) {
            buffer.writeLong(value.asLong());
        }
    };

    public static final StreamCodec<ByteBuf, UUID> UUID = new StreamCodec<>() {
        @Override
        public java.util.UUID decode(ByteBuf buffer) {
            return new java.util.UUID(buffer.readLong(), buffer.readLong());
        }

        @Override
        public void encode(ByteBuf buffer, java.util.UUID value) {
            buffer.writeLong(value.getMostSignificantBits());
            buffer.writeLong(value.getLeastSignificantBits());
        }
    };

    /** JSON component encoding of 1.20.1 ({@code FriendlyByteBuf#writeComponent}). */
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> COMPONENT = new StreamCodec<>() {
        @Override
        public Component decode(RegistryFriendlyByteBuf buffer) {
            return buffer.readComponent();
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, Component value) {
            buffer.writeComponent(value);
        }
    };

    /** 1.21.1 {@code FriendlyByteBuf.writeVec3}: three doubles. */
    public static void writeVec3(ByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x());
        buffer.writeDouble(value.y());
        buffer.writeDouble(value.z());
    }

    /** 1.21.1 {@code FriendlyByteBuf.readVec3}. */
    public static Vec3 readVec3(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static FriendlyByteBuf wrap(ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buffer);
    }

    /** DFU 8 (1.21) {@code Codec#validate(checker)} = {@code flatXmap(checker, checker)}; absent in DFU 6. */
    public static <A> com.mojang.serialization.Codec<A> validate(com.mojang.serialization.Codec<A> codec,
            java.util.function.Function<A, com.mojang.serialization.DataResult<A>> checker) {
        return codec.flatXmap(checker, checker);
    }

    /** DFU 8 (1.21) {@code MapCodec#validate(checker)} = {@code flatXmap(checker, checker)}; absent in DFU 6. */
    public static <A> com.mojang.serialization.MapCodec<A> validate(com.mojang.serialization.MapCodec<A> codec,
            java.util.function.Function<A, com.mojang.serialization.DataResult<A>> checker) {
        return codec.flatXmap(checker, checker);
    }

    /**
     * DFU 8 (1.21) {@code Codec.either(first, second)}: decodes {@code first}, then {@code second}; when both fail it
     * returns the first partial result, else the second partial result, else a combined error. DFU 6's
     * {@code Codec.either} returns the second codec's result whenever the first one fails.
     */
    public static <F, S> com.mojang.serialization.Codec<com.mojang.datafixers.util.Either<F, S>> either(
            com.mojang.serialization.Codec<F> first, com.mojang.serialization.Codec<S> second) {
        return new com.mojang.serialization.Codec<>() {
            @Override
            public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<
                    com.mojang.datafixers.util.Either<F, S>, T>> decode(
                    com.mojang.serialization.DynamicOps<T> ops, T input) {
                com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<
                        com.mojang.datafixers.util.Either<F, S>, T>> firstRead = first.decode(ops, input)
                        .map(pair -> pair.mapFirst(com.mojang.datafixers.util.Either::left));
                if (firstRead.result().isPresent()) return firstRead;
                com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<
                        com.mojang.datafixers.util.Either<F, S>, T>> secondRead = second.decode(ops, input)
                        .map(pair -> pair.mapFirst(com.mojang.datafixers.util.Either::right));
                if (secondRead.result().isPresent()) return secondRead;
                if (firstRead.resultOrPartial(message -> { }).isPresent()) return firstRead;
                if (secondRead.resultOrPartial(message -> { }).isPresent()) return secondRead;
                String firstMessage = firstRead.error().orElseThrow().message();
                String secondMessage = secondRead.error().orElseThrow().message();
                return com.mojang.serialization.DataResult.error(
                        () -> "Failed to parse either. First: " + firstMessage + "; Second: " + secondMessage);
            }

            @Override
            public <T> com.mojang.serialization.DataResult<T> encode(com.mojang.datafixers.util.Either<F, S> input,
                    com.mojang.serialization.DynamicOps<T> ops, T prefix) {
                return input.map(value -> first.encode(value, ops, prefix), value -> second.encode(value, ops, prefix));
            }

            @Override
            public String toString() {
                return "Either[" + first + ", " + second + "]";
            }
        };
    }

    /** DFU 8 (1.21) {@code Codec.withAlternative(primary, alternative)}: encodes with {@code primary}. */
    @SuppressWarnings("unchecked")
    public static <T> com.mojang.serialization.Codec<T> withAlternative(com.mojang.serialization.Codec<T> primary,
            com.mojang.serialization.Codec<? extends T> alternative) {
        return either(primary, (com.mojang.serialization.Codec<T>) alternative)
                .xmap(either -> either.map(value -> value, value -> value), com.mojang.datafixers.util.Either::left);
    }

    /** DFU 8 (1.21) {@code Codec.withAlternative(primary, alternative, converter)}. */
    public static <T, U> com.mojang.serialization.Codec<T> withAlternative(com.mojang.serialization.Codec<T> primary,
            com.mojang.serialization.Codec<U> alternative, java.util.function.Function<U, T> converter) {
        return either(primary, alternative)
                .xmap(either -> either.map(value -> value, converter), com.mojang.datafixers.util.Either::left);
    }

    /**
     * DFU 8 (1.21) {@code codec.optionalFieldOf(name)}: a missing field decodes to empty, but a present field that
     * fails to parse is an error (partial results are kept). DFU 6's {@code optionalFieldOf} silently turns such a
     * field into empty; that lenient behaviour is DFU 8's {@code lenientOptionalFieldOf}.
     */
    public static <A> com.mojang.serialization.MapCodec<java.util.Optional<A>> optionalFieldOf(
            com.mojang.serialization.Codec<A> elementCodec, String name) {
        return new com.mojang.serialization.MapCodec<>() {
            @Override
            public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) {
                return java.util.stream.Stream.of(ops.createString(name));
            }

            @Override
            public <T> com.mojang.serialization.DataResult<java.util.Optional<A>> decode(
                    com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    return com.mojang.serialization.DataResult.success(java.util.Optional.empty());
                }
                return elementCodec.parse(ops, value).map(java.util.Optional::of);
            }

            @Override
            public <T> com.mojang.serialization.RecordBuilder<T> encode(java.util.Optional<A> input,
                    com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, elementCodec.encodeStart(ops, input.get())) : prefix;
            }

            @Override
            public String toString() {
                return "OptionalFieldCodec[" + name + ": " + elementCodec + ']';
            }
        };
    }

    /** DFU 8 (1.21) {@code codec.optionalFieldOf(name, defaultValue)} (strict; the default is omitted on encode). */
    public static <A> com.mojang.serialization.MapCodec<A> optionalFieldOf(
            com.mojang.serialization.Codec<A> elementCodec, String name, A defaultValue) {
        return optionalFieldOf(elementCodec, name).xmap(
                value -> value.orElse(defaultValue),
                value -> java.util.Objects.equals(value, defaultValue) ? java.util.Optional.empty()
                        : java.util.Optional.of(value));
    }
}
