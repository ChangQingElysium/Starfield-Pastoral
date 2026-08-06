package com.stardew.craft.client.renderer;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class WateringCanOverlayRenderer {

    private static final String TAG_ACTION = "StardewAction";
    private static final int ACTION_REFILL = 2;

    // 贴图位置：需要用户提供 textures/gui/range_overlay.png
    private static final ResourceLocation RANGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/range_overlay.png");
    
    // 自定义渲染类型
    @SuppressWarnings("null")
    private static final RenderType OVERLAY_RENDER_TYPE = RenderType.create(
            "stardew_tool_overlay",
            DefaultVertexFormat.POSITION_TEX_COLOR, 
            VertexFormat.Mode.QUADS, 
            256,
            false,
            true, // sortOnUpload
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderType.TextureStateShard(RANGE_TEXTURE, false, false))
                    .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    }))
                    .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false)) // 只写颜色，不写深度
                    .setCullState(new RenderType.CullStateShard(false))
                    .setDepthTestState(new RenderType.DepthTestStateShard("always", 519)) // 总是通过深度测试 (显示在最上层) ? 不，我们要在方块上，但不要被前面的方块挡住？
                    // 最好还是用 LEQUAL，但是我们抬高了Y。
                    // 用 EQUAL 可能会闪烁。
                    // 试试由于我们是 overlay，我们可以用 NO_DEPTH_TEST 配合 Layer? 但那样会透视墙壁。
                    // 保持默认 LEQUAL 即可，因为我们增加了Y。
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
        if (player == null || mc.level == null) return;

        // 检查玩家是否正在使用喷壶
        if (!player.isUsingItem()) return;
        
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof WateringCanItem wateringCan)) return;

        // 汲水蓄力时不显示“洒水范围”预览
        @SuppressWarnings("null")
        int action = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getInt(TAG_ACTION);
        if (action == ACTION_REFILL) return;

        // getUseDuration返回最大值 (72000)。
        // player.getUseItemRemainingTicks() 返回剩余倒计时。
        // ticksUsed = total - remaining
        int activeTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
        int chargeLevel = wateringCan.getChargeLevel(stack, activeTicks);

        // 不蓄力(0级/单格)时不显示预览；只有蓄力到1级及以上才显示
        if (chargeLevel == 0) return;

        // 获取作用范围
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;
        
        BlockHitResult blockHit = (BlockHitResult) hitResult;
        List<BlockPos> positions = wateringCan.getAffectedBlocks(mc.level, blockHit.getBlockPos(), player, chargeLevel);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        @SuppressWarnings("null")
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(OVERLAY_RENDER_TYPE);

        // 以“实际将要浇到的耕地”所在平面为准（对准作物时会修正到下方耕地）
        int baseY = positions.isEmpty() ? blockHit.getBlockPos().getY() : positions.getFirst().getY();

        for (BlockPos pos : positions) {
            if (pos.getY() == baseY) {
                renderTexturedOverlay(poseStack, consumer, mc.level, pos);
            }
        }
        
        poseStack.popPose();
        
        // 必须强制刷新 buffer，因为 bufferSource 可能延迟绘制，而我们的 PoseStack pop 了
        mc.renderBuffers().bufferSource().endBatch(OVERLAY_RENDER_TYPE);
    }

    @SuppressWarnings("null")
    private static void renderTexturedOverlay(
            PoseStack poseStack, VertexConsumer consumer, net.minecraft.world.level.Level level, BlockPos pos) {
        boolean gardenPot = level.getBlockState(pos).getBlock() instanceof GardenPotBlock;
        float inset = gardenPot ? 1.5F / 16.0F : 0.0F;
        float size = gardenPot ? 13.0F / 16.0F : 1.0F;
        float x = pos.getX() + inset;
        float y = pos.getY() + (gardenPot ? 11.0F / 16.0F + 0.01F : 1.05F);
        float z = pos.getZ() + inset;
        
        // 简单的平铺贴图
        // 假设贴图是完整的 1x1 覆盖
        float minU = 0.0f;
        float maxU = 1.0f;
        float minV = 0.0f;
        float maxV = 1.0f;
        
        // 颜色设置为白色，不透明度在这控制 (或者贴图自带alpha)
        int r = 255;
        int g = 255;
        int b = 255;
        int a = 180; // 稍微透明一点，防止太亮

        // 绘制一个面 (两个三角形)
        PoseStack.Pose last = poseStack.last();
        
        consumer.addVertex(last, x, y, z).setUv(minU, minV).setColor(r, g, b, a);
        consumer.addVertex(last, x, y, z + size).setUv(minU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + size, y, z + size).setUv(maxU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + size, y, z).setUv(maxU, minV).setColor(r, g, b, a);
    }
}
