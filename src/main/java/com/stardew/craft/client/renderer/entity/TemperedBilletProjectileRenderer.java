package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.Config;
import com.stardew.craft.client.weapon.BloodForgeGeometry;
import com.stardew.craft.client.weapon.WeaponEffectRenderTypes;
import com.stardew.craft.client.weapon.WeaponProjectileGeometry;
import com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TemperedBilletProjectileRenderer extends EntityRenderer<TemperedBilletProjectileEntity> {
    public TemperedBilletProjectileRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(TemperedBilletProjectileEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
    @Override public boolean shouldRender(TemperedBilletProjectileEntity entity,
            net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z) {
        if (!entity.shouldRender(x,y,z)) return false;
        AABB bounds=entity.getBoundingBox();
        for(var sample:entity.trail()) bounds=bounds.minmax(new AABB(sample.position(),sample.position()));
        return frustum.isVisible(bounds.inflate(.2));
    }
    @Override public void render(TemperedBilletProjectileEntity entity,float yaw,float partial,
            PoseStack stack,MultiBufferSource buffer,int packedLight) {
        Vec3 head=entity.getPosition(partial);
        if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            BloodForgeGeometry.billetTrail(buffer.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW),
                    stack.last().pose(),entity.trail(),head,entity.tickCount-1+partial);
        }
        stack.pushPose();
        // Follow the projectile's interpolated heading; the billet does not tumble around its path.
        stack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,entity.yRotO,entity.getYRot())));
        stack.mulPose(Axis.XP.rotationDegrees(90-Mth.lerp(partial,entity.xRotO,entity.getXRot())));
        stack.scale(.5f,.85f,.5f);
        WeaponProjectileGeometry.render(buffer.getBuffer(WeaponEffectRenderTypes.PROJECTILE_BODY),
                stack.last().pose(),WeaponProjectileGeometry.Shape.BILLET);
        stack.popPose();
        super.render(entity,yaw,partial,stack,buffer,packedLight);
    }
}
