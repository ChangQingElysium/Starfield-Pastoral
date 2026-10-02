package com.stardew.craft.port;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * PORT(1.20.1): 1.21.1 {@code InventoryScreen#renderEntityInInventoryFollowsMouse(GuiGraphics, x1, y1, x2, y2, scale,
 * yOffset, mouseX, mouseY, entity)}. 1.20.1 only has the (x, y, scale, mouseX, mouseY) form, which anchors the feet at
 * (x, y), has no clip box and no vertical offset, so the 1.21 algorithm is reproduced here: box-centred, scissored,
 * pivoting around half the bounding-box height plus {@code yOffset * entity.getScale()}.
 */
@OnlyIn(Dist.CLIENT)
public final class PortInventoryScreen {
    private PortInventoryScreen() {}

    public static void renderEntityInInventoryFollowsMouse(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                                           int scale, float yOffset, float mouseX, float mouseY,
                                                           LivingEntity entity) {
        float centerX = (float) (x1 + x2) / 2.0F;
        float centerY = (float) (y1 + y2) / 2.0F;
        float angleX = (float) Math.atan((double) ((centerX - mouseX) / 40.0F));
        float angleY = (float) Math.atan((double) ((centerY - mouseY) / 40.0F));
        renderEntityInInventoryFollowsAngle(graphics, x1, y1, x2, y2, scale, yOffset, angleX, angleY, entity);
    }

    /** NeoForge 21.1 {@code InventoryScreen#renderEntityInInventoryFollowsAngle}. */
    public static void renderEntityInInventoryFollowsAngle(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                                           int scale, float yOffset, float angleXComponent,
                                                           float angleYComponent, LivingEntity entity) {
        float centerX = (float) (x1 + x2) / 2.0F;
        float centerY = (float) (y1 + y2) / 2.0F;
        graphics.enableScissor(x1, y1, x2, y2);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(angleYComponent * 20.0F * (float) (Math.PI / 180.0));
        pose.mul(cameraOrientation);
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleXComponent * 20.0F;
        entity.setYRot(180.0F + angleXComponent * 40.0F);
        entity.setXRot(-angleYComponent * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        // 1.21 getScale() is the SCALE attribute only (age scale is separate); PortLivingEntityScaleMixin gives
        // StardewCraft entities that value, other entities return 1.20.1's (age scale, 1 for players).
        float entityScale = entity.getScale();
        Vector3f translate = new Vector3f(0.0F, entity.getBbHeight() / 2.0F + yOffset * entityScale, 0.0F);
        float renderScale = (float) scale / entityScale;
        renderEntityInInventory(graphics, centerX, centerY, renderScale, translate, pose, cameraOrientation, entity);
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        graphics.disableScissor();
    }

    /** 1.21.1 {@code InventoryScreen#renderEntityInInventory(GuiGraphics, float, float, float, Vector3f, ...)}. */
    public static void renderEntityInInventory(GuiGraphics graphics, float x, float y, float scale, Vector3f translate,
                                               Quaternionf pose, @Nullable Quaternionf cameraOrientation,
                                               LivingEntity entity) {
        graphics.pose().pushPose();
        graphics.pose().translate((double) x, (double) y, 50.0);
        graphics.pose().scale(scale, scale, -scale);
        graphics.pose().translate(translate.x, translate.y, translate.z);
        graphics.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (cameraOrientation != null) {
            // 1.21 adds rotateY(PI) because 1.20.5 flipped the camera-orientation convention; 1.20.1's dispatcher
            // expects the plain conjugate (what 1.20.1's own InventoryScreen passes), which faces the viewer the same way.
            dispatcher.overrideCameraOrientation(cameraOrientation.conjugate(new Quaternionf()));
        }
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, graphics.pose(),
                graphics.bufferSource(), 15728880));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
