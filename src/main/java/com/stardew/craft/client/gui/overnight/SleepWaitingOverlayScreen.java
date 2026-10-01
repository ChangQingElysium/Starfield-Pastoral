package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.payload.SleepCancelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/**
 * 多人睡眠等待界面。
 * <p>
 * 玩家确认睡觉后显示此界面，渐黑背景 + "等待其他玩家…" + 投票进度。
 * 按 ESC 或任意键可取消睡眠并撤回投票。
 * 当日推进完成（收到 OvernightSettlementPayload）时自动关闭。
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class SleepWaitingOverlayScreen extends Screen {

    private static final int FADE_IN_TICKS = 20; // 1s 渐入黑屏

    private int ticksOpen;
    private int votedCount;
    private int requiredCount;
    private boolean cancelled;

    public SleepWaitingOverlayScreen(int votedCount, int requiredCount) {
        super(Component.empty());
        this.votedCount = votedCount;
        this.requiredCount = requiredCount;
    }

    /**
     * 由 SleepVoteUpdatePayload 调用，更新投票进度显示。
     */
    public void updateProgress(int votedCount, int requiredCount) {
        this.votedCount = votedCount;
        this.requiredCount = requiredCount;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渐入黑色遮罩
        float alpha;
        if (ticksOpen < FADE_IN_TICKS) {
            alpha = (ticksOpen + partialTick) / FADE_IN_TICKS;
        } else {
            alpha = 1.0f;
        }
        int a = (int) (alpha * 255) << 24;
        graphics.fill(0, 0, width, height, a);

        // 黑屏后显示文字
        if (ticksOpen >= FADE_IN_TICKS) {
            int textMaxWidth = Math.max(1, width - 32);
            // "等待其他玩家…"
            Component waitingText = Component.translatable("stardewcraft.sleep.waiting");
            GuiText.drawWrappedCentered(graphics, font, waitingText, width / 2, height / 2 - 22, textMaxWidth, 0xFFFFFFFF, false, 2);

            // 投票进度 "X/Y 位玩家已准备好"
            Component progressText = Component.translatable("stardewcraft.sleep.waiting.progress",
                    votedCount, requiredCount);
            GuiText.drawCenteredClamped(graphics, font, progressText, width / 2, height / 2 + 4, textMaxWidth, 0xFFCCCCCC, false);

            // "按 ESC 取消"
            Component hintText = Component.translatable("stardewcraft.sleep.cancel.hint");
            GuiText.drawCenteredClamped(graphics, font, hintText, width / 2, height / 2 + 24, textMaxWidth, 0xFF888888, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC (256) 或任意键取消睡眠
        cancel();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击也可以取消
        cancel();
        return true;
    }

    private void cancel() {
        if (cancelled) return;
        cancelled = true;
        // 通知服务端撤回投票
        PacketDistributor.sendToServer(new SleepCancelPayload());
        Minecraft.getInstance().setScreen(null);
    }

    /**
     * 日推进完成后由客户端结算流程调用，关闭此界面（不发取消包）。
     */
    public void onDayAdvanced() {
        cancelled = true; // 防止关闭时发取消包
        Minecraft.getInstance().setScreen(null);
    }
}
