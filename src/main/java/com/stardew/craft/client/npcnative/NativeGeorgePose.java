package com.stardew.craft.client.npcnative;

import com.stardew.craft.npc.attention.NpcAttentionMotion;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Hip-led propulsion with shoulder/elbow contact solving after the body blend. */
public final class NativeGeorgePose {
    private NativeGeorgePose() {}
    /** Raise hands during the existing anticipation beat, before the chair starts turning. */
    public static boolean preparesTurn(double time,double yaw) {
        double length=NpcAttentionMotion.turnTime(yaw),end=NpcAttentionMotion.holdEnd(yaw);
        return length>0 && ((time>=0 && time<.30+length)
                || (time>=end && time<end+.30+length));
    }
    public static void attention(NativeNpcPose pose,NpcAttentionMotion.Sample s,double weight) {
        var neck=pose.boneMatrix("head");
        double worldPitch=Math.toDegrees(Math.atan2(neck.m12(),neck.m22()));
        double yaw=s.bodyYaw()*weight,r=Math.toRadians(yaw);
        pose.addRotation("root",0,yaw,0);
        pose.addPosition("root",-2*Math.sin(r),0,2-2*Math.cos(r));
        pose.addRotation("chest_breath",0,s.shoulderYaw()*weight,0);
        pose.addRotation("head",(s.headPitch()-worldPitch)*weight,(s.headYaw()+s.shoulderYaw())*weight,0);
    }
    public static void locomotion(NativeNpcPose pose,NativeWheelchairClock.Sample s) {
        pose.blend("animation.george.effort",s.left().phase(),s.weight());
        applyWheel(pose,"right",s.right(),s.casterRight(),s.casterSpinRight());
        applyWheel(pose,"left",s.left(),s.casterLeft(),s.casterSpinLeft());
    }
    private static void applyWheel(NativeNpcPose pose,String side,NativeWheelchairClock.Wheel w,double caster,double casterSpin) {
        solveArm(pose,side,w.phase(),w.weight());
        pose.addRotation("wheel_spin_"+side,w.angle(),0,0);
        pose.addRotation("caster_fork_"+side,0,caster,0);
        pose.addRotation("caster_spin_"+side,casterSpin,0,0);
    }

    /** Hand centre in chair space; the return lifts away from the moving handrim. */
    public static Vector3f handTarget(double phase,int sign) {
        double t=phase-Math.floor(phase);
        if(t<=.38) {
            double q=t/.38,theta=Math.toRadians(-10+45*q);
            return new Vector3f((float)(sign*10.3),(float)(7+5*Math.cos(theta)+1.3-.55*q),
                    (float)(2-5*Math.sin(theta)));
        }
        double q=Math.min(1,(t-.38)/.48),ease=q*q*(3-2*q);
        var a=handTarget(.38,sign);var b=handTarget(0,sign);
        return new Vector3f((float)(a.x+sign*.9*Math.sin(Math.PI*q)),
                (float)(a.y+(b.y-a.y)*ease+2.1*Math.sin(Math.PI*q)),(float)(a.z+(b.z-a.z)*ease));
    }

    private static void solveArm(NativeNpcPose pose,String side,double phase,double weight) {
        if(weight<=0)return;
        int sign=side.equals("right")?-1:1;
        // Blend the contact point, then solve the joints. Blending Euler rotations directly
        // sweeps the forearm through the armrest during the first few moving frames.
        var hand=pose.boneMatrix("forearm_"+side).transformPosition(new Vector3f(sign*5.5F,10.8F,1));
        double transfer=com.stardew.craft.port.PortJava.clamp((weight-.15)/.70,0,1);
        transfer=transfer*transfer*(3-2*transfer);
        hand.lerp(handTarget(phase,sign),(float)transfer);
        // Lift off the forearm support before reaching outward; reverse to settle.
        double lift=Math.sin(Math.PI*weight);
        hand.y+=(float)(3*lift);
        hand.z-=(float)(2*lift);
        new Matrix4f(pose.boneMatrix("body")).invert().transformPosition(hand);
        var shoulder=new Vector3f(sign*5.5F,20,1);var delta=new Vector3f(hand).sub(shoulder);
        // Opposite wheel phases share one torso. Keep a small elbow bend when that
        // torso is farther from this hand's rim contact, instead of locking the elbow
        // and cutting the forearm diagonally through the armrest.
        if(delta.lengthSquared()>9.1*9.1) {
            hand.y=shoulder.y-(float)Math.sqrt(Math.max(.01,9.1*9.1-delta.x*delta.x-delta.z*delta.z));
            delta.set(hand).sub(shoulder);
        }
        double length=com.stardew.craft.port.PortJava.clamp(delta.length(),.801,9.199);
        var direction=delta.normalize();hand.set(shoulder).fma((float)length,direction);
        double elbowTurn=Math.min(1,transfer*2);
        elbowTurn=elbowTurn*elbowTurn*(3-2*elbowTurn);
        var pole=new Vector3f((float)(sign*8*elbowTurn),(float)(-8*(1-elbowTurn)),0);
        pole.fma(-pole.dot(direction),direction).normalize();
        double along=(25-4.2*4.2+length*length)/(2*length),height=Math.sqrt(Math.max(0,25-along*along));
        var elbow=new Vector3f(shoulder).fma((float)along,direction).fma((float)height,pole);
        var upper=new Vector3f(elbow).sub(shoulder).normalize();var lower=new Vector3f(hand).sub(elbow).normalize();
        var x=new Vector3f(upper).cross(lower).normalize();var y=new Vector3f(upper).negate();var z=new Vector3f(x).cross(y);
        double rx=Math.toDegrees(Math.atan2(y.z,z.z)),ry=Math.toDegrees(Math.asin(com.stardew.craft.port.PortJava.clamp(-x.z,-1,1))),
                rz=Math.toDegrees(Math.atan2(x.y,x.x))-sign*35;
        double bend=Math.toDegrees(Math.acos(com.stardew.craft.port.PortJava.clamp(upper.dot(lower),-1,1)));
        pose.blendRotation("arm_"+side,rx,ry,rz,1);
        pose.blendRotation("forearm_"+side,bend,0,0,1);
        pose.blendRotation("elbow_cover_"+side,bend*.5,0,0,1);
    }
}
