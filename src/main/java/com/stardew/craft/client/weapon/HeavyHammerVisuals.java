package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.HeavyHammerFxPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.combat.skill.handler.HeavyHammerRules.*;

/** Short, grounded tapered arcs and two distinct confirmed contact shapes. No effect sprites. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class HeavyHammerVisuals {
    private record Band(int delay,Vec3[] points) {}
    private record Wave(int caster,long born,Vec3 center,boolean gold,boolean heavy,List<Band> bands) {}
    private record Contact(int caster,int target,long born,Vec3 point,boolean gold,boolean echo,float size) {}
    private record Burst(int caster,long born,Vec3 center,HeavyHammerBurstGeometry.Shape shape,float radius,float yaw,long seed) {}
    private static final Map<Integer,Long> BUFFS=new HashMap<>();
    private static final Map<Integer,Long> BUFF_VERSIONS=new HashMap<>();
    private static final Map<String,Long> SEEN=new LinkedHashMap<>();
    private static final Map<Integer,Long> SOUNDS=new HashMap<>();
    private static final List<Wave> WAVES=new ArrayList<>();
    private static final List<Contact> CONTACTS=new ArrayList<>();
    private static final List<Burst> BURSTS=new ArrayList<>();
    private static ClientLevel level;
    private HeavyHammerVisuals() {}
    private static void ensureLevel() {
        ClientLevel next=Minecraft.getInstance().level;
        if(level!=next) {level=next;BUFFS.clear();BUFF_VERSIONS.clear();SEEN.clear();SOUNDS.clear();WAVES.clear();CONTACTS.clear();BURSTS.clear();}
    }
    public static boolean empowered(int caster) {
        ensureLevel();return level!=null && BUFFS.getOrDefault(caster,Long.MIN_VALUE)>level.getGameTime();
    }
    public static float remainingRatio(int caster) {
        if (!empowered(caster)) return 0;
        return Math.clamp((BUFFS.get(caster)-level.getGameTime())/(float)BURST_DURATION,0,1);
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel();if(level==null || Minecraft.getInstance().player==null
                || Minecraft.getInstance().player.distanceToSqr(p.originX(),p.originY(),p.originZ())>48*48) return;
        if(PRESS.equals(p.skillId())) {
            Vec3 center=new Vec3(p.originX(),p.originY(),p.originZ());
            var actor=level.getEntity(p.casterEntityId());
            if(actor!=null) {
                center=center.add(Vec3.directionFromRotation(0,p.yaw()).scale(1.5));
                var ground=WeaponGroundContact.find(level,actor,center);
                if(ground!=null) {
                    center=ground.getLocation();
                    addWave(p.casterEntityId(),center,3,true,false,true,false,p.yaw());
                    addBurst(p.casterEntityId(),center,HeavyHammerBurstGeometry.Shape.PULL,3,p.yaw(),p.seed());
                }
            }
        }
        if(!POUND.equals(p.skillId())) level.playLocalSound(p.originX(),p.originY()+1,p.originZ(),
                ENDLESS.equals(p.skillId()) ? SoundEvents.BEACON_ACTIVATE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,.4f,ENDLESS.equals(p.skillId())?.85f:.65f,false);
    }
    public static void receive(HeavyHammerFxPayload p) {
        ensureLevel();var mc=Minecraft.getInstance();if(level==null || mc.player==null) return;
        if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||!Float.isFinite(p.radius())||!Float.isFinite(p.yaw())) return;
        if(p.phase()==HeavyHammerFxPayload.BUFF_START||p.phase()==HeavyHammerFxPayload.BUFF_END) {
            long previous=BUFF_VERSIONS.getOrDefault(p.caster(),Long.MIN_VALUE);
            if(p.tick()<previous || p.tick()==previous && p.phase()==HeavyHammerFxPayload.BUFF_START)return;
            BUFF_VERSIONS.put(p.caster(),p.tick());
            if(p.phase()==HeavyHammerFxPayload.BUFF_END)BUFFS.remove(p.caster());
            else {
                BUFFS.put(p.caster(),level.getGameTime()+Math.min(103,Math.max(0,(long)p.radius())));
                if(mc.player.distanceToSqr(p.x(),p.y(),p.z())<=48*48)
                    addBurst(p.caster(),new Vec3(p.x(),p.y(),p.z()),HeavyHammerBurstGeometry.Shape.AWAKEN,1,p.yaw(),p.tick());
            }
            return;
        }
        Vec3 point=new Vec3(p.x(),p.y(),p.z());if(mc.player.distanceToSqr(point)>48*48) return;
        String key=p.caster()+":"+p.tick()+":"+p.skill()+":"+p.phase()+":"+p.target();
        if(SEEN.putIfAbsent(key,level.getGameTime())!=null)return;
        while(SEEN.size()>512)SEEN.remove(SEEN.keySet().iterator().next());
        boolean gold=p.skill().startsWith("infinity_");
        if(p.target()>=0) {
            if(Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
                CONTACTS.add(new Contact(p.caster(),p.target(),level.getGameTime(),point,gold,p.phase()==HeavyHammerFxPayload.HIT_ECHO,Math.clamp(p.radius(),.3f,1.3f)));
                while(CONTACTS.size()>128) CONTACTS.removeFirst();
            }
            if(!Long.valueOf(p.tick()).equals(SOUNDS.put(p.caster(),p.tick()))) {
                level.playLocalSound(point.x,point.y,point.z,SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.4f,gold?.8f:.65f,false);
                if(p.caster()==mc.player.getId() && p.phase()!=HeavyHammerFxPayload.HIT_ECHO)
                    MeleeWeaponVisuals.heavyHammerContact(p);
            }
        } else {
            if(p.radius()<=0||p.radius()>5)return;
            boolean heavy=p.phase()==HeavyHammerFxPayload.FINAL;
            addWave(p.caster(),point,p.radius(),gold,heavy,false,p.phase()==HeavyHammerFxPayload.SWEEP,p.yaw());
            addBurst(p.caster(),point,shape(p),p.radius(),p.yaw(),p.tick());
            level.playLocalSound(point.x,point.y,point.z,SoundEvents.IRON_GOLEM_ATTACK,SoundSource.PLAYERS,heavy?.7f:.4f,heavy?.6f:gold?.9f:.75f,false);
            if(p.phase()!=HeavyHammerFxPayload.ECHO)level.playLocalSound(point.x,point.y,point.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.2f,gold?1.2f:.85f,false);
        }
    }
    private static HeavyHammerBurstGeometry.Shape shape(HeavyHammerFxPayload p) {
        return switch(p.phase()) {
            case HeavyHammerFxPayload.SWEEP -> HeavyHammerBurstGeometry.Shape.SWEEP;
            case HeavyHammerFxPayload.FINAL -> HeavyHammerBurstGeometry.Shape.STAR_FINAL;
            case HeavyHammerFxPayload.PRESS -> HeavyHammerBurstGeometry.Shape.PRESS;
            case HeavyHammerFxPayload.ECHO -> HeavyHammerBurstGeometry.Shape.ECHO;
            case HeavyHammerFxPayload.POUND -> HeavyHammerBurstGeometry.Shape.POUND;
            default -> p.skill().endsWith("_echo")?HeavyHammerBurstGeometry.Shape.STAR_ECHO:HeavyHammerBurstGeometry.Shape.STAR;
        };
    }
    private static void addBurst(int caster,Vec3 center,HeavyHammerBurstGeometry.Shape shape,float radius,float yaw,long seed) {
        if(!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean())return;
        BURSTS.add(new Burst(caster,level.getGameTime(),center,shape,radius,yaw,seed));
        while(BURSTS.size()>32)BURSTS.removeFirst();
    }
    private static void addWave(int caster,Vec3 center,float radius,boolean gold,boolean heavy,boolean inward,boolean sweep,float yaw) {
        if(!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean())return;
        var actor=level.getEntity(caster);if(actor==null)return;
        List<Band> paths=new ArrayList<>();double facing=Math.toRadians(yaw+90);
        int bands=heavy?3:2;
        for(int band=0;band<bands;band++)for(int side=0;side<4;side++) {
            Vec3[] points=new Vec3[9];double r=sweep&&band==0?3.5:radius*(band+1)/bands;
            for(int i=0;i<points.length;i++) {
                double fraction=(side+(i/(double)(points.length-1))*.83)/4;
                double angle=sweep?facing-Math.toRadians(80)+fraction*Math.toRadians(160):fraction*Math.PI*2;
                var hit=WeaponGroundContact.find(level,actor,center.add(Math.cos(angle)*r,0,Math.sin(angle)*r));
                points[i]=hit==null?null:hit.getLocation().add(0,.035,0);
            }
            for(Vec3[] part:DwarfWeaponVisuals.continuousGroundPaths(points)) paths.add(new Band((inward?bands-1-band:band)*(sweep?4:2),part));
        }
        WAVES.add(new Wave(caster,level.getGameTime(),center,gold,heavy,paths));while(WAVES.size()>32)WAVES.removeFirst();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        ensureLevel();if(level==null||Minecraft.getInstance().isPaused())return;long now=level.getGameTime();
        WAVES.removeIf(w->now-w.born>=11||!living(w.caster));CONTACTS.removeIf(c->now-c.born>=7||!living(c.caster));
        BURSTS.removeIf(b->now-b.born>=HeavyHammerBurstGeometry.lifetime(b.shape)||!living(b.caster));
        BUFFS.entrySet().removeIf(e2->e2.getValue()<=now||!holdingInfinity(e2.getKey()));
        BUFF_VERSIONS.keySet().removeIf(id->level.getEntity(id)==null);SEEN.values().removeIf(t->now-t>40);SOUNDS.values().removeIf(t->now-t>40);
    }
    private static boolean living(int id){return level.getEntity(id) instanceof LivingEntity e&&e.isAlive();}
    private static boolean holdingInfinity(int id){return level.getEntity(id) instanceof LivingEntity e&&e.isAlive()&&e.getMainHandItem().getItem() instanceof IStardewWeapon w&&"infinity_gavel".equals(w.getWeaponId());}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        ensureLevel();if(level==null||!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean())return;
        var mc=Minecraft.getInstance();double now=level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=e.getCamera().getPosition();var stack=e.getPoseStack();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        var pose=stack.last().pose();var buffers=mc.renderBuffers().bufferSource();
        for(int pass=0;pass<2;pass++) {
            boolean edge=pass==0;var type=edge?WeaponEffectRenderTypes.IMPACT_EDGE:WeaponEffectRenderTypes.MOLTEN_GLOW;var out=buffers.getBuffer(type);
            for(Burst b:BURSTS) {
                if(b.center.distanceToSqr(camera)>48*48)continue;
                HeavyHammerBurstGeometry.draw(out,pose,b.center,b.shape,(float)(now-b.born),b.radius,b.yaw,b.seed,edge);
            }
            for(Wave w:WAVES) {
                if(w.center.distanceToSqr(camera)>48*48)continue;
                for(Band b:w.bands) {float age=(float)(now-w.born-b.delay);if(age<0||age>7)continue;
                    float fade=(1-age/7)*(1-age/7);WeaponContactGeometry.ribbon(out,pose,b.points,new Vec3(0,1,0),w.heavy?.16:.09,fade*.7f,edge,
                            edge?30:w.gold?255:148,edge?15:w.gold?202:135,edge?40:w.gold?103:255);}
            }
            for(Contact c:CONTACTS) {
                if(c.point.distanceToSqr(camera)>48*48)continue;
                float fade=(float)Math.max(0,1-(now-c.born)/7);fade*=fade;
                Vec3 point=c.point;if(level.getEntity(c.target) instanceof LivingEntity target)point=target.getBoundingBox().clip(camera,target.getBoundingBox().getCenter()).orElse(c.point);
                Vec3 normal=camera.subtract(point).normalize(),right=normal.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);
                Vec3 up=right.cross(normal).normalize();point=point.add(normal.scale(edge?.05:.065));
                if(c.echo)WeaponContactGeometry.slenderCross(out,pose,point,right,up,normal,c.size*.75f,fade,edge,edge?30:c.gold?255:151,edge?15:c.gold?209:144,edge?40:c.gold?126:255);
                else WeaponContactGeometry.fourDiamonds(out,pose,point,right,up,normal,c.size,fade,edge,edge?30:c.gold?255:151,edge?15:c.gold?209:144,edge?40:c.gold?126:255);
            }
            buffers.endBatch(type);
        }
        stack.popPose();
    }
}
