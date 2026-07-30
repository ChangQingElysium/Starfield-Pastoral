package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Renders {@link EventPlayerActorEntity} using its assigned player skin
 * and the vanilla {@link PlayerModel}.
 */
public class EventPlayerActorRenderer extends MobRenderer<EventPlayerActorEntity, PlayerModel<EventPlayerActorEntity>> {

    private static final ResourceLocation STEVE_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private final PlayerModel<EventPlayerActorEntity> wideModel;
    private final PlayerModel<EventPlayerActorEntity> slimModel;

    public EventPlayerActorRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // Armor layer
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        // Held items layer
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(@javax.annotation.Nonnull EventPlayerActorEntity entity) {
        AbstractClientPlayer skinSource = findSkinSource(entity);
        if (skinSource != null) {
            return skinSource.getSkin().texture();
        }
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection != null && entity.getSkinSourcePlayerId() != null) {
            var info = connection.getPlayerInfo(entity.getSkinSourcePlayerId());
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        if (mc.player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin().texture();
        }
        return STEVE_SKIN;
    }

    @Override
    public void render(@javax.annotation.Nonnull EventPlayerActorEntity entity, float entityYaw, float partialTicks,
                       @javax.annotation.Nonnull PoseStack poseStack, @javax.annotation.Nonnull MultiBufferSource buffer, int packedLight) {
        this.model = entity.isSlimSkinModel() ? slimModel : wideModel;
        poseStack.pushPose();
        if (entity.isCollapsed()) {
            // SDV's PlayerKilled event uses showFrame 5 while the farmer is
            // unconscious. Minecraft has no equivalent frame, so rotate the
            // full player model onto the floor until the authored "idle" cue.
            poseStack.translate(0.0D, 0.18D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        // Apply item-above-head arm pose before rendering
        if (entity.isHoldingItemAboveHead()) {
            PlayerModel<EventPlayerActorEntity> model = this.getModel();
            // Raise both arms straight up (90 degrees from horizontal = pointing at sky)
            model.leftArm.xRot = (float) Math.toRadians(-180);
            model.rightArm.xRot = (float) Math.toRadians(-180);
            model.leftArm.yRot = 0;
            model.rightArm.yRot = 0;
            model.leftArm.zRot = (float) Math.toRadians(10);  // slight outward angle
            model.rightArm.zRot = (float) Math.toRadians(-10);
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private static AbstractClientPlayer findSkinSource(EventPlayerActorEntity entity) {
        UUID playerId = entity.getSkinSourcePlayerId();
        if (playerId == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.getUUID().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    public static boolean isSlimSkin(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        AbstractClientPlayer player = findSkinSource(playerId);
        if (player != null) {
            return player.getSkin().model() == PlayerSkin.Model.SLIM;
        }
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection == null) {
            return false;
        }
        var info = connection.getPlayerInfo(playerId);
        return info != null && info.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    private static AbstractClientPlayer findSkinSource(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.getUUID().equals(playerId)) {
                return player;
            }
        }
        return null;
    }
}
