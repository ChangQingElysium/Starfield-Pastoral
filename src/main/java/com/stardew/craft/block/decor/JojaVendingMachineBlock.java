package com.stardew.craft.block.decor;

import com.stardew.craft.network.payload.JojaVendingPurchasePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

@SuppressWarnings("null")
public class JojaVendingMachineBlock extends MapDecorStaticBlock {

    public JojaVendingMachineBlock(Properties properties) {
        super(properties, "stardewcraft:decor/common/joja_vending_machine");
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            openPurchaseDialog(mainPos);
        }
        return InteractionResult.SUCCESS;
    }

    private void openPurchaseDialog(BlockPos pos) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ClientHelper.openDialog(pos);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHelper {
        static void openDialog(BlockPos pos) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                    com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                        Component.translatable("stardewcraft.joja_vending.question"),
                        List.of(
                            Component.translatable("stardewcraft.joja_vending.yes"),
                            Component.translatable("stardewcraft.joja_vending.no")
                        ),
                        index -> {
                            if (index == 0) {
                                PacketDistributor.sendToServer(new JojaVendingPurchasePayload(pos));
                            }
                        },
                        -1
                    )
                )
            );
        }
    }
}
