package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.FishingPresentationPhase;
import com.stardew.craft.fishing.network.FishingPresentationPayload;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class FishingPresentationClient {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    public static final class State {
        UUID session;public FishingPresentationPhase phase=FishingPresentationPhase.STOP;
        public final FishingRigPose pose,previous,scratch;
        public final FishingBaitModels.SelectionCache bait=new FishingBaitModels.SelectionCache();
        public final FishingTackleModels.SelectionCache tackles=new FishingTackleModels.SelectionCache();
        Matrix4f playerModelTransform;
        public long started=Util.getMillis(),received=started,clipChanged=started,lastFrame=started;
        public Vec3 launchHookOffset=new Vec3(.75/16,-6.875/16,0);
        public int hook=-1;public Vec3 bobber=Vec3.ZERO,oldBobber=Vec3.ZERO,origin=Vec3.ZERO;
        public ItemStack stack=ItemStack.EMPTY;public boolean fish,held,controlled;
        public float fishPosition=.927f,progress=.3f,velocity;
        public String clip="equip_raise",profile="";
        float crankOffset,lookPitch;public double clipTime,reelTime;
        double motionStarted;String reelMotion="reel_loop";
        public boolean splashed,pulled,caught,stored;long lastReel,lastDrop;
        State(){pose=new FishingRigPose(FishingRigAssets.rig);previous=new FishingRigPose(FishingRigAssets.rig);scratch=new FishingRigPose(FishingRigAssets.rig);pose.sample("equip_raise",0);previous.copy(pose);clipChanged=started-160;}
        public double elapsed(){return Math.max(0,(Util.getMillis()-started)/1000.0);}
        public double length(){return FishingRigAssets.clips.get(clip).length();}
        public boolean finishing(){return phase==FishingPresentationPhase.CATCH||phase==FishingPresentationPhase.FAIL||phase==FishingPresentationPhase.RETRIEVE;}
        public double catchTime(){return profile.equals("_near")?1.15:profile.equals("_far")?1.758333333:1.533333333;}
        public double hideTime(){return catchTime()+(fish?2.3:1.565);}
        public boolean catchVisible(){return phase==FishingPresentationPhase.CATCH && elapsed()>=.3&&elapsed()<hideTime()&&!stack.isEmpty();}
        public boolean idleClip(){return clip.equals("ready_idle")||clip.equals("ready_one_hand");}
        public boolean busy(){return phase!=FishingPresentationPhase.STOP && !(finishing()&&idleClip());}
        public void sample() {
            long now=Util.getMillis();double dt=Math.min(.05,Math.max(0,(now-lastFrame)/1000.0));lastFrame=now;
            double t=elapsed(),time=t;String next="ready_one_hand";
            switch(phase) {
                case CHARGE -> {next=t<.45?"charge_enter":"charge_hold";time=t<.45?t:t-.45;}
                case CAST -> {if(t<1.8)next="cast_release";else if(t<2.25){next="wait_enter";time=t-1.8;}else{next="wait_idle";time=t-2.25;}}
                case BITE -> {next=t<.5?"bite_notice":"wait_idle";time=t<.5?t:t-.5;}
                case HOOK -> {next=t<.45?"hook_set":"tension_hold";time=t<.45?t:t-.45;}
                case MINIGAME -> {
                    if(t<.75)next="reel_grab";
                    else if(t<1.55){next="reel_enter";time=t-.75;}
                    else {
                        reelTime+=dt*(held?(controlled?1.15:.75):.38);
                        if(velocity < -1.8 && !reelMotion.equals("fish_run") && t-motionStarted>.35){reelMotion="fish_run";motionStarted=t;}
                        else if(velocity>1.4 && !reelMotion.equals("slack_takeup") && t-motionStarted>.35){reelMotion="slack_takeup";motionStarted=t;}
                        else if(t-motionStarted>2.0 && Math.abs(velocity)<1){reelMotion="reel_loop";}
                        next=reelMotion;time=next.equals("reel_loop")?reelTime:t-motionStarted;
                        if(!next.equals("reel_loop")&&time>=FishingRigAssets.clips.get(next).length()){next="reel_loop";time=reelTime;}
                    }
                }
                case CATCH,FAIL,RETRIEVE -> {
                    String base=phase==FishingPresentationPhase.CATCH?(fish?"catch_lift":stack.getItem() instanceof BlockItem?"catch_block":"catch_item"):phase==FishingPresentationPhase.FAIL?"failed_retrieve":"cancel_retrieve";
                    next=base+profile;double duration=FishingRigAssets.clips.get(next).length();
                    if(t>=duration){if(phase==FishingPresentationPhase.CATCH && t<duration+.8){next="success_to_ready";time=t-duration;}else{next="ready_one_hand";time=t-duration;}}
                }
                default -> { if(t<1.12)next="equip_raise";else time=t-1.12; }
            }
            boolean changed=!next.equals(clip);float crank=pose.channels[pose.index("rod_crank")][3];
            if(changed){previous.copy(pose);clip=next;clipChanged=now;}
            clipTime=time;pose.sample(next,time);
            if(changed)crankOffset=crank-pose.channels[pose.index("rod_crank")][3];
            pose.channels[pose.index("rod_crank")][3]+=crankOffset;pose.matrices();
            float blend=smooth((now-clipChanged)/160f);if(blend<1)pose.mix(previous,blend);
            float onCrank=0;
            if(phase==FishingPresentationPhase.MINIGAME&&t>=.75)onCrank=1;
            else if(phase==FishingPresentationPhase.CATCH)onCrank=1-smooth((float)((t-.12)/.26));
            else if((phase==FishingPresentationPhase.FAIL||phase==FishingPresentationPhase.RETRIEVE)&&!idleClip())onCrank=1-smooth((float)((t-(length()-.83))/.33));
            FishingArmIK.grip(pose,true,1);
            FishingArmIK.grip(pose,false,onCrank);pose.aim(lookPitch,phase==FishingPresentationPhase.STOP||phase==FishingPresentationPhase.CHARGE||phase==FishingPresentationPhase.CAST&&t<.35||idleClip());
        }
    }
    public static boolean eligible(AbstractClientPlayer p){return FishingRigAssets.rig!=null&&p.isAlive()&&!p.isSpectator()&&!p.isInvisible()&&p.getMainHandItem().getItem() instanceof FishingRodItem&&p.getOffhandItem().isEmpty();}
    /** Keep every world-rendered player on Minecraft's native model and held-item animation. */
    public static boolean worldOwned(AbstractClientPlayer p){return FishingWorldRenderScope.owns(p.getUUID())&&firstPerson(p)&&eligible(p);}
    public static State state(AbstractClientPlayer p){if(!eligible(p))return null;var state=STATES.computeIfAbsent(p.getUUID(),k->new State());state.lookPitch=p.getViewXRot(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));return state;}
    public static void clear(){STATES.clear();level=Minecraft.getInstance().level;}
    public static void cancelLocal(){var p=Minecraft.getInstance().player;if(p!=null)STATES.remove(p.getUUID());FishingMinigameHud.cancel();}
    public static void receive(FishingPresentationPayload packet) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;if(level!=mc.level)clear();
        if(mc.player!=null&&packet.actor().equals(mc.player.getUUID())&&!FishingInteractionState.accepts(packet.session()))return;
        var entity=mc.level.getPlayerByUUID(packet.actor());if(!(entity instanceof AbstractClientPlayer player))return;
        if(packet.phase()==FishingPresentationPhase.STOP){var old=STATES.get(packet.actor());if(old!=null&&packet.session().equals(old.session))STATES.remove(packet.actor());return;}
        var s=state(player);if(s==null)return;
        if(!packet.session().equals(s.session)||packet.phase()!=s.phase) {
            if(packet.phase()==FishingPresentationPhase.CATCH){var actor=actorTransform(player,1);s.launchHookOffset=FishingPresentationRenderer.equippedHookOffset(player,s,actor);}
            s.previous.copy(s.pose);s.phase=packet.phase();s.session=packet.session();
            s.started=Util.getMillis()-Math.max(0,mc.level.getGameTime()-packet.started())*50;s.clipChanged=Util.getMillis();
            s.origin=packet.bobber();s.bobber=s.oldBobber=packet.bobber();s.stack=packet.stack().copy();s.fish=packet.fish();
            double distance=player.position().distanceTo(s.origin);s.profile=distance<4?"_near":distance>8?"_far":"";
            s.splashed=false;s.pulled=s.elapsed()>.45;s.caught=s.elapsed()>s.catchTime()+.15;s.stored=s.elapsed()>=s.hideTime();
            var existingHook=mc.level.getEntity(packet.hook());if(existingHook!=null&&existingHook.isInWater()&&s.elapsed()>3)s.splashed=true;
            s.reelMotion="reel_loop";s.motionStarted=0;
            if(s.phase==FishingPresentationPhase.CHARGE)sound(player,ModSounds.FISHING_ROD_BEND.get(),.3f,.9f);
        }
        s.hook=packet.hook();s.oldBobber=visualBobber(s);s.bobber=packet.bobber();s.received=Util.getMillis();
        s.fishPosition=packet.fishPosition();s.progress=packet.progress();s.velocity=packet.velocity();s.held=packet.held();s.controlled=packet.controlled();
    }
    public static void charge(UUID id){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;receive(new FishingPresentationPayload(mc.player.getUUID(),id,FishingPresentationPhase.CHARGE,mc.level.getGameTime(),-1,mc.player.position(),ItemStack.EMPTY,false,.927f,.3f,0,false,false));}
    public static void castLocal(){var mc=Minecraft.getInstance();if(mc.player==null)return;var s=state(mc.player);if(s!=null&&s.session!=null){s.previous.copy(s.pose);s.phase=FishingPresentationPhase.CAST;s.started=s.clipChanged=Util.getMillis();s.hook=-1;}}
    public static void feedback(float position,float progress,float velocity,boolean held,boolean controlled){var mc=Minecraft.getInstance();if(mc.player==null)return;var s=state(mc.player);if(s!=null){s.fishPosition=position;s.progress=progress;s.velocity=velocity;s.held=held;s.controlled=controlled;}}
    public static Vec3 visualBobber(State s){return s.oldBobber.lerp(s.bobber,Mth.clamp((Util.getMillis()-s.received)/100.0,0,1));}
    public static float smooth(float t){float q=Mth.clamp(t,0,1);return q*q*q*(10+q*(-15+6*q));}
    static boolean firstPerson(AbstractClientPlayer p){var mc=Minecraft.getInstance();return mc.getCameraEntity()==p&&mc.options.getCameraType().isFirstPerson();}
    public static Matrix4f actorTransform(AbstractClientPlayer p,float partial) {
        var actor=baseActorTransform(p,partial);var state=STATES.get(p.getUUID());
        if(!firstPerson(p)&&state!=null&&state.playerModelTransform!=null)actor.mul(state.playerModelTransform);
        return actor;
    }
    static Matrix4f baseActorTransform(AbstractClientPlayer p,float partial) {
        Vec3 position=p.getPosition(partial);float yaw=p.getViewYRot(partial);
        var m=new Matrix4f().translation((float)position.x,(float)position.y,(float)position.z).rotateY((float)Math.toRadians(180-yaw)).scale(1/16f);
        if(p.getMainArm()==HumanoidArm.LEFT)m.scale(-1,1,1);return m;
    }
    public static Vec3 world(Matrix4f m,Vector3f p){var v=m.transformPosition(new Vector3f(p));return new Vec3(v.x,v.y,v.z);}
    public static Vec3 catchOrigin(State s){return s.origin.add(0,.14,0).add(s.launchHookOffset);}
    /** Actual hook -> ballistic arc -> moving hand with matching final velocity. */
    public static Vec3 catchPosition(State s,Matrix4f actor) {
        return catchPosition(s,actor,s.elapsed());
    }
    static Vec3 catchPosition(State s,Matrix4f actor,double t) {
        double catchTime=s.catchTime();String anchor=s.fish?"caught_fish":s.stack.getItem() instanceof BlockItem?"caught_block":"caught_item";
        if(t>=catchTime)return world(actor,s.pose.anchor(anchor));
        s.scratch.sample((s.fish?"catch_lift":s.stack.getItem() instanceof BlockItem?"catch_block":"catch_item")+s.profile,catchTime);s.scratch.aim(s.lookPitch);
        Vec3 end=world(actor,s.scratch.anchor(anchor));double duration=catchTime-.3,u=Mth.clamp((t-.3)/duration,0,1);
        Vec3 start=catchOrigin(s);
        Vec3 gravity=new Vec3(0,-6.25,0),v0=end.subtract(start).subtract(gravity.scale(.5*duration*duration)).scale(1/duration);
        if(u<=.78)return start.add(v0.scale(u*duration)).add(gravity.scale(.5*u*u*duration*duration));
        double at=.78*duration,q=(u-.78)/.22,tail=duration-at;
        Vec3 a=start.add(v0.scale(at)).add(gravity.scale(.5*at*at)),va=v0.add(gravity.scale(at));
        s.scratch.sample((s.fish?"catch_lift":s.stack.getItem() instanceof BlockItem?"catch_block":"catch_item")+s.profile,catchTime+.01);s.scratch.aim(s.lookPitch);
        Vec3 vb=world(actor,s.scratch.anchor(anchor)).subtract(end).scale(100);
        return a.scale(2*q*q*q-3*q*q+1).add(va.scale((q*q*q-2*q*q+q)*tail)).add(end.scale(-2*q*q*q+3*q*q)).add(vb.scale((q*q*q-q*q)*tail));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.level!=level)clear();if(mc.level==null)return;
        STATES.entrySet().removeIf(e->!(mc.level.getPlayerByUUID(e.getKey()) instanceof AbstractClientPlayer p)||!eligible(p));
        for(var entry:STATES.entrySet()){
            var player=(AbstractClientPlayer)mc.level.getPlayerByUUID(entry.getKey());var s=entry.getValue();s.sample();double t=s.elapsed();
            var hook=mc.level.getEntity(s.hook);
            if(hook!=null&&hook.isInWater()&&!s.splashed){s.splashed=true;splash(visualBobber(s),8);sound(player,ModSounds.DROP_ITEM_IN_WATER.get(),.35f,1.1f);}
            if(s.phase==FishingPresentationPhase.MINIGAME&&Util.getMillis()-s.lastReel> (s.held?220:440)){
                s.lastReel=Util.getMillis();sound(player,s.held?ModSounds.FAST_REEL.get():ModSounds.SLOW_REEL.get(),.16f,s.controlled?1.05f:.88f);
                if(Math.abs(s.velocity)>2)splash(visualBobber(s),2);
            }
            if((s.phase==FishingPresentationPhase.FAIL||s.phase==FishingPresentationPhase.RETRIEVE)&&!s.idleClip()) {
                double pullAt=s.phase==FishingPresentationPhase.FAIL?.66:.18,arrival=s.length()-.8;
                if(t>=pullAt&&t<arrival&&Util.getMillis()-s.lastReel>320){s.lastReel=Util.getMillis();sound(player,ModSounds.SLOW_REEL.get(),.14f,.92f);}
                if(t>=pullAt&&!s.pulled){s.pulled=true;splash(s.origin,4);sound(player,ModSounds.PULL_ITEM_FROM_WATER.get(),.24f,1.15f);}
            }
            if(s.phase==FishingPresentationPhase.CATCH){
                if(t>=.3&&!s.pulled){s.pulled=true;splash(s.origin,12);sound(player,ModSounds.PULL_ITEM_FROM_WATER.get(),.65f,1);}
                if(t>=s.catchTime()&&!s.caught){s.caught=true;sound(player,ModSounds.FISH_SLAP.get(),.28f,s.fish?1.05f:.8f);}
                if(s.catchVisible()&&Util.getMillis()-s.lastDrop>95){s.lastDrop=Util.getMillis();Vec3 at=catchPosition(s,actorTransform(player,1));mc.level.addParticle(ParticleTypes.FALLING_WATER,at.x,at.y-.15,at.z,0,-.04,0);}
                if(t>=s.hideTime()&&!s.stored){s.stored=true;sound(player,ModSounds.BACKPACK_IN.get(),.45f,1);if(player==mc.player)FishingCatchVisuals.finishHandPresentation();}
            }
        }
    }
    private static void splash(Vec3 p,int count){var level=Minecraft.getInstance().level;if(level==null)return;for(int i=0;i<count;i++){double angle=i*2.3999632297;level.addParticle(ParticleTypes.SPLASH,p.x+Math.cos(angle)*.11,p.y+.03,p.z+Math.sin(angle)*.11,Math.cos(angle)*.05,.035+(i%3)*.016,Math.sin(angle)*.05);}}
    private static void sound(AbstractClientPlayer player,SoundEvent sound,float volume,float pitch){var level=Minecraft.getInstance().level;if(level!=null)level.playLocalSound(player.getX(),player.getY()+1,player.getZ(),sound,SoundSource.PLAYERS,volume,pitch,false);}
}
