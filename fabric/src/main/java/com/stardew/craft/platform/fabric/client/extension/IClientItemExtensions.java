package com.stardew.craft.platform.fabric.client.extension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.mixin.client.extension.mixin.ItemRendererAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.Nullable;

/** Client extensions actually consumed by this project, backed by the native rendering hooks. */
@Environment(EnvType.CLIENT)
public interface IClientItemExtensions {
    IClientItemExtensions DEFAULT = new IClientItemExtensions() {};
    static IClientItemExtensions of(Item item) { return FabricClientExtensions.registry().item(item); }
    static IClientItemExtensions of(ItemStack stack) { return of(stack.getItem()); }
    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return ((ItemRendererAccess) Minecraft.getInstance().getItemRenderer()).stardewcraft$blockEntityRenderer();
    }
    default boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equip, float swing) { return false; }
    @Nullable default Font getFont(ItemStack stack, FontContext context) { return null; }
    default int getDefaultDyeColor(ItemStack stack) {
        return stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR)) : 0xFFFFFFFF;
    }
    default int getArmorLayerTintColor(ItemStack stack, LivingEntity entity, ArmorMaterial.Layer layer, int layerIndex, int fallbackColor) {
        return layer.dyeable() ? fallbackColor : 0xFFFFFFFF;
    }
    enum FontContext { ITEM_COUNT, TOOLTIP, SELECTED_ITEM_NAME }
}
