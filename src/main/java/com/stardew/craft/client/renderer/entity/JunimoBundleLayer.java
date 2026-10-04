package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.math.Axis;

/**
 * SDV parity: Renders the correct held item on the Junimo's right_item bone.
 * Bundle color is applied via MC's ItemColor system (see StardewCraftClient).
 */
@SuppressWarnings("null")
public class JunimoBundleLayer {
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

    private ItemStack heldItem(JunimoEntity entity) {
        return switch(entity.getHoldingType()) {
            case JunimoEntity.HOLDING_BUNDLE -> getBundleItem();
            case JunimoEntity.HOLDING_STAR -> getStarItem();
            case JunimoEntity.HOLDING_ORANGE -> getOrangeItem();
            case JunimoEntity.HOLDING_ITEM -> entity.getHeldItem();
            default -> ItemStack.EMPTY;
        };
    }

    public void render(JunimoEntity entity, com.stardew.craft.client.model.nativebb.BlockbenchFrame frame,
                       PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack item=heldItem(entity);
        if(item.isEmpty() || entity.getAlpha()<.5f)return;
        pose.pushPose();
        try {
            if(!frame.attach(pose,"right_item"))return;
            pose.translate(HELD_ITEM_OFFSET_X/16F,HELD_ITEM_OFFSET_Y/16F,HELD_ITEM_OFFSET_Z/16F);
            pose.mulPose(Axis.ZP.rotationDegrees(HELD_ITEM_ROTATION_Z));
            pose.mulPose(Axis.YP.rotationDegrees(HELD_ITEM_ROTATION_Y));
            pose.mulPose(Axis.XP.rotationDegrees(HELD_ITEM_ROTATION_X));
            pose.scale(HELD_ITEM_SCALE,HELD_ITEM_SCALE,HELD_ITEM_SCALE);
            if(entity.getHoldingType()==JunimoEntity.HOLDING_BUNDLE)currentRenderBundleColor=entity.getBundleColor();
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(item,
                    ItemDisplayContext.GROUND,light,overlay,pose,buffers,entity.level(),entity.getId());
        } finally {currentRenderBundleColor=0xFFFFFF;pose.popPose();}
    }
}
