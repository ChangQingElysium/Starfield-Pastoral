package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.BedDecorBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.*;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Uses the world's baked models and tint handlers, with an isolated tablecloth entity. */
@OnlyIn(Dist.CLIENT)
public final class FurnitureModelPreview {
    private static final Quaternionf VIEW = Axis.XP.rotationDegrees(23).mul(Axis.YP.rotationDegrees(145));
    private final BlockState source;
    private final TableDisplayBlockEntity table;
    private final PreviewView view = new PreviewView();
    private List<BlockState> states = List.of();
    private List<BakedModel> models = List.of();
    private List<Face> faces = List.of();
    private int lastColor = Integer.MIN_VALUE;
    private float centerX, centerY, centerZ, scale;

    private record Face(BakedQuad quad, RenderType layer, int color) {}

    public FurnitureModelPreview(BlockState source) {
        this.source = source;
        table = source.getBlock() instanceof OakTableBlock ? new TableDisplayBlockEntity(BlockPos.ZERO, source) : null;
    }

    public boolean hasAuthoredOriginal() { return source.getBlock() instanceof BedDecorBlock; }
    public int initialColor(int current) {
        return hasAuthoredOriginal() && !source.getValue(BedDecorBlock.DYED) ? -1 : current;
    }

    /** Immutable states: neither selection nor normalization writes to the live block. */
    public static BlockState selectedState(BlockState source, int selection) {
        BlockState state = source;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        if (state.hasProperty(MapDecorStaticBlock.PART))
            state = state.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN);
        int color = selection < 0 ? WoodenChestColorPalette.defaultColorIndex() : WoodenChestColorPalette.clampIndex(selection);
        // Each furniture class owns a distinct property instance, even when its name is "color".
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty value && value.getName().equals("color") && value.getPossibleValues().contains(color)) {
                state = state.setValue(value, color);
                break;
            }
        }
        if (state.getBlock() instanceof BedDecorBlock) state = state.setValue(BedDecorBlock.DYED, selection >= 0);
        return state;
    }

    private List<BlockState> parts(int selection) {
        BlockState main = selectedState(source, selection);
        if (source.getBlock() instanceof OfficeStoolBlock)
            return List.of(main, selectedState(ModBlocks.OFFICE_STOOL_TOP_RENDER.get().defaultBlockState(), selection));
        if (source.getBlock() instanceof OfficeChair2Block)
            return List.of(main, selectedState(ModBlocks.OFFICE_CHAIR_2_TOP_RENDER.get().defaultBlockState(), selection));
        return List.of(main);
    }

    private void prepare(int color) {
        var mc = Minecraft.getInstance();
        List<BlockState> next = color == lastColor ? states : parts(color);
        boolean changed = color != lastColor || models.size() != next.size();
        for (int i = 0; !changed && i < next.size(); i++)
            changed = mc.getBlockRenderer().getBlockModel(next.get(i)) != models.get(i);
        if (!changed) return; // Model identity also invalidates the cache after resource reload.
        states = next;
        models = states.stream().map(mc.getBlockRenderer()::getBlockModel).toList();
        lastColor = color;
        if (table != null) table.setClothColor(color < 0 ? OakTableBlock.DEFAULT_CLOTH_COLOR : color);
        List<Face> result = new ArrayList<>();
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < states.size(); i++) {
            BlockState state = states.get(i);
            BakedModel model = models.get(i);
            view.state = state;
            ModelData data = model.getModelData(view, BlockPos.ZERO, state, ModelData.EMPTY);
            random.setSeed(42);
            for (RenderType layer : model.getRenderTypes(state, random, data)) {
                RenderType entityLayer = RenderTypeHelper.getEntityRenderType(layer, false);
                for (int side = 0; side <= 6; side++) {
                    random.setSeed(42);
                    for (BakedQuad quad : model.getQuads(state, side == 6 ? null : Direction.values()[side], random, data, layer)) {
                        int tint = quad.isTinted() ? mc.getBlockColors().getColor(state, view, BlockPos.ZERO, quad.getTintIndex()) : -1;
                        result.add(new Face(quad, entityLayer, tint));
                    }
                }
            }
        }
        faces = List.copyOf(result);
        fit();
    }

    private void fit() {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        Vector3f point = new Vector3f();
        for (Face face : faces) {
            int[] vertices = face.quad().getVertices();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) {
                int at = i * stride;
                point.set(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]), Float.intBitsToFloat(vertices[at + 2])).rotate(VIEW);
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
            }
        }
        if (faces.isEmpty()) { scale = 1; centerX = centerY = centerZ = 0; return; }
        scale = Math.min(52 / Math.max(.01F, maxX - minX), 43 / Math.max(.01F, maxY - minY));
        centerX = (minX + maxX) / 2; centerY = (minY + maxY) / 2; centerZ = (minZ + maxZ) / 2;
    }

    public void draw(GuiGraphics graphics, int x, int y, int color) {
        prepare(color);
        graphics.flush();
        var pose = graphics.pose();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(x - scale * centerX, y + scale * centerY, 70 - scale * centerZ);
            pose.scale(scale, -scale, scale);
            pose.mulPose(VIEW);
            Lighting.setupFor3DItems();
            for (Face face : faces) {
                int rgb = face.color();
                buffers.getBuffer(face.layer()).putBulkData(pose.last(), face.quad(),
                        (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F,
                        1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private final class PreviewView implements BlockAndTintGetter {
        private BlockState state;
        @Override public BlockState getBlockState(BlockPos pos) { return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return pos.equals(BlockPos.ZERO) ? table : null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 32; }
        @Override public int getMinBuildHeight() { return 0; }
        @Override public float getShade(Direction direction, boolean shade) { return 1; }
        @Override public LevelLightEngine getLightEngine() { return Minecraft.getInstance().level.getLightEngine(); }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
    }
}
