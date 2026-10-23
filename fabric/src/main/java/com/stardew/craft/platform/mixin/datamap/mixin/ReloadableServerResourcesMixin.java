package com.stardew.craft.platform.mixin.datamap.mixin;


import com.stardew.craft.platform.fabric.datamap.DataMapLoader;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Shadow @Final private ReloadableServerRegistries.Holder fullRegistryHolder;
    @Unique private DataMapLoader stardewcraft$dataMaps;

    @ModifyReturnValue(method = "listeners", at = @At("RETURN"))
    private List<PreparableReloadListener> stardewcraft$prepareDataMaps(List<PreparableReloadListener> original) {
        if (stardewcraft$dataMaps == null) stardewcraft$dataMaps = new DataMapLoader(fullRegistryHolder.get());
        var listeners = new ArrayList<>(original);
        listeners.add(stardewcraft$dataMaps);
        return List.copyOf(listeners);
    }

    @Inject(method = "updateRegistryTags()V", at = @At("RETURN"))
    private void stardewcraft$bindDataMaps(CallbackInfo callback) {
        if (stardewcraft$dataMaps == null) throw new IllegalStateException("Data map reload listener was not installed");
        stardewcraft$dataMaps.applyAfterTags();
    }
}
