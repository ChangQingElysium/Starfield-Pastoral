package com.stardew.craft.client.farm;

import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewQuestionDialogSpec;
import com.stardew.craft.network.payload.FarmJoinResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FarmJoinInviteClient {
    private static final Queue<Invite> INVITES = new ArrayDeque<>();
    private static boolean dialogOpen;

    private FarmJoinInviteClient() {}

    public static void enqueue(UUID requesterUUID, String requesterName, String farmName) {
        INVITES.add(new Invite(requesterUUID, requesterName, farmName));
        openNext();
    }

    private static void openNext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || dialogOpen || INVITES.isEmpty()) {
            return;
        }

        Invite invite = INVITES.poll();
        dialogOpen = true;
        Component question = Component.translatable(
                "stardewcraft.farm.join.dialog.question",
                invite.requesterName(),
                invite.farmName());
        mc.setScreen(StardewConfirmDialogScreen.createQuestionDialog(
                StardewQuestionDialogSpec.of(
                        question,
                        List.of(
                                Component.translatable("stardewcraft.farm.join.dialog.accept"),
                                Component.translatable("stardewcraft.farm.join.dialog.reject")
                        ),
                        index -> {
                            PacketDistributor.sendToServer(new FarmJoinResponsePayload(invite.requesterUUID(), index == 0));
                            Minecraft.getInstance().tell(() -> {
                                dialogOpen = false;
                                openNext();
                            });
                        },
                        -1
                )
        ));
    }

    private record Invite(UUID requesterUUID, String requesterName, String farmName) {}
}