package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.overnight.ClientOvernightHandler;
import com.stardew.craft.network.payload.PassOutPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 晕倒惩罚摘要屏幕。
 * 显示倒地原因、金币损失、丢失物品列表。
 * 点击或 5 秒后自动关闭。
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class PassOutSummaryScreen extends Screen {

    private static final int AUTO_CLOSE_TICKS = 100; // 5s

    private final PassOutPayload payload;
    /** null = 独立模式；非 null = 链式模式（2AM 夜间结算） */
    @javax.annotation.Nullable
    private final java.util.List<net.minecraft.client.gui.screens.Screen> siblingScreens;
    private int ticksOpen;

    /** 独立模式构造（战斗死亡/体力耗尽） */
    public PassOutSummaryScreen(PassOutPayload payload) {
        this(payload, null);
    }

    /** 链式模式构造（2AM 晕倒 → 接入夜间结算画面链） */
    public PassOutSummaryScreen(PassOutPayload payload, @javax.annotation.Nullable java.util.List<net.minecraft.client.gui.screens.Screen> siblingScreens) {
        super(Component.empty());
        this.payload = payload;
        this.siblingScreens = siblingScreens;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
        if (ticksOpen >= AUTO_CLOSE_TICKS) {
            close();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 黑色背景
        graphics.fill(0, 0, width, height, 0xFF000000);

        int centerX = width / 2;
        int y = height / 2 - 60;
        int textMaxWidth = Math.max(1, width - 32);

        // 倒地原因
        Component reason = getReasonText();
        y = GuiText.drawWrappedCentered(graphics, font, reason, centerX, y, textMaxWidth, 0xFFDD4444, false, 2) + 8;

        // 金币损失
        if (payload.moneyLost() > 0) {
            Component moneyText = Component.translatable("stardewcraft.passout.money_lost", payload.moneyLost());
            GuiText.drawCenteredClamped(graphics, font, moneyText, centerX, y, textMaxWidth, 0xFFFFCC00, false);
            y += 16;
        }

        // 丢失物品
        List<ItemStack> lostItems = payload.lostItems();
        if (lostItems != null && !lostItems.isEmpty()) {
            y += 8;
            Component itemsHeader = Component.translatable("stardewcraft.passout.items_lost");
            GuiText.drawCenteredClamped(graphics, font, itemsHeader, centerX, y, textMaxWidth, 0xFFFF8888, false);
            y += 16;

            int startX = centerX - (lostItems.size() * 20) / 2;
            for (int i = 0; i < lostItems.size(); i++) {
                ItemStack stack = lostItems.get(i);
                int ix = startX + i * 20;
                CommonGuiTextures.drawItem(graphics, stack, ix, y, 1.0f);
                if (stack.getCount() > 1) {
                    CommonGuiTextures.drawItemDecorations(graphics, font, stack, ix, y, 1.0f);
                }
            }
            y += 24;
        }

        // 提示
        y += 16;
        Component hint = Component.translatable("stardewcraft.passout.continue");
        GuiText.drawCenteredClamped(graphics, font, hint, centerX, y, textMaxWidth, 0xFF888888, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        close();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        close();
        return true;
    }

    private void close() {
        com.stardew.craft.StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] PassOutSummaryScreen.close() chainMode={}, siblingCount={}",
            siblingScreens != null, siblingScreens != null ? siblingScreens.size() : -1);
        if (siblingScreens != null) {
            ClientOvernightHandler.openNextScreen("passout_summary");
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void onClose() {
        close();
    }

    private Component getReasonText() {
        return switch (payload.passOutType()) {
            case COMBAT_MINE -> Component.translatable("stardewcraft.passout.reason.mine");
            case COMBAT_OVERWORLD -> Component.translatable("stardewcraft.passout.reason.overworld");
            case COMBAT_DESERT_FESTIVAL -> Component.translatable("stardewcraft.passout.reason.mine");
            case EXHAUSTION_2AM -> Component.translatable("stardewcraft.passout.reason.2am");
            case EXHAUSTION_STAMINA -> Component.translatable("stardewcraft.passout.reason.stamina");
        };
    }
}
