package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.utility.totem.TotemPoleBlock;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

/**
 * 图腾柱渲染器：渲染方块模型 + 在顶部显示浮动名称文字。
 * 文字特性：加粗、有颜色（按类型）、无背景、不使用 billboard（固定朝向）、两面可见。
 */
public class TotemPoleBlockEntityRenderer implements BlockEntityRenderer<TotemPoleBlockEntity> {

    private final Font font;

    public TotemPoleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @SuppressWarnings({"null", "deprecation"})
    @Override
    public void render(@Nonnull TotemPoleBlockEntity be, float partialTick, @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level == null) return;

        // ---- 渲染方块模型 ----
        poseStack.pushPose();
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        RandomSource rand = RandomSource.create(0L);
        renderer.tesselateBlock(
                level, model, state, be.getBlockPos(), poseStack,
                buffer.getBuffer(renderType), true, rand, 0L, packedOverlay);
        poseStack.popPose();

        // ---- 渲染浮动名称文字 ----
        String name = be.getPoleName();
        if (name == null || name.isEmpty()) return;

        TotemType type = be.getTotemType();
        int color = type.getTextColor();

        // 计算文字 Y 位置（模型顶部 + 偏移）
        float textY = BubbleYHelper.get(state, level, be.getBlockPos()) + 0.6f;

        // 获取方块朝向角度
        Direction facing = state.hasProperty(TotemPoleBlock.FACING)
                ? state.getValue(TotemPoleBlock.FACING) : Direction.NORTH;
        float facingAngle = switch (facing) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };

        Component text = (be.isSystemPole() && name.startsWith("stardewcraft."))
                ? Component.translatable(name).withStyle(style -> style.withBold(true))
                : Component.literal(name).withStyle(style -> style.withBold(true));
        float textWidth = font.width(text);

        // 正面渲染
        poseStack.pushPose();
        poseStack.translate(0.5, textY, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingAngle));
        poseStack.scale(-0.025f, -0.025f, 0.025f);
        font.drawInBatch(text, -textWidth / 2f, 0, color, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();

        // 背面渲染（旋转180°，从另一侧也能看到）
        poseStack.pushPose();
        poseStack.translate(0.5, textY, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingAngle + 180f));
        poseStack.scale(-0.025f, -0.025f, 0.025f);
        font.drawInBatch(text, -textWidth / 2f, 0, color, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull TotemPoleBlockEntity blockEntity) {
        return true; // 文字可能超出方块边界
    }

    @Override
    public int getViewDistance() {
        return 64; // 远距离可见
    }
}
