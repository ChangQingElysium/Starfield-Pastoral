package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Vector3f;

/** Soft Grub clips; the entity owns crawling displacement and metamorphosis timers. */
public final class NativeGrubMotion {
    private NativeGrubMotion() {}
    public static void sample(NativeNpcModel model, NativeNpcPose pose, String clip, double time) {
        pose.reset();pose.apply("animation.grub."+clip,time);ground(model,pose);
    }
    public static void ground(NativeNpcModel model,NativeNpcPose pose) {
        float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var matrices=pose.matrices();
        for(var quad:model.quads())for(var v:quad.vertices())
            low=Math.min(low,matrices[quad.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);
        if(low<.4F)pose.addPosition("root",0,.4F-low,0);
    }
    public static double smooth(double x) { x=com.stardew.craft.port.PortJava.clamp(x,0,1);return x*x*(3-2*x); }
}
