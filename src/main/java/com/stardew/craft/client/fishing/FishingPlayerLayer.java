package com.stardew.craft.client.fishing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

/** Third-person arms share the body's actual render transform, including scale and crouch offset. */
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FishingPlayerLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    public FishingPlayerLayer(PlayerRenderer renderer) {super(renderer);}

    @SubscribeEvent public static void register(EntityRenderersEvent.AddLayers event) {
        for(var skin:event.getSkins())if(event.getSkin(skin) instanceof PlayerRenderer renderer)
            renderer.addLayer(new FishingPlayerLayer(renderer));
    }

    /** Convert the native Y-up bind space to the same Y-down model space as PlayerModel. */
    static Matrix4f actor(Matrix4f modelToWorld,boolean left) {
        var actor=new Matrix4f(modelToWorld).translate(0,1.5f,0).scale(-1/16f,-1/16f,1/16f);
        if(left)actor.scale(-1,1,1);
        return actor;
    }

    @Override public void render(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,
                                 float limbSwing,float limbSwingAmount,float partial,float age,float headYaw,float headPitch) {
        if(!FishingPresentationClient.worldOwned(player)||FishingPresentationClient.firstPerson(player))return;
        var state=FishingPresentationClient.state(player);if(state==null)return;
        // setupAnim has already sampled this exact pose for the body. Do not advance it a second time.
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var modelToWorld=new Matrix4f().translation((float)camera.x,(float)camera.y,(float)camera.z).mul(stack.last().pose());
        var actor=actor(modelToWorld,player.getMainArm()==HumanoidArm.LEFT);
        state.playerModelTransform=new Matrix4f(FishingPresentationClient.baseActorTransform(player,partial)).invert().mul(actor);
        var worldStack=new PoseStack();worldStack.translate(-camera.x,-camera.y,-camera.z);
        FishingPresentationRenderer.render(player,state,actor,worldStack,buffers,light,false);
    }
}
