package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 服务端 → 客户端：打开矿井出口对话框。
 * 
 * 映射自 SDV MineShaft.checkAction case 115：
 *   createQuestionDialogue(" ", { "Leave", "Do" }, "ExitMine");
 */
public record OpenMineExitDialogPayload(int currentFloor) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<OpenMineExitDialogPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_mine_exit_dialog"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, OpenMineExitDialogPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.currentFloor()),
        buf -> new OpenMineExitDialogPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMineExitDialogPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenMineExitDialogPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // SDV GameLocation.performAction("ExitMine"):
        //   Response("Leave", "Mines_LeaveMine"), Response("Go", "Mines_GoUp"), Response("Do", "Mines_DoNothing")
        // 楼层0无需"上一层"选项
        boolean canGoUp = payload.currentFloor() > 0 && payload.currentFloor() <= 120;

        List<Component> responses = new java.util.ArrayList<>();
        responses.add(Component.translatable("stardewcraft.mine_exit.leave"));
        if (canGoUp) {
            responses.add(Component.translatable("stardewcraft.mine_exit.go_up"));
        }
        responses.add(Component.translatable("stardewcraft.mine_exit.do_nothing"));

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.mine_exit.question"),
                responses,
                index -> {
                    if (index == 0) {
                        // "Leave" → 退出矿井
                        PacketDistributor.sendToServer(new MineExitActionPayload(MineExitActionPayload.Action.EXIT_MINE));
                    } else if (canGoUp && index == 1) {
                        // "Go Up" → 返回上一层
                        PacketDistributor.sendToServer(new MineExitActionPayload(MineExitActionPayload.Action.GO_UP_FLOOR));
                    }
                    // last index → "Do nothing" → just close
                },
                -1
            )
        ));
    }
}
