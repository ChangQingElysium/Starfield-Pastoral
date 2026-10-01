package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererPurpleShortsBobberMixin {
    private static final ResourceLocation SHORTS_BOBBER_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/item/lucky_purple_shorts.png");

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    ordinal = 0
            ),
            require = 0
    )
    private VertexConsumer stardewcraft$purpleShortsBobberTexture(MultiBufferSource source, RenderType original,
                                                                  FishingHook hook, float entityYaw, float partialTick,
                                                                  PoseStack poseStack, MultiBufferSource buffer,
                                                                  int packedLight) {
        if (hasPurpleShortsTackle(hook)) {
            return source.getBuffer(RenderType.entityCutout(SHORTS_BOBBER_TEXTURE));
        }
        return source.getBuffer(original);
    }

    private static boolean hasPurpleShortsTackle(FishingHook hook) {
        if (hook == null) {
            return false;
        }
        Player owner = hook.getPlayerOwner();
        if (owner == null) {
            return false;
        }
        ItemStack rod = FishingRodItem.findRod(owner);
        return !rod.isEmpty() && FishingRodItem.hasTackle(rod, "stardewcraft:lucky_purple_shorts");
    }
}
