package com.stardew.craft.mixin;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Normalize saved/network/copied quality stacks before they enter inventory comparisons.
 * Vanilla component equality and hashing stay strict; names, flower colors and all other data survive.
 *
 * <p>PORT(1.20.1): 1.21.1 hooks the RETURN of the private master constructor
 * {@code ItemStack(ItemLike, int, PatchedDataComponentMap)}, which every stack carrying data passes through:
 * codec decoding (save data, recipes), network decoding and {@link ItemStack#copy()}. 1.20.1 has no such
 * constructor; stacks receive their NBT after construction. The same normalization therefore runs at the end of
 * each 1.20.1 path that produces a stack with data: the NBT-load constructor ({@code ItemStack.of}, Forge recipe
 * results with nbt), the {@code ItemStack.CODEC} constructor, {@code copy()} (also behind {@code copyWithCount},
 * {@code split}, {@code copyAndClear}) and {@code FriendlyByteBuf#readItem} ({@link FriendlyByteBufQualityComponentsMixin}).
 * Fresh stacks ({@code new ItemStack(item, count)}) carry no NBT in 1.20.1, so normalizing them is a no-op in both
 * versions. Priority 1100 orders the load-constructor callback after {@link ItemStackLargeCountMixin} restores
 * counts above 127, matching 1.21.1 where the real count is already set when the constructor returns.
 */
@Mixin(value = ItemStack.class, priority = 1100)
public abstract class ItemStackQualityComponentsMixin {
    @Inject(method = {
            "<init>(Lnet/minecraft/nbt/CompoundTag;)V",
            "<init>(Lnet/minecraft/world/level/ItemLike;ILjava/util/Optional;)V"
    }, at = @At("RETURN"), require = 2)
    private void stardewcraft$normalizeQualityComponents(CallbackInfo ci) {
        QualityHelper.normalizeQualityComponents((ItemStack) (Object) this);
    }

    @Inject(method = "copy()Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void stardewcraft$normalizeCopiedQualityComponents(CallbackInfoReturnable<ItemStack> cir) {
        QualityHelper.normalizeQualityComponents(cir.getReturnValue());
    }
}
