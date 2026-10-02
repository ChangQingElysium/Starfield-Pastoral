package com.stardew.craft.port.net.minecraft.network.codec;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.VarInt;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;

/**
 * 1.21.1 {@code net.minecraft.network.codec.ByteBufCodecs} rebuilt on Forge 1.20.1 buffers.
 * Primitive encodings match vanilla 1.21.1 byte-for-byte; NBT uses the 1.20.1 FriendlyByteBuf
 * format (both ends always run this port, so only symmetry matters).
 */
public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;

    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<>() {
        @Override
        public Boolean decode(ByteBuf buffer) {
            return buffer.readBoolean();
        }

        @Override
        public void encode(ByteBuf buffer, Boolean value) {
            buffer.writeBoolean(value);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<>() {
        @Override
        public Byte decode(ByteBuf buffer) {
            return buffer.readByte();
        }

        @Override
        public void encode(ByteBuf buffer, Byte value) {
            buffer.writeByte(value);
        }
    };
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<>() {
        @Override
        public Short decode(ByteBuf buffer) {
            return buffer.readShort();
        }

        @Override
        public void encode(ByteBuf buffer, Short value) {
            buffer.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return buffer.readUnsignedShort();
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            buffer.writeShort(value);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return buffer.readInt();
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            buffer.writeInt(value);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<>() {
        @Override
        public Integer decode(ByteBuf buffer) {
            return VarInt.read(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Integer value) {
            VarInt.write(buffer, value);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<>() {
        @Override
        public Long decode(ByteBuf buffer) {
            return wrap(buffer).readVarLong();
        }

        @Override
        public void encode(ByteBuf buffer, Long value) {
            wrap(buffer).writeVarLong(value);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<>() {
        @Override
        public Float decode(ByteBuf buffer) {
            return buffer.readFloat();
        }

        @Override
        public void encode(ByteBuf buffer, Float value) {
            buffer.writeFloat(value);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<>() {
        @Override
        public Double decode(ByteBuf buffer) {
            return buffer.readDouble();
        }

        @Override
        public void encode(ByteBuf buffer, Double value) {
            buffer.writeDouble(value);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<>() {
        @Override
        public byte[] decode(ByteBuf buffer) {
            return wrap(buffer).readByteArray();
        }

        @Override
        public void encode(ByteBuf buffer, byte[] value) {
            wrap(buffer).writeByteArray(value);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = new StreamCodec<>() {
        @Override
        public CompoundTag decode(ByteBuf buffer) {
            CompoundTag tag = wrap(buffer).readNbt();
            if (tag == null) {
                throw new DecoderException("Not a compound tag: null");
            }
            return tag;
        }

        @Override
        public void encode(ByteBuf buffer, CompoundTag value) {
            if (value == null) {
                throw new EncoderException("Expected non-null compound tag");
            }
            wrap(buffer).writeNbt(value);
        }
    };
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<>() {
        @Override
        public Optional<CompoundTag> decode(ByteBuf buffer) {
            return Optional.ofNullable(wrap(buffer).readNbt());
        }

        @Override
        public void encode(ByteBuf buffer, Optional<CompoundTag> value) {
            wrap(buffer).writeNbt(value.orElse(null));
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(final int maxSize) {
        return new StreamCodec<>() {
            @Override
            public byte[] decode(ByteBuf buffer) {
                return wrap(buffer).readByteArray(maxSize);
            }

            @Override
            public void encode(ByteBuf buffer, byte[] value) {
                if (value.length > maxSize) {
                    throw new EncoderException("ByteArray with size " + value.length + " is bigger than allowed " + maxSize);
                }
                wrap(buffer).writeByteArray(value);
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(final int maxLength) {
        return new StreamCodec<>() {
            @Override
            public String decode(ByteBuf buffer) {
                return wrap(buffer).readUtf(maxLength);
            }

            @Override
            public void encode(ByteBuf buffer, String value) {
                wrap(buffer).writeUtf(value, maxLength);
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<B, V> codec) {
        return new StreamCodec<>() {
            @Override
            public Optional<V> decode(B buffer) {
                return buffer.readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty();
            }

            @Override
            public void encode(B buffer, Optional<V> value) {
                if (value.isPresent()) {
                    buffer.writeBoolean(true);
                    codec.encode(buffer, value.get());
                } else {
                    buffer.writeBoolean(false);
                }
            }
        };
    }

    static int readCount(ByteBuf buffer, int maxSize) {
        int count = VarInt.read(buffer);
        if (count > maxSize) {
            throw new DecoderException(count + " elements exceeded max size of: " + maxSize);
        }
        return count;
    }

    static void writeCount(ByteBuf buffer, int count, int maxSize) {
        if (count > maxSize) {
            throw new EncoderException(count + " elements exceeded max size of: " + maxSize);
        }
        VarInt.write(buffer, count);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
            IntFunction<C> factory, StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
            final IntFunction<C> factory, final StreamCodec<? super B, V> codec, final int maxSize) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                int count = readCount(buffer, maxSize);
                C result = factory.apply(Math.min(count, MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < count; i++) {
                    result.add(codec.decode(buffer));
                }
                return result;
            }

            @Override
            public void encode(B buffer, C value) {
                writeCount(buffer, value.size(), maxSize);
                for (V element : value) {
                    codec.encode(buffer, element);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> factory) {
        return codec -> collection(factory, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return codec -> collection(ArrayList::new, codec, maxSize);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
            IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
            final IntFunction<? extends M> factory,
            final StreamCodec<? super B, K> keyCodec,
            final StreamCodec<? super B, V> valueCodec,
            final int maxSize) {
        return new StreamCodec<>() {
            @Override
            public void encode(B buffer, M value) {
                writeCount(buffer, value.size(), maxSize);
                value.forEach((key, entry) -> {
                    keyCodec.encode(buffer, key);
                    valueCodec.encode(buffer, entry);
                });
            }

            @Override
            public M decode(B buffer) {
                int count = readCount(buffer, maxSize);
                M result = factory.apply(Math.min(count, MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < count; i++) {
                    K key = keyCodec.decode(buffer);
                    V entry = valueCodec.decode(buffer);
                    result.put(key, entry);
                }
                return result;
            }
        };
    }

    /** Registry entries by numeric id, resolved against the buffer's registry access (like vanilla). */
    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(final ResourceKey<? extends Registry<T>> registryKey) {
        return new StreamCodec<>() {
            private Registry<T> lookup(RegistryFriendlyByteBuf buffer) {
                return buffer.registryAccess().registryOrThrow(registryKey);
            }

            @Override
            public T decode(RegistryFriendlyByteBuf buffer) {
                int id = VarInt.read(buffer);
                T value = this.lookup(buffer).byId(id);
                if (value == null) {
                    throw new DecoderException("Unknown id " + id + " in registry " + registryKey.location());
                }
                return value;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, T value) {
                int id = this.lookup(buffer).getId(value);
                if (id < 0) {
                    throw new EncoderException("Can't find id for '" + value + "' in registry " + registryKey.location());
                }
                VarInt.write(buffer, id);
            }
        };
    }

    /** Reuses the buffer when it already is a FriendlyByteBuf, otherwise wraps it without copying. */
    private static FriendlyByteBuf wrap(ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buffer);
    }
}
