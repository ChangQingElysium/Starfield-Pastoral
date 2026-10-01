package com.stardew.craft.client.weapon;
import com.stardew.craft.Config;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Thin light on actual sampled ground; gameplay still owns the original line and trace. */
public final class SteelFalchionLineEffectClient {
    private static final Map<Integer,Line> LINES=new LinkedHashMap<>();
    private static ClientLevel level;
    private SteelFalchionLineEffectClient(){}
    public static void ensureLevel(){var next=Minecraft.getInstance().level;if(next!=level){level=next;LINES.clear();SteelFalchionTraceClientState.clear();}}
    public static void create(int id,double x,double y,double z,int duration,float width){
        ensureLevel();Vec3 p=new Vec3(x,y,z);if(level==null||!finite(p)||duration<=0)return;
        LINES.put(id,new Line(p,level.getGameTime(),Math.clamp(duration,1,120)));
        while(LINES.size()>32)LINES.remove(LINES.keySet().iterator().next());
    }
    public static void addPoint(int id,double x,double y,double z){
        ensureLevel();Line line=LINES.get(id);var actor=Minecraft.getInstance().player;Vec3 p=new Vec3(x,y,z);
        if(line==null||actor==null||!finite(p))return;
        Vec3 old=line.previous;line.previous=p;
        if(!validSegment(old,p)){line.points.add(null);trim(line);return;}
        int steps=Math.max(1,(int)Math.ceil(old.distanceTo(p)/.35));
        for(int i=0;i<=steps;i++){
            Vec3 probe=old.lerp(p,i/(double)steps);var hit=WeaponGroundContact.find(level,actor,probe);
            Vec3 point=hit==null?null:hit.getLocation().add(0,.026,0);
            Vec3 prior=line.points.isEmpty()?null:line.points.getLast();
            if(point!=null&&prior!=null&&Math.abs(point.y-prior.y)>.55)line.points.add(null);
            if(point==null||prior==null||point.distanceToSqr(prior)>.0001)line.points.add(point);
        }
        trim(line);
    }
    private static void trim(Line line){while(line.points.size()>256)line.points.removeFirst();}
    public static void pulse(int id,int duration){ensureLevel();Line l=LINES.get(id);if(l!=null)l.pulseUntil=level.getGameTime()+Math.clamp(duration,0,10);}
    public static void burst(int id){ensureLevel();Line l=LINES.get(id);if(l!=null)l.burstUntil=level.getGameTime()+8;}
    public static void remove(int id){ensureLevel();LINES.remove(id);}
    public static void onClientTick(ClientTickEvent.Post event){ensureLevel();if(level==null||Minecraft.getInstance().isPaused())return;long now=level.getGameTime();LINES.values().removeIf(l->now>Math.max(l.end+4,l.burstUntil));}
    public static void onRenderLevel(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;ensureLevel();
        if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()||LINES.isEmpty())return;
        var mc=Minecraft.getInstance();Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        double now=level.getGameTime()+partial;var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);var pose=event.getPoseStack().last().pose();
        for(Line l:LINES.values()){
            if(l.points.stream().noneMatch(p->p!=null&&p.distanceToSqr(camera)<48*48))continue;
            float burst=(float)Math.clamp((l.burstUntil-now)/8,0,1),pulse=(float)Math.clamp((l.pulseUntil-now)/8,0,1);
            float fade=Math.max(opacity((float)(now-l.start),(int)(l.end-l.start)),burst);
            if(fade<=0)continue;
            List<Vec3> chunk=new ArrayList<>();
            for(int i=0;i<=l.points.size();i++){
                Vec3 p=i<l.points.size()?l.points.get(i):null;
                if(p!=null)chunk.add(p.subtract(camera));else if(!chunk.isEmpty()){
                    CrescentFalchionGeometry.etch(out,pose,chunk.toArray(Vec3[]::new),fade*(.6f+.4f*Math.max(burst,pulse)),burst);chunk.clear();}
            }
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
    static boolean validSegment(Vec3 a,Vec3 b){double d=a.distanceToSqr(b);return Double.isFinite(d)&&d>.0001&&d<=64;}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    static float opacity(float age,int duration){return age<0||age>=duration?0:Math.min(1,age/2)*Math.min(1,(duration-age)/10);}
    private static final class Line{
        final List<Vec3> points=new ArrayList<>();final long start,end;Vec3 previous;long pulseUntil,burstUntil;
        Line(Vec3 p,long start,int duration){this.previous=p;this.start=start;this.end=start+duration;}
    }
}
