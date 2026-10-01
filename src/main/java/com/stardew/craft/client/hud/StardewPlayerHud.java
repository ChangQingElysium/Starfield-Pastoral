package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.item.tool.WateringCanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

/** Ten native-sized health/energy icons with precise, stationary side numbers. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class StardewPlayerHud {
    private static final ResourceLocation[] HEARTS = {
            texture("health_healthy"), texture("health_faded"), texture("health_pale")
    };
    private static final ResourceLocation[] LEAVES = {
            texture("energy_green"), texture("energy_yellow"), texture("energy_red")
    };
    private static final ResourceLocation HEART_EMPTY = texture("health_empty");
    private static final ResourceLocation LEAF_EMPTY = texture("energy_empty");
    private static final ResourceLocation SWEAT_DROP = texture("sweat_drop");
    private static final ResourceLocation RED_DROP = texture("red_drop");
    private static final ResourceLocation DIGITS = texture("digits");
    private static final int ICON_COUNT = 10;
    private static final int ICON_SIZE = 9;
    private static final int ICON_STEP = 8;
    private static final int ICON_Y = 3;
    private static final int TEXTURE_SIZE = 16;
    private static final int SOURCE_OFFSET = 3;
    private static final int MIN_NUMBER_WIDTH = 17;
    private static final int CURRENT_COLOR = 0xF3EADC;
    private static final int MAX_COLOR = 0xB1B3A5;
    private static final int SEPARATOR_COLOR = 0xFF6F746B;

    private static Player animationPlayer;
    private static Object animationLevel;
    private static int animationTick;
    private static int healthShakeTicks;
    private static int energyShakeTicks;
    private static int lastHealth = -1;
    private static float lastEnergy = Float.NaN;
    private static int sweatBurstTick = -1000;
    private static int redBurstTick = -1000;

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(
                StardewCraft.MODID, "textures/gui/player_vitals/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRenderHealthBar(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !shouldRenderCustomHUD(mc.player)) return;
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            event.setCanceled(true);
            mc.gui.leftHeight += 10;
        } else if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
            event.setCanceled(true);
            // The energy row remains present while riding, unlike vanilla hunger.
            mc.gui.rightHeight += 10;
        }
    }

    @SubscribeEvent
    public static void onRenderCustomHUD(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof StardewHudLayoutEditorScreen
                || !shouldRenderCustomHUD(mc.player)
                || !event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.PLAYER_BARS, graphics.guiWidth(), graphics.guiHeight());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        renderAt(graphics, placement.x(), placement.y(), placement.scale(),
                ClientPlayerDataCache.getEnergy(), ClientPlayerDataCache.getMaxEnergy(),
                ClientPlayerDataCache.isExhausted(), ClientPlayerDataCache.getHealth(),
                ClientPlayerDataCache.getMaxHealth(), true, partialTick);

        if (ClientPlayerDataCache.isExhausted()) {
            int mouseX = (int) (mc.mouseHandler.xpos() * graphics.guiWidth() / mc.getWindow().getWidth());
            int mouseY = (int) (mc.mouseHandler.ypos() * graphics.guiHeight() / mc.getWindow().getHeight());
            double localX = (mouseX - placement.x()) / placement.scale();
            double localY = (mouseY - placement.y()) / placement.scale();
            int badgeX = fieldWidth(ClientPlayerDataCache.getMaxHealth(),
                    ClientPlayerDataCache.getMaxEnergy()) + 97;
            if (localX >= badgeX && localX < badgeX + 4 && localY >= 5 && localY < 10) {
                // Tooltip coordinates are screen-space, after the HUD pose is restored.
                graphics.renderTooltip(mc.font, Component.translatable(
                        "stardewcraft.message.player.exhausted"), mouseX, mouseY);
            }
        }
    }

    public static boolean shouldRenderCustomHUD(Player player) {
        return !Minecraft.getInstance().options.hideGui && !player.isCreative() && !player.isSpectator()
                && (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != animationPlayer || mc.level != animationLevel) {
            resetAnimation();
            animationPlayer = mc.player;
            animationLevel = mc.level;
        }
        if (mc.player == null || mc.isPaused() || !shouldRenderCustomHUD(mc.player)) return;
        animationTick++;
        healthShakeTicks = Math.max(0, healthShakeTicks - 1);
        energyShakeTicks = Math.max(0, energyShakeTicks - 1);
        if (!ClientPlayerDataCache.isSynced()) return;
        int health = ClientPlayerDataCache.getHealth();
        float energy = ClientPlayerDataCache.getEnergy();
        if (lastHealth >= 0 && health < lastHealth) {
            healthShakeTicks = Math.max(healthShakeTicks, Math.min(20, (lastHealth - health) * 2));
        }
        if (energy < lastEnergy && energy <= 20.0F && isEnergyTool(mc.player.getMainHandItem().getItem())) {
            triggerEnergyShake();
        }
        // SDV's danger thresholds are absolute values, independent of icon colour ratios.
        if (health > 0 && health < 20 && animationTick % 20 == 0 && mc.screen == null) {
            healthShakeTicks = Math.max(healthShakeTicks, health <= 10 ? 10 : 5);
            if (health <= 10) redBurstTick = animationTick;
        }
        lastHealth = health;
        lastEnergy = energy;
    }

    @SubscribeEvent
    public static void onToolInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.isPaused()
                || !shouldRenderCustomHUD(mc.player) || event.isPickBlock()
                || ClientPlayerDataCache.getEnergy() > 20.0F) return;
        if (isEnergyTool(mc.player.getItemInHand(event.getHand()).getItem())) triggerEnergyShake();
    }

    private static boolean isEnergyTool(Item item) {
        return item instanceof net.minecraft.world.item.AxeItem
                || item instanceof net.minecraft.world.item.PickaxeItem
                || item instanceof net.minecraft.world.item.HoeItem
                || item instanceof com.stardew.craft.item.tool.HoeItem
                || item instanceof WateringCanItem
                || item instanceof net.minecraft.world.item.FishingRodItem;
    }

    private static void resetAnimation() {
        animationTick = healthShakeTicks = energyShakeTicks = 0;
        lastHealth = -1;
        lastEnergy = Float.NaN;
        sweatBurstTick = redBurstTick = -1000;
    }

    public static int baseWidth() {
        // Include the rightmost one-pixel numeral shadow in the draggable bounds.
        return 189 + 2 * fieldWidth(ClientPlayerDataCache.getMaxHealth(), ClientPlayerDataCache.getMaxEnergy());
    }

    static int fieldWidth(int maxHealth, int maxEnergy) {
        return Math.max(MIN_NUMBER_WIDTH, Math.max(numberWidth(Integer.toString(Math.max(1, maxHealth))),
                numberWidth(formatEnergy(Math.max(1, maxEnergy)))));
    }

    static String formatEnergy(float energy) {
        return String.format(Locale.ROOT, "%.1f", energy);
    }

    public static void renderPreview(GuiGraphics graphics, int x, int y, float scale) {
        int maxHealth = Math.max(1, ClientPlayerDataCache.getMaxHealth());
        int maxEnergy = Math.max(1, ClientPlayerDataCache.getMaxEnergy());
        renderAt(graphics, x, y, scale, maxEnergy * 0.88F, maxEnergy, false,
                Math.round(maxHealth * 0.93F), maxHealth, false, 0.0F);
    }

    private static void renderAt(GuiGraphics graphics, int x, int y, float scale, float energy,
                                 int maxEnergy, boolean exhausted, int health, int maxHealth,
                                 boolean animate, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            int field = fieldWidth(maxHealth, maxEnergy);
            int heartX = field + 3;
            int leafX = heartX + 173;
            float healthRatio = Mth.clamp(health / (float) Math.max(1, maxHealth), 0.0F, 1.0F);
            float energyRatio = Mth.clamp(energy / Math.max(1, maxEnergy), 0.0F, 1.0F);
            int healthDx = animate && healthShakeTicks > 0 ? animationTick % 3 - 1 : 0;
            int energyDx = animate && energyShakeTicks > 0 ? animationTick % 3 - 1 : 0;
            int energyDy = animate && energyShakeTicks > 0 ? (animationTick / 2) % 3 - 1 : 0;
            float pulse = animate && health > 0 && health < 20
                    ? 0.9F + 0.1F * (float) Math.sin((animationTick + partialTick) / (float) Math.max(1, health))
                    : 1.0F;
            renderIcons(graphics, HEART_EMPTY, HEARTS[colourStage(healthRatio)], heartX + healthDx,
                    ICON_Y, healthRatio, 8, false, pulse);
            renderIcons(graphics, LEAF_EMPTY, LEAVES[colourStage(energyRatio)], leafX + energyDx,
                    ICON_Y + energyDy, energyRatio, 9, true, 1.0F);
            if (exhausted) blitDrop(graphics, SWEAT_DROP, heartX + 94, 5, 1.0F);
            if (animate) {
                renderDrops(graphics, SWEAT_DROP, leafX, sweatBurstTick, 4, 0.03F, partialTick);
                renderDrops(graphics, RED_DROP, heartX + 2, redBurstTick, 3, 0.15F, partialTick);
            }
            // Numbers stay anchored and readable while icons/particles move.
            renderFraction(graphics, Integer.toString(health), Integer.toString(maxHealth), 0, field);
            renderFraction(graphics, formatEnergy(energy), Integer.toString(maxEnergy), field + 188, field);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static int colourStage(float ratio) {
        return ratio >= 0.5F ? 0 : ratio >= 0.2F ? 1 : 2;
    }

    static int fillRows(float fraction, int height) {
        return Mth.clamp(Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * height), 0, height);
    }

    private static void renderIcons(GuiGraphics graphics, ResourceLocation empty, ResourceLocation filled,
                                    int x, int y, float ratio, int filledHeight, boolean rightToLeft, float alpha) {
        for (int i = 0; i < ICON_COUNT; i++) {
            int iconX = x + (rightToLeft ? -i : i) * ICON_STEP;
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(empty, iconX, y, SOURCE_OFFSET, SOURCE_OFFSET,
                    ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            int rows = fillRows(ratio * ICON_COUNT - i, filledHeight);
            if (rows > 0) {
                int top = filledHeight - rows;
                graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                graphics.blit(filled, iconX, y + top, SOURCE_OFFSET, SOURCE_OFFSET + top,
                        ICON_SIZE, rows, TEXTURE_SIZE, TEXTURE_SIZE);
            }
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderFraction(GuiGraphics graphics, String current, String maximum, int x, int field) {
        renderNumber(graphics, current, x + field - numberWidth(current), 0, CURRENT_COLOR);
        graphics.fill(x, 6, x + field, 7, SEPARATOR_COLOR);
        renderNumber(graphics, maximum, x + field - numberWidth(maximum), 8, MAX_COLOR);
    }

    static int numberWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += text.charAt(i) == '.' ? 2 : 4;
        return Math.max(0, width - 1);
    }

    private static void renderNumber(GuiGraphics graphics, String text, int x, int y, int rgb) {
        int shadow = ((rgb >> 16 & 255) / 4 << 16) | ((rgb >> 8 & 255) / 4 << 8) | ((rgb & 255) / 4);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int glyph = ch == '.' ? 10 : ch == '-' ? 11 : ch - '0';
            int width = ch == '.' ? 1 : 3;
            if (glyph >= 0 && glyph < 12) {
                renderDigit(graphics, glyph, width, x + 1, y + 1, shadow);
                renderDigit(graphics, glyph, width, x, y, rgb);
            }
            x += width + 1;
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderDigit(GuiGraphics graphics, int glyph, int width, int x, int y, int rgb) {
        graphics.setColor((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F,
                (rgb & 255) / 255.0F, 1.0F);
        graphics.blit(DIGITS, x, y, glyph * 4, 0, width, 5, 48, 8);
    }

    private static void renderDrops(GuiGraphics graphics, ResourceLocation texture, int x, int burstTick,
                                    int count, float delay, float partialTick) {
        float elapsed = (animationTick - burstTick + partialTick) / 20.0F;
        if (elapsed < 0.0F || elapsed > 1.2F) return;
        for (int i = 0; i < count; i++) {
            float age = elapsed - i * delay;
            if (age < 0.0F || age >= 0.65F) continue;
            int dropX = Math.round(x + i - age * 7.0F);
            int dropY = Math.round(ICON_Y - 2 - age * 32.0F + age * age * 48.0F);
            blitDrop(graphics, texture, dropX, dropY, 1.0F - age / 0.65F);
        }
    }

    private static void blitDrop(GuiGraphics graphics, ResourceLocation texture, int x, int y, float alpha) {
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(texture, x, y, SOURCE_OFFSET, SOURCE_OFFSET, 4, 5, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void triggerHealthShake() {
        healthShakeTicks = Math.max(healthShakeTicks, 5);
    }

    public static void triggerEnergyShake() {
        energyShakeTicks = Math.max(energyShakeTicks, 20);
        if (animationTick - sweatBurstTick >= 8) sweatBurstTick = animationTick;
    }
}
