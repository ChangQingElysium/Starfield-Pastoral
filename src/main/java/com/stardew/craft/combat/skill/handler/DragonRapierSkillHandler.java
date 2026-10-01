package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.HeavyHammerCombatEvents;
import com.stardew.craft.combat.network.DragonRapierFxPayload;
import com.stardew.craft.combat.skill.*;
import com.stardew.craft.combat.skill.runtime.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import static com.stardew.craft.combat.skill.handler.DragonRapierRules.*;

public final class DragonRapierSkillHandler implements PostServerRuntimeWeaponSkillHandler {
    private final String skill;
    public DragonRapierSkillHandler(String skill){this.skill=skill;}
    @Override public SkillValidation validate(SkillExecutionContext c) {
        if(c.hand()!=InteractionHand.MAIN_HAND||!supports(c.weaponId().getPath(),skill)||!c.player().isAlive()
                ||c.player().isSpectator()||c.player().isPassenger()||WeaponSkillAnimationLock.isLocked(c.player(),c.nowTick())
                ||YetiFreezeTracker.isMovementLocked(c.player(),c.nowTick())||WeaponSkillRuntime.hasActive(c.player().getUUID(),c.skillId()))
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        return WeaponSkillCooldowns.isOnCooldown(c.player(),c.weaponId().getPath(),skill,c.nowTick())
                ?SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN):SkillValidation.accept();
    }
    @Override public void begin(SkillExecutionContext c,SkillInstance i) {
        WeaponSkillRuntime.commitCooldown(c,i,cooldown(skill)*20);i.initializeExecutionState(new State(c));
        i.registerCommittedEffect(()->{
            WeaponSkillAnimationLock.setLock(c.player(),c.nowTick(),duration(skill));
            WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(),c.weaponId().getPath(),skill,duration(skill));
        });
    }
    @Override public boolean completesImmediately(){return false;}
    @Override public SkillTickResult tick(SkillExecutionContext c,SkillInstance i){return i.requireExecutionState(State.class).valid(c.player())?SkillTickResult.CONTINUE:SkillTickResult.CANCEL;}
    @Override public SkillTickResult postServerTick(SkillExecutionContext c,SkillInstance i) {
        State s=i.requireExecutionState(State.class);if(!s.valid(c.player()))return SkillTickResult.CANCEL;
        long age=c.nowTick()-s.started;
        if(s.lastTick==c.nowTick())return SkillTickResult.CONTINUE;s.lastTick=c.nowTick();
        if(s.phase<count(skill)&&age>=hitTick(skill,s.phase)) {
            int phase=s.phase++;
            if(BREATH.equals(skill)&&phase==0){s.center=HeavyHammerExecutionState.ground(c.player(),c.player().position());if(s.center==null)return SkillTickResult.COMPLETE;}
            Vec3 center=BREATH.equals(skill)?s.center:c.player().position();
            for(var target:HeavyHammerExecutionState.targets(c,center,radius(skill,phase))) {
                if(!s.valid(c.player()))return SkillTickResult.CANCEL;
                Vec3 offset=target.position().subtract(center);
                if(inArc(skill,phase,offset.dot(s.forward),offset.dot(s.side)))
                    WeaponSkillDamage.apply(c.player(),target,damageContext(skill,phase,s.guard.counter()),c.weaponSnapshot(),c.nowTick()+2,
                            WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,WeaponSkillDamage.HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE);
            }
            DragonRapierFxPayload.send(c.player(),hitId(skill,phase,s.guard.counter()),center,phase,-1,s.yaw);
        }
        return age>=duration(skill)?SkillTickResult.COMPLETE:SkillTickResult.CONTINUE;
    }
    @Override public void finish(SkillExecutionContext c,SkillInstance i,SkillInstance.EndReason reason) {
        i.executionState(State.class).ifPresent(s->{s.canceled=true;if(c.nowTick()<s.started+duration(skill))WeaponSkillAnimationLock.clear(c.player());});
    }
    private static final class State implements SkillInstance.ExecutionState {
        final long started;final ItemStack held;final int slot;final Vec3 forward,side;final float yaw;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final Guard guard=new Guard();boolean canceled;int phase;Vec3 center;long lastTick=Long.MIN_VALUE;
        State(SkillExecutionContext c){started=c.nowTick();held=c.player().getMainHandItem();slot=c.player().getInventory().selected;dimension=c.player().level().dimension();
            forward=HeavyHammerExecutionState.forward(c.player());side=new Vec3(-forward.z,0,forward.x);yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));}
        boolean valid(ServerPlayer p){return !canceled&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&dimension.equals(p.level().dimension())
                &&p.getMainHandItem()==held&&p.getInventory().selected==slot&&!YetiFreezeTracker.isMovementLocked(p,p.level().getGameTime());}
    }
    /** Called after accepted-hit defenses in both native-health and custom-health player paths. */
    public static void parry(LivingIncomingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||event.getAmount()<=0)return;
        var source=event.getSource();
        if(!(source.getDirectEntity() instanceof LivingEntity attacker)||source.getEntity()!=attacker
                ||source.is(DamageTypeTags.BYPASSES_SHIELD)||!p.hasLineOfSight(attacker)||Math.abs(attacker.getY()-p.getY())>2.5)return;
        WeaponSkillRuntime.activeExecutionState(p.getUUID(),new ResourceLocation("stardewcraft",RIPOSTE),State.class).ifPresent(s->{
            Vec3 d=attacker.position().subtract(p.position());
            if(s.valid(p)&&s.guard.consume(p.level().getGameTime()-s.started,d.dot(s.forward),d.dot(s.side),true)) {
                event.setAmount(event.getAmount()*.5f);
                DragonRapierFxPayload.send(p,RIPOSTE+"_guard",p.position(),0,-1,s.yaw);
            }
        });
    }
    public static void appliedHit(ResolvedWeaponHit hit) {
        if(!hit.dealtPositiveDamage()||!(hit.attacker() instanceof ServerPlayer p)||!isWeapon(hit.weaponIdentity().logicId()))return;
        String actual=hit.authoredSkillContext().getSkillId(),base=null;int phase=-1;
        for(String id:new String[]{JAW,BREATH,RIPOSTE})for(int n=0;n<count(id);n++)if(hitId(id,n,false).equals(actual)||RIPOSTE.equals(id)&&(RIPOSTE+"_counter").equals(actual)){base=id;phase=n;}
        if(base==null||!supports(hit.weaponIdentity().logicId(),base))return;
        var target=hit.target();long now=hit.gameTick();
        if(target.isAlive()&&!RIPOSTE.equals(base)&&target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)<.8
                &&!YetiFreezeTracker.isMovementLocked(target,now)&&!WeaponSkillMovementControl.isLocked(target,now)) {
            int ticks=YetiFreezeTracker.applyWithEquipmentProtection(target,now,phase==0?4:1,YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER);
            if(ticks>0&&BREATH.equals(base)&&phase==4)HeavyHammerCombatEvents.queuePush(target,p.position(),.35f,now+ticks);
        }
        DragonRapierFxPayload.send(p,actual,target.getBoundingBox().getCenter(),phase,target.getId(),p.getYRot());
    }
}
