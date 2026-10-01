package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S → C：打开矮人王雕像 2 选 1 buff 菜单。
 * 对应 SDV ChooseFromIconsMenu.cs:118-145 (which == "dwarfStatue") — 服务端
 * 已根据当日确定性随机预选两个不同的图标 ID [0..4]。
 */
public record OpenDwarfStatueChoicePayload(int icon1, int icon2) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<OpenDwarfStatueChoicePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "open_dwarf_statue_choice"));

    public static final StreamCodec<ByteBuf, OpenDwarfStatueChoicePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, OpenDwarfStatueChoicePayload::icon1,
        ByteBufCodecs.VAR_INT, OpenDwarfStatueChoicePayload::icon2,
        OpenDwarfStatueChoicePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenDwarfStatueChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.stardew.craft.client.gui.mastery.DwarfStatueChoiceScreen.open(payload.icon1(), payload.icon2()));
    }
}
