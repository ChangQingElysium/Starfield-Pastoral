package com.stardew.craft.platform.mixin.menu.mixin;


import com.stardew.craft.platform.fabric.menu.FabricMenuDataSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs the adapter at the same menu initialization boundary used by vanilla. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuDataMixin {
    @Redirect(method = "initMenu", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/inventory/AbstractContainerMenu;setSynchronizer(Lnet/minecraft/world/inventory/ContainerSynchronizer;)V"))
    private void stardewcraft$preserveFullIntData(AbstractContainerMenu menu, ContainerSynchronizer synchronizer) {
        menu.setSynchronizer(FabricMenuDataSync.wrap(menu, synchronizer, (ServerPlayer) (Object) this));
    }
}
