package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → Client: 打开邮件阅读界面。
 * <p>
 * 携带完整的邮件渲染数据，客户端无需再查注册表。
 */
@SuppressWarnings("null")
public record OpenMailPayload(
        String mailId,
        String text,
        String secretSantaNameKey,
        int background,
        String textColorName,       // "" = default
        List<ItemAttachment> items,
        int money,
        String learnedRecipe,       // "" = none
        String cookingOrCrafting,   // "" = none
        boolean hasQuest,
        int remainingMailCount      // 剩余未读邮件数（用于连续弹出）
) implements CustomPacketPayload {

    public static final Type<OpenMailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_mail"));

    public record ItemAttachment(String itemId, int count) {}

    private static final StreamCodec<ByteBuf, ItemAttachment> ITEM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ItemAttachment::itemId,
            ByteBufCodecs.INT, ItemAttachment::count,
            ItemAttachment::new
    );

    public static final StreamCodec<ByteBuf, OpenMailPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenMailPayload decode(ByteBuf buf) {
            String mailId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String text = ByteBufCodecs.STRING_UTF8.decode(buf);
            String secretSantaNameKey = ByteBufCodecs.STRING_UTF8.decode(buf);
            int background = buf.readInt();
            String textColorName = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<ItemAttachment> items = ITEM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            int money = buf.readInt();
            String learnedRecipe = ByteBufCodecs.STRING_UTF8.decode(buf);
            String cookingOrCrafting = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean hasQuest = buf.readBoolean();
            int remainingMailCount = buf.readInt();
            return new OpenMailPayload(mailId, text, secretSantaNameKey, background, textColorName,
                    items, money, learnedRecipe, cookingOrCrafting, hasQuest, remainingMailCount);
        }

        @Override
        public void encode(ByteBuf buf, OpenMailPayload p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.mailId);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.text);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.secretSantaNameKey);
            buf.writeInt(p.background);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.textColorName);
            ITEM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.items);
            buf.writeInt(p.money);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.learnedRecipe);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.cookingOrCrafting);
            buf.writeBoolean(p.hasQuest);
            buf.writeInt(p.remainingMailCount);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenMailPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.LetterViewerScreen(payload));
    }
}
