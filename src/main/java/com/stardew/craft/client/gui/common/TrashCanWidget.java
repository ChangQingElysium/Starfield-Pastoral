package com.stardew.craft.client.gui.common;

import com.mojang.math.Axis;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.inventory.InventoryTrashPolicy;
import com.stardew.craft.inventory.TrashCanService;
import com.stardew.craft.inventory.TrashCanTier;
import com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shared rendering and reclaim preview for every carried-item trash-can control. */
@OnlyIn(Dist.CLIENT)
public final class TrashCanWidget {
    private static final int NATIVE_WIDTH = 18;
    private static final int NATIVE_BODY_HEIGHT = 26;
    private static final int NATIVE_LID_PIVOT_X = 15;
    private static final int NATIVE_LID_PIVOT_Y = 10;
    private static final float MAX_LID_ROTATION = (float) Math.PI / 2.0F;
    private static final float LID_ROTATION_STEP = (float) Math.PI / 48.0F;

    private TrashCanWidget() {
    }

    /**
     * Placement-only data for the original trash-can control. Animation, tier art,
     * sound, tooltip and trash behavior stay in {@link Controller}.
     */
    public record Layout(int bodyX, int bodyY, float scale,
                         int hitX, int hitY, int hitWidth, int hitHeight) {
        public static Layout compact(int x, int y) {
            return new Layout(x, y + 8, 1.0F, x, y, NATIVE_WIDTH, NATIVE_BODY_HEIGHT + 8);
        }

        public static Layout original(int bodyX, int bodyY, float scale, int hitWidth, int hitHeight) {
            return new Layout(bodyX, bodyY, scale, bodyX, bodyY, hitWidth, hitHeight);
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= hitX && mouseX < hitX + hitWidth
                    && mouseY >= hitY && mouseY < hitY + hitHeight;
        }
    }

    public static void render(GuiGraphics graphics, int bodyX, int bodyY, float bodyScale,
                              int lidPivotX, int lidPivotY, float lidScale,
                              int lidX, int lidY, float lidRotation) {
        int level = ClientPlayerDataCache.getTrashCanLevel();
        CommonGuiTextures.drawGameMenuTrashBody(graphics, bodyX, bodyY, bodyScale, level);
        graphics.pose().pushPose();
        graphics.pose().translate(lidPivotX, lidPivotY, 0);
        graphics.pose().mulPose(Axis.ZP.rotation(lidRotation));
        graphics.pose().scale(lidScale, lidScale, 1.0f);
        CommonGuiTextures.drawGameMenuTrashLidAtCurrentPose(graphics, lidX, lidY, level);
        graphics.pose().popPose();
    }

    public static int previewRefund(ItemStack stack) {
        if (!TrashCanService.isReclaimEligible(stack)) {
            return 0;
        }
        SellQuote quote = ProfessionSellPriceService.quoteItemForProfessionNames(
                Set.copyOf(ClientPlayerDataCache.getProfessions()),
                ClientPlayerDataCache.getMailFlags(), ClientPlayerDataCache.getSpecialItems(),
                ClientPlayerDataCache.getStat("Book_Artifact") > 0,
                stack, SellSource.TRASH_CAN);
        TrashCanTier tier = TrashCanTier.forLevel(ClientPlayerDataCache.getTrashCanLevel());
        return TrashCanService.calculateRefund(quote.finalUnitPrice(), stack.getCount(), tier.reclaimPercent());
    }

    public static List<Component> tooltip(ItemStack carried) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("stardewcraft.game_menu.crafting.trash_can").withStyle(ChatFormatting.WHITE));
        if (carried == null || carried.isEmpty()) {
            return lines;
        }
        lines.add(carried.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        int refund = previewRefund(carried);
        if (refund > 0) {
            lines.add(Component.translatable("stardewcraft.inventory.trash_can.reclaim", refund)
                    .withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** Shared original-game trash-can control used by inventory and container screens. */
    public static final class Controller {
        private float lidRotation;

        public int xBeside(int panelX, int panelWidth, int screenWidth) {
            int right = panelX + panelWidth + 4;
            if (right + NATIVE_WIDTH <= screenWidth) {
                return right;
            }
            int left = panelX - 22;
            if (left >= 0) {
                return left;
            }
            return Math.max(0, Math.min(screenWidth - NATIVE_WIDTH, panelX + panelWidth - 22));
        }

        public boolean contains(int x, int y, double mouseX, double mouseY) {
            return contains(Layout.compact(x, y), mouseX, mouseY);
        }

        public boolean contains(Layout layout, double mouseX, double mouseY) {
            return layout.contains(mouseX, mouseY);
        }

        public void render(GuiGraphics graphics, int x, int y, double mouseX, double mouseY) {
            render(graphics, Layout.compact(x, y), mouseX, mouseY);
        }

        public void render(GuiGraphics graphics, Layout layout, double mouseX, double mouseY) {
            boolean hovered = contains(layout, mouseX, mouseY);
            Minecraft client = Minecraft.getInstance();
            if (hovered && lidRotation <= 0.0F) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TRASHCANLID.get(), 1.0f));
            }
            lidRotation = hovered
                    ? Math.min(lidRotation + LID_ROTATION_STEP, MAX_LID_ROTATION)
                    : Math.max(lidRotation - LID_ROTATION_STEP, 0.0F);

            int pivotX = layout.bodyX() + Math.round(NATIVE_LID_PIVOT_X * layout.scale());
            int pivotY = layout.bodyY() + Math.round(NATIVE_LID_PIVOT_Y * layout.scale());
            TrashCanWidget.render(graphics, layout.bodyX(), layout.bodyY(), layout.scale(),
                    pivotX, pivotY, layout.scale(), -16, -10, lidRotation);
        }

        public boolean click(AbstractContainerMenu menu, int x, int y,
                             double mouseX, double mouseY, int button) {
            return click(menu, Layout.compact(x, y), mouseX, mouseY, button);
        }

        public boolean click(AbstractContainerMenu menu, Layout layout,
                             double mouseX, double mouseY, int button) {
            if (button != 0 || !contains(layout, mouseX, mouseY)) {
                return false;
            }
            trashCarried(menu);
            return true;
        }

        public boolean deleteKey(AbstractContainerMenu menu, int keyCode) {
            if (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_DELETE || menu.getCarried().isEmpty()) {
                return false;
            }
            trashCarried(menu);
            return true;
        }

        public boolean renderTooltip(GuiGraphics graphics, Font font, AbstractContainerMenu menu, int x, int y,
                                     int mouseX, int mouseY) {
            return renderTooltip(graphics, font, menu, Layout.compact(x, y), mouseX, mouseY);
        }

        public boolean renderTooltip(GuiGraphics graphics, Font font, AbstractContainerMenu menu, Layout layout,
                                     int mouseX, int mouseY) {
            if (!contains(layout, mouseX, mouseY)) {
                return false;
            }
            graphics.renderTooltip(font, TrashCanWidget.tooltip(menu.getCarried()),
                    java.util.Optional.empty(), mouseX, mouseY);
            return true;
        }

        private void trashCarried(AbstractContainerMenu menu) {
            Minecraft client = Minecraft.getInstance();
            ItemStack carried = menu.getCarried();
            if (InventoryTrashPolicy.canTrash(carried)) {
                PacketDistributor.sendToServer(new CraftingMenuInventoryActionPayload(
                        CraftingMenuInventoryActionPayload.ACTION_TRASH_CARRIED, -1, false));
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TRASHCAN.get(), 1.0f));
            } else if (!carried.isEmpty()) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 1.0f));
            }
        }
    }
}
