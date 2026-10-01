package com.stardew.craft.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class StardewCraftMixinPlugin implements IMixinConfigPlugin {
    private static final String PURPLE_SHORTS_BOBBER_MIXIN =
            "com.stardew.craft.mixin.FishingHookRendererPurpleShortsBobberMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("com.stardew.craft.mixin.OptionalAnimatedArmorFishingMixin")) {
            return hasRendererVersion("geckolib", "4.8.2");
        }
        if (mixinClassName.equals("com.stardew.craft.mixin.CtmModelInitializationMixin")) {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById("ctm") != null;
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.XaeroWorldMap")) {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById("xaeroworldmap") != null;
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.XaeroMinimap")) {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById("xaerominimap") != null;
        }
        if (mixinClassName.equals("com.stardew.craft.mixin.Ae2FacadeItemMixin")) {
            LoadingModList modList = LoadingModList.get();
            return modList != null && modList.getModFileById("ae2") != null;
        }
        if (PURPLE_SHORTS_BOBBER_MIXIN.equals(mixinClassName) && isHybridAquaticLoaded()) {
            return false;
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.SodiumRingLight")) {
            LoadingModList modList = LoadingModList.get();
            return modList != null && modList.getModFileById("sodium") != null;
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.TownDoorSodium")) {
            return hasRendererVersion("sodium", "0.6.13");
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.TownDoorIris")) {
            return hasRendererVersion("iris", "1.8.12");
        }
        // These hooks target renderer internals, not a stable public RGB-light API.
        // Unknown versions retain normal block light instead of risking a startup failure.
        if (mixinClassName.startsWith("com.stardew.craft.mixin.SodiumColoredLight")) {
            return hasRendererVersion("sodium", "0.6.13");
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.IrisMineLamp")) {
            return hasRendererVersion("iris", "1.8.12");
        }
        return true;
    }

    private static boolean hasRendererVersion(String id, String version) {
        LoadingModList list = LoadingModList.get();
        var file = list == null ? null : list.getModFileById(id);
        return file != null && file.getMods().stream().anyMatch(mod -> mod.getModId().equals(id)
                && (mod.getVersion().toString().equals(version) || mod.getVersion().toString().startsWith(version + "+")
                || mod.getVersion().toString().startsWith(version + "-snapshot+")));
    }

    private static boolean isHybridAquaticLoaded() {
        try {
            LoadingModList modList = LoadingModList.get();
            return modList != null && modList.getModFileById("hybrid_aquatic") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
