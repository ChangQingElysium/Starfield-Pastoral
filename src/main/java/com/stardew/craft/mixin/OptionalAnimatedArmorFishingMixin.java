package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.client.fishing.FishingArmorVisibility;
import com.stardew.craft.client.fishing.FishingPresentationClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional third-party armor compatibility; absent from every Stardew model/rendering path.
 * The target is enabled only if another mod installed the audited renderer version.
 */
@Pseudo
@Mixin(targets="software.bernie.geckolib.renderer.GeoArmorRenderer", remap=false)
public abstract class OptionalAnimatedArmorFishingMixin {
    @Shadow protected Entity currentEntity;
    @Shadow protected ItemStack currentStack;
    @Inject(method="renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at=@At("HEAD"), cancellable=true, require=0)
    private void stardewcraft$hideFishingArmor(PoseStack pose, VertexConsumer buffer, int light,
                                              int overlay, int color, CallbackInfo ci) {
        if(currentEntity instanceof AbstractClientPlayer player && FishingPresentationClient.worldOwned(player)
                && FishingArmorVisibility.shouldHide(player,currentStack))ci.cancel();
    }
}
