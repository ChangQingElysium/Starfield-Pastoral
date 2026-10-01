package com.stardew.craft.client.aquarium;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.aquarium.AquariumMotion;
import com.stardew.craft.aquarium.AquariumRules;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.AquariumBlockEntity;
import com.stardew.craft.client.fishpond.ClientFishPondFishRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class AquariumRenderer implements BlockEntityRenderer<AquariumBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<AquariumBlockEntity> {
    private final AquariumSpecialCreatures special;
    public AquariumRenderer(BlockEntityRendererProvider.Context context) { special = new AquariumSpecialCreatures(context); }
    @Override public AABB getRenderBoundingBox(AquariumBlockEntity tank) {
        return tank.getBlockState().getShape(tank.getLevel(), tank.getBlockPos()).bounds().move(tank.getBlockPos()).inflate(.04);
    }
    @Override public int getViewDistance() { return 64; }
    @Override public void render(AquariumBlockEntity tank, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); pose.translate(.5, 0, .5);
        float angle = switch (tank.getBlockState().getValue(MapDecorStaticBlock.FACING)) {
            case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(1.5, 0, .5); pose.scale(1/16f, 1/16f, 1/16f);
        AquariumModels.render("large_fish_tank", false, pose, buffers, light);
        double seconds = tank.getLevel() == null ? 0 : (tank.getLevel().getGameTime() + partial) / 20.0;
        renderContents(tank, seconds, pose, buffers, light);
        AquariumModels.render("large_fish_tank", true, pose, buffers, light);
        pose.popPose();
    }
    private void renderContents(AquariumBlockEntity tank, double seconds, PoseStack pose, MultiBufferSource buffers, int light) {
        for (int slot = 9; slot < AquariumRules.SIZE; slot++) {
            var stack = tank.getItem(slot); if (stack.isEmpty()) continue;
            String id = AquariumRules.decoration(stack);
            String planted = switch (id) { case "152" -> "seaweed"; case "390" -> "stone"; case "393" -> "coral"; default -> ""; };
            if (!planted.isEmpty()) { AquariumModels.render(planted, false, pose, buffers, light); continue; }
            // Original item art/models are also used for the small treasure-like floor decorations.
            int place = switch (id) { case "117" -> 0; case "166" -> 1; case "832" -> 2; case "109" -> 3;
                case "709" -> 4; case "392" -> 5; case "394" -> 6; case "167" -> 7; case "789" -> 8; case "330" -> 9; default -> 10; };
            pose.pushPose();
            pose.translate(-19 + place % 6 * 7.6, 9.1, place < 6 ? 8 : 1);
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.floorMod(tank.layoutSeed() + place * 137, 35) - 17));
            pose.scale(4, 4, 4);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, tank.getLevel(), slot);
            pose.popPose();
        }
        int hat = 6;
        for (int slot = 0; slot < 6; slot++) {
            var stack = tank.getItem(slot); if (stack.isEmpty()) continue;
            var size = ClientFishPondFishRenderer.modelSize(stack);
            float originalDiameter = size.length();
            float factor = originalDiameter == 0 ? 1 : Math.min(.72f, 14 / originalDiameter);
            if (AquariumRules.hatWearer(stack)) size.set(10.24f, 5.22f, 10.24f);
            if (AquariumRules.frog(stack)) size.set(12, 7, 12);
            size.mul(factor);
            var motion = AquariumMotion.sample(AquariumRules.movement(stack), slot, tank.layoutSeed(), seconds, size.x, size.y, size.z);
            pose.pushPose(); pose.translate(motion.x(), motion.y(), motion.z());
            if (AquariumRules.hatWearer(stack)) {
                special.urchin(pose, buffers, light);
                while (hat < 9 && tank.getItem(hat).isEmpty()) hat++;
                if (hat < 9) special.hat(tank.getItem(hat++), pose, buffers, light);
            } else if (AquariumRules.frog(stack)) {
                special.frog(stack, seconds, motion.yaw(), (float)Math.max(0, motion.y() - 10.85), pose, buffers, light);
            } else ClientFishPondFishRenderer.renderFish(stack, pose, buffers,
                    Minecraft.getInstance().level, light, motion.yaw(), 0, motion.roll(), originalDiameter * factor);
            pose.popPose();
        }
        if (!tank.isEmpty()) special.bubbles(tank.layoutSeed(), seconds, pose, buffers, light);
    }
}
