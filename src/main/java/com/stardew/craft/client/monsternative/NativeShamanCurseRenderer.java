package com.stardew.craft.client.monsternative;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.projectile.ShamanCurseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.PortVertex;
/** Small native cuboid core, six-sided inverted cyan hull and four source tail samples. */
@SuppressWarnings({"null","removal"})
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeShamanCurseRenderer extends EntityRenderer<ShamanCurseEntity>{
    private static final ResourceLocation TEXTURE=new ResourceLocation("stardewcraft:textures/entity/monster_native/shaman_curse.png");
    private static volatile NativeNpcModel model;
    public NativeShamanCurseRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ModEntities.SHAMAN_CURSE.get(),NativeShamanCurseRenderer::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)r->{try(var reader=r.getResourceOrThrow(new ResourceLocation("stardewcraft:monster_native/shaman_curse.json")).openAsReader()){
        var m=new Gson().fromJson(reader,NativeNpcModel.class);if(m==null||m.version()!=1||m.quads().size()!=12||!TEXTURE.toString().equals(m.texture()))throw new IllegalArgumentException("Invalid Shaman curse model");r.getResourceOrThrow(TEXTURE);model=m;
    }catch(Exception ex){throw new IllegalStateException("Cannot load Shaman curse model",ex);}});}
    @Override protected boolean shouldShowName(ShamanCurseEntity e){return false;}@Override public ResourceLocation getTextureLocation(ShamanCurseEntity e){return TEXTURE;}
    public static void draw(PoseStack stack,MultiBufferSource buffers,float alpha){
        var m=model;if(m==null)return;
        // Both surfaces use alpha and back-face culling; the reversed hull is behind the opaque core.
        var c=buffers.getBuffer(NativeGhostRenderTypes.CURSE);
        for(var q:m.quads())for(var v:q.vertices())PortVertex.of(c).addVertex(stack.last().pose(),v[0],v[1],v[2]).setColor(255,255,255,(int)(255*alpha)).setUv(v[3],v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(stack.last(),q.normal()[0],q.normal()[1],q.normal()[2]).endVertex();
    }
    @Override public void render(ShamanCurseEntity e,float yaw,float p,PoseStack stack,MultiBufferSource buffers,int light){
        record Dot(Vec3 pos,float alpha){}var dots=new java.util.ArrayList<Dot>();dots.add(new Dot(e.position(),1));int index=0;for(var at:e.tail())dots.add(new Dot(at,Math.max(.1F,.65F-index++*.15F)));
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();dots.sort(java.util.Comparator.comparingDouble((Dot d)->-d.pos.distanceToSqr(camera)));
        for(var d:dots){var offset=d.pos.subtract(e.position());stack.pushPose();stack.translate(offset.x,offset.y+e.getBbHeight()/2,offset.z);stack.mulPose(Axis.YP.rotationDegrees((e.tickCount+p)*33.75F));stack.scale(1F/16,1F/16,1F/16);draw(stack,buffers,d.alpha);stack.popPose();}
        super.render(e,yaw,p,stack,buffers,light);
    }
}
