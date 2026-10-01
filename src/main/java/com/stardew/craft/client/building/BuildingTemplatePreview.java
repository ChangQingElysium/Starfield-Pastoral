package com.stardew.craft.client.building;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/** Uses the actual shipped template, registered block models and saved appearance data. */
public final class BuildingTemplatePreview {
    private record Part(BlockPos pos, BlockState state, ModelData data, boolean floorOnly) {}
    private static List<Part> parts = List.of();
    private static java.util.List<net.minecraft.world.entity.Entity> decorations=List.of();
    private static boolean centered;
    private static java.util.Map<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover> covers=java.util.Map.of();
    private static BlockPos size = BlockPos.ZERO, anchor = BlockPos.ZERO;
    private record Template(List<Part> parts, BlockPos size, BlockPos anchor, CompoundTag bounds,java.util.List<net.minecraft.world.entity.Entity> decorations, java.util.Map<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover> covers) {}
    private static final java.util.Map<String, Template> templates = new java.util.HashMap<>();
    public static boolean select(net.minecraft.resources.ResourceLocation family, int tier) {
        Template template = templates.get(family + ":" + tier); centered = false;
        if (template == null) { parts = List.of(); decorations=List.of(); return false; }
        parts = template.parts(); size = template.size(); anchor = template.anchor(); decorations=template.decorations(); covers=template.covers(); return true;
    }
    public static void selectMove(java.util.UUID id) {
        var template=templates.get(id.toString());
        if(template==null){parts=List.of();decorations=List.of();return;}
        parts=template.parts();size=template.size();anchor=template.anchor();decorations=template.decorations(); covers=template.covers();centered=template.bounds().getBoolean("Centered");
    }
    public static com.stardew.craft.network.payload.BuildingPreviewPayload describe(net.minecraft.resources.ResourceLocation family, int tier,
            BlockPos worldAnchor, net.minecraft.core.Direction facing, int sequence) {
        Template template = templates.get(family + ":" + tier); centered = false;
        return describe(template,family,tier,worldAnchor,facing,sequence);
    }
    public static com.stardew.craft.network.payload.BuildingPreviewPayload describeMove(java.util.UUID id,net.minecraft.resources.ResourceLocation family,int tier,BlockPos worldAnchor,net.minecraft.core.Direction facing,int sequence){
        return describe(templates.get(id.toString()),family,tier,worldAnchor,facing,sequence);
    }
    private static com.stardew.craft.network.payload.BuildingPreviewPayload describe(Template template,net.minecraft.resources.ResourceLocation family,int tier,BlockPos worldAnchor,net.minecraft.core.Direction facing,int sequence){
        if (template == null) return null;
        var tag = template.bounds(); var rotation = com.stardew.craft.building.runtime.PrefabDefinitions.rotation(facing);
        var claim = com.stardew.craft.building.runtime.PrefabDefinitions.transform(new com.stardew.craft.building.runtime.BuildingBounds(
                BlockPos.of(tag.getLong("ReservationMin")), BlockPos.of(tag.getLong("ReservationMax"))), worldAnchor, rotation);
        var bounds = com.stardew.craft.building.runtime.PrefabDefinitions.transform(new com.stardew.craft.building.runtime.BuildingBounds(
                BlockPos.of(tag.getLong("BoundsMin")), BlockPos.of(tag.getLong("BoundsMax"))), worldAnchor, rotation);
        if(tag.getBoolean("Centered")) {
            claim=new com.stardew.craft.building.runtime.BuildingBounds(BlockPos.of(tag.getLong("ReservationMin")).offset(worldAnchor),BlockPos.of(tag.getLong("ReservationMax")).offset(worldAnchor));
            bounds=claim;
        }
        var manager = tag.getBoolean("Centered")?worldAnchor:worldAnchor.offset(com.stardew.craft.building.runtime.PrefabDefinitions.rotateCell(BlockPos.of(tag.getLong("ManagerRelative")), rotation));
        return new com.stardew.craft.network.payload.BuildingPreviewPayload(worldAnchor, facing, false, sequence, "checking", worldAnchor,
                claim.min(), claim.maxExclusive(), bounds.min(), bounds.maxExclusive(), manager, family, tier);
    }
    private BuildingTemplatePreview() {}
    public static void clear() { parts = List.of(); decorations=List.of(); templates.clear(); }
    public static void load(CompoundTag template) {
        if (template == null) { clear(); return; }
        var mc = Minecraft.getInstance();
        int[] dimensions = template.getIntArray("Size"), origin = template.getIntArray("Anchor");
        if (dimensions.length != 3 || origin.length != 3) { clear(); return; }
        size = new BlockPos(dimensions[0], dimensions[1], dimensions[2]);
        anchor = new BlockPos(origin[0], origin[1], origin[2]);
        var covers=new java.util.HashMap<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover>();
        for(var raw:template.getList("Floors",10)){var floor=(CompoundTag)raw;covers.put(BlockPos.of(floor.getLong("Pos")),new com.stardew.craft.floor.SurfaceFloorData.Cover(com.stardew.craft.floor.SurfaceFloorType.byId(floor.getString("Type")),floor.getInt("Variant")));}
        List<Part> loaded = new ArrayList<>();
        for (var entry : template.getList("Blocks", 10)) {
            var tag = (CompoundTag) entry; var xyz = tag.getIntArray("Pos");
            if (xyz.length != 3) continue;
            BlockPos pos = new BlockPos(xyz[0], xyz[1], xyz[2]);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("State"));
            ModelData data = ModelData.EMPTY;
            if (tag.contains("Appearance", 10) && state.getBlock() instanceof EntityBlock block) {
                var entity = block.newBlockEntity(pos, state);
                if (entity != null && mc.level != null) {
                    entity.setLevel(mc.level);
                    entity.load(tag.getCompound("Appearance"));
                    data = entity.getModelData();
                }
            }
            loaded.add(new Part(pos, state, data, tag.getBoolean("FloorOnly")));
        }
        var props=new ArrayList<net.minecraft.world.entity.Entity>();
        if(mc.level!=null)for(var raw:template.getList("Decorations",10)){var entity=net.minecraft.world.entity.EntityType.loadEntityRecursive((CompoundTag)raw,mc.level,value->value);if(entity!=null)props.add(entity);}
        decorations=List.copyOf(props);
        parts = List.copyOf(loaded);
        templates.put(template.hasUUID("Moving") ? template.getUUID("Moving").toString() : template.getString("Family") + ":" + template.getInt("Tier"), new Template(parts, size, anchor, template.copy(),decorations,java.util.Map.copyOf(covers)));
        BuildingTemplatePreview.covers=java.util.Map.copyOf(covers);
    }
    public static boolean drawCatalog(GuiGraphics graphics, int x, int y, int width, int height) {
        if (parts.isEmpty()) return false;
        graphics.flush();
        PoseStack pose = graphics.pose();
        float scale = Math.min(width / (float) (size.getX() + size.getZ()), height / (size.getY() + 0.5f * (size.getX() + size.getZ()))) * 0.85f;
        pose.pushPose(); pose.translate(x + width / 2f, y + height / 2f + size.getY() * scale * 0.3f, 180);
        pose.scale(scale, -scale, scale); pose.mulPose(Axis.XP.rotationDegrees(25)); pose.mulPose(Axis.YP.rotationDegrees(135));
        pose.translate(-size.getX() / 2f, 0, -size.getZ() / 2f);
        draw(pose, Rotation.NONE, false, BlockPos.ZERO);
        pose.popPose();
        return true;
    }
    public static void drawWorld(PoseStack pose, BlockPos worldAnchor, Rotation rotation, boolean valid) {
        if (parts.isEmpty()) return;
        pose.pushPose(); pose.translate(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ());
        RenderSystem.setShaderColor(valid ? 0.65f : 1f, valid ? 1f : 0.45f, valid ? 1f : 0.4f, 0.35f);
        RenderSystem.enablePolygonOffset(); RenderSystem.polygonOffset(-1,-1);
        try { draw(pose, rotation, true, worldAnchor); }
        finally { RenderSystem.polygonOffset(0,0); RenderSystem.disablePolygonOffset(); RenderSystem.setShaderColor(1,1,1,1);pose.popPose(); }
    }
    private static BlockPos relative(BlockPos pos, Rotation rotation) {
        return centered ? pos.subtract(anchor).rotate(rotation) : com.stardew.craft.building.runtime.PrefabDefinitions.rotateCell(pos.subtract(anchor),rotation);
    }
    private static void draw(PoseStack pose, Rotation rotation, boolean ghost, BlockPos worldAnchor) {
        var mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType renderType = ghost ? RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                : RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        var worldCovers = new java.util.HashMap<BlockPos,com.stardew.craft.floor.SurfaceFloorData.Cover>();
        covers.forEach((pos,cover) -> worldCovers.put((ghost ? relative(pos,rotation) : pos).offset(worldAnchor),cover));
        for (var part : parts) {
            BlockPos pos = ghost ? relative(part.pos(),rotation) : part.pos();
            pose.pushPose();
            // The preview reuses the production surface-floor compositor.  Its
            // floor-only quad is coplanar with the real floor while moving a
            // building, which causes visible z-fighting.  Lift only that ghost
            // layer by a tiny amount; structure blocks retain exact placement.
            pose.translate(pos.getX(), pos.getY() + (ghost && part.floorOnly() ? 0.0025 : 0), pos.getZ());
            var state = part.state().rotate(rotation); var data = part.data();
            if (!worldCovers.isEmpty() && mc.level != null) data = com.stardew.craft.client.model.terrain.SurfaceFloorModels.previewData(
                    mc.getBlockRenderer().getBlockModel(state),mc.level,pos.offset(worldAnchor),state,data,worldCovers,part.floorOnly());
            if (state.is(com.stardew.craft.block.ModBlocks.FISH_NET.get())) {
                if (state.getValue(com.stardew.craft.block.decor.MapDecorStaticBlock.PART)
                        == com.stardew.craft.block.decor.MapDecorStaticBlock.Part.MAIN) {
                    var net = new com.stardew.craft.blockentity.FishNetBlockEntity(pos.offset(worldAnchor),state);
                    if (mc.level != null) net.setLevel(mc.level);
                    mc.getBlockEntityRenderDispatcher().renderItem(net,pose,buffer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                }
            } else if (state.is(com.stardew.craft.block.ModBlocks.FISH_POND_WATER.get())) {
                // LiquidBlock's normal single-block path is invisible. Its preview model
                // supplies just the animated surface, without running fluid simulation.
                boolean covered = parts.stream().anyMatch(other -> other.pos().equals(part.pos().above())
                        && other.state().is(com.stardew.craft.block.ModBlocks.FISH_POND_WATER.get()));
                if (!covered) {
                    pose.translate(0,state.getFluidState().getOwnHeight()-1,0);
                    int tint = com.stardew.craft.fluid.ModFluids.DEFAULT_POND_WATER_COLOR;
                    mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffer.getBuffer(renderType),state,
                            mc.getBlockRenderer().getBlockModel(state),((tint >> 16) & 255) / 255F,
                            ((tint >> 8) & 255) / 255F,(tint & 255) / 255F,
                            LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,data,renderType);
                }
            } else {
                mc.getBlockRenderer().renderSingleBlock(state, pose, buffer,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, data, renderType);
            }
            pose.popPose();
        }
        buffer.endBatch(renderType);
        if(ghost && !decorations.isEmpty()) {
            pose.pushPose();
            if (centered) pose.translate(.5, 0, .5);
            pose.mulPose(Axis.YP.rotationDegrees(switch(rotation){case NONE->0;case CLOCKWISE_90->-90;case CLOCKWISE_180->-180;case COUNTERCLOCKWISE_90->90;}));
            if (centered) pose.translate(-.5, 0, -.5);
            for(var entity:decorations)mc.getEntityRenderDispatcher().render(entity,entity.getX(),entity.getY(),entity.getZ(),entity.getYRot(),0,pose,buffer,LightTexture.FULL_BRIGHT);
            pose.popPose();buffer.endBatch();
        }
    }
}
