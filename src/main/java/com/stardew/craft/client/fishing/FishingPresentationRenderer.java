package com.stardew.craft.client.fishing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishpond.ClientFishPondFishRenderer;
import com.stardew.craft.fishing.FishingPresentationPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** First-person fishing rig. Hand meshes use the player's live skin. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class FishingPresentationRenderer {
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var p=Minecraft.getInstance().player;
        if(p!=null&&FishingPresentationClient.eligible(p))event.setCanceled(true);
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // The world player renderer stays vanilla; this pass only replaces the local first-person hands.
        if(event.getCamera().getEntity() instanceof AbstractClientPlayer player&&FishingPresentationClient.firstPerson(player)) {
            var s=FishingPresentationClient.state(player);if(s==null)return;s.sample();
            var actor=FishingPresentationClient.actorTransform(player,partial);var pose=event.getPoseStack();pose.pushPose();
            Vec3 camera=event.getCamera().getPosition();pose.translate(-camera.x,-camera.y,-camera.z);
            int light=mc.getEntityRenderDispatcher().getPackedLightCoords(player,partial);
            render(player,s,actor,pose,mc.renderBuffers().bufferSource(),light,mc.options.hideGui);
            pose.popPose();
        }
    }
    private static boolean descendant(FishingRigPose pose,int bone,int root){for(int i=bone;i>=0;i=pose.rig.bones().get(i).parent())if(i==root)return true;return false;}
    private static void moveTackle(FishingRigPose pose,Vector3f target) {
        int root=pose.index("rod_stowed_tackle");Vector3f delta=target.sub(pose.anchor("rod_anchor_bobber_line"));
        for(int i=0;i<pose.world.length;i++)if(descendant(pose,i,root)){pose.world[i].m30(pose.world[i].m30()+delta.x).m31(pose.world[i].m31()+delta.y).m32(pose.world[i].m32()+delta.z);pose.skin[i].set(pose.world[i]).mul(new Matrix4f().set(pose.rig.bones().get(i).inverse()));}
    }
    /** Extend only the hook subtree. During the catch, keep its old position until handoff. */
    static void attachEquipment(FishingRigPose pose,Matrix4f actor,Matrix4f bobber,List<FishingTackleModels.Mounted> mounted,float attached) {
        if(mounted.isEmpty())return;
        int root=pose.index("rod_hook");var target=bobber.transformPosition(FishingTackleModels.hookPosition(mounted));
        if(attached>0) {
            var old=actor.transformPosition(pose.anchor("rod_hook"));var correction=old.sub(target).mul(attached);
            bobber.m30(bobber.m30()+correction.x).m31(bobber.m31()+correction.y).m32(bobber.m32()+correction.z);target.add(correction);
        }
        new Matrix4f(actor).invert().transformPosition(target);var delta=target.sub(pose.anchor("rod_hook"));
        for(int i=0;i<pose.world.length;i++)if(descendant(pose,i,root)) {
            pose.world[i].m30(pose.world[i].m30()+delta.x).m31(pose.world[i].m31()+delta.y).m32(pose.world[i].m32()+delta.z);
            pose.skin[i].set(pose.world[i]).mul(new Matrix4f().set(pose.rig.bones().get(i).inverse()));
        }
    }
    private static float stowed(FishingPresentationClient.State s,double t) {
        return s.phase==FishingPresentationPhase.STOP||s.phase==FishingPresentationPhase.CHARGE||s.idleClip()?1:
                s.phase==FishingPresentationPhase.CAST?1-FishingPresentationClient.smooth((float)((t-.35)/.2)):0;
    }
    private static boolean inWater(FishingPresentationClient.State s,net.minecraft.world.entity.Entity hook) {
        return s.phase==FishingPresentationPhase.BITE||s.phase==FishingPresentationPhase.HOOK||s.phase==FishingPresentationPhase.MINIGAME
                ||s.phase==FishingPresentationPhase.CAST&&hook!=null&&hook.isInWater();
    }
    /** Capture the equipped hook's actual depth before starting the existing fish-to-hand arc. */
    static Vec3 equippedHookOffset(AbstractClientPlayer player,FishingPresentationClient.State s,Matrix4f actor) {
        var selected=s.tackles.update(player.getMainHandItem());
        boolean shorts=com.stardew.craft.item.tool.FishingRodItem.hasTackle(player.getMainHandItem(),"stardewcraft:lucky_purple_shorts");
        var bobber=FishingBobberModels.get(shorts?39:BobberStyleClient.style(player.getUUID()));var mounted=FishingTackleModels.layout(selected,bobber);
        if(mounted.isEmpty())return FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_hook_tip")).subtract(FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_bobber_line")));
        Vec3 end=tackle(s,actor);boolean water=inWater(s,player.level().getEntity(s.hook));
        var matrix=FishingBobberModels.transform(bobber,new Matrix4f(actor).mul(s.pose.world[s.pose.index("rod_bobber")]),end,stowed(s,s.elapsed()),water?end.y-.14:null);
        var root=matrix.transformPosition(FishingTackleModels.hookPosition(mounted));
        var tip=FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_hook_tip")).subtract(FishingPresentationClient.world(actor,s.pose.anchor("rod_hook")));
        return new Vec3(root.x,root.y,root.z).add(tip).subtract(end);
    }
    public static Vec3 tackle(FishingPresentationClient.State s,Matrix4f actor) {return tackle(s,actor,s.elapsed());}
    static Vec3 tackle(FishingPresentationClient.State s,Matrix4f actor,double t) {
        Vec3 hanging=FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_line_exit")).add(0,-.6328,0);
        if(s.phase==FishingPresentationPhase.CATCH && !s.idleClip()) {
            if(t>=s.catchTime()+.3)return hanging;
            Vec3 fish=FishingPresentationClient.catchPosition(s,actor,t);
            Vec3 offset=FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_hook_tip")).subtract(FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_bobber_line")));
            Vec3 onHook=fish.subtract(offset);
            double detach=FishingPresentationClient.smooth((float)((t-s.catchTime())/.3));
            return onHook.lerp(hanging,detach);
        }
        if((s.phase==FishingPresentationPhase.FAIL||s.phase==FishingPresentationPhase.RETRIEVE)&&!s.idleClip()) {
            double start=s.phase==FishingPresentationPhase.FAIL?.66:.18,arrival=s.length()-.8;
            double q=FishingPresentationClient.smooth((float)((t-start)/(arrival-start))),u=1-q;double span=s.origin.distanceTo(hanging);
            Vec3 a=s.origin.add(0,.14,0),b=a.add(hanging.subtract(a).scale(.35)).add(0,Math.min(1.2,span*.12),0),c=hanging.add(0,.5,-.15);
            return a.scale(u*u*u).add(b.scale(3*u*u*q)).add(c.scale(3*u*q*q)).add(hanging.scale(q*q*q));
        }
        if(s.phase==FishingPresentationPhase.CAST&&(t<.35||s.hook<0))return FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_bobber_line"));
        if(s.phase==FishingPresentationPhase.CAST||s.phase==FishingPresentationPhase.BITE||s.phase==FishingPresentationPhase.HOOK||s.phase==FishingPresentationPhase.MINIGAME) {
            var mc=Minecraft.getInstance();var hook=mc.level==null?null:mc.level.getEntity(s.hook);
            Vec3 at=s.phase!=FishingPresentationPhase.MINIGAME&&hook!=null?hook.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false)):FishingPresentationClient.visualBobber(s);
            if(s.phase==FishingPresentationPhase.BITE)at=at.add(0,-.10*Math.sin(Math.PI*Math.min(t/.5,1)),0);
            return at.add(0,.14,0);
        }
        return FishingPresentationClient.world(actor,s.pose.anchor("rod_anchor_bobber_line"));
    }
    static void render(AbstractClientPlayer player,FishingPresentationClient.State s,Matrix4f actor,PoseStack stack,MultiBufferSource buffers,int light,boolean hideHands) {
        var pose=s.pose;Vec3 end=tackle(s,actor);var inverse=new Matrix4f(actor).invert();moveTackle(pose,inverse.transformPosition(new Vector3f((float)end.x,(float)end.y,(float)end.z)));
        boolean left=player.getMainArm()==HumanoidArm.LEFT;
        boolean shorts=com.stardew.craft.item.tool.FishingRodItem.hasTackle(player.getMainHandItem(),"stardewcraft:lucky_purple_shorts");
        int bobberStyle=shorts?39:BobberStyleClient.style(player.getUUID());
        int lineColor=com.stardew.craft.fishing.BobberStyles.lineColor(bobberStyle);
        var bobber=FishingBobberModels.get(bobberStyle);
        double t=s.elapsed();
        float stowed=stowed(s,t);
        var liveHook=player.level().getEntity(s.hook);boolean water=inWater(s,liveHook);
        var selectedTackles=s.tackles.update(player.getMainHandItem());var mounted=FishingTackleModels.layout(selectedTackles,bobber);
        double tackleTime=net.minecraft.Util.getMillis()/1000.0;float tackleRotation=s.tackles.rotation(tackleTime,water);
        var bobberMatrix=FishingBobberModels.transform(bobber,new Matrix4f(actor).mul(pose.world[pose.index("rod_bobber")]),end,stowed,water?end.y-.14:null);
        float attached=s.phase==FishingPresentationPhase.CATCH&&!s.idleClip()?1-FishingPresentationClient.smooth((float)((t-s.catchTime())/.3)):0;
        attachEquipment(pose,actor,bobberMatrix,mounted,attached);
        Vec3 mainLine=FishingBobberModels.anchor(bobberMatrix,bobber.mainLine());
        Vec3 leader=FishingBobberModels.anchor(bobberMatrix,bobber.leader());
        boolean slim=player.getSkin().model()==com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        stack.pushPose();stack.mulPose(actor);
        for(var face:pose.rig.faces()) {
            if(face.part().startsWith("rod_bobber"))continue;
            if(face.texture()==1&&!face.part().startsWith("rod_hook"))continue;
            boolean arm=face.part().startsWith("player_continuous_");
            if(face.part().startsWith("player_")&&!arm)continue;
            if(hideHands && !face.part().contains("bobber")&&!face.part().contains("hook"))continue;
            ResourceLocation texture=face.texture()==0?player.getSkin().texture():FishingRigAssets.resource("textures/entity/fishing_native/"+face.texture()+".png");
            var consumer=buffers.getBuffer(face.cull()?RenderType.entityCutout(texture):RenderType.entityCutoutNoCull(texture));
            float shift=0;
            if(arm&&slim) {
                String side=face.part().endsWith("right")?"right":"left";
                var bone=pose.world[pose.index(side+"_elbow_skin_6")];
                var palm=bone.transformPosition(new Vector3f(0,-1.5f,0));
                var target=pose.anchor(side.equals("right")?"rod_anchor_main_grip":s.catchVisible()?(s.fish?"caught_fish":"caught_item"):"rod_anchor_reel_handle");
                shift=Math.signum(target.sub(palm).dot(bone.transformDirection(new Vector3f(1,0,0))))*.5f;
            }
            Vector3f[] points=new Vector3f[4];for(int i=0;i<4;i++)points[i]=arm?pose.skinVertex(face.vertices().get(i),new Vector3f(),0,slim?.75f:1,shift):pose.vertex(face.vertices().get(i),new Vector3f());
            var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();
            // Reflection reverses winding. Reverse once, including the UVs; never duplicate hull faces.
            for(int k=0;k<4;k++){int i=left?3-k:k;var v=face.vertices().get(i);var p=points[i];consumer.addVertex(stack.last().pose(),p.x,p.y,p.z).setColor(255,255,255,255).setUv(arm?skinU(v.uv()[0],v.uv()[1],face.part().endsWith("right"),slim,left):v.uv()[0],arm?skinV(v.uv()[1],face.part().endsWith("right"),left):v.uv()[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z);}
            if(arm&&player.isModelPartShown((face.part().endsWith("right")!=left)?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE)) {
                // Inflate the shared bind surface before skinning; adjacent sleeve faces stay connected.
                // A skin overlay is an opaque cutout layer.  Alpha blending the
                // sleeve over the base arm makes the two layers flicker and lets
                // transparent texels darken the hand during the fishing pose.
                var layer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
                for(int k=0;k<4;k++){int i=left?3-k:k;var v=face.vertices().get(i);var p=pose.skinVertex(v,new Vector3f(),.18f,slim?.75f:1,shift);boolean right=face.part().endsWith("right");float u=skinU(v.uv()[0],v.uv()[1],right,slim,left),vv=skinV(v.uv()[1],right,left);if(right!=left)vv+=.25f;else u+=.25f;layer.addVertex(stack.last().pose(),p.x,p.y,p.z).setColor(255,255,255,255).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z);}
            }
            if(arm) {
                var chest=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                if(chest.getItem() instanceof net.minecraft.world.item.ArmorItem armor&&!FishingArmorVisibility.shouldHide(player,chest)) {
                    var extensions=net.minecraftforge.client.extensions.common.IClientItemExtensions.of(chest);
                    var layers=armor.getMaterial().value().layers();
                    for(int layerIndex=0;layerIndex<layers.size();layerIndex++) {
                        var material=layers.get(layerIndex);int color=extensions.getArmorLayerTintColor(chest,player,material,layerIndex,extensions.getDefaultDyeColor(chest));
                        if(color==0)continue;
                        var armorTexture=com.stardew.craft.port.net.neoforged.neoforge.client.ClientHooks.getArmorTexture(player,chest,material,false,net.minecraft.world.entity.EquipmentSlot.CHEST);
                        var armorConsumer=buffers.getBuffer(RenderType.armorCutoutNoCull(armorTexture));
                        armorFace(pose,face,stack,armorConsumer,light,normal,left,color);
                    }
                    var trim=chest.get(com.stardew.craft.port.net.minecraft.core.component.DataComponents.TRIM);
                    if(trim!=null){var atlas=Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.Sheets.ARMOR_TRIMS_SHEET);var sprite=atlas.getSprite(trim.outerTexture(armor.getMaterial()));armorFace(pose,face,stack,sprite.wrap(buffers.getBuffer(net.minecraft.client.renderer.Sheets.armorTrimsSheet(trim.pattern().value().decal()))),light,normal,left,-1);}
                    if(chest.hasFoil())armorFace(pose,face,stack,buffers.getBuffer(RenderType.armorEntityGlint()),light,normal,left,-1);
                }
            }
        }
        stack.popPose();
        if(!hideHands)FishingRodModels.render(FishingRodModels.select(player.getMainHandItem()),pose,actor,stack,buffers,light);
        int bobberLight=net.minecraft.client.renderer.LevelRenderer.getLightColor(player.level(),net.minecraft.core.BlockPos.containing(mainLine));
        FishingBobberModels.render(bobber,bobberMatrix,stack,buffers,bobberLight);
        FishingTackleModels.render(selectedTackles,bobber,bobberMatrix,stack,buffers,bobberLight,tackleRotation,tackleTime,
                s.phase==FishingPresentationPhase.BITE||s.phase==FishingPresentationPhase.HOOK||s.phase==FishingPresentationPhase.MINIGAME);
        Vec3 tip=FishingPresentationClient.world(actor,pose.anchor("rod_anchor_line_exit"));
        double distance=tip.distanceTo(mainLine),sag=Math.min(.65,.045+distance*.025)*(s.phase==FishingPresentationPhase.MINIGAME?(s.velocity>0?1.4:.3):1);
        Vec3 previous=tip;for(int i=1;i<=24;i++){double q=i/24.0;Vec3 p=tip.lerp(mainLine,q).add(0,-4*sag*q*(1-q),0);line(stack,buffers,light,previous,p,.0022f,lineColor);previous=p;}
        String[] route={"rod_shaft_root","rod_shaft_middle","rod_shaft_upper","rod_shaft_tip","rod_anchor_line_exit"};
        for(int i=0;i<route.length-1;i++) {
            Vector3f a=pose.anchor(route[i]);a=pose.world[pose.index(route[i])].transformPosition(new Vector3f(0,route[i].equals("rod_shaft_root")?2:0,-2.1f));
            Vector3f b=i==route.length-2?pose.anchor(route[i+1]):pose.world[pose.index(route[i+1])].transformPosition(new Vector3f(0,0,-2.1f));
            line(stack,buffers,light,FishingPresentationClient.world(actor,a),FishingPresentationClient.world(actor,b),.0018f,lineColor);
        }
        Vec3 hook=FishingPresentationClient.world(actor,pose.anchor("rod_hook"));
        if(!mounted.isEmpty()){var exit=FishingTackleModels.outlet(mounted.getLast(),bobberMatrix,tackleRotation,tackleTime);leader=new Vec3(exit.x,exit.y,exit.z);}
        line(stack,buffers,light,leader,hook,.0018f,lineColor);
        FishingBaitModels.render(s.bait.update(player.getMainHandItem()),FishingBaitModels.attachment(pose,actor),stack,buffers,bobberLight);
        if(s.catchVisible()) {
            Vec3 p=FishingPresentationClient.catchPosition(s,actor);stack.pushPose();stack.translate(p.x,p.y,p.z);
            String anchor=s.fish?"caught_fish":s.stack.getItem() instanceof BlockItem?"caught_block":"caught_item";
            // A reflected matrix is not a rotation. Conjugate the bone by the mirror before extracting it.
            Matrix4f rotation=new Matrix4f(actor).mul(pose.world[pose.index(anchor)]);
            if(left)rotation.scale(-1,1,1);
            Quaternionf orientation=rotation.getUnnormalizedRotation(new Quaternionf());stack.mulPose(orientation);
            float fade=Math.max(.001f,1-FishingPresentationClient.smooth((float)((s.elapsed()-s.hideTime()+.14)/.14)));
            if(s.fish&&ClientFishPondFishRenderer.available(s.stack)) {stack.scale(fade,fade,fade);ClientFishPondFishRenderer.renderCaughtFish(s.stack,stack,buffers,light);}
            else {
                boolean block=s.stack.getItem() instanceof BlockItem;float size=(block?4.5f:6f)/16;
                stack.scale(size*fade,size*fade,size*fade);stack.translate(block?-2.1/4.5:-1.65/6,block?-.5:-2.375/6,0);
                Minecraft.getInstance().getItemRenderer().renderStatic(s.stack,ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,stack,buffers,player.level(),player.getId());
            }
            stack.popPose();
        }
    }
    private static void armorFace(FishingRigPose pose,FishingRigAssets.Face face,PoseStack stack,com.mojang.blaze3d.vertex.VertexConsumer consumer,int light,Vector3f normal,boolean mirror,int color) {
        for(int k=0;k<4;k++){int i=mirror?3-k:k;var v=face.vertices().get(i);var p=pose.skinVertex(v,new Vector3f(),.65f,1,0);boolean right=face.part().endsWith("right");float u=v.uv()[0]+(right?0:.125f),vv=(v.uv()[1]-(right?0:.5f))*2;
            consumer.addVertex(stack.last().pose(),p.x,p.y,p.z).setColor(color).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z);}
    }
    private static float skinU(float u,float v,boolean right,boolean slim,boolean mirror) {
        float x=u*64-(right?40:32),y=v*64-(right?16:48);
        if(slim){if(y<4 && x>=8)x=7+(x-8)*.75f;else if(x>=12)x=11+(x-12)*.75f;else if(x>=8)x-=1;else if(x>=4)x=4+(x-4)*.75f;}
        return (x+((right!=mirror)?40:32))/64;
    }
    private static float skinV(float v,boolean right,boolean mirror){return mirror?v+(right?.5f:-.5f):v;}
    static void line(PoseStack stack,MultiBufferSource buffers,int light,Vec3 a,Vec3 b,float radius,int color) {
        Vec3 axis=b.subtract(a);if(axis.lengthSqr()<1e-10)return;Vec3 side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-8)side=axis.cross(new Vec3(1,0,0));side=side.normalize().scale(radius);Vec3 cross=axis.normalize().cross(side);
        var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(FishingRigAssets.resource("textures/entity/fishing_native/2.png")));
        for(Vec3 offset:new Vec3[]{side,cross})for(Vec3 p:new Vec3[]{a.add(offset),a.subtract(offset),b.subtract(offset),b.add(offset)})consumer.addVertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,255).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),0,1,0);
    }
}
