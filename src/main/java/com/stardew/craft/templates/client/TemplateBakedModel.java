package com.stardew.craft.templates.client;

import com.stardew.craft.port.PortItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateMaterials;
import com.stardew.craft.templates.TemplateShape;
import com.stardew.craft.templates.RoundWindowProfile;
import com.stardew.craft.client.model.terrain.ShapedMaterialContext;
import com.stardew.craft.client.model.terrain.ShapedMaterialQuads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import org.joml.Vector3f;

import com.stardew.craft.port.PortVertex;
final class TemplateBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel, ModelDataAmbientOcclusion {
    private static final net.minecraftforge.client.model.data.ModelProperty<Integer> ROOF_PHASE =
            new net.minecraftforge.client.model.data.ModelProperty<>();
    private static final net.minecraftforge.client.model.data.ModelProperty<Integer> ROOF_EDGES =
            new net.minecraftforge.client.model.data.ModelProperty<>();
    private static final net.minecraftforge.client.model.data.ModelProperty<Integer> ROOF_HIDDEN_SECTIONS =
            new net.minecraftforge.client.model.data.ModelProperty<>();
    static final int FILL_TINT_OFFSET = 1 << 16;
    private ModelData itemMaterials = ModelData.EMPTY;
    private volatile TemplateMesh mesh;
    private final Map<Integer, TemplateMesh> roofMeshes = new ConcurrentHashMap<>();
    private final TemplateShape shape;
    private final BlockState templateState;
    private final boolean itemModel;
    private final Map<CacheKey, TemplateBakedModel> itemPassCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, List<BakedQuad>> quadCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(32, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<CacheKey, List<BakedQuad>> entry) { return size() > 512; }
            });

    TemplateBakedModel(BakedModel originalModel, TemplateShape shape, BlockState templateState, boolean itemModel) {
        super(originalModel);
        this.shape = shape;
        this.templateState = templateState;
        this.itemModel = itemModel;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        if (PortItemData.has(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)) {
            ModelData data = TemplateBlockEntity.itemMaterials(stack);
            CacheKey key = new CacheKey(material(data), fillMaterial(data), null, null, 0, 0, 15, 0, usesStudy(data), null);
            return List.of(itemPassCache.computeIfAbsent(key, ignored -> {
                TemplateBakedModel copy = new TemplateBakedModel(originalModel, shape, templateState, true);
                copy.itemMaterials = data;
                return copy;
            }));
        }
        return List.of(this);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        if (!shape.isComposite()) return super.getRenderTypes(stack, fabulous);
        var types = getRenderTypes(templateState, RandomSource.create(42L), itemMaterials);
        return List.of(net.minecraftforge.client.RenderTypeHelper.getEntityRenderType(
                types.contains(RenderType.translucent()) ? RenderType.translucent() : RenderType.solid(), fabulous));
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        if (itemModel && VanillaTemplateModels.supports(shape)) {
            ItemStack reference = new ItemStack(VanillaTemplateModels.referenceBlock(shape));
            Minecraft.getInstance().getItemRenderer().getModel(reference, null, null, 0)
                    .applyTransform(context, poseStack, leftHand);
            return this;
        }
        originalModel.applyTransform(context, poseStack, leftHand);
        return this;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState ignoredState, @Nullable Direction side, RandomSource random,
                                    ModelData modelData, @Nullable RenderType renderType) {
        if (itemModel && side != null) {
            return List.of();
        }
        ModelData data = itemModel ? itemMaterials : modelData;
        BlockState material = material(data);
        BlockState fill = fillMaterial(data);
        int phase = !itemModel && data.has(ROOF_PHASE) ? data.get(ROOF_PHASE) : 0;
        CacheKey key = new CacheKey(material, fill, renderType, side,
                com.stardew.craft.client.model.terrain.TerrainSeasonTextures.currentTextureSet(), phase,
                data.has(ROOF_EDGES) ? data.get(ROOF_EDGES) : 15,
                data.has(ROOF_HIDDEN_SECTIONS) ? data.get(ROOF_HIDDEN_SECTIONS) : 0, usesStudy(data), data.get(ShapedMaterialContext.PROPERTY));
        List<BakedQuad> cached = quadCache.get(key);
        if (cached != null) return cached;
        // Material queries can re-enter this LRU or query another worker's model.
        // Build without a cache lock or a compute callback, then publish atomically.
        List<BakedQuad> built = buildCompositeQuads(key);
        List<BakedQuad> existing = quadCache.putIfAbsent(key, built);
        return existing == null ? built : existing;
    }

    private List<BakedQuad> buildCompositeQuads(CacheKey key) {
        if ((shape == TemplateShape.BALCONY_RAILING || shape == TemplateShape.GRID_WINDOW) && key.study) {
            return originalModel.getQuads(itemModel ? null : templateState, key.side, RandomSource.create(42), ModelData.EMPTY, key.renderType);
        }
        var renderer = Minecraft.getInstance().getBlockRenderer();
        BlockState roofState = key.study ? templateState : key.material;
        BakedModel roofModel = key.study ? originalModel : renderer.getBlockModel(key.material);
        TemplateMesh roofMesh = key.phase == 0 && key.edges == 15 ? mesh() : roofMeshes.computeIfAbsent(
                (key.phase << 4) | key.edges, ignored -> TemplateMesh.createRoof(shape, templateState, itemModel,
                        key.phase & 3, key.phase >>> 4, (key.phase >>> 2) & 3, key.edges));
        if (key.hiddenSections != 0) roofMesh = roofMesh.withoutJoinedEdges(key.hiddenSections);
        if (key.study) roofMesh = roofMesh.studyTexture(key.phase & 3, (key.phase >>> 2) & 3);
        boolean windowArtwork = shape.isWindow()
                && key.material.is(com.stardew.craft.block.ModBlocks.BLUE_GRAY_TIMBER.get());
        if (windowArtwork) roofMesh = TemplateMesh.windowArtwork(shape, templateState);
        TemplateMesh fillMesh = key.fill == null ? null : TemplateMesh.createFill(shape, templateState);
        if (shape == TemplateShape.ROUND_WINDOW) {
            roofMesh = TemplateMesh.roundWindowLayer(templateState, RoundWindowProfile.frame(),
                    key.fill != null && (key.fill.canOcclude() || !key.material.canOcclude())
                            ? RoundWindowProfile.fill() : List.of(), false);
            if (key.fill != null) fillMesh = TemplateMesh.roundWindowLayer(templateState, RoundWindowProfile.fill(),
                    key.material.canOcclude() ? RoundWindowProfile.frame() : List.of(), false);
        }
        if (fillMesh != null && shape.isCompositeWall()) {
            if (key.material.canOcclude()) fillMesh=fillMesh.withOpenFacadeFront(roofMesh,templateState);
            if (key.fill.canOcclude() || !key.material.canOcclude()) roofMesh=roofMesh.withoutFacadeBack(templateState);
        } else if (fillMesh != null && shape.meshKind()==TemplateShape.MeshKind.ROOF) {
            // Remove both hidden faces for opaque pairs. For transparent pairs
            // keep one interface only; duplicating the reversed faces would flicker.
            if (key.fill.canOcclude() || !key.material.canOcclude()) roofMesh = roofMesh.withoutInterface(templateState, false);
            if (key.material.canOcclude()) fillMesh = fillMesh.withoutInterface(templateState, true);
        }
        ArrayList<BakedQuad> result = new ArrayList<>();
        if (supportsLayer(roofModel, roofState, key.renderType)) {
            if (windowArtwork) {
                var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                        .apply(new net.minecraft.resources.ResourceLocation("stardewcraft", "block/building/window_frame_finish"));
                for (var quad : roofMesh.quads()) {
                    Direction cull = quad.cullFace();
                    if (itemModel ? key.side != null : (key.side == null ? cull != null : cull != key.side)) continue;
                    result.add(bakeFallback(quad, sprite));
                }
            } else result.addAll(buildMaterialQuads(roofState, roofModel, key.renderType, key.side, roofMesh, key.context));
        }
        if (fillMesh != null) {
            BakedModel fillModel = renderer.getBlockModel(key.fill);
            if (supportsLayer(fillModel, key.fill, key.renderType)) {
                for (BakedQuad quad : buildMaterialQuads(key.fill, fillModel, key.renderType, key.side, fillMesh, key.context)) {
                    result.add(quad.isTinted() ? new BakedQuad(quad.getVertices(), quad.getTintIndex()+FILL_TINT_OFFSET,
                            quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()) : quad);
                }
            }
        }
        if (shape == TemplateShape.ROUND_WINDOW) {
            if (key.side == null && (key.renderType == null || key.renderType == RenderType.translucent())) {
                var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                        .apply(new net.minecraft.resources.ResourceLocation("stardewcraft", "block/material_templates/round_window_glass"));
                for (var quad : TemplateMesh.roundWindowLayer(templateState, RoundWindowProfile.glass(), List.of(), true).quads())
                    result.add(bakeFallback(quad, sprite));
            }
        }
        if (shape == TemplateShape.GRID_WINDOW && (key.renderType == null || key.renderType == RenderType.translucent())) {
            for (BakedQuad quad : originalModel.getQuads(itemModel ? null : templateState, key.side, RandomSource.create(42), ModelData.EMPTY, key.renderType))
                if (quad.getSprite().contents().name().getPath().endsWith("grid_window_glass")) result.add(quad);
        }
        return List.copyOf(result);
    }

    private static boolean supportsLayer(BakedModel model, BlockState material, @Nullable RenderType layer) {
        return layer == null || model.getRenderTypes(material, RandomSource.create(42L), ModelData.EMPTY).contains(layer);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData tileData) {
        if (surfaceMaterial(material(tileData)) || surfaceMaterial(fillMaterial(tileData))) {
            tileData = tileData.derive().with(ShapedMaterialContext.PROPERTY, ShapedMaterialContext.capture(level, pos)).build();
        }
        if (shape.meshKind() != TemplateShape.MeshKind.ROOF) return tileData;
        return tileData.derive().with(ROOF_PHASE, (Math.floorMod(pos.getX(), 4)
                | (Math.floorMod(pos.getZ(), 4) << 2) | (Math.floorMod(pos.getY(), 272) << 4))).with(ROOF_EDGES,
                com.stardew.craft.templates.RoofTemplateEdges.exposed(level, pos, state)).with(ROOF_HIDDEN_SECTIONS,
                com.stardew.craft.templates.RoofTemplateEdges.hiddenSections(level,pos,state)).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        boolean study = usesStudy(data);
        // Multipart artwork reads template properties such as facing and connections.
        BlockState modelState = study ? templateState : material(data);
        BakedModel model = study ? originalModel : Minecraft.getInstance().getBlockRenderer().getBlockModel(modelState);
        var roofTypes = model.getRenderTypes(modelState, random, ModelData.EMPTY);
        if (shape == TemplateShape.ROUND_WINDOW || shape == TemplateShape.GRID_WINDOW) {
            roofTypes = ChunkRenderTypeSet.union(roofTypes, ChunkRenderTypeSet.of(RenderType.translucent()));
        }
        BlockState fill = fillMaterial(data);
        if (fill == null) return roofTypes;
        var fillTypes = Minecraft.getInstance().getBlockRenderer().getBlockModel(fill).getRenderTypes(fill, random, ModelData.EMPTY);
        return ChunkRenderTypeSet.union(roofTypes, fillTypes);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = shape == TemplateShape.WINDOW_FRAME
                && com.stardew.craft.templates.ConnectedFacadeTemplateBlock.connections(templateState) == 15
                ? fillMaterial(data) : material(data);
        return (usesStudy(data) ? originalModel : Minecraft.getInstance().getBlockRenderer().getBlockModel(material)).getParticleIcon(ModelData.EMPTY);
    }

    /**
     * 1.21 {@code useAmbientOcclusion(state, data, renderType)}. PORT(1.20.1): Forge's ModelBlockRenderer asks
     * {@code useAmbientOcclusion(state, renderType)} without model data; {@code TemplateModelAmbientOcclusionMixin}
     * routes block tesselation here with the block's ModelData. The 1.20.1 material model answers a boolean
     * (1.21 DEFAULT/FALSE), and Forge keeps the light-emission check that 1.21 applies to DEFAULT.
     */
    @Override
    public boolean stardewcraft$useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState material = material(data);
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material)
                .useAmbientOcclusion(material, renderType);
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return stardewcraft$useAmbientOcclusion(state, ModelData.EMPTY, renderType);
    }

    private List<BakedQuad> buildMaterialQuads(BlockState material, BakedModel materialModel,
                                               @Nullable RenderType renderType, @Nullable Direction side) {
        return buildMaterialQuads(material, materialModel, renderType, side, mesh(), null);
    }

    private List<BakedQuad> buildMaterialQuads(BlockState material, BakedModel materialModel,
                                               @Nullable RenderType renderType, @Nullable Direction side, TemplateMesh mesh, @Nullable ShapedMaterialContext context) {
        List<BakedQuad> front = buildShapeQuads(material, materialModel, renderType, side, mesh, context);
        boolean transparent = renderType != null ? renderType != RenderType.solid()
                : materialModel.getRenderTypes(material, RandomSource.create(42L), ModelData.EMPTY)
                        .asList().stream().anyMatch(layer -> layer != RenderType.solid());
        if (side != null || !transparent) {
            return front;
        }

        ArrayList<BakedQuad> result = new ArrayList<>(front);
        for (BakedQuad quad : front) {
            result.add(backFace(quad));
        }
        if (!itemModel) {
            // Inward faces must remain unculled: a solid neighbor may hide the
            // outward face while the inward face is still visible through a hole.
            for (Direction direction : Direction.values()) {
                for (BakedQuad quad : buildShapeQuads(material, materialModel, renderType, direction, mesh, context)) {
                    result.add(backFace(quad));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<BakedQuad> buildShapeQuads(BlockState material, BakedModel materialModel,
                                            @Nullable RenderType renderType, @Nullable Direction side, TemplateMesh mesh, @Nullable ShapedMaterialContext context) {
        return VanillaTemplateModels.supports(shape)
                ? buildReferenceQuads(material, materialModel, renderType, side, context)
                : buildQuads(material, materialModel, renderType, side, mesh, context);
    }

    private static BakedQuad backFace(BakedQuad front) {
        int[] original = front.getVertices();
        int[] data = new int[original.length];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            System.arraycopy(original, (3 - vertex) * IQuadTransformer.STRIDE, data, offset, IQuadTransformer.STRIDE);
            int normal = data[offset + IQuadTransformer.NORMAL];
            data[offset + IQuadTransformer.NORMAL] = (-(byte) normal & 0xFF)
                    | (-(byte) (normal >> 8) & 0xFF) << 8
                    | (-(byte) (normal >> 16) & 0xFF) << 16;
        }
        return new BakedQuad(data, front.getTintIndex(), front.getDirection().getOpposite(), front.getSprite(),
                front.isShade(), front.hasAmbientOcclusion());
    }

    private List<BakedQuad> buildQuads(BlockState material, BakedModel materialModel,
                                       @Nullable RenderType renderType, @Nullable Direction side, TemplateMesh mesh, @Nullable ShapedMaterialContext context) {
        ArrayList<BakedQuad> result = new ArrayList<>(mesh.quads().size());
        List<BakedQuad> geometry = surfaceMaterial(material) ? mesh.quads().stream()
                .map(q -> bakeFallback(q, materialModel.getParticleIcon(ModelData.EMPTY))).toList() : List.of();
        RandomSource random = RandomSource.create(42L);
        for (TemplateMesh.MeshQuad meshQuad : mesh.quads()) {
            Direction cullFace = meshQuad.cullFace();
            if (itemModel ? side != null : (side == null ? cullFace != null : cullFace != side)) {
                continue;
            }
            if (!geometry.isEmpty()) {
                BakedQuad target = bakeFallback(meshQuad, materialModel.getParticleIcon(ModelData.EMPTY));
                result.addAll(mapSurface(target, geometry, material, materialModel, renderType, context));
                continue;
            }
            List<BakedQuad> sources = findSourceQuads(materialModel, material, meshQuad.textureDirection(), random, renderType);
            if (sources.isEmpty()) {
                result.add(bakeFallback(meshQuad, materialModel.getParticleIcon(ModelData.EMPTY)));
            } else {
                for (BakedQuad source : sources) {
                    result.add(materialModel == originalModel && shape.meshKind() == TemplateShape.MeshKind.ROOF
                            ? bakeFallback(meshQuad, source.getSprite(), source.getTintIndex(), source.isShade(), source.hasAmbientOcclusion())
                            : bake(meshQuad, source));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<BakedQuad> buildReferenceQuads(BlockState material, BakedModel materialModel,
                                                @Nullable RenderType renderType, @Nullable Direction side, @Nullable ShapedMaterialContext context) {
        RandomSource random = RandomSource.create(42L);
        BakedModel referenceModel;
        BlockState referenceState = VanillaTemplateModels.referenceState(shape, templateState);
        if (itemModel) {
            ItemStack referenceItem = new ItemStack(VanillaTemplateModels.referenceBlock(shape));
            referenceModel = Minecraft.getInstance().getItemRenderer().getModel(referenceItem, null, null, 0);
        } else {
            referenceModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(referenceState);
        }

        ArrayList<BakedQuad> referenceQuads = new ArrayList<>();
        if (itemModel) {
            referenceQuads.addAll(referenceModel.getQuads(null, null, random, ModelData.EMPTY, null));
        } else {
            referenceQuads.addAll(referenceModel.getQuads(referenceState, side, random, ModelData.EMPTY, null));
        }

        ArrayList<BakedQuad> result = new ArrayList<>();
        List<BakedQuad> geometry = surfaceMaterial(material)
                ? ShapedMaterialContext.all(referenceModel, referenceState, ModelData.EMPTY, null) : List.of();
        for (BakedQuad reference : referenceQuads) {
            if (!geometry.isEmpty()) {
                result.addAll(mapSurface(reference, geometry, material, materialModel, renderType, context));
                continue;
            }
            List<BakedQuad> sources = findSourceQuads(
                    materialModel, material, reference.getDirection(), random, renderType);
            if (sources.isEmpty()) {
                result.add(remapReference(reference, null, materialModel.getParticleIcon(ModelData.EMPTY)));
            } else {
                for (BakedQuad source : sources) {
                    result.add(remapReference(reference, source, source.getSprite()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean surfaceMaterial(@Nullable BlockState material) {
        return material != null && (material.getBlock() instanceof net.minecraft.world.level.block.GrassBlock
                || com.stardew.craft.block.terrain.TerrainFaceConnections.rank(material) >= 0
                || material.getBlock() instanceof com.stardew.craft.block.terrain.TownPavingBlock
                || material.getBlock() instanceof com.stardew.craft.block.terrain.AsphaltRoadBlock
                || material.getBlock() instanceof com.stardew.craft.block.terrain.PalePavingBlock);
    }

    private static List<BakedQuad> mapSurface(BakedQuad target, List<BakedQuad> geometry, BlockState material,
            BakedModel model, @Nullable RenderType type, @Nullable ShapedMaterialContext context) {
        Direction face = target.getDirection();
        return ShapedMaterialQuads.map(target, geometry, material.getBlock() instanceof net.minecraft.world.level.block.GrassBlock,
                context == null ? List.of() : context.breaks(face, true), context == null ? List.of() : context.breaks(face, false),
                (patch, direction) -> context == null
                        ? com.stardew.craft.client.model.terrain.ShapedTerrainModels.nativeFace(model, material, direction, type)
                        : context.sources(model, material, patch, direction, type));
    }

    private static BakedQuad remapReference(BakedQuad reference, @Nullable BakedQuad source,
                                            TextureAtlasSprite sprite) {
        int[] data = Arrays.copyOf(reference.getVertices(), reference.getVertices().length);
        float[] targetBounds = faceBounds(reference);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            Vector3f position = new Vector3f(
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 1]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 2]));
            float[] point = faceCoordinates(reference.getDirection(), position);
            float[] uv = source == null
                    ? spriteUv(sprite, point[0], point[1])
                    : sourceUv(source, point, targetBounds);
            data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(uv[0]);
            data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(uv[1]);
        }
        return new BakedQuad(data, source == null ? -1 : source.getTintIndex(),
                reference.getDirection(), sprite, reference.isShade() && (source == null || source.isShade()),
                reference.hasAmbientOcclusion() && (source == null || source.hasAmbientOcclusion()));
    }

    private static List<BakedQuad> findSourceQuads(BakedModel model, BlockState material, Direction direction,
                                                   RandomSource random, @Nullable RenderType renderType) {
        random.setSeed(42L);
        List<BakedQuad> directed = model.getQuads(material, direction, random, ModelData.EMPTY, renderType);
        if (!directed.isEmpty()) {
            return representativeSurfaces(directed);
        }
        random.setSeed(42L);
        ArrayList<BakedQuad> matching = new ArrayList<>();
        for (BakedQuad quad : model.getQuads(material, null, random, ModelData.EMPTY, renderType)) {
            if (quad.getDirection() == direction) {
                matching.add(quad);
            }
        }
        return representativeSurfaces(matching);
    }

    /**
     * Multipart and shaped block models can expose several small faces in one direction. A building template
     * borrows a material surface, not the source block's geometry, so stamping every small face over the whole
     * template creates overlapping, unrelated textures. Keep the largest coincident surface and its overlays.
     */
    private static List<BakedQuad> representativeSurfaces(List<BakedQuad> candidates) {
        if (candidates.size() < 2) return candidates;
        BakedQuad largest = candidates.stream().max(java.util.Comparator.comparingDouble(TemplateBakedModel::faceArea))
                .orElse(com.stardew.craft.port.PortJava.getFirst(candidates));
        float[] bounds = faceBounds(largest);
        java.util.LinkedHashMap<SurfaceKey, BakedQuad> result = new java.util.LinkedHashMap<>();
        for (BakedQuad candidate : candidates) {
            if (sameBounds(bounds, faceBounds(candidate))) {
                result.putIfAbsent(new SurfaceKey(candidate.getSprite().contents().name(), candidate.getTintIndex(),
                        candidate.isShade(), candidate.hasAmbientOcclusion()), candidate);
            }
        }
        return result.isEmpty() ? List.of(largest) : List.copyOf(result.values());
    }

    private static BakedQuad bake(TemplateMesh.MeshQuad quad, BakedQuad source) {
        int[] data = Arrays.copyOf(source.getVertices(), source.getVertices().length);
        Vector3f normal = normal(quad);
        int packedNormal = packNormal(normal);
        float[] targetBounds = faceBounds(quad);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            Vector3f position = quad.vertices().get(vertex);
            data[offset + IQuadTransformer.POSITION] = Float.floatToRawIntBits(position.x);
            data[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(position.y);
            data[offset + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(position.z);

            TemplateMesh.TexturePoint texturePoint = quad.texturePoint(vertex);
            float[] uv = texturePoint == null
                    ? sourceUv(source, faceCoordinates(quad.direction(), position), targetBounds)
                    : sourceUv(source, new float[]{texturePoint.u(), texturePoint.v()}, targetBounds);
            data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(uv[0]);
            data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(uv[1]);
            data[offset + IQuadTransformer.NORMAL] = packedNormal;
        }
        return new BakedQuad(data, source.getTintIndex(),
                Direction.getNearest(normal.x, normal.y, normal.z), source.getSprite(),
                source.isShade(), source.hasAmbientOcclusion());
    }

    private static BakedQuad bakeFallback(TemplateMesh.MeshQuad quad, TextureAtlasSprite sprite) {
        return bakeFallback(quad, sprite, -1, true, true);
    }

    private static BakedQuad bakeFallback(TemplateMesh.MeshQuad quad, TextureAtlasSprite sprite, int tintIndex,
                                          boolean shade, boolean ambientOcclusion) {
        // PORT(1.20.1): Forge's Buffered baker emits the quad on the fourth endVertex() (NeoForge: bakeQuad()).
        QuadBakingVertexConsumer.Buffered baker = new QuadBakingVertexConsumer.Buffered();
        Vector3f normal = normal(quad);
        baker.setDirection(Direction.getNearest(normal.x, normal.y, normal.z));
        baker.setSprite(sprite);
        baker.setTintIndex(tintIndex);
        baker.setShade(shade);
        baker.setHasAmbientOcclusion(ambientOcclusion);

        for (int vertexIndex = 0; vertexIndex < quad.vertices().size(); vertexIndex++) {
            Vector3f vertex = quad.vertices().get(vertexIndex);
            TemplateMesh.TexturePoint texturePoint = quad.texturePoint(vertexIndex);
            float[] uv = texturePoint == null
                    ? fallbackUv(quad.direction(), vertex, sprite)
                    : spriteUv(sprite, texturePoint.u(), texturePoint.v());
            PortVertex.of(baker).addVertex(vertex.x, vertex.y, vertex.z)
                    .setUv(uv[0], uv[1])
                    .setColor(-1)
                    .setNormal(normal.x, normal.y, normal.z).endVertex();
        }
        return baker.getQuad();
    }

    private static float[] faceCoordinates(Direction direction, Vector3f vertex) {
        return switch (direction) {
            case NORTH -> new float[]{1F - vertex.x, 1F - vertex.y};
            case SOUTH -> new float[]{vertex.x, 1F - vertex.y};
            case WEST -> new float[]{vertex.z, 1F - vertex.y};
            case EAST -> new float[]{1F - vertex.z, 1F - vertex.y};
            case UP -> new float[]{vertex.x, vertex.z};
            case DOWN -> new float[]{vertex.x, 1F - vertex.z};
        };
    }

    private static float[] fallbackUv(Direction direction, Vector3f vertex, TextureAtlasSprite sprite) {
        float[] point = faceCoordinates(direction, vertex);
        return spriteUv(sprite, point[0], point[1]);
    }

    private static float[] sourceUv(BakedQuad source, float[] point, float[] targetBounds) {
        int[] data = source.getVertices();
        float[][] points = new float[4][2];
        float[][] texture = new float[4][2];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            Vector3f position = new Vector3f(
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 1]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 2]));
            points[vertex] = faceCoordinates(source.getDirection(), position);
            texture[vertex][0] = com.stardew.craft.port.PortSprites.getUOffset(source.getSprite(), 
                    Float.intBitsToFloat(data[offset + IQuadTransformer.UV0]));
            texture[vertex][1] = com.stardew.craft.port.PortSprites.getVOffset(source.getSprite(), 
                    Float.intBitsToFloat(data[offset + IQuadTransformer.UV0 + 1]));
        }

        float[] sourceBounds = bounds(points);
        boolean contained = contains(sourceBounds, targetBounds);
        if (!contained) {
            normalize(points, sourceBounds);
            point = normalized(point, targetBounds);
            float[] textureBounds = bounds(texture);
            normalize(texture, textureBounds);
        }
        int[] triangle = nonDegenerateTriangle(points);
        if (triangle == null) {
            return spriteUv(source.getSprite(), point[0], point[1]);
        }
        float[] weights = barycentric(points[triangle[0]], points[triangle[1]], points[triangle[2]], point);
        float u = weights[0] * texture[triangle[0]][0] + weights[1] * texture[triangle[1]][0]
                + weights[2] * texture[triangle[2]][0];
        float v = weights[0] * texture[triangle[0]][1] + weights[1] * texture[triangle[1]][1]
                + weights[2] * texture[triangle[2]][1];
        return contained
                ? new float[]{com.stardew.craft.port.PortSprites.getU(source.getSprite(), Mth.clamp(u, 0F, 1F)),
                        com.stardew.craft.port.PortSprites.getV(source.getSprite(), Mth.clamp(v, 0F, 1F))}
                : spriteUv(source.getSprite(), u, v);
    }

    private static float[] spriteUv(TextureAtlasSprite sprite, float u, float v) {
        float shrink = sprite.uvShrinkRatio();
        float safeU = Mth.lerp(shrink, Mth.clamp(u, 0F, 1F), 0.5F);
        float safeV = Mth.lerp(shrink, Mth.clamp(v, 0F, 1F), 0.5F);
        return new float[]{com.stardew.craft.port.PortSprites.getU(sprite, safeU), com.stardew.craft.port.PortSprites.getV(sprite, safeV)};
    }

    private static float[] faceBounds(BakedQuad quad) {
        int[] data = quad.getVertices();
        float[][] points = new float[4][2];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            points[vertex] = faceCoordinates(quad.getDirection(), new Vector3f(
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 1]),
                    Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 2])));
        }
        return bounds(points);
    }

    private static float[] faceBounds(TemplateMesh.MeshQuad quad) {
        float[][] points = new float[quad.vertices().size()][2];
        for (int vertex = 0; vertex < quad.vertices().size(); vertex++) {
            TemplateMesh.TexturePoint texturePoint = quad.texturePoint(vertex);
            points[vertex] = texturePoint == null
                    ? faceCoordinates(quad.direction(), quad.vertices().get(vertex))
                    : new float[]{texturePoint.u(), texturePoint.v()};
        }
        return bounds(points);
    }

    private static float[] bounds(float[][] points) {
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (float[] point : points) {
            minU = Math.min(minU, point[0]);
            minV = Math.min(minV, point[1]);
            maxU = Math.max(maxU, point[0]);
            maxV = Math.max(maxV, point[1]);
        }
        return new float[]{minU, minV, maxU, maxV};
    }

    private static boolean contains(float[] outer, float[] inner) {
        float epsilon = 1.0E-5F;
        return inner[0] >= outer[0] - epsilon && inner[1] >= outer[1] - epsilon
                && inner[2] <= outer[2] + epsilon && inner[3] <= outer[3] + epsilon;
    }

    private static boolean sameBounds(float[] first, float[] second) {
        float epsilon = 1.0E-5F;
        for (int index = 0; index < first.length; index++) {
            if (Math.abs(first[index] - second[index]) > epsilon) return false;
        }
        return true;
    }

    private static float faceArea(BakedQuad quad) {
        float[] bounds = faceBounds(quad);
        return Math.max(0F, bounds[2] - bounds[0]) * Math.max(0F, bounds[3] - bounds[1]);
    }

    private static void normalize(float[][] points, float[] bounds) {
        float width = bounds[2] - bounds[0];
        float height = bounds[3] - bounds[1];
        if (width <= 1.0E-6F || height <= 1.0E-6F) return;
        for (float[] point : points) {
            point[0] = (point[0] - bounds[0]) / width;
            point[1] = (point[1] - bounds[1]) / height;
        }
    }

    private static float[] normalized(float[] point, float[] bounds) {
        float width = bounds[2] - bounds[0];
        float height = bounds[3] - bounds[1];
        return new float[]{width <= 1.0E-6F ? 0.5F : (point[0] - bounds[0]) / width,
                height <= 1.0E-6F ? 0.5F : (point[1] - bounds[1]) / height};
    }

    @Nullable
    private static int[] nonDegenerateTriangle(float[][] points) {
        int[][] candidates = {{0, 1, 2}, {0, 1, 3}, {0, 2, 3}, {1, 2, 3}};
        for (int[] candidate : candidates) {
            float[] a = points[candidate[0]];
            float[] b = points[candidate[1]];
            float[] c = points[candidate[2]];
            float determinant = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
            if (Math.abs(determinant) > 1.0E-6F) {
                return candidate;
            }
        }
        return null;
    }

    private static float[] barycentric(float[] a, float[] b, float[] c, float[] point) {
        float determinant = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        float first = ((b[1] - c[1]) * (point[0] - c[0]) + (c[0] - b[0]) * (point[1] - c[1])) / determinant;
        float second = ((c[1] - a[1]) * (point[0] - c[0]) + (a[0] - c[0]) * (point[1] - c[1])) / determinant;
        return new float[]{first, second, 1F - first - second};
    }

    private static Vector3f normal(TemplateMesh.MeshQuad quad) {
        Vector3f normal = new Vector3f();
        List<Vector3f> vertices = quad.vertices();
        for (int index = 0; index < vertices.size(); index++) {
            Vector3f current = vertices.get(index);
            Vector3f next = vertices.get((index + 1) % vertices.size());
            normal.x += (current.y - next.y) * (current.z + next.z);
            normal.y += (current.z - next.z) * (current.x + next.x);
            normal.z += (current.x - next.x) * (current.y + next.y);
        }
        if (normal.lengthSquared() < 1.0E-6F) {
            Direction direction = quad.direction();
            return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }
        return normal.normalize();
    }

    private static int packNormal(Vector3f normal) {
        return (Math.round(Mth.clamp(normal.x, -1F, 1F) * 127F) & 0xFF)
                | ((Math.round(Mth.clamp(normal.y, -1F, 1F) * 127F) & 0xFF) << 8)
                | ((Math.round(Mth.clamp(normal.z, -1F, 1F) * 127F) & 0xFF) << 16);
    }

    private TemplateMesh mesh() {
        if (mesh == null) mesh = TemplateMesh.create(shape, templateState, itemModel);
        return mesh;
    }

    private BlockState fillMaterial(ModelData data) {
        if (!shape.isComposite()) return null;
        BlockState fill=data.get(TemplateBlockEntity.FILL_MATERIAL_PROPERTY);
        return fill==null && shape.isWindow()?com.stardew.craft.block.ModBlocks.PALE_BLUE_WINDOW_GLASS.get().defaultBlockState():fill;
    }

    private boolean usesStudy(ModelData data) {
        if (shape == TemplateShape.BALCONY_RAILING || shape == TemplateShape.GRID_WINDOW) return data.get(TemplateBlockEntity.MATERIAL_PROPERTY) == null;
        if (shape.meshKind() != TemplateShape.MeshKind.ROOF) return false;
        BlockState material = data.get(TemplateBlockEntity.MATERIAL_PROPERTY);
        // Applying the public tile material retains the authored continuous roof UVs.
        return material == null || material.is(com.stardew.craft.block.ModBlocks.GRAY_VIOLET_ROOF_TILES.get());
    }

    private BlockState material(ModelData data) {
        BlockState material = data.get(TemplateBlockEntity.MATERIAL_PROPERTY);
        if (material == null && shape.isWindow()) return com.stardew.craft.block.ModBlocks.BLUE_GRAY_TIMBER.get().defaultBlockState();
        return TemplateMaterials.orientTimber(material == null ? TemplateMaterials.defaultMaterial() : material, shape, templateState);
    }

    private record CacheKey(BlockState material, @Nullable BlockState fill, @Nullable RenderType renderType, @Nullable Direction side, int season, int phase, int edges, int hiddenSections, boolean study, @Nullable ShapedMaterialContext context) {
    }

    private record SurfaceKey(net.minecraft.resources.ResourceLocation sprite, int tint, boolean shade,
                              boolean ambientOcclusion) {
    }
}
