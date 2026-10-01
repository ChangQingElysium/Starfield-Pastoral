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
 * S→C：玩家拿着任务物品右键匹配的 NPC 时，弹出「确定要把 X 交给 Y 来完成 Z 吗？」对话框。
 * 优先级高于普通送礼 —— 在 NpcInteractionService 里 gift 前就拦截。
 */
@SuppressWarnings("null")
public record OpenQuestDeliveryConfirmPayload(
    String npcId,
    String questId,
    String itemDescriptionId,   // item.stardewcraft.carp
    String npcDisplayName,      // literal NPC name（NPC displayName 本身是 literal 名字）
    String questTitleJson       // quest 的 Title Component 序列化成 JSON（可能是 translatable）
) implements CustomPacketPayload {

    public static final Type<OpenQuestDeliveryConfirmPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_quest_delivery_confirm"));

    public static final StreamCodec<FriendlyByteBuf, OpenQuestDeliveryConfirmPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.npcId(), 64);
            buf.writeUtf(p.questId(), 64);
            buf.writeUtf(p.itemDescriptionId(), 128);
            buf.writeUtf(p.npcDisplayName(), 128);
            buf.writeUtf(p.questTitleJson(), 512);
        },
        buf -> new OpenQuestDeliveryConfirmPayload(
            buf.readUtf(64), buf.readUtf(64), buf.readUtf(128), buf.readUtf(128), buf.readUtf(512))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenQuestDeliveryConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenQuestDeliveryConfirmPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // 还原 quest 标题 Component（如果反序列化失败就用 literal 回退）
        Component questTitle;
        try {
            questTitle = Component.Serializer.fromJson(
                payload.questTitleJson(), mc.level.registryAccess());
            if (questTitle == null) questTitle = Component.literal(payload.questTitleJson());
        } catch (Exception e) {
            questTitle = Component.literal(payload.questTitleJson());
        }

        Component question = Component.translatable(
            "stardewcraft.quest.delivery.confirm",
            Component.translatable(payload.itemDescriptionId()),
            Component.literal(payload.npcDisplayName()),
            questTitle
        );

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                question,
                List.of(
                    Component.translatable("stardewcraft.quest.delivery.confirm.yes"),
                    Component.translatable("stardewcraft.quest.delivery.confirm.no")
                ),
                index -> {
                    if (index == 0) {
                        PacketDistributor.sendToServer(new ConfirmQuestDeliveryPayload(payload.npcId(), payload.questId()));
                    }
                },
                -1
            )
        ));
    }
}
