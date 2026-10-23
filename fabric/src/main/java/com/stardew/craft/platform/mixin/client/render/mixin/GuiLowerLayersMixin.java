package com.stardew.craft.platform.mixin.client.render.mixin;


import com.stardew.craft.platform.mixin.client.render.mixin.LayeredDrawAccess;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.platform.fabric.client.render.FabricGuiLayers;
import com.stardew.craft.platform.fabric.client.render.GuiHealthFrame;
import com.stardew.craft.platform.fabric.client.render.GuiHudLayout;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Splits only the vanilla lower composite callback; all other native layer callbacks are retained. */
@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class GuiLowerLayersMixin implements GuiHudLayout.Access {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RandomSource random;
    @Shadow @Final private SpectatorGui spectatorGui;
    @Shadow @Final private static ResourceLocation AIR_SPRITE;
    @Shadow @Final private static ResourceLocation AIR_BURSTING_SPRITE;
    @Shadow private int tickCount;
    @Shadow private int lastHealth;
    @Shadow private int displayHealth;
    @Shadow private long lastHealthTime;
    @Shadow private long healthBlinkTime;
    @Shadow protected abstract Player getCameraPlayer();
    @Shadow protected abstract LivingEntity getPlayerVehicleWithHealth();
    @Shadow protected abstract int getVehicleMaxHearts(LivingEntity vehicle);
    @Shadow protected abstract boolean isExperienceBarVisible();
    @Shadow protected abstract void renderItemHotbar(GuiGraphics graphics, DeltaTracker partialTick);
    @Shadow protected abstract void renderJumpMeter(PlayerRideableJumping vehicle, GuiGraphics graphics, int x);
    @Shadow protected abstract void renderExperienceBar(GuiGraphics graphics, int x);
    @Shadow protected abstract void renderHearts(GuiGraphics graphics, Player player, int x, int y, int spacing,
            int regeneration, float maximum, int health, int displayHealth, int absorption, boolean blinking);
    @Shadow protected abstract void renderFood(GuiGraphics graphics, Player player, int y, int x);
    @Shadow protected abstract void renderVehicleHealth(GuiGraphics graphics);
    @Shadow protected abstract void renderSelectedItemName(GuiGraphics graphics);
    @Shadow private static void renderArmor(GuiGraphics graphics, Player player, int y, int rows, int height, int x) {
        throw new AssertionError("shadow");
    }
    @Unique private final GuiHudLayout.State stardewcraft$heights = new GuiHudLayout.State();
    @Override @Unique public GuiHudLayout.State stardewcraft$hudLayout() { return stardewcraft$heights; }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/LayeredDraw;add(Lnet/minecraft/client/gui/LayeredDraw;Ljava/util/function/BooleanSupplier;)Lnet/minecraft/client/gui/LayeredDraw;"))
    private LayeredDraw stardewcraft$lowerLayers(LayeredDraw parent, LayeredDraw nativeLower, BooleanSupplier visible,
                                                Operation<LayeredDraw> original) {
        List<LayeredDraw.Layer> components = List.of(this::stardewcraft$hotbar, this::stardewcraft$jump,
                this::stardewcraft$experience, this::stardewcraft$health, this::stardewcraft$armor,
                this::stardewcraft$food, this::stardewcraft$vehicle, this::stardewcraft$air,
                this::stardewcraft$name, this::stardewcraft$spectatorTooltip);
        // Flatten into the existing parent, as NeoForge does: every real component advances by
        // LayeredDraw.Z_SEPARATION even while hidden or canceled. No nested-wrapper extra Z step.
        FabricGuiLayers.expandLowerLayers(((LayeredDrawAccess) nativeLower).stardewcraft$layers(), visible,
                () -> minecraft.gameMode.canHurtPlayer(), components).forEach(parent::add);
        return parent;
    }

    @Unique private void stardewcraft$hotbar(GuiGraphics graphics, DeltaTracker partialTick) {
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) spectatorGui.renderHotbar(graphics);
        else renderItemHotbar(graphics, partialTick);
    }

    @Unique private void stardewcraft$jump(GuiGraphics graphics, DeltaTracker partialTick) {
        PlayerRideableJumping vehicle = minecraft.player.jumpableVehicle();
        if (vehicle != null) renderJumpMeter(vehicle, graphics, graphics.guiWidth() / 2 - 91);
    }

    @Unique private void stardewcraft$experience(GuiGraphics graphics, DeltaTracker partialTick) {
        if (minecraft.player.jumpableVehicle() == null && isExperienceBarVisible())
            renderExperienceBar(graphics, graphics.guiWidth() / 2 - 91);
    }

    @Unique private void stardewcraft$health(GuiGraphics graphics, DeltaTracker partialTick) {
        Player player = getCameraPlayer();
        if (player == null) return;
        var frame = GuiHealthFrame.advance(Mth.ceil(player.getHealth()), lastHealth, displayHealth,
                lastHealthTime, healthBlinkTime, tickCount, player.invulnerableTime, Util.getMillis());
        lastHealth = frame.health();
        displayHealth = frame.displayHealth();
        lastHealthTime = frame.lastHealthTime();
        healthBlinkTime = frame.healthBlinkTime();
        random.setSeed((long) (tickCount * 312871));
        float maximum = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH),
                (float) Math.max(frame.displayHealth(), frame.health()));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int rows = Mth.ceil((maximum + absorption) / 2.0F / 10.0F);
        int spacing = Math.max(10 - (rows - 2), 3);
        int y = graphics.guiHeight() - stardewcraft$heights.left();
        stardewcraft$heights.addLeft((rows - 1) * spacing + 10);
        int regeneration = player.hasEffect(MobEffects.REGENERATION) ? tickCount % Mth.ceil(maximum + 5.0F) : -1;
        minecraft.getProfiler().push("health");
        renderHearts(graphics, player, graphics.guiWidth() / 2 - 91, y, spacing, regeneration,
                maximum, frame.health(), frame.displayHealth(), absorption, frame.blinking());
        minecraft.getProfiler().pop();
    }

    @Unique private void stardewcraft$armor(GuiGraphics graphics, DeltaTracker partialTick) {
        Player player = getCameraPlayer();
        if (player == null) return;
        minecraft.getProfiler().push("armor");
        renderArmor(graphics, player, graphics.guiHeight() - stardewcraft$heights.left() + 10, 1, 0, graphics.guiWidth() / 2 - 91);
        minecraft.getProfiler().pop();
        if (player.getArmorValue() > 0) stardewcraft$heights.addLeft(10);
    }

    @Unique private void stardewcraft$food(GuiGraphics graphics, DeltaTracker partialTick) {
        Player player = getCameraPlayer();
        if (player == null || getVehicleMaxHearts(getPlayerVehicleWithHealth()) != 0) return;
        minecraft.getProfiler().push("food");
        renderFood(graphics, player, graphics.guiHeight() - stardewcraft$heights.right(), graphics.guiWidth() / 2 + 91);
        stardewcraft$heights.addRight(10);
        minecraft.getProfiler().pop();
    }

    @Unique private void stardewcraft$vehicle(GuiGraphics graphics, DeltaTracker partialTick) {
        LivingEntity vehicle = getPlayerVehicleWithHealth();
        int hearts = getVehicleMaxHearts(vehicle);
        renderVehicleHealth(graphics);
        stardewcraft$heights.addVehicleRows(hearts);
    }

    @ModifyConstant(method = "renderVehicleHealth", constant = @Constant(intValue = 39))
    private int stardewcraft$vehicleBase(int original) { return stardewcraft$heights.right(); }

    @Unique private void stardewcraft$air(GuiGraphics graphics, DeltaTracker partialTick) {
        Player player = getCameraPlayer();
        if (player == null) return;
        minecraft.getProfiler().push("air");
        int maximum = player.getMaxAirSupply();
        int air = Math.min(player.getAirSupply(), maximum);
        if (player.isEyeInFluid(FluidTags.WATER) || air < maximum) {
            int y = graphics.guiHeight() - stardewcraft$heights.right();
            int full = Mth.ceil((double) (air - 2) * 10.0 / maximum);
            int bursting = Mth.ceil((double) air * 10.0 / maximum) - full;
            RenderSystem.enableBlend();
            for (int i = 0; i < full + bursting; i++) graphics.blitSprite(i < full ? AIR_SPRITE : AIR_BURSTING_SPRITE,
                    graphics.guiWidth() / 2 + 91 - i * 8 - 9, y, 9, 9);
            RenderSystem.disableBlend();
            stardewcraft$heights.addRight(10);
        }
        minecraft.getProfiler().pop();
    }

    @Unique private void stardewcraft$name(GuiGraphics graphics, DeltaTracker partialTick) {
        if (minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) renderSelectedItemName(graphics);
    }

    @ModifyConstant(method = "renderSelectedItemName", constant = @Constant(intValue = 59))
    private int stardewcraft$nameBase(int original) { return stardewcraft$heights.selectedItemHeight(); }

    @Unique private void stardewcraft$spectatorTooltip(GuiGraphics graphics, DeltaTracker partialTick) {
        if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR && minecraft.player.isSpectator()) spectatorGui.renderTooltip(graphics);
    }
}
