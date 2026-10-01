package com.stardew.craft.client.combat;

/** Authored poses, in degrees/ticks. No render-frame clock or random limb motion. */
public final class CombatCollapsePose {
    public static final int FALL_TICKS = 24;
    public static final int RECOVER_TICKS = 44;
    public record Rotation(float x, float y, float z) {
        Rotation mix(Rotation to, float t) {
            return new Rotation(lerp(x,to.x,t),lerp(y,to.y,t),lerp(z,to.z,t));
        }
    }
    public record Frame(float pitch, float roll, float offset,
                        Rotation head, Rotation leftArm, Rotation rightArm,
                        Rotation leftLeg, Rotation rightLeg,
                        float leftElbow, float rightElbow, float leftKnee, float rightKnee) {
        Frame mix(Frame to, float t) {
            return new Frame(lerp(pitch,to.pitch,t),lerp(roll,to.roll,t),lerp(offset,to.offset,t),
                    head.mix(to.head,t),leftArm.mix(to.leftArm,t),rightArm.mix(to.rightArm,t),
                    leftLeg.mix(to.leftLeg,t),rightLeg.mix(to.rightLeg,t),
                    lerp(leftElbow,to.leftElbow,t),lerp(rightElbow,to.rightElbow,t),
                    lerp(leftKnee,to.leftKnee,t),lerp(rightKnee,to.rightKnee,t));
        }
    }
    private static Rotation r(float x,float y,float z) { return new Rotation(x,y,z); }
    private static final Rotation ZERO=r(0,0,0);
    public static final Frame STANDING=new Frame(0,0,0,ZERO,ZERO,ZERO,ZERO,ZERO,0,0,0,0);
    // Lose balance, reach for support, settle on the back with relaxed asymmetric limbs.
    private static final Frame SLUMP=new Frame(8,-4,-.08f,r(22,0,4),r(-20,0,-12),r(-28,0,12),r(-26,0,-4),r(-18,0,3),-20,-28,38,28);
    private static final Frame FALLING=new Frame(58,-6,-.38f,r(10,8,2),r(-15,0,-30),r(-22,0,25),r(-35,0,-7),r(-20,0,5),-28,-18,40,25);
    public static final Frame PRONE=new Frame(90,0,-.62f,r(-8,22,-5),r(3,5,-18),r(-5,-8,24),r(-12,0,-7),r(-4,0,5),-12,-22,16,6);
    // Roll toward the supporting arm; draw both feet in before rising.
    private static final Frame WAKE=new Frame(78,12,-.58f,r(20,12,0),r(70,0,-10),r(-10,0,15),r(-45,0,-7),r(-32,0,5),-12,-55,62,48);
    private static final Frame BRACE=new Frame(28,8,-.22f,r(12,5,0),r(28,0,-8),r(-25,0,10),r(-78,0,-8),r(-60,0,6),-5,-68,112,96);
    private static final Frame RISE=new Frame(8,-2,-.06f,r(10,0,0),r(-12,0,-8),r(-18,0,9),r(-28,0,-3),r(-18,0,2),-22,-28,38,26);
    private CombatCollapsePose() {}
    public static Frame fall(float ticks) {
        if(ticks<7) return blend(STANDING,SLUMP,ticks/7);
        if(ticks<17) return blend(SLUMP,FALLING,(ticks-7)/10);
        return blend(FALLING,PRONE,(ticks-17)/7);
    }
    public static Frame recover(float ticks) {
        if(ticks<10) return blend(PRONE,WAKE,ticks/10);
        if(ticks<24) return blend(WAKE,BRACE,(ticks-10)/14);
        if(ticks<37) return blend(BRACE,RISE,(ticks-24)/13);
        return blend(RISE,STANDING,(ticks-37)/7);
    }
    private static Frame blend(Frame a,Frame b,float t) {
        t=com.stardew.craft.port.PortJava.clamp(t,0,1); return a.mix(b,t*t*(3-2*t));
    }
    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }
}
