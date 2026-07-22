package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.mojang.math.Axis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * SDV parity: Renders the correct held item on the Junimo's right_item bone.
 * Bundle color is applied via MC's ItemColor system (see StardewCraftClient).
 */
@SuppressWarnings("null")
public class JunimoBundleLayer extends BlockAndItemGeoLayer<JunimoEntity> {
    // Final attachment tuning lives here. The model bone defines the actual
    // head-top anchor; these values only define how an item faces at that anchor.
    // Keep them centralized so visual tuning never touches harvesting logic.
    private static final float HELD_ITEM_OFFSET_X = 0.0F;
    private static final float HELD_ITEM_OFFSET_Y = 0.0F;
    private static final float HELD_ITEM_OFFSET_Z = 0.0F;
    private static final float HELD_ITEM_ROTATION_X = 0.0F;
    private static final float HELD_ITEM_ROTATION_Y = 180.0F;
    private static final float HELD_ITEM_ROTATION_Z = 0.0F;
    private static final float HELD_ITEM_SCALE = 1.0F;

    /**
     * Thread-local current bundle color for the ItemColor handler to read.
     * Set before rendering the bundle item, reset after.
     */
    public static volatile int currentRenderBundleColor = 0xFFFFFF;

    private ItemStack bundleItem;
    private ItemStack starItem;
    private ItemStack orangeItem;

    public JunimoBundleLayer(GeoRenderer<JunimoEntity> renderer) {
        super(renderer);
    }

    private ItemStack getBundleItem() {
        if (bundleItem == null) bundleItem = new ItemStack(ModItems.JUNIMO_BUNDLE.get());
        return bundleItem;
    }

    private ItemStack getStarItem() {
        if (starItem == null) starItem = new ItemStack(ModItems.JUNIMO_STAR.get());
        return starItem;
    }

    private ItemStack getOrangeItem() {
        if (orangeItem == null) {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "orange"));
            if (item == Items.AIR) {
                orangeItem = getBundleItem();
            } else {
                orangeItem = new ItemStack(item);
                QualityHelper.setQuality(orangeItem, QualityHelper.IRIDIUM);
                QualityHelper.ensureQualityModelData(orangeItem);
            }
        }
        return orangeItem;
    }

    @Override
    @Nullable
    protected ItemStack getStackForBone(GeoBone bone, JunimoEntity animatable) {
        if ("right_item".equals(bone.getName())) {
            int type = animatable.getHoldingType();
            if (type == JunimoEntity.HOLDING_BUNDLE) return getBundleItem();
            if (type == JunimoEntity.HOLDING_STAR)   return getStarItem();
            if (type == JunimoEntity.HOLDING_ORANGE) return getOrangeItem();
            if (type == JunimoEntity.HOLDING_ITEM)   return animatable.getHeldItem();
        }
        return null;
    }

    @Override
    @Nullable
    protected BlockState getBlockForBone(GeoBone bone, JunimoEntity animatable) {
        return null;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, JunimoEntity animatable) {
        return ItemDisplayContext.GROUND;
    }

    /** Bundle color is set via static field for ItemColor to pick up. */
    @Override
    public void renderForBone(PoseStack poseStack, JunimoEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        ItemStack stack = getStackForBone(bone, animatable);
        if (stack == null) return;

        // Don't render held item during fade (item renderer doesn't support per-entity alpha)
        if (animatable.getAlpha() < 0.5f) return;

        poseStack.pushPose();
        // The parent transform is already present here. Applying the complete
        // local transform places us at the body-owned head-top anchor.
        RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);
        poseStack.translate(HELD_ITEM_OFFSET_X / 16.0F,
                HELD_ITEM_OFFSET_Y / 16.0F, HELD_ITEM_OFFSET_Z / 16.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(HELD_ITEM_ROTATION_Z));
        poseStack.mulPose(Axis.YP.rotationDegrees(HELD_ITEM_ROTATION_Y));
        poseStack.mulPose(Axis.XP.rotationDegrees(HELD_ITEM_ROTATION_X));
        poseStack.scale(HELD_ITEM_SCALE, HELD_ITEM_SCALE, HELD_ITEM_SCALE);

        // SDV parity: set the bundle color for the ItemColor handler to read
        if (animatable.getHoldingType() == JunimoEntity.HOLDING_BUNDLE) {
            currentRenderBundleColor = animatable.getBundleColor();
        }

        renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);

        // Reset to white (no tint)
        currentRenderBundleColor = 0xFFFFFF;
        poseStack.popPose();
    }
}
