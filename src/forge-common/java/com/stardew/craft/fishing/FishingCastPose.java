package com.stardew.craft.fishing;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Native cast_release at 0.350 s, in model units. Shared release marker for hook physics. */
public final class FishingCastPose {
    private FishingCastPose() {}
    public static Vector3f release(float pitch,boolean left) {
        var pivot=new Matrix4f().translation(.25f,12,.18f)
                .rotateZYX((float)Math.toRadians(-1.981968),(float)Math.toRadians(8.831705),(float)Math.toRadians(-8.891811)).translate(0,11,0);
        var aim=new Matrix4f(pivot).rotateX((float)Math.toRadians(-pitch)).mul(new Matrix4f(pivot).invert());
        var point=aim.transformPosition(new Vector3f(10.5316325f,53.1341485f,-14.1960593f)).div(16);
        if(left)point.x=-point.x;
        return point;
    }
}
