package com.stardew.craft.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;
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
        // PORT(1.20.1): audited against Embeddium 0.3.31 / Oculus 1.8.0 release bytecode.
        // Unknown versions retain the ordinary rendering path without applying internal hooks.
        if (isRendererModMixin(mixinClassName) && !targetClassPresent(targetClassName)) {
            return false;
        }
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
        // PORT(1.20.1): Jade 11 internals (verified against 11.13.3) for Jade 15-style block-keyed server data.
        if (mixinClassName.startsWith("com.stardew.craft.mixin.PortJade")) {
            return hasModVersionPrefix("jade", "11.");
        }
        if (PURPLE_SHORTS_BOBBER_MIXIN.equals(mixinClassName) && isHybridAquaticLoaded()) {
            return false;
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.SodiumRingLight")
                || mixinClassName.equals("com.stardew.craft.mixin.SodiumBlockEntityBoundsMixin")
                || mixinClassName.startsWith("com.stardew.craft.mixin.SodiumImmediate")
                || mixinClassName.equals("com.stardew.craft.mixin.SodiumEntityNormalsMixin")) {
            return hasAuditedEmbeddium();
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.TownDoorSodium")) {
            return hasAuditedEmbeddium();
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.TownDoorIris")
                || mixinClassName.startsWith("com.stardew.craft.mixin.TownDoorOculus")) {
            return hasAuditedOculus();
        }
        // These hooks target renderer internals, not a stable public RGB-light API.
        // Unknown versions retain normal block light instead of risking a startup failure.
        if (mixinClassName.startsWith("com.stardew.craft.mixin.SodiumColoredLight")) {
            return hasAuditedEmbeddium();
        }
        if (mixinClassName.startsWith("com.stardew.craft.mixin.IrisMineLamp")) {
            return hasAuditedOculus();
        }
        return true;
    }

    public static boolean hasAuditedEmbeddium() {
        return hasExactModVersion("embeddium", "0.3.31+mc1.20.1");
    }

    public static boolean hasAuditedOculus() {
        return hasExactModVersion("oculus", "1.8.0");
    }

    private static boolean hasExactModVersion(String id, String version) {
        LoadingModList list = LoadingModList.get();
        var file = list == null ? null : list.getModFileById(id);
        return file != null && file.getMods().stream().anyMatch(mod -> mod.getModId().equals(id)
                && mod.getVersion().toString().equals(version));
    }

    private static boolean isRendererModMixin(String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return simpleName.startsWith("Sodium") || simpleName.startsWith("Iris")
                || simpleName.startsWith("TownDoorSodium") || simpleName.startsWith("TownDoorIris")
                || simpleName.startsWith("TownDoorOculus");
    }

    private static boolean targetClassPresent(String targetClassName) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(targetClassName.replace('.', '/')) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRendererVersion(String id, String version) {
        LoadingModList list = LoadingModList.get();
        var file = list == null ? null : list.getModFileById(id);
        return file != null && file.getMods().stream().anyMatch(mod -> mod.getModId().equals(id)
                && (mod.getVersion().toString().equals(version) || mod.getVersion().toString().startsWith(version + "+")
                || mod.getVersion().toString().startsWith(version + "-snapshot+")));
    }

    private static boolean hasModVersionPrefix(String id, String prefix) {
        LoadingModList list = LoadingModList.get();
        var file = list == null ? null : list.getModFileById(id);
        return file != null && file.getMods().stream().anyMatch(mod -> mod.getModId().equals(id)
                && mod.getVersion().toString().startsWith(prefix));
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
