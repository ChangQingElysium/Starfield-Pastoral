package com.stardew.craft.platform.mixin.resource.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.resource.ServerResourceReloads;
import java.util.List;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ReloadableServerResources.class)
public abstract class ServerResourceReloadListenersMixin {
    @Shadow @Final private ReloadableServerRegistries.Holder fullRegistryHolder;
    @Unique private ServerResourceReloads stardewcraft$serverResourceReloads;

    @ModifyReturnValue(method = "listeners", at = @At("RETURN"))
    private List<PreparableReloadListener> stardewcraft$appendResourceListeners(
            List<PreparableReloadListener> existing) {
        if (stardewcraft$serverResourceReloads == null) {
            stardewcraft$serverResourceReloads = new ServerResourceReloads(
                    (ReloadableServerResources) (Object) this, fullRegistryHolder.get());
        }
        // Chain the current value without an early return that could bypass a
        // data-map or another mod's return-value modifier.
        return stardewcraft$serverResourceReloads.appendTo(existing);
    }
}
