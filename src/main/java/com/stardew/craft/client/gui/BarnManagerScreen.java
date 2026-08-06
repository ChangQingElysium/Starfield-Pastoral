package com.stardew.craft.client.gui;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.menu.BarnManagerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class BarnManagerScreen extends AbstractContainerScreen<BarnManagerMenu> {
    private Button actionButton;
    private Button demolishButton;
    private Button relocateButton;
    private Button confirmButton;
    private Button backButton;
    private ConfirmDialogMode confirmMode = ConfirmDialogMode.NONE;

    private enum ConfirmDialogMode {
        NONE,
        DEMOLISH,
        RELOCATE
    }

    public BarnManagerScreen(BarnManagerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 220;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        actionButton = this.addRenderableWidget(Button.builder(getActionLabel(), button -> {
                var mc = this.minecraft;
                if (mc != null && mc.gameMode != null) {
                    mc.gameMode.handleInventoryButtonClick(menu.containerId, BarnManagerMenu.ACTION_BUILD_OR_UPGRADE);
                }
            })
            .bounds(this.leftPos + 16, this.topPos + this.imageHeight - 28, 188, 20)
            .build());

        demolishButton = this.addRenderableWidget(Button.builder(getDemolishLabel(), button -> {
                enterConfirm(ConfirmDialogMode.DEMOLISH);
            })
            .bounds(this.leftPos + 16, this.topPos + this.imageHeight - 52, 92, 18)
            .build());

        relocateButton = this.addRenderableWidget(Button.builder(getRelocateLabel(), button -> {
                enterConfirm(ConfirmDialogMode.RELOCATE);
            })
            .bounds(this.leftPos + 112, this.topPos + this.imageHeight - 52, 92, 18)
            .build());

        confirmButton = this.addRenderableWidget(Button.builder(getConfirmButtonLabel(), button -> {
                var mc = this.minecraft;
                if (mc != null && mc.gameMode != null) {
                    executeConfirm();
                }
            })
            .bounds(this.leftPos + 16, this.topPos + this.imageHeight - 52, 92, 18)
            .build());

        backButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.stardew_craft.barn_manager.dialog.back"), button -> {
                exitConfirm();
            })
            .bounds(this.leftPos + 112, this.topPos + this.imageHeight - 52, 92, 18)
            .build());

        updateActionButtonState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateActionButtonState();
    }

    private void updateActionButtonState() {
        if (actionButton == null) {
            return;
        }
        actionButton.setMessage(getActionLabel());
        demolishButton.setMessage(getDemolishLabel());
        relocateButton.setMessage(getRelocateLabel());
        confirmButton.setMessage(getConfirmButtonLabel());

        actionButton.active = !menu.isAtMaxTier() && menu.canBuildOrUpgrade();
        boolean canManage = menu.hasExistingBuilding();
        demolishButton.active = canManage;
        relocateButton.active = canManage;

        boolean showingDialog = isConfirmMode();
        actionButton.visible = !showingDialog;
        demolishButton.visible = !showingDialog;
        relocateButton.visible = !showingDialog;
        confirmButton.visible = showingDialog;
        backButton.visible = showingDialog;
        confirmButton.active = showingDialog && canConfirmCurrentAction();
        backButton.active = showingDialog;

        if (!canManage) {
            exitConfirm();
        }
    }

    private Component getActionLabel() {
        if (menu.isRevalidating()) {
            return Component.translatable(
                    "gui.stardew_craft.animal_manager.revalidate");
        }
        if (menu.isAtMaxTier()) {
            return Component.translatable("gui.stardew_craft.barn_manager.max");
        }
        if (menu.getCurrentTier() <= 0) {
            return Component.translatable("gui.stardew_craft.barn_manager.build");
        }
        return Component.translatable("gui.stardew_craft.barn_manager.upgrade");
    }

    private Component getDemolishLabel() {
        return Component.translatable("gui.stardew_craft.barn_manager.demolish");
    }

    private Component getRelocateLabel() {
        return Component.translatable("gui.stardew_craft.barn_manager.relocate");
    }

    private Component getConfirmButtonLabel() {
        return switch (confirmMode) {
            case DEMOLISH -> Component.translatable("gui.stardew_craft.barn_manager.dialog.confirm_demolish");
            case RELOCATE -> Component.translatable("gui.stardew_craft.barn_manager.dialog.confirm_relocate");
            default -> Component.translatable("gui.stardew_craft.barn_manager.dialog.confirm");
        };
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xC0101010);

        if (isConfirmMode()) {
            int panelX = this.leftPos + 12;
            int panelY = this.topPos + 50;
            int panelW = this.imageWidth - 24;
            int panelH = 80;
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD0181818);
            graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF9C7A36);
            graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF9C7A36);
            graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF9C7A36);
            graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF9C7A36);
            return;
        }

        List<MissingEntry> missing = collectMissingEntries();
        int y = this.topPos + 78;
        int maxRows = 4;
        for (int i = 0; i < missing.size() && i < maxRows; i++) {
            MissingEntry entry = missing.get(i);
            int rowY = y + i * 16;
            CommonGuiTextures.drawItem(graphics, entry.icon(), this.leftPos + 16, rowY, 1.0f);
            graphics.drawString(this.font, GuiText.ellipsize(this.font, entry.text(), this.imageWidth - 52),
                this.leftPos + 36, rowY + 4, 0xFFE3E3E3, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component titleText = Component.translatable("container.stardew_craft.barn_manager");
        GuiText.drawCenteredClamped(graphics, this.font, titleText, this.imageWidth / 2, 8,
            this.imageWidth - 24, 0xFFFFD46A, false);

        Component stageText = Component.translatable("gui.stardew_craft.barn_manager.stage", stageLabel(menu.getCurrentTier()));
        graphics.drawString(this.font, GuiText.ellipsize(this.font, stageText, this.imageWidth - 32),
            16, 24, 0xFFFFFFFF, false);

        if (isConfirmMode()) {
            renderConfirmDialogLabels(graphics);
            return;
        }

        if (menu.isAtMaxTier()) {
            graphics.drawString(this.font, GuiText.ellipsize(this.font,
                Component.translatable("gui.stardew_craft.barn_manager.ready"), this.imageWidth - 32),
                16, 40, 0xFF8FEA8F, false);
            return;
        }

        Component targetText = Component.translatable("gui.stardew_craft.barn_manager.target", menu.getTargetTier());
        graphics.drawString(this.font, GuiText.ellipsize(this.font, targetText, this.imageWidth - 92),
            16, 40, 0xFFE0E0E0, false);

        boolean unenclosed = menu.isEnclosedRequired() && !menu.isEnclosed();
        if (unenclosed) {
            graphics.drawString(this.font, GuiText.ellipsize(this.font,
                Component.translatable("gui.stardew_craft.barn_manager.need.enclosed"), this.imageWidth - 32),
                16, 56, 0xFFE68B8B, false);
        } else {
            Component reqSizeText = Component.translatable(
                "gui.stardew_craft.barn_manager.req_size",
                menu.getReqInteriorBlocks()
            );
            graphics.drawString(this.font, GuiText.ellipsize(this.font, reqSizeText, this.imageWidth - 32),
                16, 50, 0xFFBFC6D1, false);

            Component curSizeText = Component.translatable(
                "gui.stardew_craft.barn_manager.cur_size",
                menu.getCurInteriorBlocks(), menu.getCurWidth(), menu.getCurLength(), menu.getCurHeight()
            );
            graphics.drawString(this.font, GuiText.ellipsize(this.font, curSizeText, this.imageWidth - 32),
                16, 60, 0xFFBFC6D1, false);

            Component reqFacilityText = Component.translatable(
                "gui.stardew_craft.barn_manager.req_facilities",
                menu.getReqFeedTrough(), menu.getReqAutoFeedTrough(), menu.getReqHayHopper(), menu.getReqIncubator()
            );
            graphics.drawString(this.font, GuiText.ellipsize(this.font, reqFacilityText, this.imageWidth - 32),
                16, 70, 0xFFBFC6D1, false);
        }

        if (menu.canBuildOrUpgrade()) {
            graphics.drawString(this.font, GuiText.ellipsize(this.font,
                Component.translatable("gui.stardew_craft.barn_manager.ready"), 44),
                162, 40, 0xFF8FEA8F, false);
        } else {
            graphics.drawString(this.font, GuiText.ellipsize(this.font,
                Component.translatable("gui.stardew_craft.barn_manager.missing"), 60),
                146, 40, 0xFFE68B8B, false);
        }
    }

    private void renderConfirmDialogLabels(GuiGraphics graphics) {
        Component title = switch (confirmMode) {
            case DEMOLISH -> Component.translatable("gui.stardew_craft.barn_manager.dialog.demolish.title");
            case RELOCATE -> Component.translatable("gui.stardew_craft.barn_manager.dialog.relocate.title");
            default -> Component.empty();
        };

        graphics.drawString(this.font, GuiText.ellipsize(this.font, title, this.imageWidth - 32),
            16, 56, 0xFFFFD46A, false);

        int lineY = 70;
        int lineW = this.imageWidth - 32;

        if (confirmMode == ConfirmDialogMode.DEMOLISH) {
            lineY = GuiText.drawWrapped(graphics, this.font,
                Component.translatable("gui.stardew_craft.barn_manager.dialog.demolish.line1"),
                16, lineY, lineW, 0xFFE3E3E3, false, 2);
            lineY = GuiText.drawWrapped(graphics, this.font,
                Component.translatable("gui.stardew_craft.barn_manager.dialog.demolish.line2"),
                16, lineY, lineW, 0xFFE3E3E3, false, 2);
            if (menu.getBoundAnimalCount() > 0) {
                GuiText.drawWrapped(graphics, this.font,
                    Component.translatable("gui.stardew_craft.barn_manager.dialog.demolish.blocked_animals", menu.getBoundAnimalCount()),
                    16, lineY, lineW, 0xFFE68B8B, false, 2);
            } else {
                GuiText.drawWrapped(graphics, this.font,
                    Component.translatable("gui.stardew_craft.barn_manager.dialog.demolish.line3"),
                    16, lineY, lineW, 0xFFE68B8B, false, 2);
            }
            return;
        }

        lineY = GuiText.drawWrapped(graphics, this.font,
            Component.translatable("gui.stardew_craft.barn_manager.dialog.relocate.line1"),
            16, lineY, lineW, 0xFFE3E3E3, false, 2);
        lineY = GuiText.drawWrapped(graphics, this.font,
            Component.translatable("gui.stardew_craft.barn_manager.dialog.relocate.line2"),
            16, lineY, lineW, 0xFFE3E3E3, false, 2);
        GuiText.drawWrapped(graphics, this.font,
            Component.translatable("gui.stardew_craft.barn_manager.dialog.relocate.line3"),
            16, lineY, lineW, 0xFFE68B8B, false, 2);
    }

    private void enterConfirm(ConfirmDialogMode mode) {
        if (!menu.hasExistingBuilding()) {
            return;
        }
        confirmMode = mode;
        updateActionButtonState();
    }

    private void exitConfirm() {
        if (confirmMode == ConfirmDialogMode.NONE) {
            return;
        }
        confirmMode = ConfirmDialogMode.NONE;
        updateActionButtonState();
    }

    private boolean isConfirmMode() {
        return confirmMode != ConfirmDialogMode.NONE;
    }

    private boolean canConfirmCurrentAction() {
        return confirmMode != ConfirmDialogMode.DEMOLISH || menu.getBoundAnimalCount() <= 0;
    }

    private void executeConfirm() {
        var mc = this.minecraft;
        if (mc == null || mc.gameMode == null) {
            return;
        }
        switch (confirmMode) {
            case DEMOLISH -> mc.gameMode.handleInventoryButtonClick(menu.containerId, BarnManagerMenu.ACTION_DEMOLISH);
            case RELOCATE -> mc.gameMode.handleInventoryButtonClick(menu.containerId, BarnManagerMenu.ACTION_RELOCATE);
            default -> {
            }
        }
        exitConfirm();
    }

    private Component stageLabel(int tier) {
        return switch (tier) {
            case 1 -> Component.translatable("gui.stardew_craft.barn_manager.stage.t1");
            case 2 -> Component.translatable("gui.stardew_craft.barn_manager.stage.t2");
            case 3 -> Component.translatable("gui.stardew_craft.barn_manager.stage.t3");
            default -> Component.translatable("gui.stardew_craft.barn_manager.stage.unformed");
        };
    }

    private List<MissingEntry> collectMissingEntries() {
        List<MissingEntry> missing = new ArrayList<>();

        if (menu.isEnclosedRequired() && !menu.isEnclosed()) {
            missing.add(new MissingEntry(new ItemStack(Items.BRICKS), Component.translatable("gui.stardew_craft.barn_manager.need.enclosed")));
            return missing;
        }

        addIfMissing(missing, menu.getReqFeedTrough(), menu.getCurFeedTrough(),
            new ItemStack(ModBlocks.FEED_TROUGH.get().asItem()),
            Component.translatable("gui.stardew_craft.barn_manager.need.feed_trough", menu.getReqFeedTrough() - menu.getCurFeedTrough()));

        addIfMissing(missing, menu.getReqAutoFeedTrough(), menu.getCurAutoFeedTrough(),
            new ItemStack(ModBlocks.AUTOFEED_TROUGH.get().asItem()),
            Component.translatable("gui.stardew_craft.barn_manager.need.auto_feed_trough", menu.getReqAutoFeedTrough() - menu.getCurAutoFeedTrough()));

        addIfMissing(missing, menu.getReqHayHopper(), menu.getCurHayHopper(),
            new ItemStack(ModBlocks.HAY_HOPPER.get().asItem()),
            Component.translatable("gui.stardew_craft.barn_manager.need.hay_hopper", menu.getReqHayHopper() - menu.getCurHayHopper()));

        addIfMissing(missing, menu.getReqIncubator(), menu.getCurIncubator(),
            new ItemStack(ModBlocks.INCUBATOR.get().asItem()),
            Component.translatable("gui.stardew_craft.barn_manager.need.incubator", menu.getReqIncubator() - menu.getCurIncubator()));

        if (!menu.hasInteriorSpace()) {
            missing.add(new MissingEntry(new ItemStack(Items.BARRIER), Component.translatable("gui.stardew_craft.barn_manager.need.interior")));
            return missing;
        }

        addIfMissing(missing, menu.getReqInteriorBlocks(), menu.getCurInteriorBlocks(),
            new ItemStack(Items.SCAFFOLDING),
            Component.translatable("gui.stardew_craft.barn_manager.need.interior_blocks", menu.getReqInteriorBlocks(), menu.getCurInteriorBlocks()));

        if (menu.isDoorRequired() && menu.getCurDoorCount() < menu.getReqDoorCount()) {
            missing.add(new MissingEntry(
                new ItemStack(Items.OAK_DOOR),
                Component.translatable("gui.stardew_craft.barn_manager.need.door", menu.getReqDoorCount(), menu.getCurDoorCount())
            ));
        }

        if (missing.isEmpty() && !menu.canBuildOrUpgrade()) {
            missing.add(new MissingEntry(new ItemStack(Items.PAPER), Component.translatable("gui.stardew_craft.barn_manager.need.unknown")));
        }
        return missing;
    }

    private static void addIfMissing(List<MissingEntry> missing, int required, int current, ItemStack icon, Component text) {
        if (current < required) {
            missing.add(new MissingEntry(icon, text));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private record MissingEntry(ItemStack icon, Component text) {
    }
}
