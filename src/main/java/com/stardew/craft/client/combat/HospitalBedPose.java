package com.stardew.craft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;

/** Clinic bed choreography. Hip coordinates are relative to the safe bedside handoff point. */
public final class HospitalBedPose {
    public static final int GET_UP_TICKS=104;
    private static final float SCALE=.9375f;
    private static final float HIP=(1.501f-.75f)*SCALE;
    private static final float LEG=6*SCALE/16;
    public record Sample(CombatCollapsePose.Frame frame,float hipX,float hipY,float hipZ,float yaw,float headZ) {}
    private HospitalBedPose() {}
    private static CombatCollapsePose.Rotation r(float x,float y,float z) {return new CombatCollapsePose.Rotation(x,y,z);}
    private static float ease(float t) {t=com.stardew.craft.port.PortJava.clamp(t,0,1);return t*t*(3-2*t);}
    private static float mix(float a,float b,float t) {return a+(b-a)*t;}
    private static CombatCollapsePose.Frame pose(float pitch,float thigh,float knee,float support) {
        return new CombatCollapsePose.Frame(pitch,0,0,r(pitch*.25f,0,0),r(support,0,-3),r(support,0,3),
                r(thigh,0,-1),r(thigh,0,1),-8*(1-pitch/90),-12*(1-pitch/90),knee,knee);
    }
    public static Sample sample(float ticks) {
        if(ticks<0) return new Sample(pose(90,0,0,0),-1.15f,.642f,.12f,0,-3.0f);
        if(ticks<24) {
            float t=ease(ticks/24),pitch=mix(90,0,t);
            // Clear the blanket before bringing the palms back beside the hips.
            float arms=35*t-50*(float)Math.sin(Math.PI*t);
            return new Sample(pose(pitch,pitch-90,0,arms),-1.15f,.642f,.12f,0,-3.0f*(1-t));
        }
        if(ticks<50) {
            float t=ease((ticks-24)/26);
            return new Sample(pose(0,-90,0,mix(35,12,t)),mix(-1.15f,-.65f,t),.642f,.12f*(1-t),-90*t,0);
        }
        if(ticks<66) {
            float t=ease((ticks-50)/16);
            return new Sample(pose(0,-90,90*t,12),-.65f,.642f,0,-90,0);
        }
        if(ticks<80) {
            float t=ease((ticks-66)/14);
            // Move the seated torso beyond the wooden edge before lowering to the floor.
            return new Sample(pose(0,-90,90,mix(12,-18,t)),mix(-.65f,-LEG,ease((ticks-66)/7)),
                    mix(.642f,HIP-LEG,ease((ticks-73)/7)),0,-90,0);
        }
        if(ticks<GET_UP_TICKS) {
            float t=ease((ticks-80)/24),angle=90*(1-t);
            float radians=(float)Math.toRadians(angle);
            // Both soles remain planted while the hips travel over them.
            var frame=new CombatCollapsePose.Frame(0,0,0,r(0,0,0),r(-18*(1-t),0,-3*(1-t)),r(-18*(1-t),0,3*(1-t)),
                    r(-angle,0,-(1-t)),r(-angle,0,1-t),-8*(1-t),-12*(1-t),angle,angle);
            return new Sample(frame,-LEG*(float)Math.sin(radians),HIP-LEG+LEG*(float)Math.cos(radians),0,-90,0);
        }
        return new Sample(CombatCollapsePose.STANDING,0,HIP,0,-90,0);
    }
    public static Sample sample(float ticks, boolean armored) {
        Sample pose=sample(ticks);
        if(!armored)return pose;
        // Vanilla armor extends one model pixel beyond the skin. Keep it above the
        // mattress until the torso clears the edge, then settle onto the same floor pose.
        float clearance=.065f*(1-ease((ticks-73)/7));
        return new Sample(pose.frame(),pose.hipX(),pose.hipY()+clearance,pose.hipZ(),pose.yaw(),pose.headZ());
    }
    public static boolean hasArmor(net.minecraft.world.entity.LivingEntity entity) {
        for(var slot: new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET})
            if(entity.getItemBySlot(slot).getItem() instanceof net.minecraft.world.item.ArmorItem)return true;
        return false;
    }
    public static void apply(PlayerModel<?> model,Sample sample) {
        CombatCollapseModelPose.apply(model,sample.frame());
        // Lift the square head onto the actual pillow while keeping the back on the mattress.
        model.head.z+=sample.headZ();model.hat.copyFrom(model.head);
    }
    /** Owns the root BEFORE vanilla entity yaw, flip and player-model scale. */
    public static void root(PoseStack stack,PlayerModel<?> model,Sample sample) {
        apply(model,sample);
        float pitch=(float)Math.toRadians(sample.frame().pitch());
        float yaw=(float)Math.toRadians(180-sample.yaw());
        float hipZ=HIP*(float)Math.sin(pitch);
        stack.translate(sample.hipX()-Math.sin(yaw)*hipZ,sample.hipY()-HIP*Math.cos(pitch),sample.hipZ()-Math.cos(yaw)*hipZ);
        stack.mulPose(Axis.YP.rotationDegrees(180-sample.yaw()));
        stack.mulPose(Axis.XP.rotationDegrees(sample.frame().pitch()));
    }
}
