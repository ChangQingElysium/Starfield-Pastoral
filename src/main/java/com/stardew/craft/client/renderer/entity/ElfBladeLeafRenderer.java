package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.Config;
import com.stardew.craft.client.weapon.ElfLightGeometry;
import com.stardew.craft.client.weapon.WeaponEffectRenderTypes;
import com.stardew.craft.entity.projectile.ElfBladeLeafEntity;
import java.util.ArrayList;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Three weightless lights: fractional orbit samples never inherit tick-wise projectile rotation. */
public class ElfBladeLeafRenderer extends EntityRenderer<ElfBladeLeafEntity> {
    public ElfBladeLeafRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(ElfBladeLeafEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
    @Override public void render(ElfBladeLeafEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
        if (entity.distanceToSqr(camera) > 48 * 48) return;
        Vec3 position = entity.getPosition(partial);
        var samples = new ArrayList<ElfLightGeometry.Sample>();
        Vec3 head = position;
        if (entity.isOrbiting() && entity.getOwner() instanceof LivingEntity owner) {
            Vec3 center = owner.getPosition(partial).add(0, owner.getBbHeight() * 0.6, 0);
            double time = entity.level().getGameTime() - 1 + partial;
            head = center.add(ElfBladeLeafEntity.orbitOffset(time, entity.getOrbitIndex()));
            for (int i = 0; i <= 24; i++) {
                double age = (24 - i) / 6.0;
                samples.add(new ElfLightGeometry.Sample(center.add(ElfBladeLeafEntity.orbitOffset(time - age, entity.getOrbitIndex())), (float) (1 - age / 4)));
            }
        } else {
            for (var point : entity.getTrailPoints()) {
                // The tick-end sample lies ahead of the interpolated head until partial == 1.
                if (point.age < 1) continue;
                float fade = Math.max(0, 1 - (point.age - 1 + partial) / entity.getTrailMaxAgeValue());
                samples.add(new ElfLightGeometry.Sample(point.position, fade));
            }
            samples.add(new ElfLightGeometry.Sample(head, 1));
        }
        stack.pushPose(); stack.translate(-position.x, -position.y, -position.z);
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        ElfLightGeometry.core(out, stack.last().pose(), head);
        if (Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get())
            ElfLightGeometry.trail(out, stack.last().pose(), samples, entity.isOrbiting() ? 0.025 : 0.045);
        stack.popPose();
        super.render(entity, yaw, partial, stack, buffers, light);
    }
}
