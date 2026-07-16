package com.stardew.craft.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.ScarecrowBlock;
import com.stardew.craft.block.utility.SprinklerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class SprinklerOverlayRenderer {
    private static final ResourceLocation RANGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
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

    private static final int SEARCH_RADIUS = 32;
    private static final int SEARCH_HEIGHT = 4;
    private static final int SPRINKLER_RED = 255;
    private static final int SPRINKLER_GREEN = 255;
    private static final int SPRINKLER_BLUE = 255;
    private static final int SPRINKLER_ALPHA = 180;
    private static final int SCARECROW_RED = 255;
    private static final int SCARECROW_GREEN = 196;
    private static final int SCARECROW_BLUE = 64;
    private static final int SCARECROW_ALPHA = 150;

    private record OverlaySelection(Set<BlockPos> positions, int red, int green, int blue, int alpha) {
    }

    private record HeldScarecrow(ScarecrowBlock block, ItemStack stack, InteractionHand hand) {
    }

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

        HitResult hitResult = mc.hitResult;
        OverlaySelection selection = resolveScarecrowSelection(player, level, hitResult);
        if (selection == null) {
            selection = resolveSprinklerSelection(player, level, hitResult);
        }
        if (selection == null || selection.positions().isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(OVERLAY_RENDER_TYPE);

        for (BlockPos pos : selection.positions()) {
            renderTexturedOverlay(poseStack, consumer, pos,
                    selection.red(), selection.green(), selection.blue(), selection.alpha());
        }

        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(OVERLAY_RENDER_TYPE);
    }

    private static OverlaySelection resolveScarecrowSelection(Player player, Level level, HitResult hitResult) {
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState hitState = level.getBlockState(hitPos);
            if (hitState.getBlock() instanceof ScarecrowBlock scarecrow) {
                BlockPos mainPos = scarecrow.findMainPos(level, hitPos, hitState);
                if (mainPos != null) {
                    return scarecrowSelection(mainPos, scarecrow.getRadius());
                }
            }

            HeldScarecrow held = heldScarecrow(player);
            if (held != null) {
                BlockPlaceContext context = new BlockPlaceContext(player, held.hand(), held.stack(), blockHit);
                BlockState placementState = held.block().getStateForPlacement(context);
                if (placementState != null) {
                    return scarecrowSelection(context.getClickedPos(), held.block().getRadius());
                }
            }
        }
        return null;
    }

    private static OverlaySelection scarecrowSelection(BlockPos center, int radius) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos groundCenter = center.below();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx != 0 || dz != 0)
                        && ScarecrowBlock.protects(center, center.offset(dx, 0, dz), radius)) {
                    positions.add(groundCenter.offset(dx, 0, dz).immutable());
                }
            }
        }
        return new OverlaySelection(positions,
                SCARECROW_RED, SCARECROW_GREEN, SCARECROW_BLUE, SCARECROW_ALPHA);
    }

    private static HeldScarecrow heldScarecrow(Player player) {
        HeldScarecrow mainHand = heldScarecrow(player.getMainHandItem(), InteractionHand.MAIN_HAND);
        return mainHand != null
                ? mainHand
                : heldScarecrow(player.getOffhandItem(), InteractionHand.OFF_HAND);
    }

    private static HeldScarecrow heldScarecrow(ItemStack stack, InteractionHand hand) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ScarecrowBlock scarecrow) {
            return new HeldScarecrow(scarecrow, stack, hand);
        }
        return null;
    }

    private static OverlaySelection resolveSprinklerSelection(Player player, Level level, HitResult hitResult) {
        BlockPos center = null;
        if (isHoldingSprinkler(player)) {
            center = player.blockPosition();
        }
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockState hitState = level.getBlockState(blockHit.getBlockPos());
            if (hitState.getBlock() instanceof SprinklerBlock) {
                center = blockHit.getBlockPos();
            }
        }
        if (center == null) {
            return null;
        }

        Set<BlockPos> overlayPositions = new HashSet<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_HEIGHT; y <= SEARCH_HEIGHT; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof SprinklerBlock sprinkler)) {
                        continue;
                    }
                    List<BlockPos> watered = SprinklerBlock.getWateredPositions(pos, sprinkler.getTier());
                    for (BlockPos target : watered) {
                        overlayPositions.add(target.immutable());
                    }
                }
            }
        }
        return new OverlaySelection(overlayPositions,
                SPRINKLER_RED, SPRINKLER_GREEN, SPRINKLER_BLUE, SPRINKLER_ALPHA);
    }

    private static boolean isHoldingSprinkler(Player player) {
        return isSprinklerItem(player.getMainHandItem()) || isSprinklerItem(player.getOffhandItem());
    }

    private static boolean isSprinklerItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() instanceof SprinklerBlock;
    }

    @SuppressWarnings("null")
    private static void renderTexturedOverlay(PoseStack poseStack, VertexConsumer consumer, BlockPos pos,
                                              int r, int g, int b, int a) {
        float x = pos.getX();
        float y = pos.getY() + 0.9385f;
        float z = pos.getZ();

        float minU = 0.0f;
        float maxU = 1.0f;
        float minV = 0.0f;
        float maxV = 1.0f;

        PoseStack.Pose last = poseStack.last();

        consumer.addVertex(last, x, y, z).setUv(minU, minV).setColor(r, g, b, a);
        consumer.addVertex(last, x, y, z + 1).setUv(minU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + 1, y, z + 1).setUv(maxU, maxV).setColor(r, g, b, a);
        consumer.addVertex(last, x + 1, y, z).setUv(maxU, minV).setColor(r, g, b, a);
    }
}
