package com.stardew.craft.client.npcnative;

import com.stardew.craft.npc.animation.SamActivity;
import net.minecraft.nbt.CompoundTag;

/** Sample the same server event for every observer, including observers joining mid-action. */
final class NativeSamSchedulePose {
    private NativeSamSchedulePose() {}

    static void apply(NativeNpcPose pose, NativeNpcPose reference, NativeNpcModel model, SamActivity action, CompoundTag event, double now, double idleTime) {
        double elapsed = Math.max(0,(now-event.getLong("start"))/20);
        double enter = (event.contains("enterTicks") ? event.getInt("enterTicks") : action.enterTicks())/20.0;
        boolean exiting = event.contains("exit");
        double exitTime = exiting ? Math.max(0,(now-event.getLong("exit"))/20) : 0;
        double sampleTime = exiting ? Math.max(0,(event.getLong("exit")-event.getLong("start"))/20.0) : elapsed;
        pose.reset();
        if (action.supported()) {
            if (exiting) {
                pose.apply(action.playClip(),Math.max(0,sampleTime-enter));
                pose.blend(action.exitClip(),exitTime,smooth(exitTime/.2));
            } else if (elapsed < enter) {
                pose.apply(action.enterClip(),elapsed);
            } else {
                pose.apply(action.enterClip(),enter);
                pose.blend(action.playClip(),elapsed-enter,smooth((elapsed-enter)/.25));
            }
            double standWeight = exiting ? smooth((exitTime-(event.contains("exitTicks") ? event.getInt("exitTicks") : action.exitTicks())/20.0+.25)/.25)
                    : 1-smooth(elapsed/.2);
            if (standWeight > 0) {
                reference.reset(); reference.apply("animation.sam.idle",idleTime);
                reference.addPosition("root",action == SamActivity.SLEEP ? -14 : 0,0,
                        action == SamActivity.SLEEP ? 10 : -13);
                if (action == SamActivity.SLEEP) reference.addRotation("root",0,90,0);
                pose.blendFrom(reference,standWeight);
            }
        } else {
            pose.apply("animation.sam.idle",idleTime);
            double weight = exiting ? 1-smooth(exitTime/((event.contains("exitTicks") ? event.getInt("exitTicks") : action.exitTicks())/20.0)) : smooth(elapsed/enter);
            pose.blend(action.playClip(),sampleTime,weight);
            String prop = switch(action) {
                case GUITAR -> "guitar"; case GAMEBOY -> "handheld"; case SWEEP -> "broom_prop";
                case SKATEBOARD -> "skate_scene"; default -> "";
            };
            if (!prop.isEmpty()) {
                if (action != SamActivity.SKATEBOARD && weight < 1) {
                    String hand = action == SamActivity.SWEEP ? "forearm_right" : "forearm_left";
                    reference.reset(); reference.apply(action.playClip(),sampleTime);
                    var grip = new org.joml.Matrix4f(reference.boneMatrix(hand)).invert()
                            .mul(new org.joml.Matrix4f(reference.boneMatrix(prop)));
                    pose.overrideWorldTransform(prop,new org.joml.Matrix4f(pose.boneMatrix(hand)).mul(grip));
                }
                double visible = exiting ? 1-smooth((exitTime-.5)/.3) : smooth(elapsed/.3);
                pose.setOpacity(prop,(float)visible);
            }
            if (action == SamActivity.GUITAR && (exiting || elapsed < enter))
                for (int i=1;i<=3;i++) pose.setOpacity("music_note_"+i,0);

        }
    }

    private static double smooth(double x) {
        x = com.stardew.craft.port.PortJava.clamp(x,0,1); return x*x*x*(10+x*(-15+6*x));
    }
}
