package com.stardew.craft.port.net.neoforged.neoforge.registries;

import com.stardew.craft.port.net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

/** PORT(1.20.1): NeoForge registry keys mapped to Forge registries; attachment types live in the port layer. */
public final class NeoForgeRegistries {
    private NeoForgeRegistries() {}

    public static final class Keys {
        private Keys() {}

        public static final ResourceKey<Registry<FluidType>> FLUID_TYPES = ForgeRegistries.Keys.FLUID_TYPES;
        /** No Forge registry exists; {@link DeferredRegister} keeps these entries itself. */
        public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES =
                ResourceKey.createRegistryKey(new ResourceLocation("neoforge", "attachment_types"));
    }
}
