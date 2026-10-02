package com.stardew.craft.port.net.neoforged.neoforge.client.extensions.common;

import com.stardew.craft.mixin.PortBlockRenderPropertiesAccessor;
import com.stardew.craft.mixin.PortFluidTypeRenderPropertiesAccessor;
import com.stardew.craft.mixin.PortItemRenderPropertiesAccessor;
import com.stardew.craft.mixin.PortMobEffectRenderPropertiesAccessor;
import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 client-extension registration (mod bus, client only).
 * <p>
 * Forge 1.20.1 stores client extensions on the object itself ({@code Item/Block/FluidType.renderProperties},
 * {@code MobEffect.effectRenderer}), normally filled by {@code initializeClient} at construction. NeoForge removed
 * {@code initializeClient}; this event writes the same fields through accessor mixins, so {@code
 * IClientItemExtensions.of(item)} etc. resolve exactly as if the object had supplied them. Posted on the mod bus on
 * the main thread during {@code FMLClientSetupEvent}, before any item, block or fluid is rendered.
 * As in NeoForge, registering a second extension for the same object is an error.
 */
public class RegisterClientExtensionsEvent extends Event implements IModBusEvent {
    public RegisterClientExtensionsEvent() {}

    public void registerBlock(IClientBlockExtensions extensions, Block... blocks) {
        for (Block block : blocks) {
            PortBlockRenderPropertiesAccessor accessor = (PortBlockRenderPropertiesAccessor) block;
            if (accessor.stardewcraft$getRenderProperties() != null) throw duplicate("Block", block);
            accessor.stardewcraft$setRenderProperties(extensions);
        }
    }

    @SafeVarargs
    public final void registerBlock(IClientBlockExtensions extensions, Holder<Block>... blocks) {
        registerBlock(extensions, Arrays.stream(blocks).map(Holder::value).toArray(Block[]::new));
    }

    public boolean isBlockRegistered(Block block) {
        return ((PortBlockRenderPropertiesAccessor) block).stardewcraft$getRenderProperties() != null;
    }

    public void registerItem(IClientItemExtensions extensions, Item... items) {
        for (Item item : items) {
            PortItemRenderPropertiesAccessor accessor = (PortItemRenderPropertiesAccessor) item;
            if (accessor.stardewcraft$getRenderProperties() != null) throw duplicate("Item", item);
            accessor.stardewcraft$setRenderProperties(extensions);
        }
    }

    @SafeVarargs
    public final void registerItem(IClientItemExtensions extensions, Holder<Item>... items) {
        registerItem(extensions, Arrays.stream(items).map(Holder::value).toArray(Item[]::new));
    }

    public boolean isItemRegistered(Item item) {
        return ((PortItemRenderPropertiesAccessor) item).stardewcraft$getRenderProperties() != null;
    }

    public void registerMobEffect(IClientMobEffectExtensions extensions, MobEffect... mobEffects) {
        for (MobEffect effect : mobEffects) {
            PortMobEffectRenderPropertiesAccessor accessor = (PortMobEffectRenderPropertiesAccessor) effect;
            if (accessor.stardewcraft$getEffectRenderer() != null) throw duplicate("MobEffect", effect);
            accessor.stardewcraft$setEffectRenderer(extensions);
        }
    }

    public boolean isMobEffectRegistered(MobEffect mobEffect) {
        return ((PortMobEffectRenderPropertiesAccessor) mobEffect).stardewcraft$getEffectRenderer() != null;
    }

    public void registerFluidType(IClientFluidTypeExtensions extensions, FluidType... fluidTypes) {
        for (FluidType type : fluidTypes) {
            PortFluidTypeRenderPropertiesAccessor accessor = (PortFluidTypeRenderPropertiesAccessor) type;
            if (accessor.stardewcraft$getRenderProperties() != null) throw duplicate("FluidType", type);
            accessor.stardewcraft$setRenderProperties(extensions);
        }
    }

    public boolean isFluidTypeRegistered(FluidType fluidType) {
        return ((PortFluidTypeRenderPropertiesAccessor) fluidType).stardewcraft$getRenderProperties() != null;
    }

    private static IllegalStateException duplicate(String kind, Object target) {
        return new IllegalStateException("Duplicate client extensions registration for " + kind + " " + target);
    }
}
