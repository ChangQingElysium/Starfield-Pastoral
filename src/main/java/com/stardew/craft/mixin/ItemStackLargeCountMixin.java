package com.stardew.craft.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): keeps stacks above 127 items (the mod uses 999) intact in item NBT.
 *
 * <p>Replaces the 1.21.1 ItemStackCodecCountRangeMixin/DataComponentsMixin pair, which raised the
 * {@code count}/{@code max_stack_size} codec ranges from 99 to 999. 1.20.1 has no such ranges
 * ({@code ItemStack.CODEC} uses an unbounded int, {@code Item.Properties#stacksTo} is unchecked), but it
 * stores {@code Count} as a byte, which wraps above 127. Counts that fit a byte are still written as a
 * byte, so ordinary stacks keep the vanilla layout; larger counts are written as an int and read back here
 * ({@code CompoundTag#getInt} accepts both).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackLargeCountMixin {
    @Shadow
    private int count;

    @Redirect(method = "save",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;putByte(Ljava/lang/String;B)V"))
    private void stardewcraft$saveLargeCount(CompoundTag tag, String key, byte value) {
        if (count > Byte.MAX_VALUE) {
            tag.putInt(key, count);
        } else {
            tag.putByte(key, value);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void stardewcraft$loadLargeCount(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("Count", Tag.TAG_INT)) {
            count = tag.getInt("Count");
        }
    }
}
