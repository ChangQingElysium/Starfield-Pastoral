package com.stardew.craft.joja.client;

import com.stardew.craft.client.gui.LocalizedGuiAssets;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.joja.JojaConstants;
import com.stardew.craft.joja.network.CloseJojaCDMenuPayload;
import com.stardew.craft.joja.network.JojaPurchasePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side 1:1 replica of SDV JojaCDMenu.cs.
 *
 * <p>Geometry (SDV pixels, before ×4 scale):
 * <ul>
 *   <li>Menu base 320×144 → on-screen 1280×576</li>
 *   <li>5 click zones at (xPositionOnScreen+4, yPositionOnScreen+208) size 588×120;
 *       step x+=592, wrap to x=xPositionOnScreen+4 / y+=120 when x > xPositionOnScreen+1184.</li>
 *   <li>Completed checkmark source rect (0,144,16,16), drawn at (boxLeft+16, boxTop+16) scale 4.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class JojaCDScreen extends Screen {

    // SDV geometry, expressed in SDV pixel space (the ×4 scale is applied by render mapping s4())
    private static final int SDV_W = JojaConstants.CD_MENU_WIDTH;   // 1280
    private static final int SDV_H = JojaConstants.CD_MENU_HEIGHT;  // 576
    private static final int BOX_OFFSET_X = 4;
    private static final int BOX_OFFSET_Y = 208;
    private static final int BOX_W = 588;
    private static final int BOX_H = 120;
    private static final int BOX_STEP_X = 592;
    private static final int BOX_MAX_X = 1184;

    private final List<BoxSdv> boxes = new ArrayList<>();
    private StardewRenderMapping mapping;
    private float s4;
    private int menuX, menuY;

    private int completedMask;
    private boolean boughtSomething = false;
    private int exitTimerMs = -1;
    private long lastFrameNanos = 0;

    private JojaCDScreen(int completedMask) {
        super(Component.literal("JojaCDMenu"));
        this.completedMask = completedMask;
    }

    /** Called by {@link com.stardew.craft.joja.network.OpenJojaCDMenuPayload} handler. */
    public static void openFromServer(int completedMask, int money) {
        StardewTimeHud.updateClientMoney(money);
        Minecraft.getInstance().setScreen(new JojaCDScreen(completedMask));
    }

    /** Called by {@link com.stardew.craft.joja.network.JojaPurchaseResultPayload} handler. */
    public static void applyResult(int buttonIdx, int resultCode, int newMoney, int newCompletedMask) {
        StardewTimeHud.updateClientMoney(newMoney);
        if (Minecraft.getInstance().screen instanceof JojaCDScreen screen) {
            screen.completedMask = newCompletedMask;
            if (resultCode == JojaConstants.RESULT_OK) {
                screen.boughtSomething = true;
                screen.exitTimerMs = JojaConstants.CD_EXIT_TIMER_MS;
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.stardew.craft.sound.ModSounds.REWARD.get(), 1.0F));
            } else if (resultCode == JojaConstants.RESULT_NOT_ENOUGH_MONEY) {
                StardewTimeHud.triggerMoneyShake(JojaConstants.MONEY_SHAKE_MS);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.mapping = new StardewRenderMapping(this.width, this.height, (float) this.minecraft.getWindow().getGuiScale());
        this.s4 = mapping.s4();
        this.menuX = (this.width - mapping.ui(SDV_W)) / 2;
        this.menuY = (this.height - mapping.ui(SDV_H)) / 2;
        rebuildBoxes();
    }

    private void rebuildBoxes() {
        boxes.clear();
        int x = BOX_OFFSET_X;
        int y = BOX_OFFSET_Y;
        for (int i = 0; i < 5; i++) {
            boxes.add(new BoxSdv(i, x, y, BOX_W, BOX_H));
            x += BOX_STEP_X;
            if (x > BOX_MAX_X) {
                x = BOX_OFFSET_X;
                y += BOX_H;
            }
        }
    }

    /** mouseX/Y (MC GUI space) → SDV pixel relative to menu top-left. */
    private int mouseToSdvX(double mouseX) {
        // ui(sdvPx) = sdvPx / guiScale ; s4/4 = 1/guiScale ; sdvPx = (mouseX - menuX) * 4 / s4
        return (int) Math.round((mouseX - menuX) * 4.0 / s4);
    }
    private int mouseToSdvY(double mouseY) {
        return (int) Math.round((mouseY - menuY) * 4.0 / s4);
    }

    private boolean isComplete(int idx) {
        return (completedMask & (1 << idx)) != 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Exit timer (SDV: after boughtSomething, 1000ms then close)
        if (exitTimerMs >= 0) {
            long now = System.nanoTime();
            if (lastFrameNanos > 0) {
                exitTimerMs -= (int)((now - lastFrameNanos) / 1_000_000L);
            }
            lastFrameNanos = now;
            if (exitTimerMs <= 0) {
                this.onClose();
                return;
            }
        } else {
            lastFrameNanos = 0;
        }

        // Background fade (SDV: Color.Black * 0.75f)
        g.fill(0, 0, this.width, this.height, 0xC0000000);

        // Hover detection (before drawing so we can highlight)
        int sdvMx = mouseToSdvX(mouseX);
        int sdvMy = mouseToSdvY(mouseY);
        int hoveredButton = -1;
        if (exitTimerMs < 0) {
            for (BoxSdv b : boxes) {
                if (b.contains(sdvMx, sdvMy) && !isComplete(b.idx)) {
                    hoveredButton = b.idx;
                    break;
                }
            }
        }

        // Menu body: 320×144 source, scale by s4 around (menuX, menuY)
        g.pose().pushPose();
        g.pose().translate(menuX, menuY, 0);
        g.pose().scale(s4, s4, 1.0f);
        ResourceLocation jojaCdForm = LocalizedGuiAssets.texture("joja/joja_cd_form.png");
        g.blit(jojaCdForm, 0, 0, 0, 0, JojaConstants.FORM_W, JojaConstants.FORM_H,
            JojaConstants.TEX_W, JojaConstants.TEX_H);

        // Completed checkmarks: source rect (0,144,16,16) at (box.left+16, box.top+16) scale 4 in SDV space.
        // We're in scale(s4) context and SDV ×4 is baked into the texture — but the checkmark sprite
        // sits at source (0,144,16,16) outside the 320×144 form. So blit the 16×16 sprite at
        // (boxX/4 + 4, boxY/4 + 4) in texture-space (because we're already scaled by s4 which maps 1 tex px → 4 sdv px).
        for (BoxSdv b : boxes) {
            if (isComplete(b.idx)) {
                int texX = b.sdvX / 4 + 4;   // SDV +16 offset == tex +4 px
                int texY = b.sdvY / 4 + 4;
                g.blit(jojaCdForm, texX, texY, 0, 144, 16, 16, JojaConstants.TEX_W, JojaConstants.TEX_H);
            }
        }
        g.pose().popPose();

        // NOTE: intentionally NOT calling super.render — Screen.render() would re-apply
        // a blurred background over our form. We have no widgets to render anyway.

        // Hover tooltip (SDV: parseText @ dialogueFont, 384 width)
        if (hoveredButton >= 0) {
            Component tooltip = Component.translatable("gui.stardewcraft.joja_cd.hover." + hoveredButton);
            g.renderTooltip(this.font, this.font.split(tooltip, 240), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (exitTimerMs >= 0) return true;
        if (button == 0) {
            int sdvMx = mouseToSdvX(mouseX);
            int sdvMy = mouseToSdvY(mouseY);
            for (BoxSdv b : boxes) {
                if (b.contains(sdvMx, sdvMy) && !isComplete(b.idx)) {
                    PacketDistributor.sendToServer(new JojaPurchasePayload(b.idx));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new CloseJojaCDMenuPayload(boughtSomething));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record BoxSdv(int idx, int sdvX, int sdvY, int sdvW, int sdvH) {
        boolean contains(int mx, int my) {
            return mx >= sdvX && mx < sdvX + sdvW && my >= sdvY && my < sdvY + sdvH;
        }
    }
}
