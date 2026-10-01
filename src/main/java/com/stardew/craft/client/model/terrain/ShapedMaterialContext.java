package com.stardew.craft.client.model.terrain;

import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.terrain.TerrainShapeBlock;
import com.stardew.craft.templates.TemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Immutable nearby surfaces. Connections use actual contact points, including half-height tops and stair corners. */
public record ShapedMaterialContext(BlockPos origin,List<Cell> cells) {
    public static final ModelProperty<ShapedMaterialContext> PROPERTY=new ModelProperty<>();
    private record Surface(BakedQuad quad,BlockState material) {}
    public record Cell(BlockPos offset,BlockState state,List<AABB> collision,List<Surface> surfaces) {}

    public static ShapedMaterialContext capture(BlockAndTintGetter level,BlockPos pos){
        List<Cell> cells=new ArrayList<>();var renderer=Minecraft.getInstance().getBlockRenderer();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            BlockPos offset=new BlockPos(x,y,z),at=pos.offset(offset);BlockState actual=level.getBlockState(at);
            BlockState material=TerrainShapeBlock.material(actual),fill=null;ModelData data=ModelData.EMPTY;
            if(level.getBlockEntity(at) instanceof TemplateBlockEntity be){
                data=be.getModelData();if(be.material()!=null)material=be.material();fill=data.get(TemplateBlockEntity.FILL_MATERIAL_PROPERTY);
            }
            List<Surface> surfaces=new ArrayList<>();
            if(!actual.isAir()&&(actual.getBlock() instanceof TemplateBlock||actual.getBlock() instanceof TerrainShapeBlock
                    ||actual.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                    ||actual.getBlock() instanceof net.minecraft.world.level.block.StairBlock||actual.isCollisionShapeFullBlock(level,at)
                    ||TerrainFaceConnections.rank(actual)>=0)){
                List<BakedQuad> quads=all(renderer.getBlockModel(actual),actual,data,null);
                var fillSprites=fill==null?List.of():all(renderer.getBlockModel(fill),fill,ModelData.EMPTY,null).stream().map(BakedQuad::getSprite).distinct().toList();
                for(BakedQuad quad:quads)surfaces.add(new Surface(quad,fill!=null&&fillSprites.contains(quad.getSprite())?fill:material));
            }
            cells.add(new Cell(offset,actual,List.copyOf(actual.getCollisionShape(level,at).toAabbs()),List.copyOf(surfaces)));
        }
        return new ShapedMaterialContext(pos.immutable(),List.copyOf(cells));
    }

    public static List<BakedQuad> all(BakedModel model,BlockState state,ModelData data,@Nullable RenderType type){
        List<BakedQuad> out=new ArrayList<>();RandomSource r=RandomSource.create(42);
        out.addAll(model.getQuads(state,null,r,data,type));
        for(Direction face:Direction.values()){r.setSeed(42);out.addAll(model.getQuads(state,face,r,data,type));}
        return out;
    }

    public List<Double> breaks(Direction face,boolean u){
        TreeSet<Double> values=new TreeSet<>();var frame=TerrainFaceConnections.frame(face);
        for(Cell cell:cells)for(Surface s:cell.surfaces){
            int[] d=s.quad.getVertices();for(int at=0;at<d.length;at+=d.length/4){
                Vec3 p=new Vec3(Float.intBitsToFloat(d[at]),Float.intBitsToFloat(d[at+1]),Float.intBitsToFloat(d[at+2])).add(Vec3.atLowerCornerOf(cell.offset));
                double v=u?frame.x(p):frame.y(p);if(v>1e-6&&v<1-1e-6)values.add(v);
            }
        }
        return List.copyOf(values);
    }

    public List<BakedQuad> sources(BakedModel model,BlockState material,ShapedMaterialQuads.Patch patch,
            Direction face,@Nullable RenderType type){
        Vec3 point=ShapedMaterialQuads.point(patch.quad(),patch.u(),patch.v());
        ModelData data=occluded(point,face)?ModelData.EMPTY:model.getModelData(new View(material,face,point),origin,material,ModelData.EMPTY);
        RandomSource r=RandomSource.create(42);List<BakedQuad> out=model.getQuads(material,face,r,data,type);
        if(!out.isEmpty())return out;
        return model.getQuads(material,null,r,data,type).stream().filter(q->q.getDirection()==face).toList();
    }

    private Cell cell(BlockPos offset){for(Cell c:cells)if(c.offset.equals(offset))return c;return null;}
    private boolean occluded(Vec3 point,Direction face){
        Vec3 p=point.add(Vec3.atLowerCornerOf(face.getNormal()).scale(1e-4));BlockPos at=BlockPos.containing(p);Cell c=cell(at);
        if(c==null)return false;Vec3 local=p.subtract(Vec3.atLowerCornerOf(at));
        if(!c.state.getFluidState().isEmpty())return true;
        return c.collision.stream().anyMatch(b->b.contains(local));
    }

    private final class View implements BlockAndTintGetter {
        private final BlockState material;private final Direction face;private final Vec3 point;
        View(BlockState material,Direction face,Vec3 point){this.material=material;this.face=face;this.point=point;}
        @Override public BlockState getBlockState(BlockPos at){
            BlockPos offset=at.subtract(origin);if(offset.equals(BlockPos.ZERO))return material;
            Cell cell=cell(offset);if(cell==null)return Blocks.AIR.defaultBlockState();
            int[] steps={offset.getX(),offset.getY(),offset.getZ()};int axis=face.getAxis()==Direction.Axis.X?0:face.getAxis()==Direction.Axis.Y?1:2;
            int tangents=0;Direction folded=null;double[] probe={point.x,point.y,point.z};
            for(int i=0;i<3;i++)if(i!=axis&&steps[i]!=0){
                tangents++;probe[i]=steps[i]>0?1:0;
                folded=Direction.fromAxisAndDirection(Direction.Axis.values()[i],steps[i]>0?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE);
            }
            // The virtual material is exposed only if the real sampled surface was exposed.
            if(tangents==0)return Blocks.AIR.defaultBlockState();
            Direction donorFace=steps[axis]==0?face:folded;
            if(steps[axis]!=0&&(tangents!=1||steps[axis]!=face.getStepX()+face.getStepY()+face.getStepZ()))return Blocks.AIR.defaultBlockState();
            Vec3 edge=new Vec3(probe[0],probe[1],probe[2]),local=edge.subtract(Vec3.atLowerCornerOf(offset));
            Cell own=cell(BlockPos.ZERO);
            if(own==null||own.surfaces.stream().noneMatch(s->s.quad.getDirection()==face&&ShapedMaterialQuads.contains(s.quad,edge)))return Blocks.AIR.defaultBlockState();
            for(Surface s:cell.surfaces)if(s.quad.getDirection()==donorFace&&ShapedMaterialQuads.contains(s.quad,local)) {
                // Probe inside the donor face. At an exact floor/wall edge the
                // neighbouring wall owns the boundary coordinate, not the floor.
                int[] data=s.quad.getVertices();Vec3 center=Vec3.ZERO;
                for(int i=0;i<4;i++){int vertex=i*data.length/4;center=center.add(
                        Float.intBitsToFloat(data[vertex]),Float.intBitsToFloat(data[vertex+1]),Float.intBitsToFloat(data[vertex+2]));}
                Vec3 inside=local.lerp(center.scale(.25),1e-4).add(Vec3.atLowerCornerOf(offset));
                if(!occluded(inside,donorFace))return s.material;
            }
            return Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return null;}
        @Override public int getHeight(){return Minecraft.getInstance().level.getHeight();}
        @Override public int getMinBuildHeight(){return Minecraft.getInstance().level.getMinBuildHeight();}
        @Override public float getShade(Direction direction,boolean shade){return Minecraft.getInstance().level.getShade(direction,shade);}
        @Override public LevelLightEngine getLightEngine(){return Minecraft.getInstance().level.getLightEngine();}
        @Override public int getBlockTint(BlockPos pos,ColorResolver resolver){return Minecraft.getInstance().level.getBlockTint(pos,resolver);}
    }
}
