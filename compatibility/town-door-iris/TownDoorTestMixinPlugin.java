package com.stardew.craft.compat;

import cpw.mods.modlauncher.Launcher;
import net.neoforged.fml.common.asm.RuntimeDistCleaner;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * JUnitMain disables dist-cleaning to allow headless tests to inspect client classes, but does so
 * after Mixin preparation. Apply that same test-only setting early enough to validate client hooks.
 * This does not switch to a client launch target, initialize Minecraft, or create a game window.
 */
public final class TownDoorTestMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {
        var cleaner = (RuntimeDistCleaner) Launcher.INSTANCE.environment()
                .findLaunchPlugin("runtimedistcleaner").orElseThrow();
        cleaner.setDistribution(null);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) {}
}
