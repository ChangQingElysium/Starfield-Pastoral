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
    // PORT(1.20.1): GeckoLib 4.8.2 for Forge 1.20.1 overrides the 1.20.1 Model#renderToBuffer
    // (PoseStack, VertexConsumer, light, overlay, r, g, b, a) instead of 1.21's (..., light, overlay, color). The
    // override carries the vanilla SRG name (m_7695_) in production and the official name in a deobfuscated dev
    // environment; the AP cannot remap a method of a @Pseudo third-party class, so both names are listed.
    @Inject(method={"renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            "m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"},
            at=@At("HEAD"), cancellable=true, require=0)
    private void stardewcraft$hideFishingArmor(PoseStack pose, VertexConsumer buffer, int light,
                                              int overlay, float red, float green, float blue, float alpha,
                                              CallbackInfo ci) {
        if(currentEntity instanceof AbstractClientPlayer player && FishingPresentationClient.worldOwned(player)
                && FishingArmorVisibility.shouldHide(player,currentStack))ci.cancel();
    }
}
