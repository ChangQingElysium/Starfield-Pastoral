package com.stardew.craft.forge;

import net.minecraftforge.fml.common.Mod;

/**
 * Minimal Forge entrypoint used while the NeoForge runtime is being ported in layers.
 * The production source tree is deliberately not compiled until its first Forge slice exists.
 */
@Mod(ForgeBootstrap.MOD_ID)
public final class ForgeBootstrap {
    public static final String MOD_ID = "stardewcraft";
}
