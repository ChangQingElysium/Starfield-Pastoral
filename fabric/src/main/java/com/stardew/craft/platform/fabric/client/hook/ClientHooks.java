package com.stardew.craft.platform.fabric.client.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents;
import com.stardew.craft.platform.fabric.client.model.BakedModels;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class ClientHooks {
    private static final ThreadLocal<Operation<Void>> SCREEN_RENDER = new ThreadLocal<>();
    private ClientHooks() {}
    public static BakedModel handleCameraTransforms(PoseStack pose, BakedModel model, ItemDisplayContext context, boolean left) {
        return BakedModels.applyTransform(model, context, pose, left);
    }
    public static ResourceLocation getArmorTexture(Entity entity, ItemStack armor, ArmorMaterial.Layer layer, boolean innerModel, EquipmentSlot slot) {
        // The project's armor items have no Neo Item#getArmorTexture override. Neo's default query
        // returns null and resolves this exact native layer texture, including the inner-model flag.
        return layer.texture(innerModel);
    }
    public static void drawScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawScreen(screen, graphics, mouseX, mouseY, partialTick, arguments -> {
            ((Screen) arguments[0]).renderWithTooltip((GuiGraphics) arguments[1],
                    (int) arguments[2], (int) arguments[3], (float) arguments[4]);
            return null;
        });
    }
    public static void drawScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            Operation<Void> original) {
        Objects.requireNonNull(original, "screen render operation");
        var previous = SCREEN_RENDER.get();
        SCREEN_RENDER.set(original);
        try {
            graphics.pose().pushPose();
            try { drawScreenInternal(screen, graphics, mouseX, mouseY, partialTick); }
            finally { graphics.pose().popPose(); }
        } finally {
            if (previous == null) SCREEN_RENDER.remove();
            else SCREEN_RENDER.set(previous);
        }
    }
    // Stable named target for the unmodified StardewGuiRenderMixin viewport/zoom/scissor wrapper.
    private static void drawScreenInternal(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var original = SCREEN_RENDER.get();
        if (original == null) throw new IllegalStateException("Screen draw has no scoped render operation");
        // The shared viewport wrapper may change mouse coordinates before entering this body.
        // Continue the actual GameRenderer operation here, retaining FAPI/optional-mod wrappers.
        FabricScreenEvents.render(FabricScreenEvents.bus(), screen, graphics, mouseX, mouseY, partialTick,
                () -> original.call(screen, graphics, mouseX, mouseY, partialTick));
    }
}
