package com.stardew.craft.client.slingshot;

import com.mojang.blaze3d.vertex.*;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import org.joml.*;
import java.lang.Math;

import com.stardew.craft.port.PortVertex;
/** Animates only native Java item parts, inside Minecraft's ordinary held-item transform. */
@EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT)
public final class SlingshotRenderer {
    @SubscribeEvent public static void emptyHand(RenderHandEvent event){
        var player=Minecraft.getInstance().player;
        if(player!=null&&event.getItemStack().isEmpty()&&(player.getMainHandItem().getItem() instanceof SlingshotItem
                ||player.getOffhandItem().getItem() instanceof SlingshotItem))event.setCanceled(true);
    }
    public static ResourceLocation id(String s){return new ResourceLocation(StardewCraft.MODID,s);}
    public static void renderHeld(net.minecraft.world.entity.LivingEntity entity,ItemStack item,ItemDisplayContext context,boolean left,
                                  PoseStack stack,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
        float draw=SlingshotPresentation.draw(entity,item,partial);
        boolean drawing=SlingshotPresentation.isDrawing(entity,item);
        var slingshot=(SlingshotItem)item.getItem();
        String weaponId=slingshot.getWeaponId();
        float anchorX=slingshot.isMaster()?2.84f:4.25f;
        float anchorY=slingshot.isMaster()?11.25f:10.5f;
        var model=mc.getItemRenderer().getModel(item,entity.level(),entity,entity.getId());
        stack.pushPose();
        net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(stack,model,context,left);
        // ItemInHandLayer contributes -90 degrees around X. The raised vanilla bow
        // arm supplies the other quarter turn; a resting arm needs it on the item.
        if ((context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                && !drawing)
            stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        stack.translate(-.5,-.5,-.5);stack.scale(1/16f,1/16f,1/16f);
        var frame=new Matrix4f();var pouch=new Matrix4f().translation(0,0,7*draw);
        baked(item,weaponId,"frame",frame,stack,buffers,light);baked(item,weaponId,"pouch",pouch,stack,buffers,light);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(id("textures/item/weapon/"+weaponId+".png")));
        for(int side:new int[]{-1,1})band(new Vector3f(8+side*anchorX,anchorY,8.5f),new Vector3f(8+side,anchorY,8.5f+7*draw),stack,out,light);
        ItemStack ammo=SlingshotItem.ammunition(drawing?entity.getUseItem():item);
        if(drawing&&!ammo.isEmpty()){
            // Reuse the item's extruded sprite, above the pouch lip. FIXED applies
            // each item's display scale and previously buried small sprites inside the leather.
            stack.pushPose();stack.translate(8,anchorY+1.6f,8.05f+7*draw);
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(20));stack.scale(4,4,4);
            mc.getItemRenderer().renderStatic(ammo.copyWithCount(1),ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,stack,buffers,entity.level(),0);stack.popPose();
        }
        stack.popPose();
    }
    private static void baked(ItemStack item,String weaponId,String part,Matrix4f matrix,PoseStack stack,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();stack.pushPose();PortVertex.mulPose(stack, matrix);stack.scale(16,16,16);
        var model=mc.getModelManager().getModel(new ModelResourceLocation(id("item/"+weaponId+"_"+part),"standalone"));
        mc.getItemRenderer().renderModelLists(model,item,light,OverlayTexture.NO_OVERLAY,stack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));stack.popPose();
    }
    private static void band(Vector3f a,Vector3f b,PoseStack stack,VertexConsumer out,int light){
        var direction=new Vector3f(b).sub(a).normalize();var up=new Vector3f(0,.45f,0);var depth=new Vector3f(direction).cross(up).normalize().mul(.3f);
        Vector3f[] ring={new Vector3f(up).add(depth),new Vector3f(up).sub(depth),new Vector3f(up).negate().sub(depth),new Vector3f(up).negate().add(depth)};
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(stack,out,light,new Vector3f[]{new Vector3f(a).add(ring[i]),new Vector3f(b).add(ring[i]),new Vector3f(b).add(ring[j]),new Vector3f(a).add(ring[j])},30/64f,8/64f,33.5f/64f,9/64f);}
    }
    private static void quad(PoseStack stack,VertexConsumer out,int light,Vector3f[] points,float u,float v,float u1,float v1){
        var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();float[][] uv={{u,v},{u1,v},{u1,v1},{u,v1}};
        for(int i=0;i<4;i++)PortVertex.of(out).addVertex(stack.last().pose(),points[i].x,points[i].y,points[i].z).setColor(-1).setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z).endVertex();
    }
    @EventBusSubscriber(modid=StardewCraft.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent event) {
            for (var item : new Item[]{ModItems.SLINGSHOT.get(), ModItems.MASTER_SLINGSHOT.get()}) event.register(item, (graphics, font, stack, x, y) -> {
                var ammo = SlingshotItem.ammunition(stack);
                if (ammo.isEmpty()) return false;
                graphics.renderItemDecorations(font, ammo.copyWithCount(1), x, y, String.valueOf(ammo.getCount()));
                return true;
            });
        }
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(String weapon:new String[]{"slingshot","master_slingshot"})for(String s:new String[]{"frame","pouch"})e.register(new ModelResourceLocation(id("item/"+weapon+"_"+s),"standalone"));}
        @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(com.stardew.craft.entity.ModEntities.SLINGSHOT_PROJECTILE.get(),SlingshotProjectileRenderer::new);}
    }
}
