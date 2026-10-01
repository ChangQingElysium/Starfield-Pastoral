package com.stardew.craft.mixin;

import com.stardew.craft.port.net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow @Final @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC;

    @Shadow public abstract Item getItem();
    @Shadow public abstract int getCount();
    @Shadow public abstract DataComponentPatch getComponentsPatch();

    /**
     * Replaces the ItemStack STREAM_CODEC with a custom one that uses ByteBufCodecs.VAR_INT for the count.
     * This bypasses any hardcoded limit (like 99) in the vanilla codec.
     * We use a manual implementation to avoid generic type inference issues with StreamCodec.composite.
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void modifyStreamCodec(CallbackInfo ci) {
        STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            @SuppressWarnings({"null", "deprecation"})
            @Override
            public ItemStack decode(@SuppressWarnings("null") RegistryFriendlyByteBuf buf) {
                // Must decode in order: Item, Count, Components
                @SuppressWarnings("null")
                Item item = ByteBufCodecs.registry(Registries.ITEM).decode(buf);
                @SuppressWarnings("null")
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                @SuppressWarnings("null")
                DataComponentPatch components = DataComponentPatch.STREAM_CODEC.decode(buf);
                // Convert Item to Holder to use the main constructor (assuming builtin registry)
                return new ItemStack(item.builtInRegistryHolder(), count, components);
            }

            @SuppressWarnings("null")
            @Override
            public void encode(@SuppressWarnings("null") RegistryFriendlyByteBuf buf, @SuppressWarnings("null") ItemStack stack) {
                // Must encode in order: Item, Count, Components
                ByteBufCodecs.registry(Registries.ITEM).encode(buf, stack.getItem());
                ByteBufCodecs.VAR_INT.encode(buf, stack.getCount());
                DataComponentPatch.STREAM_CODEC.encode(buf, stack.getComponentsPatch());
            }
        };
    }
}
