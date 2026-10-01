package com.stardew.craft.client.light;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineLampBlock;
import com.stardew.craft.blockentity.MineLampBlockEntity;
import com.stardew.craft.blockentity.SkullLobbyLightBlockEntity;
import com.stardew.craft.block.mine.SkullLobbyAssemblyBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import java.util.*;

/** Cached, occluded RGB irradiance samples. Published snapshots never access the live world from mesh workers. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ColoredLightEngine {
    public static final int RADIUS = 6;
    private static final float SATURATION = .22F;
    private static volatile Map<Long, Integer> samples = Map.of();
    private static final Map<BlockPos, Source> sources = new HashMap<>();
    private static final Map<BlockPos, Map<Long, Integer>> fields = new HashMap<>();
    private static final Set<BlockPos> pending = new LinkedHashSet<>();
    private static ClientLevel level;
    private static boolean nativeLighting;
    private static long scanTick;
    private ColoredLightEngine() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level) {
            level = mc.level;
            sources.clear(); fields.clear(); pending.clear(); samples = Map.of(); scanTick = Long.MIN_VALUE;
        }
        if (level == null || mc.player == null) return;
        boolean nativeNow = ComplementaryLampBridge.nativeLightingActive();
        if (nativeNow != nativeLighting) {
            nativeLighting = nativeNow;
            if (nativeNow) { fields.keySet().removeIf(p -> sources.containsKey(p) && !sources.get(p).dynamic); publish(mergeFields()); }
            else pending.addAll(sources.keySet());
        }
        if (scanTick == Long.MIN_VALUE || level.getGameTime() - scanTick >= 5) {
            scanTick = level.getGameTime();
            Map<BlockPos, Source> next = new HashMap<>();
            int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
            for (int x=cx-4;x<=cx+4;x++) for (int z=cz-4;z<=cz+4;z++) {
                var chunk = level.getChunk(x,z,ChunkStatus.FULL,false);
                if (!(chunk instanceof LevelChunk loaded)) continue;
                for (var entity : loaded.getBlockEntities().values()) {
                    if (entity.isRemoved()) continue;
                    var state = entity.getBlockState();
                    Source source;
                    if (entity instanceof MineLampBlockEntity) {
                        if (!state.getValue(MineLampBlock.LIT)) continue;
                        var theme = state.getValue(MineLampBlock.THEME);
                        var facing = state.getValue(MineLampBlock.FACING);
                        double height = theme == MineLampBlock.Theme.FROST_DARK || theme == MineLampBlock.Theme.DESERT_DARK ? 11.5/16 : theme == MineLampBlock.Theme.DESERT ? .5 : 7.0/16;
                        double back = theme == MineLampBlock.Theme.DESERT ? 3.0/16 : 1.0/16;
                        source = new Source(theme.lightColor(), new Vec3(.5-facing.getStepX()*back, height, .5-facing.getStepZ()*back));
                    } else if (entity instanceof SkullLobbyLightBlockEntity && state.getBlock() instanceof SkullLobbyAssemblyBlock block) {
                        if (SkullLobbyAssemblyBlock.emission(state) == 0) continue;
                        source = new Source(block.lightColor(), block.lightOffset(state));
                    } else continue;
                    BlockPos pos = entity.getBlockPos();
                    if (pos.distSqr(mc.player.blockPosition()) > 64*64) continue;
                    next.put(pos,source);
                    if (!nativeLighting && !source.equals(sources.get(pos))) pending.add(pos);
                }
            }
            // Nearby native ghosts are small moving emitters. Quantize to quarter blocks and cap work.
            var ghosts=new ArrayList<com.stardew.craft.entity.monster.MineGhostEntity>();
            for(var entity:level.entitiesForRendering())if(entity instanceof com.stardew.craft.entity.monster.MineGhostEntity ghost
                    && ghost.isAlive() && ghost.distanceToSqr(mc.player)<32*32)ghosts.add(ghost);
            ghosts.sort(Comparator.comparingDouble(g -> g.distanceToSqr(mc.player)));
            for(int i=0;i<Math.min(12,ghosts.size());i++){
                var ghost=ghosts.get(i);var emitter=ghost.position().add(0,.55,0);var pos=BlockPos.containing(emitter);
                var offset=emitter.subtract(Vec3.atLowerCornerOf(pos));
                offset=new Vec3(Math.floor(offset.x*4)/4,Math.floor(offset.y*4)/4,Math.floor(offset.z*4)/4);
                var source=new Source(ghost.carbon()?0xb2d8ef:0xc4e5d3,offset,4,10,true);
                if(next.putIfAbsent(pos,source)==null&&!source.equals(sources.get(pos)))pending.add(pos);
            }
            boolean removed = fields.keySet().removeIf(pos -> !next.containsKey(pos) || nativeLighting && !next.get(pos).dynamic);
            sources.clear(); sources.putAll(next); pending.retainAll(next.keySet());
            pending.removeIf(pos -> nativeLighting && !sources.get(pos).dynamic);
            if (removed) publish(mergeFields());
        }
        if (pending.isEmpty()) return;
        // One bounded source per tick. Static lights are rebuilt only when their surroundings change.
        BlockPos pos = pending.iterator().next(); pending.remove(pos);
        fields.put(pos, trace(pos,sources.get(pos)));
        publish(mergeFields());
    }

    public static void blockChanged(BlockPos pos) {
        for (BlockPos source : sources.keySet()) {
            if (Math.abs(source.getX()-pos.getX()) <= RADIUS+1 && Math.abs(source.getY()-pos.getY()) <= RADIUS+1
                    && Math.abs(source.getZ()-pos.getZ()) <= RADIUS+1) pending.add(source);
        }
        scanTick = Long.MIN_VALUE;
    }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) { chunkChanged(event); }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) { chunkChanged(event); }
    private static void chunkChanged(ChunkEvent event) {
        if (!(event.getLevel() instanceof ClientLevel changedLevel)) return;
        var chunk = event.getChunk().getPos();
        Minecraft.getInstance().execute(() -> {
            if (level != changedLevel) return;
            for (BlockPos source : sources.keySet()) {
                if (Math.abs((source.getX() >> 4) - chunk.x) <= 1 && Math.abs((source.getZ() >> 4) - chunk.z) <= 1)
                    pending.add(source);
            }
            scanTick = Long.MIN_VALUE;
        });
    }

    private static Map<Long,Integer> trace(BlockPos origin, Source source) {
        Map<Long,Integer> result = new HashMap<>();
        Vec3 emitter = Vec3.atLowerCornerOf(origin).add(source.offset);
        int rgb = source.rgb;int radius=source.radius;
        for (BlockPos cell : BlockPos.betweenClosed(origin.offset(-radius,-radius,-radius),origin.offset(radius,radius,radius))) {
            if (!level.hasChunkAt(cell)) continue;
            Vec3 target = Vec3.atCenterOf(cell);
            double distance = target.distanceTo(emitter);
            if (distance >= radius) continue;
            if (level.clip(new ClipContext(emitter,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType() != HitResult.Type.MISS) continue;
            float strength = (float)Math.pow(1-distance/radius,1.3);
            int red = Math.round((rgb>>16&255)*strength), green=Math.round((rgb>>8&255)*strength), blue=Math.round((rgb&255)*strength);
            int brightness=Math.round(source.luminance*16*strength);
            result.put(cell.asLong(),brightness<<24|red<<16|green<<8|blue);
        }
        return Map.copyOf(result);
    }

    public static int combine(int a, int b) {
        return Math.max(a>>>24,b>>>24)<<24 | Math.max(a>>16&255,b>>16&255)<<16 | Math.max(a>>8&255,b>>8&255)<<8 | Math.max(a&255,b&255);
    }
    private static Map<Long,Integer> mergeFields() {
        Map<Long,Integer> merged = new HashMap<>();
        fields.values().forEach(field -> field.forEach((pos,color) -> merged.merge(pos,color,ColoredLightEngine::combine)));
        return Map.copyOf(merged);
    }
    private static void publish(Map<Long,Integer> next) {
        Map<Long,Integer> old = samples;
        if (old.equals(next)) return;
        Set<Long> sections = new HashSet<>();
        old.forEach((pos,color) -> { if (!color.equals(next.get(pos))) dirtySections(sections,pos); });
        next.forEach((pos,color) -> { if (!color.equals(old.get(pos))) dirtySections(sections,pos); });
        samples = next;
        var renderer = Minecraft.getInstance().levelRenderer;
        for (long section : sections) renderer.setSectionDirty(SectionPos.x(section),SectionPos.y(section),SectionPos.z(section));
    }
    private static void dirtySections(Set<Long> result,long packed) {
        BlockPos p=BlockPos.of(packed);
        for (int x=-1;x<=1;x+=2) for (int y=-1;y<=1;y+=2) for (int z=-1;z<=1;z+=2)
            result.add(SectionPos.asLong((p.getX()+x)>>4,(p.getY()+y)>>4,(p.getZ()+z)>>4));
    }

    public static boolean active() { return !samples.isEmpty(); }
    /** RGB multiplier. Missing/occluded samples contribute neutral light; sunlight washes out the small tint. */
    public static int tint(double x,double y,double z,int packedLight) {
        Map<Long,Integer> field=samples;
        if (field.isEmpty()) return 0xffffff;
        double sx=x-.5,sy=y-.5,sz=z-.5;int ix=Mth.floor(sx),iy=Mth.floor(sy),iz=Mth.floor(sz);
        double fx=sx-ix,fy=sy-iy,fz=sz-iz,red=0,green=0,blue=0;
        for(int dx=0;dx<2;dx++) for(int dy=0;dy<2;dy++) for(int dz=0;dz<2;dz++) {
            double w=(dx==0?1-fx:fx)*(dy==0?1-fy:fy)*(dz==0?1-fz:fz);
            int c=field.getOrDefault(BlockPos.asLong(ix+dx,iy+dy,iz+dz),0);
            red+=(c>>16&255)*w;green+=(c>>8&255)*w;blue+=(c&255)*w;
        }
        double maximum=Math.max(red,Math.max(green,blue));
        int sky=packedLight>>>16&255;
        double amount=SATURATION*(1-.8*sky/240.0);
        int r=(int)Math.round(255-(maximum-red)*amount),g=(int)Math.round(255-(maximum-green)*amount),b=(int)Math.round(255-(maximum-blue)*amount);
        return Mth.clamp(r,0,255)<<16|Mth.clamp(g,0,255)<<8|Mth.clamp(b,0,255);
    }
    /** Ghost light raises only block light. Preserve sunlight and existing brighter emitters. */
    public static int light(double x,double y,double z,int packedLight) {
        var field=samples;if(field.isEmpty())return packedLight;
        double sx=x-.5,sy=y-.5,sz=z-.5;int ix=Mth.floor(sx),iy=Mth.floor(sy),iz=Mth.floor(sz);
        double fx=sx-ix,fy=sy-iy,fz=sz-iz,emission=0;
        for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)for(int dz=0;dz<2;dz++){
            double weight=(dx==0?1-fx:fx)*(dy==0?1-fy:fy)*(dz==0?1-fz:fz);
            emission+=(field.getOrDefault(BlockPos.asLong(ix+dx,iy+dy,iz+dz),0)>>>24)*weight;
        }
        return packedLight&0xffff0000 | Math.max(packedLight&0xffff,(int)Math.round(emission));
    }
    public static int multiplyArgb(int original,int tint) {
        return original&0xff000000 | ((original>>16&255)*(tint>>16&255)/255)<<16 | ((original>>8&255)*(tint>>8&255)/255)<<8 | (original&255)*(tint&255)/255;
    }
    private record Source(int rgb, Vec3 offset,int radius,int luminance,boolean dynamic) {
        Source(int rgb,Vec3 offset){this(rgb,offset,RADIUS,0,false);}
    }
}
