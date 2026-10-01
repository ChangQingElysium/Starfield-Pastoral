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
}
