package com.stardew.craft.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.HoeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class HoeOverlayRenderer {

    private static final ResourceLocation RANGE_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/gui/range_overlay.png");

    @SuppressWarnings("null")
    private static final RenderType OVERLAY_RENDER_TYPE = RenderType.create(
            "stardew_tool_overlay",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderType.TextureStateShard(RANGE_TEXTURE, false, false))
                    .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    }))
                    .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
                    .setCullState(new RenderType.CullStateShard(false))
                    .setDepthTestState(new RenderType.DepthTestStateShard("always", 519))
                    .createCompositeState(false)
    );

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }

        if (!player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof HoeItem hoe)) {
            return;
        }

        int activeTicks = stack.getUseDuration() - player.getUseItemRemainingTicks();
        int chargeLevel = hoe.getChargeLevel(stack, activeTicks);
        if (chargeLevel == 0) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        List<BlockPos> positions = hoe.getPreviewBlocks(level, blockHit.getBlockPos(), player, chargeLevel);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        @SuppressWarnings("null")
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(OVERLAY_RENDER_TYPE);

        int baseY = blockHit.getBlockPos().getY();
        for (BlockPos pos : positions) {
            if (pos.getY() == baseY) {
                renderTexturedOverlay(poseStack, consumer, pos);
            }
        }

        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(OVERLAY_RENDER_TYPE);
    }

    @SuppressWarnings("null")
    private static void renderTexturedOverlay(PoseStack poseStack, VertexConsumer consumer, BlockPos pos) {
        float x = pos.getX();
        float y = pos.getY() + 1.05f;
        float z = pos.getZ();

        float minU = 0.0f;
        float maxU = 1.0f;
        float minV = 0.0f;
        float maxV = 1.0f;

        int r = 255;
        int g = 255;
        int b = 255;
        int a = 180;

        PoseStack.Pose last = poseStack.last();

        consumer.addVertex(last, x, y, z).setUv(minU, minV).setColor(r, g, b, a);
        consumer.addVertex(last, x, y, z + 1).setUv(minU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + 1, y, z + 1).setUv(maxU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + 1, y, z).setUv(maxU, minV).setColor(r, g, b, a);
    }
}
