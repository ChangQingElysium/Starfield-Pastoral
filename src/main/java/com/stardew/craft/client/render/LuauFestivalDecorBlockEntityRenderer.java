package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.client.renderer.LightTexture;
import com.stardew.craft.client.model.nativebb.BlockbenchFrame;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.LuauFestivalDecorBlockEntity;
import com.stardew.craft.client.model.block.LuauFestivalDecorGeoModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class LuauFestivalDecorBlockEntityRenderer extends StardewGeoBlockRenderer<LuauFestivalDecorBlockEntity> {
    public LuauFestivalDecorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new LuauFestivalDecorGeoModel());
    }

    @Nullable
    @Override
    public RenderType getRenderType(LuauFestivalDecorBlockEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return isCauldron(animatable) ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void render(LuauFestivalDecorBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = animatable.getBlockState();
        if (state.hasProperty(MapDecorStaticBlock.PART)
            && state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) {
            return;
        }

        Direction facing = state.hasProperty(MapDecorStaticBlock.FACING)
            ? state.getValue(MapDecorStaticBlock.FACING)
            : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // Newly exported pots use Blockbench NORTH (-Z); legacy festival models retain their orientation.
        float angle = isCauldron(animatable) ? switch (facing) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        } : -facing.toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static boolean isCauldron(LuauFestivalDecorBlockEntity entity) {
        return entity.getBlockState().is(ModBlocks.LUAU_SOUP_POT.get())
                || entity.getBlockState().is(ModBlocks.WIZARD_CAULDRON.get());
    }

    @Override
    protected RenderType boneType(LuauFestivalDecorBlockEntity entity, BlockbenchFrame frame, int bone, RenderType type) {
        return isCauldron(entity) && frame.under(bone, "steam") ? SteamType.forTexture(getTextureLocation(entity)) : type;
    }

    @Override
    protected int boneLight(LuauFestivalDecorBlockEntity entity, BlockbenchFrame frame, int bone, int light) {
        return isCauldron(entity) && frame.under(bone, "fire") ? LightTexture.FULL_BRIGHT : light;
    }

    private static final class SteamType extends RenderType {
        private static final java.util.Map<ResourceLocation, RenderType> TYPES = new java.util.HashMap<>();
        private SteamType() { super("cauldron_steam", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                16384, false, true, () -> {}, () -> {}); }
        private static RenderType forTexture(ResourceLocation texture) {
            return TYPES.computeIfAbsent(texture, key -> create("cauldron_steam", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(key, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(ITEM_ENTITY_TARGET)
                            .createCompositeState(false)));
        }
    }

    @Override
    protected void rotateBlock(@Nonnull Direction facing, @Nonnull PoseStack poseStack) {
    }
}
