package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.client.hud.StardewHotbarHud;
import com.stardew.craft.port.PortGuiSprites;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swap native HUD sprites without replacing ItemStack rendering or GUI coordinates.
 *
 * <p>PORT(1.20.1): 1.21 swaps the {@code hud/*} sprite ids passed to {@code blitSprite} in {@code renderItemHotbar}
 * and {@code renderExperienceBar}, inserts the ornament after the hotbar background, replaces the experience bar
 * when a wide level gap is needed and replaces the separate {@code renderExperienceLevel} layer. 1.20.1 draws the
 * same elements from {@code widgets.png}/{@code icons.png} regions (and draws the level inside
 * {@code renderExperienceBar}). Each vanilla region is recognised by its UV rectangle and, while the seasonal theme
 * is active, drawn with the 1.21 sprite call (size, sub-region, blend state) instead; otherwise vanilla 1.20.1 runs.
 * 1.21 draws the hotbar group and XP bar with blending enabled, so replacements enable blend around the draw.
 */
@Mixin(Gui.class)
public abstract class StardewHotbarSpriteMixin {
    @Unique private static final ResourceLocation stardewcraft$WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    @Unique private static final ResourceLocation stardewcraft$ICONS = new ResourceLocation("textures/gui/icons.png");
    @Unique private boolean stardewcraft$wideExperienceBar;

    @Unique
    private static ResourceLocation stardewcraft$theme(String vanillaSprite) {
        ResourceLocation original = new ResourceLocation(vanillaSprite);
        ResourceLocation replacement = StardewHotbarHud.replaceSprite(original);
        return replacement == original ? null : replacement;
    }

    @Unique
    private static void stardewcraft$sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        PortGuiSprites.blitSprite(graphics, sprite, x, y, width, height);
        RenderSystem.disableBlend();
    }

    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void stardewcraft$seasonalHotbar(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v,
                                             int width, int height, Operation<Void> original) {
        ResourceLocation sprite = null;
        int drawHeight = height;
        if (stardewcraft$WIDGETS.equals(texture)) {
            if (u == 0 && v == 0 && width == 182 && height == 22) {
                sprite = stardewcraft$theme("hud/hotbar");
            } else if (u == 0 && v == 22 && width == 24 && height == 22) {
                sprite = stardewcraft$theme("hud/hotbar_selection");
                drawHeight = 23; // 1.21 draws the selection 24x23.
            } else if (u == 24 && v == 22 && width == 29 && height == 24) {
                sprite = stardewcraft$theme("hud/hotbar_offhand_left");
            } else if (u == 53 && v == 22 && width == 29 && height == 24) {
                sprite = stardewcraft$theme("hud/hotbar_offhand_right");
            }
        } else if (stardewcraft$ICONS.equals(texture)) {
            if (u == 0 && v == 94 && width == 18 && height == 18) {
                sprite = stardewcraft$theme("hud/hotbar_attack_indicator_background");
            } else if (u == 18 && width == 18 && v == 112 - height) {
                ResourceLocation progress = stardewcraft$theme("hud/hotbar_attack_indicator_progress");
                if (progress != null) {
                    // 1.21: blitSprite(progress, 18, 18, 0, 18 - l1, x, y, 18, l1) inside the blend-enabled section.
                    PortGuiSprites.blitSprite(graphics, progress, 18, 18, 0, 18 - height, x, y, 18, height);
                    return;
                }
            }
        }
        if (sprite == null) {
            original.call(graphics, texture, x, y, u, v, width, height);
        } else {
            stardewcraft$sprite(graphics, sprite, x, y, width, drawHeight);
        }
        if (stardewcraft$WIDGETS.equals(texture) && u == 0 && v == 0 && width == 182 && height == 22) {
            // 1.21 injects the ornament right after the hotbar background, inside the blended z -90 group;
            // renderOrnament itself returns unless the seasonal theme is active.
            RenderSystem.enableBlend();
            StardewHotbarHud.renderOrnament(graphics);
            RenderSystem.disableBlend();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void stardewcraft$wideLevelGap(GuiGraphics graphics, int x, CallbackInfo callback) {
        // 1.21 cancels renderExperienceBar here; the 1.20.1 method also draws the level, so only the bar is skipped.
        this.stardewcraft$wideExperienceBar = StardewHotbarHud.renderWideExperienceBar(graphics, x);
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void stardewcraft$seasonalExperience(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v,
                                                 int width, int height, Operation<Void> original) {
        if (this.stardewcraft$wideExperienceBar) return;
        if (stardewcraft$ICONS.equals(texture) && u == 0 && height == 5) {
            if (v == 64 && width == 182) {
                ResourceLocation sprite = stardewcraft$theme("hud/experience_bar_background");
                if (sprite != null) {
                    stardewcraft$sprite(graphics, sprite, x, y, 182, 5);
                    return;
                }
            } else if (v == 69) {
                ResourceLocation sprite = stardewcraft$theme("hud/experience_bar_progress");
                if (sprite != null) {
                    RenderSystem.enableBlend();
                    PortGuiSprites.blitSprite(graphics, sprite, 182, 5, 0, 0, x, y, width, 5);
                    RenderSystem.disableBlend();
                    return;
                }
            }
        }
        original.call(graphics, texture, x, y, u, v, width, height);
    }

    /** 1.21 {@code renderExperienceLevel} HEAD: the seasonal digits replace vanilla's outlined number. */
    @Inject(method = "renderExperienceBar", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V", ordinal = 1))
    private void stardewcraft$compactLevel(GuiGraphics graphics, int x, CallbackInfo callback) {
        if (StardewHotbarHud.renderExperienceLevel(graphics)) callback.cancel();
    }
}
