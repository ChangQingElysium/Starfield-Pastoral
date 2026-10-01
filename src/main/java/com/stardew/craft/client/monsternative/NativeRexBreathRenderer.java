package com.stardew.craft.client.monsternative;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.projectile.RexBreathEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
/** Source sprite10 is shared with SquidKid; BreathProjectile has no trail or reflected variants. */
@SuppressWarnings({"null","removal"})
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeRexBreathRenderer extends EntityRenderer<RexBreathEntity>{
 public NativeRexBreathRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
 @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ModEntities.REX_BREATH.get(),NativeRexBreathRenderer::new);}
 @Override protected boolean shouldShowName(RexBreathEntity e){return false;}
 @Override public ResourceLocation getTextureLocation(RexBreathEntity e){return new ResourceLocation("stardewcraft:textures/entity/monster_native/squid_fireball.png");}
 @Override public void render(RexBreathEntity e,float yaw,float p,PoseStack stack,MultiBufferSource b,int light){stack.pushPose();stack.translate(0,e.getBbHeight()/2,0);stack.scale(1.5F/16,1.5F/16,1.5F/16);NativeSquidFireballRenderer.draw(stack,b,e.opacity(p));stack.popPose();super.render(e,yaw,p,stack,b,light);}
}
