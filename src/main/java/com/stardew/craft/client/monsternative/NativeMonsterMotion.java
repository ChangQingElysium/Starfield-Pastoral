package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;

/** Rotation-only reactions add to the authored living pose instead of snapping its moving joints to zero. */
public final class NativeMonsterMotion {
    private NativeMonsterMotion(){}
    public static void addRotationClip(NativeNpcModel model,NativeNpcPose pose,String name,double time){
        var clip=model.clips().get(name);float[] v=new float[3];
        double t=com.stardew.craft.port.PortJava.clamp(time,0,clip.length());
        for(var track:clip.tracks()){
            if(!track.channel().equals("rotation"))throw new IllegalArgumentException("Not a rotation overlay: "+name);
            NativeNpcPose.sample(track,t,v);pose.addRotation(model.bones().get(track.bone()).name(),v[0],v[1],v[2]);
        }
    }
}
