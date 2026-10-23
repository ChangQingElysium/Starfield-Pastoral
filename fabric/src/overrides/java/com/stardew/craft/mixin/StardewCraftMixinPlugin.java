// Upstream-SHA256: 318da4b6d0d34d9494fb7d5ed80f952a720893c7907aaafb94edb07784740936
package com.stardew.craft.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Physical-loader lookup only; do not load optional renderer classes here. */
public final class StardewCraftMixinPlugin implements IMixinConfigPlugin {
    private static final String PREFIX = "com.stardew.craft.mixin.";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(PREFIX + "OptionalAnimatedArmorFishingMixin")) {
            return hasRendererVersion("geckolib", "4.8.2");
        }
        if (mixinClassName.equals(PREFIX + "CtmModelInitializationMixin")) {
            return loaded("ctm");
        }
        if (mixinClassName.startsWith(PREFIX + "XaeroWorldMap")) return loaded("xaeroworldmap");
        if (mixinClassName.startsWith(PREFIX + "XaeroMinimap")) return loaded("xaerominimap");
        if (mixinClassName.equals(PREFIX + "Ae2FacadeItemMixin")) return loaded("ae2");
        if (mixinClassName.equals(PREFIX + "FishingHookRendererPurpleShortsBobberMixin")
                && loaded("hybrid_aquatic")) return false;
        if (mixinClassName.startsWith(PREFIX + "SodiumRingLight")) return loaded("sodium");
        if (mixinClassName.startsWith(PREFIX + "TownDoorSodium")
                || mixinClassName.startsWith(PREFIX + "SodiumColoredLight")) {
            return hasRendererVersion("sodium", "0.6.13");
        }
        if (mixinClassName.startsWith(PREFIX + "TownDoorIris")
                || mixinClassName.startsWith(PREFIX + "IrisMineLamp")) {
            // Fabric 1.21.1 stable release; 1.8.12 is the NeoForge-only build.
            return hasRendererVersion("iris", "1.8.8");
        }
        return true;
    }

    private static boolean loaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    private static boolean hasRendererVersion(String id, String expected) {
        return FabricLoader.getInstance().getModContainer(id).map(container -> {
            String version = container.getMetadata().getVersion().getFriendlyString();
            return version.equals(expected) || version.startsWith(expected + "+")
                    || version.startsWith(expected + "-snapshot+");
        }).orElse(false);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}
}
