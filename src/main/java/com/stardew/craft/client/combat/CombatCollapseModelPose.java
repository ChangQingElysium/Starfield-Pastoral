package com.stardew.craft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Same pose and floor support for the real player and the skin-preserving rescue actor. */
public final class CombatCollapseModelPose {
    private CombatCollapseModelPose() {}
    public static CombatCollapsePose.Frame frame(LivingEntity entity,float partialTick) {
        if(entity instanceof AbstractClientPlayer player && CombatCollapseClientState.isCollapsing(player))
            return CombatCollapsePose.fall(CombatCollapseClientState.collapseTime(partialTick));
        if(entity instanceof EventPlayerActorEntity actor) {
            if(actor.isInHospitalBedScene()) return HospitalBedPose.sample(actor.hospitalBedTime(partialTick)).frame();
            if(actor.isCollapsed()) return CombatCollapsePose.PRONE;
            float recovery=actor.recoveryTime(partialTick);
            if(recovery>=0 && recovery<CombatCollapsePose.RECOVER_TICKS) return CombatCollapsePose.recover(recovery);
        }
        return null;
    }
    public static void apply(PlayerModel<?> model,CombatCollapsePose.Frame frame) {
        // These channels are exclusively owned during knockout/recovery; no walking or head tracking.
        model.crouching=false;
        model.head.resetPose();model.body.resetPose();
        model.leftArm.resetPose();model.rightArm.resetPose();
        model.leftLeg.resetPose();model.rightLeg.resetPose();
        rotate(model.head,frame.head());
        rotate(model.leftArm,frame.leftArm());rotate(model.rightArm,frame.rightArm());
        rotate(model.leftLeg,frame.leftLeg());rotate(model.rightLeg,frame.rightLeg());
        model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);
        if(model instanceof CollapsePlayerModel<?> articulated) articulated.bend(frame);
    }
    private static void rotate(ModelPart part,CombatCollapsePose.Rotation r) {
        part.setRotation((float)Math.toRadians(r.x()),(float)Math.toRadians(r.y()),(float)Math.toRadians(r.z()));
    }
    /** Call AFTER entity yaw, BEFORE LivingEntityRenderer's model-axis flip/scale. */
    public static void root(PoseStack stack,PlayerModel<?> model,CombatCollapsePose.Frame frame,float modelScale) {
        root(stack,model,frame,modelScale,null);
    }
    public static void root(PoseStack stack,PlayerModel<?> model,CombatCollapsePose.Frame frame,float modelScale,LivingEntity entity) {
        apply(model,frame);
        float lift=floorLift(model,frame,modelScale);
        if(entity!=null && model instanceof CollapsePlayerModel<?> articulated) {
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
                if(!(entity.getItemBySlot(slot).getItem() instanceof ArmorItem)) continue;
                var armor=articulated.armorSupport(slot==EquipmentSlot.LEGS);
                armor.setAllVisible(false);
                switch(slot) {
                    case HEAD -> {armor.head.visible=true;armor.hat.visible=true;}
                    case CHEST -> {armor.body.visible=true;armor.leftArm.visible=true;armor.rightArm.visible=true;}
                    case LEGS -> {armor.body.visible=true;armor.leftLeg.visible=true;armor.rightLeg.visible=true;}
                    case FEET -> {armor.leftLeg.visible=true;armor.rightLeg.visible=true;}
                    default -> {}
                }
                armor.follow(articulated,armor);
                lift=Math.max(lift,floorLift(armor,frame,modelScale));
            }
        }
        stack.translate(0,lift,frame.offset()*modelScale);
        stack.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
        stack.mulPose(Axis.ZP.rotationDegrees(frame.roll()));
    }
    public static float floorLift(HumanoidModel<?> model,CombatCollapsePose.Frame frame,float modelScale) {
        PoseStack support=new PoseStack();
        support.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
        support.mulPose(Axis.ZP.rotationDegrees(frame.roll()));
        support.scale(-modelScale,-modelScale,modelScale);
        support.translate(0,-1.501,0);
        var minimum=new MinimumY();
        // Actual rendered vertices include inflated hat/sleeves/pants and both skin widths.
        model.renderToBuffer(support,minimum,0,0,1,1,1,1); // PORT(1.20.1): 1.21 default color -1 (opaque white)
        float weight=com.stardew.craft.port.PortJava.clamp(frame.pitch()/13,0,1);
        return (-minimum.y+.012f)*weight;
    }
    private static final class MinimumY implements VertexConsumer {
        float y=Float.POSITIVE_INFINITY;
        // PORT(1.20.1): 1.20.1 VertexConsumer API; ModelPart passes float positions widened to double.
        public VertexConsumer vertex(double x,double y,double z) { this.y=Math.min(this.y,(float)y);return this; }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer uv(float u,float v) { return this; }
        public VertexConsumer overlayCoords(int u,int v) { return this; }
        public VertexConsumer uv2(int u,int v) { return this; }
        public VertexConsumer normal(float x,float y,float z) { return this; }
        public void endVertex() { }
        public void defaultColor(int r,int g,int b,int a) { }
        public void unsetDefaultColor() { }
    }
}
