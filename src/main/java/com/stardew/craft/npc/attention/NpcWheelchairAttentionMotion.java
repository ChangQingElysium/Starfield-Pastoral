package com.stardew.craft.npc.attention;

/** George reorients the chair about its rear axle; seated legs never take turning steps. */
public final class NpcWheelchairAttentionMotion {
    private NpcWheelchairAttentionMotion() {}
    public static NpcAttentionMotion.Sample sample(double time,double yaw,double pitch,double distance) {
        var head=NpcAttentionMotion.sample(time,yaw,pitch,distance);
        double length=NpcAttentionMotion.turnTime(yaw),end=NpcAttentionMotion.holdEnd(yaw);
        double progress=length==0?0:NpcAttentionMotion.smooth((time-.30)/length)
                *(1-NpcAttentionMotion.smooth((time-end-.30)/length));
        double chair=NpcAttentionMotion.bodyTarget(yaw)*progress;
        double headWeight=NpcAttentionMotion.smooth((time-.08)/.38)
                *(1-NpcAttentionMotion.smooth((time-end-.10)/(.40+length)));
        double shoulder=com.stardew.craft.port.PortJava.clamp((yaw*headWeight-chair)*.08,-.8,.8);
        double angle=Math.toRadians(chair);
        var fixed=new NpcAttentionMotion.Foot(0,0,0,0,0);
        return new NpcAttentionMotion.Sample(chair,-2*Math.sin(angle),2-2*Math.cos(angle),shoulder,
                com.stardew.craft.port.PortJava.clamp(yaw*headWeight-chair-shoulder,-48,48),com.stardew.craft.port.PortJava.clamp(pitch,-15,15)*headWeight,
                0,0,head.blink(),fixed,fixed);
    }
}
