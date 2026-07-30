package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.client.combat.CombatCollapseClientState;
import com.stardew.craft.network.payload.PassOutPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Non-skippable screen shell for the combat-collapse client state.
 *
 * <p>Timing, ACK idempotence, movement locking and rendering state live in
 * {@link CombatCollapseClientState}; this screen only captures GUI input and draws its overlays.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PassOutOverlayScreen extends Screen {
    private final long transactionId;

    public PassOutOverlayScreen(long transactionId) {
        super(Component.empty());
        this.transactionId = transactionId;
    }

    /** Compatibility constructor for internal callers. Prefer {@link #show(PassOutPayload)}. */
    public PassOutOverlayScreen(PassOutPayload payload) {
        this(payload == null ? 0L : payload.transactionId());
    }

    public static void show(PassOutPayload payload) {
        CombatCollapseClientState.begin(payload);
    }

    public long transactionId() {
        return transactionId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CombatCollapseClientState.renderOverlay(graphics, width, height, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    /** Called by the destination-load handshake immediately before its ready ACK. */
    public static void destinationReady() {
        CombatCollapseClientState.destinationReady();
    }
}
