package com.stardew.craft.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT(1.20.1): lets item stacks above 127 items (the mod uses 999) cross the network.
 *
 * <p>Replaces the 1.21.1 ItemStack stream-codec range mixins. 1.20.1 writes the stack count as one byte,
 * which wraps above 127 (999 arrives as -25, i.e. an empty stack). Counts that fit a byte keep the vanilla
 * encoding; larger counts are written as the marker byte {@value #LARGE_COUNT_MARKER} followed by a VarInt.
 * A non-empty vanilla stack never has a negative count, so the marker is unambiguous.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufLargeItemCountMixin {
    private static final int LARGE_COUNT_MARKER = Byte.MIN_VALUE;

    // writeItemStack is a Forge method and writeByte a Netty method: neither is obfuscated.
    @Redirect(method = "writeItemStack", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;",
                    remap = false))
    private ByteBuf stardewcraft$writeLargeCount(FriendlyByteBuf buf, int count) {
        if (count >= 0 && count <= Byte.MAX_VALUE) {
            return buf.writeByte(count);
        }
        buf.writeByte(LARGE_COUNT_MARKER);
        return buf.writeVarInt(count);
    }

    @ModifyVariable(method = "readItem", ordinal = 0,
            at = @At(value = "STORE", ordinal = 0))
    private int stardewcraft$readLargeCount(int count) {
        return count == LARGE_COUNT_MARKER ? ((FriendlyByteBuf) (Object) this).readVarInt() : count;
    }
}
