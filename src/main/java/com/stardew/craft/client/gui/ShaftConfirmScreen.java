package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import com.stardew.craft.network.ShaftJumpPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * 竖井确认屏幕 — SDV "There's a shaft leading down." 对话框。
 * 复用 {@link StardewConfirmDialogScreen} 以与游戏内其它 query 对话框风格一致。
 */
public final class ShaftConfirmScreen {

    private ShaftConfirmScreen() {}

    public static StardewConfirmDialogScreen create(BlockPos shaftPos) {
        return StardewConfirmDialogScreen.createQuestionDialog(
            StardewQuestionDialogSpec.of(
                Component.translatable("gui.stardewcraft.shaft.title"),
                List.of(
                    Component.translatable("gui.stardewcraft.shaft.jump_in"),
                    Component.translatable("gui.stardewcraft.shaft.do_nothing")
                ),
                index -> {
                    if (index == 0) {
                        PacketDistributor.sendToServer(new ShaftJumpPacket(shaftPos));
                    }
                },
                -1
            )
        );
    }
}
