package com.stardew.craft.platform.mixin.brewing.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.brewing.BrewingBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class ServerBrewingRegistriesMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;bootstrap(Lnet/minecraft/world/flag/FeatureFlagSet;)Lnet/minecraft/world/item/alchemy/PotionBrewing;"))
    private PotionBrewing stardewcraft$registryContext(FeatureFlagSet flags, Operation<PotionBrewing> original) {
        return BrewingBootstrap.withRegistries(((MinecraftServer) (Object) this).registryAccess(), () -> original.call(flags));
    }
}
