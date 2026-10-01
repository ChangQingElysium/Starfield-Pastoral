package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.combat.CombatCollapseModelPose;
import com.stardew.craft.client.combat.CollapsePlayerModel;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Renders {@link EventPlayerActorEntity} using its assigned player skin
 * and the vanilla {@link PlayerModel}.
 */
public class EventPlayerActorRenderer extends MobRenderer<EventPlayerActorEntity, PlayerModel<EventPlayerActorEntity>> {

    private static final ResourceLocation STEVE_SKIN =
            new ResourceLocation("textures/entity/player/wide/steve.png");
    private final PlayerModel<EventPlayerActorEntity> wideModel;
    private final PlayerModel<EventPlayerActorEntity> slimModel;
    private final PlayerModel<EventPlayerActorEntity> collapsedWide = new CollapsePlayerModel<>(false);
    private final PlayerModel<EventPlayerActorEntity> collapsedSlim = new CollapsePlayerModel<>(true);

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
            return com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(skinSource).texture();
        }
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection != null && entity.getSkinSourcePlayerId() != null) {
            var info = connection.getPlayerInfo(entity.getSkinSourcePlayerId());
            if (info != null) {
                return com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(info).texture();
            }
        }
        if (mc.player instanceof AbstractClientPlayer clientPlayer) {
            return com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(clientPlayer).texture();
        }
        return STEVE_SKIN;
    }

    @Override
    public void render(@javax.annotation.Nonnull EventPlayerActorEntity entity, float entityYaw, float partialTicks,
                       @javax.annotation.Nonnull PoseStack poseStack, @javax.annotation.Nonnull MultiBufferSource buffer, int packedLight) {
        // The hidden actor anchor is beside the bed. Do not cast a detached floor
        // shadow there while the visible body is still lying on the mattress.
        float bedsideShadow = entity.isInHospitalBedScene()
                ? com.stardew.craft.port.PortJava.clamp((entity.hospitalBedTime(partialTicks) - 80) / 24, 0, 1) : 1;
        this.shadowRadius = .5F * bedsideShadow * bedsideShadow * (3 - 2 * bedsideShadow);
        this.model = CombatCollapseModelPose.frame(entity, partialTicks) != null
                ? (entity.isSlimSkinModel() ? collapsedSlim : collapsedWide)
                : (entity.isSlimSkinModel() ? slimModel : wideModel);
        this.model.rightArmPose = entity.isEatingItem()
                ? HumanoidModel.ArmPose.ITEM
                : HumanoidModel.ArmPose.EMPTY;
        poseStack.pushPose();
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

    @Override
    protected void setupRotations(EventPlayerActorEntity entity, PoseStack stack, float bob,
                                  float yaw, float partialTick, float scale) {
        if (entity.isInHospitalBedScene()) {
            com.stardew.craft.client.combat.HospitalBedPose.root(stack,model,
                    com.stardew.craft.client.combat.HospitalBedPose.sample(entity.hospitalBedTime(partialTick),
                            com.stardew.craft.client.combat.HospitalBedPose.hasArmor(entity)));
            return;
        }
        super.setupRotations(entity, stack, bob, yaw, partialTick, scale);
        var frame = CombatCollapseModelPose.frame(entity, partialTick);
        if (frame != null) CombatCollapseModelPose.root(stack, model, frame, .9375F, entity);
    }

    @Override
    protected void scale(EventPlayerActorEntity entity, PoseStack stack, float partialTick) {
        // Match PlayerRenderer so the skin actor does not grow during the scene handoff.
        stack.scale(.9375F, .9375F, .9375F);
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
            return com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(player).model() == PlayerSkin.Model.SLIM;
        }
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection == null) {
            return false;
        }
        var info = connection.getPlayerInfo(playerId);
        return info != null && com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(info).model() == PlayerSkin.Model.SLIM;
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
