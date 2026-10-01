package com.stardew.craft.port;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.mixin.PortContainerScreenAccessor;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;

/**
 * PORT(1.20.1): 1.21.1 screen background rendering for {@link PortScreen}s.
 *
 * <ul>
 *   <li>{@code Screen#renderBackground}: panorama when there is no level, the menu blur post effect over everything
 *       drawn so far (world + HUD), the 25%-black menu background texture, then {@code ScreenEvent.BackgroundRendered}.
 *       1.20.1 vanilla would instead draw a dark gradient (in world) or dirt (no level).</li>
 *   <li>The blur reproduces 1.21's {@code shaders/post/blur.json}: six linear-filtered separable box-blur passes at
 *       radius 5. 1.21 reads the radius from the "Menu Background Blur" option (default 5); 1.20.1 has no such
 *       option, so the default is used. The {@code RadiusMultiplier} keys in vanilla's json sit inside the
 *       {@code BlurDir} uniform object and are ignored by the parser, so every vanilla pass runs at multiplier 1;
 *       the port json therefore omits them.</li>
 *   <li>{@code AbstractContainerScreen#renderBackground}: {@code renderTransparentBackground} + {@code renderBg}
 *       (no blur, no BackgroundRendered event), exactly as 1.21.1.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortScreens {
    /** 1.21 {@code Screen.PANORAMA} (1.21 shares one instance with the title screen; see bulk-port-gaps). */
    private static final PanoramaRenderer PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private static final ResourceLocation PANORAMA_OVERLAY =
            new ResourceLocation("textures/gui/title/background/panorama_overlay.png");
    /** 1.21 {@code menu_background.png} / {@code inworld_menu_background.png}: both are uniform RGBA(0,0,0,64). */
    private static final ResourceLocation MENU_BACKGROUND =
            new ResourceLocation(StardewCraft.MODID, "textures/gui/port/menu_background.png");
    private static final ResourceLocation BLUR_LOCATION =
            new ResourceLocation(StardewCraft.MODID, "shaders/post/port_menu_blur.json");
    /** 1.21 {@code Options#menuBackgroundBlurriness} default. */
    private static final int MENU_BACKGROUND_BLURRINESS = 5;
    private static final int GL_NEAREST = 9728;
    private static final int GL_LINEAR = 9729;

    private static PostChain blurEffect;
    private static boolean blurLoaded;
    private static int blurWidth = -1;
    private static int blurHeight = -1;

    private PortScreens() {}

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        // 1.21 GameRenderer reloads the blur chain with the other post shaders.
        event.registerReloadListener((ResourceManagerReloadListener) manager -> closeBlur());
    }

    /** 1.21.1 {@code Screen#renderBackground} / {@code AbstractContainerScreen#renderBackground}. */
    public static void renderBackground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            renderTransparentBackground(screen, graphics);
            ((PortContainerScreenAccessor) container).port$renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            renderPanorama(screen, graphics, partialTick);
        }
        renderBlurredBackground(partialTick);
        renderMenuBackground(screen, graphics, 0, 0, screen.width, screen.height);
        MinecraftForge.EVENT_BUS.post(new ScreenEvent.BackgroundRendered(screen, graphics));
    }

    /** 1.21.1 {@code Screen#renderTransparentBackground}. */
    public static void renderTransparentBackground(Screen screen, GuiGraphics graphics) {
        graphics.fillGradient(0, 0, screen.width, screen.height, -1072689136, -804253680);
    }

    /** 1.21.1 {@code Screen#renderPanorama} → {@code PanoramaRenderer#render(graphics, width, height, 1, partialTick)}. */
    public static void renderPanorama(Screen screen, GuiGraphics graphics, float partialTick) {
        // NeoForge: a non-zero partial tick is replaced by the real-time frame delta.
        float delta = partialTick == 0.0F ? 0.0F : DeltaTracker.client().getRealtimeDeltaTicks();
        // 1.20.1 PanoramaRenderer#render(delta, fade) performs the same spin/bob update and cube-map draw.
        PANORAMA.render(delta, 1.0F);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(PANORAMA_OVERLAY, 0, 0, screen.width, screen.height, 0.0F, 0.0F, 16, 128, 16, 128);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    /** 1.21.1 {@code Screen#renderBlurredBackground} (NeoForge disables depth first). */
    public static void renderBlurredBackground(float partialTick) {
        RenderSystem.disableDepthTest();
        processBlurEffect(partialTick);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    /** 1.21.1 {@code Screen#renderMenuBackground(GuiGraphics, int, int, int, int)}. */
    public static void renderMenuBackground(Screen screen, GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        graphics.blit(MENU_BACKGROUND, x, y, 0, 0.0F, 0.0F, width, height, 32, 32);
        RenderSystem.disableBlend();
    }

    /** 1.21.1 {@code GameRenderer#processBlurEffect} + {@code PostChain#process} (per-pass linear filtering). */
    private static void processBlurEffect(float partialTick) {
        if (MENU_BACKGROUND_BLURRINESS < 1) return;
        PostChain chain = blurEffect();
        if (chain == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width != blurWidth || height != blurHeight) {
            chain.resize(width, height);
            blurWidth = width;
            blurHeight = height;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        RenderTarget swap = chain.getTempTarget("swap");
        // Every vanilla blur pass sets use_linear_filter; PostChain switches the filter once and restores NEAREST.
        main.setFilterMode(GL_LINEAR);
        swap.setFilterMode(GL_LINEAR);
        try {
            chain.process(partialTick);
        } finally {
            main.setFilterMode(GL_NEAREST);
            swap.setFilterMode(GL_NEAREST);
        }
    }

    private static PostChain blurEffect() {
        if (!blurLoaded) {
            blurLoaded = true;
            Minecraft minecraft = Minecraft.getInstance();
            try {
                blurEffect = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getMainRenderTarget(), BLUR_LOCATION);
                blurEffect.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
                blurWidth = minecraft.getWindow().getWidth();
                blurHeight = minecraft.getWindow().getHeight();
            } catch (IOException | JsonSyntaxException e) {
                // 1.21 GameRenderer#loadBlurEffect logs and leaves the effect unset.
                StardewCraft.LOGGER.warn("Failed to load shader: {}", BLUR_LOCATION, e);
                blurEffect = null;
            }
        }
        return blurEffect;
    }

    private static void closeBlur() {
        RenderSystem.recordRenderCall(() -> {
            if (blurEffect != null) blurEffect.close();
            blurEffect = null;
            blurLoaded = false;
            blurWidth = -1;
            blurHeight = -1;
        });
    }

    // ---- AbstractContainerScreen slot hooks (see PortContainerScreen) ----

    /** 1.21.1 {@code AbstractContainerScreen#renderSlot}; 1.20.1's private implementation is the same code. */
    public static void vanillaRenderSlot(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot) {
        ((PortContainerScreenAccessor) screen).port$renderSlot(graphics, slot);
    }

    /** 1.21.1 {@code AbstractContainerScreen#renderSlotHighlight(GuiGraphics, Slot, int, int, float)}. */
    public static void vanillaRenderSlotHighlight(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot) {
        if (slot.isHighlightable()) {
            AbstractContainerScreen.renderSlotHighlight(graphics, slot.x, slot.y, 0, screen.getSlotColor(slot.index));
        }
    }
}
