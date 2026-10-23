// Upstream-SHA256: ba4e80980a87cd678b8c228634c38f1c74fe4fd4a8dd6a349440389a72efdf90
package com.stardew.craft.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Raises the ItemStack CODEC "count" range from [1, 99] to [1, 999].
 *
 * Vanilla builds ItemStack.CODEC via ExtraCodecs.intRange(1, 99) for the "count" field.
 * This range is used during validation (e.g. validatedStreamCodec re-encodes with ItemStack.CODEC),
 * and will otherwise disconnect clients when counts exceed 99.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCodecCountRangeMixin {

    @ModifyArg(
            method = "method_57371(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)Lcom/mojang/datafixers/kinds/App;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
        ),
        index = 1
    )
    private static int stardewcraft$raiseItemStackCodecMaxCount(int max) {
    return max == 99 ? 999 : max;
    }
}
