package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.SebastianComputerBlock;
import com.stardew.craft.blockentity.SebastianComputerBlockEntity;
import com.stardew.craft.client.model.SebastianComputerModel;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.client.npcnative.NativeNpcPoseRenderer;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Uses the authoritative NPC activity epoch; unrelated nearby NPCs cannot animate this computer. */
public final class SebastianComputerBlockEntityRenderer implements LargeDecorBlockEntityRenderer<SebastianComputerBlockEntity> {
    private final NativeNpcPoseRenderer geometry = new NativeNpcPoseRenderer();
    private NativeNpcModel model;
    private NativeNpcPose pose;

    public SebastianComputerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public AABB getRenderBoundingBox(SebastianComputerBlockEntity entity) {
        return ((SebastianComputerBlock) entity.getBlockState().getBlock()).renderBounds(entity.getBlockState(), entity.getBlockPos());
    }

    @Override public void render(SebastianComputerBlockEntity entity, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        var loaded = SebastianComputerModel.get();
        var level = entity.getLevel();
        if (loaded == null || level == null) return;
        if (loaded != model) { model = loaded; pose = new NativeNpcPose(model); }
        pose.reset();
        var facing = entity.getBlockState().getValue(SebastianComputerBlock.FACING);
        double now = level.getGameTime() + (double) partialTick;
        for (var npc : level.getEntitiesOfClass(StardewNpcEntity.class, new AABB(entity.getBlockPos()).inflate(4))) {
            var event = npc.getScheduleActivityEvent();
            if (!event.contains("workstation") || event.getLong("workstation") != entity.getBlockPos().asLong()
                    || !event.getString("workstationBlock").equals("stardewcraft:sebastian_computer")
                    || event.getInt("workstationFacing") != facing.get2DDataValue()
                    || !model.clips().containsKey(event.getString("workstationClip"))) continue;
            double start = event.getLong("start") + Math.max(1, event.getInt("enterTicks"));
            if (now < start) break;
            double sampleTick = event.contains("exit") ? event.getLong("exit") : now;
            double fade = event.contains("exit") ? 1 - smooth((now - event.getLong("exit")) / 4) : smooth((now - start) / 5);
            pose.blend(event.getString("workstationClip"), Math.max(0, (sampleTick - start) / 20), fade);
            break;
        }
        stack.pushPose();
        stack.translate(.5, 0, .5);
        stack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        stack.translate(-.5, 0, -.5);
        stack.scale(1/16F, 1/16F, 1/16F);
        geometry.renderGeometry(stack, buffers, light, model, pose);
        stack.popPose();
    }

    private static double smooth(double value) {
        double t = com.stardew.craft.port.PortJava.clamp(value, 0, 1);
        return t*t*t*(10+t*(-15+6*t));
    }
}
